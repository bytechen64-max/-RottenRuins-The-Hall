package org.bytechen.rottenruins.overworld.difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.core.difficulty.DifficultyHelper;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

/**
 * 难度倍率的施加点：按当前难度给王庭生物挂最大生命值 / 攻击伤害的 {@link AttributeModifier}。
 * 不烤进 {@code createAttributes}（属性表全服只有一份，难度按存档走 → 存档会互相污染）；固定 UUID 使重复施加
 * 只覆盖不叠加、切难度可精准摘除；改 MAX_HEALTH 前后按比例还原血量（原版只向上钳，满血怪会显示成被打残）。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HallDifficultyAttributes {

    public static final UUID HEALTH_MODIFIER_ID =
            UUID.fromString("8f2a5c41-6d3e-4b97-a1c8-5e40f7b93d12");

    public static final UUID DAMAGE_MODIFIER_ID =
            UUID.fromString("1c7b9e04-3a52-4d8f-b6e1-72d9c4a05f38");

    /**
     * 「本实体身上已施加的难度档案」持久化键，格式 {@code <档位>@<生命倍率>/<伤害倍率>}（如 {@code normal@1.5/1.5}）。
     * 倍率一起记：倍率是配置项，只存档位会让"改了配置只有新刷的怪生效"，存档不一致时进场自动重算。
     */
    private static final String APPLIED_TIER_KEY = "HallDifficultyTier";

    private static final char MARKER_SEPARATOR = '@';

    private HallDifficultyAttributes() {}

    /**
     * 该实体是否为需要吃难度倍率的王庭生物：实现 {@link IInfectedEntity} 且感染类型属于 {@code hall}。
     * 用 {@link IInfectedEntity} 而不是 {@code instanceof AbstractRottenEntity}，天然排除技能类实体与玩家。
     */
    public static boolean isScaledCreature(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (!(entity instanceof IInfectedEntity infected)) return false;

        return RottenFaction.HALL.matches(infected.getInfectionType());
    }

    /** 实体进场时施加 / 刷新难度倍率；已按当前档位施加过的实体会被跳过，可放心重复调用。 */
    public static void applyIfNeeded(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        if (!isScaledCreature(entity)) return;

        CompoundTag data = entity.getPersistentData();

        DifficultyScaleProfile.Tier currentTier = DifficultyScaleProfile.tierOf(
                HallDifficultyEventHandler.getCurrentDifficulty());
        DifficultyScaleProfile currentProfile = DifficultyScaleProfile.fromConfig(currentTier);

        // 三项全吻合才跳过重算：档位没换、倍率与当前配置一致、该实体该有的修饰符都在。
        // ② 不能省：少了它就会出现"改了配置只有新刷的怪生效"的半生效状态，比多读一次配置难查得多。
        boolean upToDate = currentTier == readAppliedTier(data)
                && currentProfile.equals(readAppliedProfile(data))
                && !needsApply(entity);
        if (upToDate) {
            return;
        }

        apply(entity, currentProfile, currentTier);
    }

    /** 强制按给定档案重算（难度切换、配置重载时用）。 */
    public static void refresh(LivingEntity entity, DifficultyScaleProfile profile,
                              DifficultyScaleProfile.Tier tier) {
        if (entity.level().isClientSide()) return;
        if (!isScaledCreature(entity)) return;
        apply(entity, profile, tier);
    }

    /** 按给定档案刷新全服已加载的王庭生物（只在难度切换 / 配置重载时调用，低频操作）。 */
    public static int refreshAllLoaded(MinecraftServer server, DifficultyScaleProfile profile,
                                       DifficultyScaleProfile.Tier tier) {
        if (server == null) return 0;

        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && isScaledCreature(living)) {
                    apply(living, profile, tier);
                    count++;
                }
            }
        }
        return count;
    }

    public static int refreshAllLoaded(MinecraftServer server) {
        ResourceLocation difficulty = HallDifficultyEventHandler.getCurrentDifficulty();
        DifficultyScaleProfile.Tier tier = DifficultyScaleProfile.tierOf(difficulty);
        return refreshAllLoaded(server, DifficultyScaleProfile.fromConfig(tier), tier);
    }

    private static void apply(LivingEntity entity, DifficultyScaleProfile profile,
                              DifficultyScaleProfile.Tier tier) {
        try {
            applyMaxHealth(entity, profile.healthScale());
            applyAttackDamage(entity, profile.damageScale());
            writeApplied(entity, tier, profile);
        } catch (Throwable t) {
            // 单个实体算属性失败不该影响整个世界加载
            HallMod.LOGGER.warn("[hall-difficulty] 施加难度倍率失败：{} ({})",
                    entity.getType(), t.toString());
        }
    }

    private static void applyMaxHealth(LivingEntity entity, float scale) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;

        float oldMax = entity.getMaxHealth();
        boolean hadHealth = oldMax > 0.0F && !Float.isNaN(oldMax);
        float ratio = hadHealth ? entity.getHealth() / oldMax : 1.0F;

        setModifier(instance, HEALTH_MODIFIER_ID, "hall_difficulty_health", scale);

        float newMax = entity.getMaxHealth();
        if (!hadHealth || Float.isNaN(ratio)) return;

        if (ratio >= 1.0F) {
            // 原本满血 → 改档后仍满血：只按比例算会留下 40.0 → 47.999996 这类浮点残血。
            entity.setHealth(newMax);
            return;
        }
        entity.setHealth(Math.max(1.0F, newMax * ratio));
    }

    private static void applyAttackDamage(LivingEntity entity, float scale) {
        AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) return;

        setModifier(instance, DAMAGE_MODIFIER_ID, "hall_difficulty_damage", scale);
    }

    /**
     * 把固定 UUID 的修饰符设成指定倍率：{@code MULTIPLY_BASE} 下 最终值 = 基础值 × (1 + amount)，
     * 所以取 {@code amount = scale - 1}。用乘法而非 ADDITION：倍率是相对该生物自身基础值定义的。
     */
    private static void setModifier(AttributeInstance instance, UUID id, String name, float scale) {
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addPermanentModifier(
                new AttributeModifier(id, name, scale - 1.0D, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /**
     * 「本实体的难度修饰符是齐的」才跳过重算。只要求该实体确实拥有的那几项属性（有的生物没注册
     * {@code ATTACK_DAMAGE}），也不能只看 NBT 标记 —— 修饰符是运行时状态，读档/被清洗会掉。
     */
    private static boolean needsApply(LivingEntity entity) {
        return needsModifier(entity, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID)
                || needsModifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_ID);
    }

    private static boolean needsModifier(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.getModifier(id) == null;
    }

    /** 读回已施加的档位；没有标记或格式损坏返回 {@code null}（比对失败 → 重算，偏向安全一侧）。 */
    @Nullable
    private static DifficultyScaleProfile.Tier readAppliedTier(CompoundTag data) {
        String raw = readAppliedRaw(data);
        if (raw == null) return null;

        int at = raw.indexOf(MARKER_SEPARATOR);
        String tierName = at < 0 ? raw : raw.substring(0, at);
        return DifficultyScaleProfile.Tier.fromWireName(tierName);
    }

    /** 读回已施加的倍率；读不出来返回 {@link DifficultyScaleProfile#fallback()}（普通档而非 0，0 被拿去乘算是灾难）。 */
    private static DifficultyScaleProfile readAppliedProfile(CompoundTag data) {
        String raw = readAppliedRaw(data);
        if (raw == null) return DifficultyScaleProfile.fallback();

        int at = raw.indexOf(MARKER_SEPARATOR);
        if (at < 0 || at + 1 >= raw.length()) return DifficultyScaleProfile.fallback();

        String payload = raw.substring(at + 1);
        int slash = payload.indexOf('/');
        if (slash < 0) return DifficultyScaleProfile.fallback();

        Float health = parseFloat(payload.substring(0, slash));
        Float damage = parseFloat(payload.substring(slash + 1));
        if (health == null || damage == null) return DifficultyScaleProfile.fallback();

        return new DifficultyScaleProfile(health, damage);
    }

    private static void writeApplied(LivingEntity entity, DifficultyScaleProfile.Tier tier,
                                     DifficultyScaleProfile profile) {
        entity.getPersistentData().putString(APPLIED_TIER_KEY,
                tier.wireName() + MARKER_SEPARATOR + profile.healthScale() + "/" + profile.damageScale());
    }

    @Nullable
    private static String readAppliedRaw(CompoundTag data) {
        if (!data.contains(APPLIED_TIER_KEY)) return null;
        String raw = data.getString(APPLIED_TIER_KEY);
        return raw.isEmpty() ? null : raw;
    }

    @Nullable
    private static Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取生物<b>生效后</b>的攻击伤害：必须用 {@code getAttributeValue}，不能用 {@code getAttributeBaseValue}
     * —— 后者一个修饰符都不算，难度倍率等于没被读到（表现为"血量变了、伤害一点没变"）。
     * 属性不存在时返回 {@code fallback}：0 伤害的攻击会静默失效，比偏小的伤害更难发现。
     */
    public static float effectiveAttackDamage(LivingEntity entity, float fallback) {
        AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) return fallback;

        double value = instance.getValue();
        return (float) value;
    }

    /** 实体加入世界（新生成与读档都走这里）；读档老怪身上没有修饰符，正好在此按当前难度补上。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        applyIfNeeded(living);
    }

    /**
     * 服务器启动：把存档里的难度读回缓存并重算。难度缓存初值是硬编码的 {@code hall:normal}，且只由
     * {@code DifficultyChangeEvent.Post} 更新（启动时不会触发），不修就会每次重启让已加载的怪按普通档施加并累积偏差。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        ResourceLocation saved = DifficultyHelper.getDifficulty(server.overworld());
        DifficultyScaleProfile profile = HallDifficultyEventHandler.syncDifficulty(saved);
        int refreshed = refreshAllLoaded(server, profile,
                DifficultyScaleProfile.tierOf(HallDifficultyEventHandler.getCurrentDifficulty()));

        HallMod.LOGGER.info("[hall-difficulty] 服务器启动，从存档读回难度 {}（生命 x{} / 伤害 x{}），预刷新 {} 只王庭生物",
                saved, profile.healthScale(), profile.damageScale(), refreshed);
    }
}
