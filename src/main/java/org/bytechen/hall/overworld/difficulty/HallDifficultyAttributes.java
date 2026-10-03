package org.bytechen.hall.overworld.difficulty;

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
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.infcore.core.difficulty.DifficultyHelper;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 难度倍率的实际施加点：按当前难度给王庭生物乘上最大生命值与攻击伤害。
 *
 * <h3>为什么挂在实体进场事件，而不是 {@code createAttributes}</h3>
 * <p>属性表是通过 {@code EntityAttributeCreationEvent} 注册的，每种生物全服只有一份
 * （见 {@code EntityTypeRegistry.registerMob} → {@code HallEntityManager.createAttributes}）。
 * 难度是按存档走的，把倍率烤进属性表等于「后开的存档会改掉先开的存档」。
 * 所以这里改成进场时挂 {@link AttributeModifier}。
 *
 * <h3>为什么用固定 UUID</h3>
 * <p>{@link AttributeModifier} 的相等性是按 UUID 判定的，固定 UUID 带来两个好处：
 * <ul>
 *   <li>重复进场 / 重复调用只会覆盖，不会叠加成 1.5×1.5；</li>
 *   <li>难度切换时能把旧修饰符精准摘掉再挂新的。</li>
 * </ul>
 *
 * <h3>为什么保留生命比例</h3>
 * <p>改 {@code MAX_HEALTH} 时原版会把当前血量向上钳到新上限，
 * 但不会按比例放大 —— 一只满血的畸骸玩家从普通档切到困难档会变成
 * 40/160 血（看起来像被打残了）。这里在改属性前后按比例还原，
 * 满血仍是满血、半血仍是半血。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HallDifficultyAttributes {

    // ==================== 常量 ====================

    /** 最大生命值修饰符的固定 UUID。 */
    public static final UUID HEALTH_MODIFIER_ID =
            UUID.fromString("8f2a5c41-6d3e-4b97-a1c8-5e40f7b93d12");

    /** 攻击伤害修饰符的固定 UUID。 */
    public static final UUID DAMAGE_MODIFIER_ID =
            UUID.fromString("1c7b9e04-3a52-4d8f-b6e1-72d9c4a05f38");

    /**
     * 「本实体身上已施加的难度档案」持久化键。
     * <p>格式 {@code <档位>@<生命倍率>/<伤害倍率>}，例如 {@code normal@1.5/1.5}。
     * <p>为什么不只存档位：倍率是配置项，玩家可能把普通档从 1.5 改成 2.0 而不换难度。
     * 只存档位的话，标记看着仍然匹配，场上已有的怪就会一直用旧倍率，
     * 变成「改了配置只有新刷的怪生效」这种半生效状态。把倍率一起记下来，
     * 下次实体进场/区块加载时会自动发现不一致并重算，不需要玩家手动重载。
     */
    private static final String APPLIED_TIER_KEY = "HallDifficultyTier";

    /** 标记里档位与倍率的分隔符。 */
    private static final char MARKER_SEPARATOR = '@';

    private HallDifficultyAttributes() {}

    // ==================== 判据 ====================

    /**
     * 该实体是否为需要吃难度倍率的王庭生物。
     * <p>判据与模组其它地方保持一致（{@code ForgeEventHelpers.isHallInfected}、
     * {@code MeteoriteEntity} 的友军判定）：实现 {@link IInfectedEntity}，
     * 且感染类型的命名空间是 {@code hall}。
     * <p>这样写而不是 {@code instanceof AbstractHallEntity}，是因为
     * {@link IInfectedEntity} 才是 infcore 约定的「感染生物」标记；
     * 同时它天然排除了技能类实体（{@code SwordAuraEntity}、{@code BlackHoleEntity}
     * 这些是纯 {@code Entity}）和玩家。
     */
    public static boolean isScaledCreature(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (!(entity instanceof IInfectedEntity infected)) return false;

        ResourceLocation type = infected.getInfectionType();
        return type != null && HallMod.MODID.equals(type.getNamespace());
    }

    // ==================== 施加 ====================

    /**
     * 实体进场时施加 / 刷新难度倍率。
     * <p>已按当前档位施加过、且属性实例仍然完好的实体会被直接跳过，
     * 因此这个方法可以放心地重复调用。
     */
    public static void applyIfNeeded(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        if (!isScaledCreature(entity)) return;

        // 持久化数据只取一次：getPersistentData() 返回的是同一个可变 tag，
        // 反复取既有开销，也容易让人以为每次拿到的是不同快照。
        CompoundTag data = entity.getPersistentData();

        DifficultyScaleProfile.Tier currentTier = DifficultyScaleProfile.tierOf(
                HallDifficultyEventHandler.getCurrentDifficulty());
        DifficultyScaleProfile currentProfile = DifficultyScaleProfile.fromConfig(currentTier);

        // 三项全部吻合才跳过重算：
        //   ① 档位没换（难度没变）；
        //   ② 倍率与当前配置一致（玩家没改难度系数）；
        //   ③ 该实体该有的修饰符都在（没被读档或别的模组洗掉）。
        //
        // ② 不能省。它的成本只是一次配置读取（ConfigHolder.get() 返回缓存实例，
        // 读八个 float 字段），但少了它就会出现「改了配置、只有新刷的怪生效，
        // 老怪一直用旧倍率」这种半生效状态 —— 那比多读一次配置难查得多。
        boolean upToDate = currentTier == readAppliedTier(data)
                && currentProfile.equals(readAppliedProfile(data))
                && !needsApply(entity);
        if (upToDate) {
            return;
        }

        apply(entity, currentProfile, currentTier);
    }

    /**
     * 强制按给定档案重算（难度切换、配置重载时用）。
     */
    public static void refresh(LivingEntity entity, DifficultyScaleProfile profile,
                              DifficultyScaleProfile.Tier tier) {
        if (entity.level().isClientSide()) return;
        if (!isScaledCreature(entity)) return;
        apply(entity, profile, tier);
    }

    /**
     * 按给定档案刷新全服所有已加载的王庭生物。
     * <p>只在难度切换与配置重载时调用 —— 这两个动作都很低频，
     * 遍历一次已加载实体比让每 tick 去比对难度便宜得多，也不会漏掉已经站在场上的怪。
     *
     * @return 实际刷新过的实体数量（0 表示当时没有玩家在线，或没有可刷新的生物）
     */
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

    /** 便捷重载：按当前难度刷新全服。 */
    public static int refreshAllLoaded(MinecraftServer server) {
        ResourceLocation difficulty = HallDifficultyEventHandler.getCurrentDifficulty();
        DifficultyScaleProfile.Tier tier = DifficultyScaleProfile.tierOf(difficulty);
        return refreshAllLoaded(server, DifficultyScaleProfile.fromConfig(tier), tier);
    }

    // ==================== 内部实现 ====================

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

        // 先记下比例：当前血量可能是「满血」，也可能是被打到一半
        float oldMax = entity.getMaxHealth();
        boolean hadHealth = oldMax > 0.0F && !Float.isNaN(oldMax);
        float ratio = hadHealth ? entity.getHealth() / oldMax : 1.0F;

        setModifier(instance, HEALTH_MODIFIER_ID, "hall_difficulty_health", scale);

        float newMax = entity.getMaxHealth();
        if (!hadHealth || Float.isNaN(ratio)) return;

        if (ratio >= 1.0F) {
            // 原本满血 → 改档后仍然满血。
            // 这条分支不能省：只按比例算的话，40.0 → 47.999996 这种浮点误差
            // 会让「满血」的生物带着零点几的空血，看上去像是被蹭过一下。
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
     * 把固定 UUID 的修饰符设成指定倍率。
     * <p>{@code MULTIPLY_BASE} 是「在基础值上乘」——最终值 = 基础值 × (1 + amount)，
     * 所以 {@code amount = scale - 1} 正好得到「基础值 × scale」。
     * <p>选 {@code MULTIPLY_BASE} 而不是 {@code ADDITION}：伤害加成走加法的话，
     * 基础伤害 25 的生物加 11 点只有 1.44 倍，而倍率是相对它自己的基础值定义的。
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
     * 快速判断「本实体的难度修饰符是齐的」，齐了才跳过重算。
     *
     * <h3>为什么只要求「该实体确实拥有的」那几项</h3>
     * <p>不能一刀切要求两项都在：有的王庭生物压根没注册 {@code ATTACK_DAMAGE}
     * （属性是各生物自己 {@code createAttributes} 里逐项加的，不是统一模板）。
     * 对这类生物，伤害那一项永远挂不上，一刀切就会每次都判「没齐」→ 每次进场
     * 都白重算一遍，而且在刷怪量大的时候是纯浪费。
     *
     * <h3>为什么不能只看标记</h3>
     * <p>标记是存在实体 NBT 里的纯文本，存得住；属性修饰符是运行时状态，
     * 读档、被别的模组重算属性、被指令清洗都会掉。只看标记会漏掉
     * 「标记说已施加、实际光着」这种最难查的情况。
     *
     * @return 是否还需要（重新）施加
     */
    private static boolean needsApply(LivingEntity entity) {
        return needsModifier(entity, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID)
                || needsModifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_ID);
    }

    /** 该实体拥有这个属性、但属性上没有我们的修饰符 → 需要补。 */
    private static boolean needsModifier(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.getModifier(id) == null;
    }

    // ==================== 标记持久化 ====================

    /**
     * 读回已施加的档位；没有标记或格式损坏时返回 {@code null}
     * （返回 {@code null} 会让比对失败 → 触发重算，偏向安全的一侧）。
     */
    @Nullable
    private static DifficultyScaleProfile.Tier readAppliedTier(CompoundTag data) {
        String raw = readAppliedRaw(data);
        if (raw == null) return null;

        int at = raw.indexOf(MARKER_SEPARATOR);
        String tierName = at < 0 ? raw : raw.substring(0, at);
        return DifficultyScaleProfile.Tier.fromWireName(tierName);
    }

    /**
     * 读回已施加的倍率；没有标记或格式损坏时返回 {@link DifficultyScaleProfile#fallback()}。
     * <p>兜底取普通档而不是 0：读不出来时它只会让比对失败（该重算就重算），
     * 而 0 万一被别处直接拿去乘算就是灾难 —— 两个方向的风险不对称。
     */
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

    // ==================== 伤害读取 ====================

    /**
     * 读取生物<b>生效后</b>的攻击伤害（含难度倍率等全部属性修饰符）。
     *
     * <h3>必须用这个，不要用 {@code getAttributeBaseValue}</h3>
     * <p>这是个踩过的坑：{@code getAttributeBaseValue(Attributes.ATTACK_DAMAGE)}
     * 读的是属性的<b>基础值</b>，而
     * <ul>
     *   <li>{@code getAttributeValue(...)} → {@code AttributeMap.getValue}
     *       → {@code AttributeInstance.getValue} → 把 ADDITION / MULTIPLY_BASE /
     *       MULTIPLY_TOTAL 三类修饰符全部算进去；</li>
     *   <li>{@code getAttributeBaseValue(...)} → {@code AttributeMap.getBaseValue}
     *       → <b>一个修饰符都不算</b>。</li>
     * </ul>
     * 王庭生物的普攻是直接 {@code target.hurt(mobAttack, 读到的值)}，
     * 所以读 base value 等于完全绕开属性系统 —— 难度倍率挂上去了却没人读，
     * 表现就是「血量变了、伤害一点没变」。
     *
     * <h3>返回值</h3>
     * <p>属性不存在时返回传入的兜底值。用兜底而不是 0：
     * 0 伤害的攻击会静默失效，比一个偏小的伤害更难发现。
     *
     * @param entity   读取属性的生物
     * @param fallback 没有该属性时的兜底伤害
     */
    public static float effectiveAttackDamage(LivingEntity entity, float fallback) {
        AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) return fallback;

        double value = instance.getValue();
        return (float) value;
    }

    // ==================== 事件挂钩 ====================

    /**
     * 实体加入世界。
     * <p>新生成与从磁盘读档都会走这里（{@code loadedFromDisk()} 可区分，本处不需要区分）：
     * 读档进来的老怪身上没有我们的修饰符（属性修饰符本身不写进实体 NBT），
     * 正好借这个时机按当前难度补上。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        applyIfNeeded(living);
    }

    /**
     * 服务器启动：把世界存档里存的难度读回缓存，并按它重算一遍。
     *
     * <h3>这一步在修什么</h3>
     * <p>{@link HallDifficultyEventHandler#getCurrentDifficulty()} 的初值是硬编码的
     * {@code hall:normal}，只由 {@code DifficultyChangeEvent.Post} 更新 ——
     * 那个事件只在「切难度」时触发，<b>启动时不会触发</b>。
     * 于是重启后缓存退回 normal，而存档里存的可能是 {@code hall:incomprehensible}：
     * 之后加载的怪会全部按普通档施加、标记也写成 normal，
     * 并且没有任何东西会纠正它们。这个偏差会随每次重启累积。
     *
     * <h3>时序</h3>
     * <p>{@code ServerStartedEvent} 时各维度都已加载、但区块里的实体大多还没被读出来，
     * 所以这里的 {@code refreshAllLoaded} 通常只覆盖到启动阶段已加载的少数实体；
     * 真正兜底的是：缓存此时已经是正确值，后续每个实体进场时
     * {@link #applyIfNeeded} 都会拿到正确的档位，或发现标记不符而重算。
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
