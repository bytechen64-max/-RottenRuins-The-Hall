package org.bytechen.rottenruins.gameplay.infection.evolution;
import org.bytechen.rottenruins.core.infection.rules.WeightedSelection;
import org.bytechen.rottenruins.core.infection.evolution.InfectionFallback;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionTarget;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionEntry;

import org.bytechen.rottenruins.utils.logging.InfectionLog;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.infection.InfectionRuntime;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;
import org.bytechen.rottenruins.utils.logging.ThrottledLogger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import org.bytechen.rottenruins.core.infection.rules.InfectionRules;
import org.bytechen.rottenruins.core.infection.rules.RuleIndex;
import java.util.function.DoublePredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进化/感染管理器。
 * <p>
 * 统一管理所有感染类型的进化规则，不再将进化、感染、夺取分成多套 JSON 配置。
 * 每条规则通过 {@code type} 字段区分感染类型。
 * <p>
 * 数据文件路径：{@code data/<namespace>/infcore_evolution/*.json}
 * <p>
 * 主要 API：
 * <ul>
 *   <li>{@link #applyEvolution(ServerLevel, LivingEntity, ResourceLocation)} —— 对实体应用指定类型的进化</li>
 *   <li>{@link #registerFallback(ResourceLocation, EntityType, int, DoublePredicate)} —— 注册感染兜底档位</li>
 *   <li>{@link #hasEvolveRule(LivingEntity)} —— 检查实体是否存在任何进化规则</li>
 *   <li>{@link #hasEvolveRule(EntityType, ResourceLocation)} —— 检查指定类型和实体类型是否有规则</li>
 * </ul>
 * <p>
 * 感染兜底形态（{@link InfectionFallback}）：当感染作用于一个没有对应感染形态的生物时，
 * 按注册顺序取第一个满足碰撞体积（宽 × 高）条件的档位，把生物替换成若干目标实体。
 */
public final class EvolutionManager {

    private static final Pattern CONFIG_PATTERN =
            Pattern.compile("^(.+)\\s*->\\s*(.+)\\s*:\\s*(\\d+)\\s*$");

    /**
     * type -> 感染兜底形态（程序注册的多档位，按注册顺序判定）
     */
    private static final Map<ResourceLocation, List<InfectionFallback<EntityType<?>>>> FALLBACK_MAP = new LinkedHashMap<>();

    /**
     * 进化明细日志节流器：逐实体明细默认进 TRACE，每 30 秒最多汇总一条 DEBUG。
     * 大规模感染时进化会成片触发，逐条打印会刷爆 debug.log。
     */
    private static final ThrottledLogger EVOLUTION_LOG =
            new ThrottledLogger(InfectionLog.LOGGER, "EvolutionManager");

    /**
     * 兜底转化的生命上限门槛：<b>生命上限</b>低于该值的弱小生物不感染，按正常死亡处理。
     * <p>
     * 只作用于「没有对应感染形态」的兜底路径 —— 在进化表里有专属形态的生物（玩家、骷髅……）
     * 不受此门槛影响。
     * <p>
     * 注意：判定发生在击杀事件里，此时生物的当前生命值已经是 0，所以这里比的是生命上限。
     */
    public static final float FALLBACK_MIN_MAX_HEALTH = 5.0F;


    private EvolutionManager() {}

    // ==================== 公共 API ====================

    /**
     * 对实体应用指定类型的进化。
     *
     * @param level  服务端世界
     * @param entity 要进化的实体
     * @param type   进化类型的 ResourceLocation（如 {@code infcore:infection}）
     * @return 是否成功进化
     */
    public static boolean applyEvolution(ServerLevel level, LivingEntity entity, ResourceLocation type) {
        if (level == null || type == null) return false;
        return doTransform(level, entity, type);
    }

    // ==================== 感染兜底形态 ====================

    /**
     * 注册某种感染的一个兜底档位。
     * <p>
     * 感染流程中，如果生物<b>没有对应的感染形态</b>（进化表里没有它的规则），
     * 就按注册顺序检查该感染的所有兜底档位，<b>第一个</b>碰撞体积
     * （{@code 宽 × 高}）满足条件的档位生效：把生物替换成 {@code count} 个 {@code target}。
     * 所有档位都不满足时生物保持原样。
     * <p>
     * 兜底档位是程序注册的，不参与 JSON 数据扫描，也不会被 {@link #forceReload()} 清除。
     *
     * @param type            感染类型，与 {@link #applyEvolution} 使用的类型一致
     * @param target          兜底转化的目标实体类型
     * @param count           一次转化生成的个数（小于 1 时按 1 处理）
     * @param volumeCondition 碰撞体积（{@code 宽 × 高}）需要满足的条件，{@code null} 表示不限制
     * @see InfectionFallback
     */
    public static void registerFallback(ResourceLocation type, EntityType<?> target, int count,
                                        DoublePredicate volumeCondition) {
        if (type == null || target == null) return;
        InfectionFallback<EntityType<?>> tier = new InfectionFallback<>(target, count, volumeCondition);
        FALLBACK_MAP.computeIfAbsent(type, k -> new ArrayList<>()).add(tier);
        InfectionLog.LOGGER.debug("EvolutionManager: registered fallback tier for '{}' -> '{}' x{}",
                type, InfectionRuntime.services().entityId(target), tier.count());
    }

    /**
     * 移除某种感染的所有兜底档位。
     */
    public static void clearFallback(ResourceLocation type) {
        if (type == null) return;
        FALLBACK_MAP.remove(type);
    }

    /**
     * 移除所有已注册的兜底档位。
     */
    public static void clearFallbacks() {
        FALLBACK_MAP.clear();
    }

    /**
     * 获取某种感染已注册的兜底档位（按判定顺序排列），未注册时返回空列表。
     */
    public static List<InfectionFallback<EntityType<?>>> getFallbacks(ResourceLocation type) {
        if (type == null) return List.of();
        return List.copyOf(FALLBACK_MAP.getOrDefault(type, List.of()));
    }

    /**
     * 检查实体是否存在任何进化规则（不限类型）。
     *
     * @param entity 要检查的实体
     * @return 是否存在进化规则
     */
    public static boolean hasEvolveRule(LivingEntity entity) {
        return hasEvolveRule(entity.getType());
    }

    /**
     * 检查指定实体类型是否存在任何进化规则。
     */
    public static boolean hasEvolveRule(EntityType<?> type) {
        return InfectionRules.current().evolution().containsSource(InfectionRuntime.services().entityId(type).toString());
    }

    /**
     * 检查指定类型和实体类型是否存在进化规则。
     */
    public static boolean hasEvolveRule(EntityType<?> entityType, ResourceLocation evolutionType) {
        return InfectionRules.current().evolution().get(evolutionType.toString(),
                InfectionRuntime.services().entityId(entityType).toString()) != null;
    }

    /**
     * 获取指定实体类型和进化类型的可能目标列表。
     */
    @Nullable
    public static List<EvolutionTarget> getEvolutionTargets(EntityType<?> entityType, ResourceLocation evolutionType) {
        EvolutionEntry entry = InfectionRules.current().evolution().get(evolutionType.toString(),
                InfectionRuntime.services().entityId(entityType).toString());
        return entry != null ? entry.results() : null;
    }

    /**
     * 获取已加载的进化条目总数（用于调试）。
     */
    public static int getEntryCount() {
        return InfectionRules.current().evolution().size();
    }

    // ==================== 配置重载 ====================

    /**
     * 从当前服务端资源重新加载感染与扩散规则；包含已启用的数据包。
     */
    public static void forceReload() {
        InfectionRules.reload();
    }

    /**
     * 从配置字符串合并进化规则。
     * 格式：{@code sourceRegistryName -> targetRegistryName : weight}
     */
    public static void mergeOverrides(ResourceLocation type, List<String> overrides) {
        if (overrides == null || type == null) return;
        RuleIndex.Builder<EvolutionEntry> rules = InfectionRules.current().evolution().toBuilder();

        for (String line : overrides) {
            Matcher m = CONFIG_PATTERN.matcher(line.trim());
            if (!m.matches()) {
                InfectionLog.LOGGER.warn("EvolutionManager: invalid override '{}'", line);
                continue;
            }
            ResourceLocation src = ResourceLocation.tryParse(m.group(1).trim());
            ResourceLocation target = ResourceLocation.tryParse(m.group(2).trim());
            int weight;
            try {
                weight = Integer.parseInt(m.group(3).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (src == null || target == null || weight <= 0) continue;

            List<EvolutionTarget> results = new ArrayList<>();
            results.add(new EvolutionTarget(target.toString(), weight));
            EvolutionEntry entry = new EvolutionEntry(type.toString(), src.toString(), false, false, results);
            rules.put(type.toString(), src.toString(), entry);
        }
        InfectionRules.replaceEvolution(rules.build());
    }

    // ==================== 内部核心 ====================

    private static boolean doTransform(ServerLevel level, LivingEntity entity, ResourceLocation evolutionType) {

        ResourceLocation sourceKey = InfectionRuntime.services().entityId(entity.getType());

        EvolutionEntry entry = InfectionRules.current().evolution().get(evolutionType.toString(), sourceKey.toString());

        // 没有对应感染形态 → 交给感染兜底形态判定
        if (entry == null || entry.results() == null || entry.results().isEmpty()) {
            return doFallbackTransform(level, entity, evolutionType);
        }

        EvolutionTarget chosen = WeightedSelection.pick(entry.results(), EvolutionTarget::weight, level.random::nextInt);
        if (chosen == null) return doFallbackTransform(level, entity, evolutionType);

        ResourceLocation targetKey = ResourceLocation.tryParse(chosen.target());
        if (targetKey == null) return doFallbackTransform(level, entity, evolutionType);

        EntityType<?> targetType = InfectionRuntime.services().entity(targetKey);
        if (targetType == null) {
            InfectionLog.LOGGER.warn("EvolutionManager: unknown target entity type '{}'", chosen.target());
            return doFallbackTransform(level, entity, evolutionType);
        }

        if (!replaceEntity(level, entity, targetType, 1, entry.keepNbt(), entry.keepEquipment())) {
            return false;
        }

        EVOLUTION_LOG.record("'{}' evolved into '{}' (type={})",
                sourceKey, chosen.target(), evolutionType);
        return true;
    }

    /**
     * 感染兜底形态判定。
     * <p>
     * 该生物<b>没有对应的感染形态</b>（进化表里查不到它的规则）时，按注册顺序检查
     * 该感染的所有兜底档位，取第一个满足碰撞体积（{@code 宽 × 高}）条件的档位，
     * 把生物替换成 {@code count} 个目标实体。
     * <p>
     * 已经是该感染任一兜底形态的生物不会被再次兜底转化；
     * 生命上限低于 {@link #FALLBACK_MIN_MAX_HEALTH} 的弱小生物也不转化，让它正常死亡。
     *
     * @return 是否成功转化
     */
    private static boolean doFallbackTransform(ServerLevel level, LivingEntity entity, ResourceLocation evolutionType) {
        List<InfectionFallback<EntityType<?>>> tiers = FALLBACK_MAP.get(evolutionType);
        if (tiers == null || tiers.isEmpty()) return false;

        // 已经是兜底形态本身 → 不再兜底转化
        for (InfectionFallback<EntityType<?>> tier : tiers) {
            if (tier.target() != null && entity.getType() == tier.target()) return false;
        }

        ResourceLocation sourceKey = InfectionRuntime.services().entityId(entity.getType());

        // 生命上限太低的弱小生物（小鸡、兔子、鱼这类）不感染，直接正常死亡
        if (entity.getMaxHealth() < FALLBACK_MIN_MAX_HEALTH) {
            EVOLUTION_LOG.record("'{}' has no '{}' form but max health {} < {}, left to die normally",
                    sourceKey, evolutionType, entity.getMaxHealth(), FALLBACK_MIN_MAX_HEALTH);
            return false;
        }

        double volume = entity.getBbWidth() * entity.getBbHeight();

        for (InfectionFallback<EntityType<?>> tier : tiers) {
            if (!tier.matches(volume)) continue;

            EntityType<?> targetType = tier.target();
            if (targetType == null) continue;

            if (!replaceEntity(level, entity, targetType, tier.count(), false, false)) {
                return false;
            }

            EVOLUTION_LOG.record("'{}' has no '{}' form, fell back to '{}' x{} (volume={})",
                    sourceKey, evolutionType, InfectionRuntime.services().entityId(targetType), tier.count(), volume);
            return true;
        }

        EVOLUTION_LOG.record("'{}' has no '{}' form and volume {} matches no fallback tier",
                sourceKey, evolutionType, volume);
        return false;
    }

    /**
     * 用目标类型的实体替换源实体（复制位置、朝向、运动状态与自定义名称），并触发
     * {@link EntityEvolveEvent}。
     * <p>
     * {@code count > 1} 时，第一个目标出现在源实体的原位，其余目标围绕源位置分散开，
     * 自定义名称、完整 NBT 与装备只保留给第一个目标。
     *
     * @param count         生成个数（小于 1 时按 1 处理）
     * @param keepNbt       是否保留完整 NBT
     * @param keepEquipment 是否保留装备
     * @return 是否成功替换
     */
    private static boolean replaceEntity(ServerLevel level, LivingEntity entity, EntityType<?> targetType, int count,
                                         boolean keepNbt, boolean keepEquipment) {
        if (count < 1) count = 1;
        double splitRadius = Math.max(0.75D, targetType.getDimensions().width * 0.8D);

        List<Entity> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Entity newEntity = targetType.create(level);
            if (newEntity == null) break;

            // 复制位置和运动状态：第一个保持原位，其余围绕源位置分散
            if (i == 0) {
                newEntity.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
            } else {
                double angle = (Math.PI * 2.0D / count) * i;
                double offsetX = Math.cos(angle) * splitRadius;
                double offsetZ = Math.sin(angle) * splitRadius;
                newEntity.moveTo(entity.getX() + offsetX, entity.getY(), entity.getZ() + offsetZ,
                        entity.getYRot(), entity.getXRot());
                // 分散点被方块占住时退回源位置（避免卡在墙里窒息）
                if (level.getBlockCollisions(newEntity, newEntity.getBoundingBox()).iterator().hasNext()) {
                    newEntity.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                }
            }
            newEntity.setDeltaMovement(entity.getDeltaMovement());
            newEntity.setYHeadRot(entity.getYHeadRot());
            newEntity.setYBodyRot(entity.yBodyRot);

            // 复制自定义名称（只给第一个，避免同名分身）
            if (i == 0 && entity.hasCustomName()) {
                newEntity.setCustomName(entity.getCustomName());
                newEntity.setCustomNameVisible(entity.isCustomNameVisible());
            }

            // 根据配置决定是否保留 NBT / 装备（只对第一个生效）
            if (keepNbt && i == 0) {
                copyFullNbt(entity, newEntity);
            } else {
                copyBasicNbt(entity, newEntity);
            }

            if (keepEquipment && i == 0 && entity instanceof Mob srcMob && newEntity instanceof Mob dstMob) {
                copyEquipment(srcMob, dstMob);
            }

            spawned.add(newEntity);
        }

        if (spawned.isEmpty()) return false;

        // 移除源实体
        if (!(entity instanceof Player)) {
            entity.discard();
        }

        for (Entity newEntity : spawned) {
            level.addFreshEntity(newEntity);

            // 粒子效果
            level.sendParticles(ParticleTypes.EXPLOSION,
                    newEntity.getX(), newEntity.getY() + newEntity.getBbHeight() / 2.0, newEntity.getZ(),
                    1, 0, 0, 0, 0.05);

            // 触发进化事件
            if (newEntity instanceof LivingEntity livingResult) {
                InfectionRuntime.services().afterEvolution(new EntityEvolveEvent(level, entity, livingResult));
            }
        }
        return true;
    }

    // ==================== NBT 复制 ====================

    private static void copyBasicNbt(LivingEntity from, Entity to) {
        if (from instanceof Mob fromMob && to instanceof Mob toMob) {
            if (fromMob.isPersistenceRequired()) {
                toMob.setPersistenceRequired();
            }
            toMob.setLeftHanded(fromMob.isLeftHanded());
        }
        to.invulnerableTime = from.invulnerableTime;
    }

    private static void copyFullNbt(LivingEntity from, Entity to) {
        copyBasicNbt(from, to);
        CompoundTag tag = new CompoundTag();
        from.saveWithoutId(tag);
        to.load(tag);
        // 恢复实体类型相关信息（saveWithoutId 不会保存这个）
        to.setPos(from.getX(), from.getY(), from.getZ());
    }

    private static void copyEquipment(Mob from, Mob to) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = from.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                to.setItemSlot(slot, stack.copy());
            }
        }
    }

}
