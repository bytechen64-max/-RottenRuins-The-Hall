package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;


import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.utils.combat.CorruptionDamageUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.bytechen.infcore.api.IInfectedEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 腐蚀生物技能工具库
 *
 * <p>把原先 {@code AvilaEntity} 里私有的技能写法抽成普通生物也能直接调用的静态方法。
 * 所有方法内部自行判断服务端，客户端表现依赖粒子与同步实体，因此
 * <b>不需要生物具备专属技能动画</b>——这一点很关键，因为项目里绝大多数生物
 * 的动画文件只有 idle / walk / run。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>纯新增，不改动 {@code AbstractCorruptionEntity} 既有行为</li>
 *   <li>伤害默认走 {@link CorruptionDamageUtil} 或标准 hurt，遵守盾牌 / 不死图腾规则</li>
 *   <li>目标筛选统一排除 {@link ICorruptionEntity} 与创造 / 旁观玩家</li>
 *   <li>所有"先兆"类方法都保证：看得懂 = 躲得掉（先兆点即落点）</li>
 * </ul>
 */
public final class CorruptionSkillUtil {

    private CorruptionSkillUtil() {}

    /** 腐蚀主题红：粒子 / 先兆 / 冲击环统一用它，改一处即全局生效 */
    public static final int CORRUPTION_RED = 0xFFFF2020;

    // =========================================================================
    // 粒子 / 先兆
    // =========================================================================

    /** 红色尘埃粒子（尺寸可调） */
    public static DustParticleOptions redDust(float scale) {
        return new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.12F), scale);
    }

    /**
     * 蓄力先兆：红色尘埃从四周向实体中心汇聚。
     *
     * <p>粒子生成在半径 {@code radius} 的球壳上，速度指向中心且大小恰好在约 4 tick 内抵达，
     * 视觉上形成"吸入"感；释放瞬间配合 {@link #burst} 做一次外扩，收—放对比让技能读得清。
     *
     * @param entity   蓄力中的实体
     * @param radius   汇聚半径（格）
     * @param count    基准数量，实际会随进度加密
     * @param progress 蓄力进度 0~1
     */
    public static void chargeIn(LivingEntity entity, double radius, int count, float progress) {
        if (!(entity.level() instanceof ServerLevel level)) return;

        float p = Mth.clamp(progress, 0F, 1F);
        int n = Math.max(1, Math.round(count * (0.4F + 0.6F * p)));
        Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        RandomSource random = level.getRandom();
        DustParticleOptions dust = redDust(0.7F + p);

        for (int i = 0; i < n; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double r = radius * (0.75 + random.nextDouble() * 0.25);
            double ox = r * Math.sin(phi) * Math.cos(theta);
            double oy = r * Math.cos(phi) * 0.6;
            double oz = r * Math.sin(phi) * Math.sin(theta);

            Vec3 velocity = new Vec3(ox, oy, oz).scale(-0.25);
            level.sendParticles(dust,
                    center.x + ox, center.y + oy, center.z + oz,
                    0, velocity.x, velocity.y, velocity.z, 1.0);
        }
    }

    /**
     * 释放帧：由中心向外炸开的粒子。
     *
     * @param entity 释放技能的实体
     * @param radius 外扩半径（格）
     * @param count  粒子数量
     * @param speed  初速度（格/tick）
     */
    public static void burst(LivingEntity entity, double radius, int count, double speed) {
        if (!(entity.level() instanceof ServerLevel level)) return;

        Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        RandomSource random = level.getRandom();
        DustParticleOptions dust = redDust(1.4F);

        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double ox = radius * Math.sin(phi) * Math.cos(theta);
            double oy = radius * Math.cos(phi) * 0.5;
            double oz = radius * Math.sin(phi) * Math.sin(theta);

            Vec3 dir = new Vec3(ox, oy, oz).normalize().scale(speed);
            level.sendParticles(dust, center.x + ox, center.y + oy, center.z + oz,
                    0, dir.x, dir.y, dir.z, 1.0);
        }
    }

    /**
     * 地面预警圈：在地面画一个圆环提示"这里马上要出事"。
     *
     * @param level  服务端世界
     * @param center 圆心（建议取目标脚下）
     * @param radius 半径
     * @param count  采样点数量
     * @param dust   粒子类型
     */
    public static void warningRing(Level level, Vec3 center, double radius, int count, ParticleOptions dust) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;
            serverLevel.sendParticles(dust, px, center.y + 0.12, pz, 1, 0, 0.02, 0, 0.0);
        }
    }

    /** 圆形预警圈（腐蚀红） */
    public static void warningRing(Level level, Vec3 center, double radius, int count) {
        warningRing(level, center, radius, count, redDust(1.0F));
    }

    /**
     * 落点先兆：在目标脚下亮起预警圈，并返回预警点。
     *
     * <p>技能真正落点<b>必须</b>使用返回值而不是重新读取目标位置，
     * 否则玩家躲开了也会被打中，先兆就失去意义。
     */
    @Nullable
    public static Vec3 telegraphGround(Level level, @Nullable LivingEntity target, double radius) {
        if (target == null) return null;
        Vec3 pos = target.position();
        warningRing(level, pos, radius, Math.max(8, (int) (radius * 6)));
        return pos;
    }

    /** 头顶烟柱：远距离可读的"正在蓄力"提示 */
    public static void chargeSmoke(LivingEntity entity, int count) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.2, entity.getZ(),
                count, 0.25, 0.05, 0.25, 0.01);
    }

    // =========================================================================
    // 音效
    // =========================================================================

    /** 以实体为中心播放音效（服务端广播，追踪客户端可听） */
    public static void playSound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** 带随机音高的技能音效，避免连发听感呆板 */
    public static void playSoundPitched(LivingEntity entity, SoundEvent sound,
                                        float volume, float pitch, float pitchJitter) {
        if (sound == null) return;
        float p = pitch + (entity.getRandom().nextFloat() - 0.5F) * pitchJitter;
        playSound(entity, sound, volume, Math.max(0.5F, p));
    }

    // =========================================================================
    // 目标筛选
    // =========================================================================

    /** 是否为合法技能目标：存活、非腐蚀生物、非创造 / 旁观玩家 */
    public static boolean isValidTarget(@Nullable Entity attacker, @Nullable Entity candidate) {
        if (candidate == null || candidate == attacker) return false;
        if (!(candidate instanceof LivingEntity living) || !living.isAlive()) return false;
        if (living instanceof IInfectedEntity infected
                && RottenFaction.isOurs(infected.getInfectionType())) return false;
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return true;
    }

    /** 范围内所有合法目标，按距离由近到远排序 */
    public static List<LivingEntity> findTargets(LivingEntity source, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        AABB area = source.getBoundingBox().inflate(radius);
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (isValidTarget(source, e)) result.add(e);
        }
        result.sort(Comparator.comparingDouble(source::distanceToSqr));
        return result;
    }

    /**
     * 前方扇区内的合法目标。
     *
     * @param range 半径
     * @param angle 扇区总张角（度），180 = 半圆
     */
    public static List<LivingEntity> findTargetsInSector(LivingEntity source, double range, double angle) {
        List<LivingEntity> result = new ArrayList<>();
        Vec3 forward = Vec3.directionFromRotation(0, source.getYRot());
        double halfCos = Math.cos(Math.toRadians(angle) * 0.5);

        for (LivingEntity e : findTargets(source, range)) {
            Vec3 to = e.position().subtract(source.position());
            if (to.lengthSqr() < 1.0e-4) {
                result.add(e);
                continue;
            }
            if (forward.dot(to.normalize()) >= halfCos) result.add(e);
        }
        return result;
    }

    /** 直线路径附近的合法目标（碰撞盒放宽 {@code pad} 格） */
    public static List<LivingEntity> findTargetsAlong(Vec3 from, Vec3 to, @Nullable Entity owner,
                                                      Level level, double pad) {
        List<LivingEntity> result = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(pad);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (isValidTarget(owner, e)) result.add(e);
        }
        return result;
    }

    /** 距离最近的合法目标，找不到返回 null */
    @Nullable
    public static LivingEntity nearestTarget(LivingEntity source, double radius) {
        List<LivingEntity> list = findTargets(source, radius);
        return list.isEmpty() ? null : list.get(0);
    }

    // =========================================================================
    // 技能结算
    // =========================================================================

    /** 对单个目标造成可被盾牌格挡的物理伤害，并按 {@code knockback} 水平击退 */
    public static boolean strike(LivingEntity attacker, LivingEntity target,
                                 float damage, double knockback) {
        if (!isValidTarget(attacker, target)) return false;

        boolean hit = CorruptionDamageUtil.TrueHurt(attacker, target, 0, damage, 1, true);
        if (hit && knockback > 0) {
            Vec3 flat = target.position().subtract(attacker.position()).multiply(1, 0, 1);
            if (flat.lengthSqr() > 1.0e-4) {
                Vec3 n = flat.normalize();
                target.push(n.x * knockback, 0.25, n.z * knockback);
                target.hurtMarked = true;
            }
        }
        return hit;
    }

    /**
     * 范围物理伤害（可被格挡）+ 可选击退。
     *
     * @return 命中数量
     */
    public static int areaDamage(LivingEntity attacker, double radius, float damage, double knockback) {
        int count = 0;
        for (LivingEntity target : findTargets(attacker, radius)) {
            if (strike(attacker, target, damage, knockback)) count++;
        }
        return count;
    }

    /**
     * 范围真伤：无视无敌帧的最小伤害 + 常规伤害，用于有明显前摇的重击。
     *
     * @return 命中数量
     */
    public static int areaTrueDamage(LivingEntity attacker, double radius,
                                     float minDamage, float commonDamage, double knockback) {
        int count = 0;
        for (LivingEntity target : findTargets(attacker, radius)) {
            if (CorruptionDamageUtil.TrueHurt(attacker, target, minDamage, commonDamage, 2, true)) {
                count++;
                if (knockback > 0) {
                    Vec3 flat = target.position().subtract(attacker.position()).multiply(1, 0, 1);
                    if (flat.lengthSqr() > 1.0e-4) {
                        Vec3 n = flat.normalize();
                        target.push(n.x * knockback, 0.3, n.z * knockback);
                        target.hurtMarked = true;
                    }
                }
            }
        }
        return count;
    }

    /** 把 {@code victim} 朝 {@code toward} 拉拽（牵引类技能） */
    public static void pull(Vec3 toward, LivingEntity victim, double strength, double lift) {
        Vec3 dir = toward.subtract(victim.position());
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        victim.setDeltaMovement(flat.x * strength, lift, flat.z * strength);
        victim.hurtMarked = true;
    }

    // =========================================================================
    // 位移
    // =========================================================================

    /**
     * 朝目标突进（水平为主，保留少量垂直分量）。
     *
     * @param horizontal 水平初速度
     * @param vertical   垂直初速度
     */
    public static void lungeAt(LivingEntity entity, LivingEntity target,
                               double horizontal, double vertical) {
        Vec3 dir = target.position().subtract(entity.position());
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = Vec3.directionFromRotation(0, entity.getYRot());
        }
        Vec3 n = flat.normalize();
        entity.setDeltaMovement(n.x * horizontal, vertical, n.z * horizontal);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    /** 朝目标方向转身，避免突进 / 指向性技能斜着冲出去 */
    public static void faceTarget(LivingEntity entity, Entity target) {
        Vec3 dir = target.position().subtract(entity.position());
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
    }

    // =========================================================================
    // 效果 / 增益
    // =========================================================================

    public static void applyEffect(LivingEntity target, MobEffect effect, int duration, int amplifier) {
        if (target == null || effect == null) return;
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
    }

    /** 施加可叠加效果（沿用腐植柱的叠加规则） */
    public static void stackEffect(LivingEntity target, MobEffect effect, int duration, int amplifier) {
        if (target == null || effect == null) return;
        net.minecraft.world.effect.MobEffectInstance current = target.getEffect(effect);
        if (current == null) {
            target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
        } else {
            target.addEffect(new MobEffectInstance(effect,
                    Math.max(duration, current.getDuration()),
                    Math.max(amplifier, current.getAmplifier()), false, true));
        }
    }

    /**
     * 给半径内的同族腐蚀生物上增益（排除自己与非 AI 生物）。
     *
     * @return 受增益数量
     */
    public static int buffAllies(Mob source, double radius, MobEffect effect,
                                 int duration, int amplifier) {
        int count = 0;
        AABB area = source.getBoundingBox().inflate(radius);
        for (LivingEntity ally : source.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (ally == source || !ally.isAlive()) continue;
            if (!isCorruptionAlly(ally)) continue;
            if (!(ally instanceof Mob)) continue;
            ally.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
            count++;
        }
        return count;
    }

    /**
     * 净化半径内同族身上的负面效果（含腐蚀）。
     *
     * @return 被净化的盟友数量
     */
    public static int purifyAllies(Mob source, double radius) {
        int count = 0;
        AABB area = source.getBoundingBox().inflate(radius);
        for (LivingEntity ally : source.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (ally == source || !ally.isAlive()) continue;
            if (!isCorruptionAlly(ally)) continue;

            boolean cleaned = false;
            for (MobEffectInstance inst : new ArrayList<>(ally.getActiveEffects())) {
                if (inst.getEffect().isBeneficial()) continue;
                ally.removeEffect(inst.getEffect());
                cleaned = true;
            }
            if (cleaned) count++;
        }
        return count;
    }

    // ---- 主题效果快捷入口，省去各生物重复 import 与常量 ----

    public static void corrupt(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, CorruptionEffects.CORRUPTION.get(), duration, amplifier);
    }

    public static void bleed(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, CorruptionEffects.BLEEDING.get(), duration, amplifier);
    }

    public static void fear(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, CorruptionEffects.FEAR.get(), duration, amplifier);
    }

    public static void lysis(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, CorruptionEffects.LYSIS.get(), duration, amplifier);
    }

    public static void slow(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier);
    }

    public static void wither(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, MobEffects.WITHER, duration, amplifier);
    }

    public static void poison(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, MobEffects.POISON, duration, amplifier);
    }

    public static void nausea(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, MobEffects.CONFUSION, duration, amplifier);
    }

    public static void hunger(LivingEntity target, int duration, int amplifier) {
        applyEffect(target, MobEffects.HUNGER, duration, amplifier);
    }

    // =========================================================================
    // 吸取 / 回复
    // =========================================================================

    /**
     * 汲血：按伤害比例回血，自身残血时效果翻倍。
     *
     * @param ratio 基础吸血比例（0~1）
     */
    public static void lifesteal(LivingEntity self, float damage, float ratio) {
        if (damage <= 0) return;
        float effective = ratio;
        if (self.getHealth() <= self.getMaxHealth() * 0.5F) effective *= 2.0F;
        float heal = Math.min(self.getMaxHealth() - self.getHealth(), damage * effective);
        if (heal > 0) self.heal(heal);
    }

    // =========================================================================
    // 腐蚀池 / 阵营查询
    // =========================================================================

    /**
     * 生成一片腐蚀液池。
     *
     * @param pos      池中心（通常取技能落点）
     * @param radius   半径
     * @param lifetime 持续 tick
     * @param damage   每跳伤害
     */
    /**
     * 生成一片腐蚀液池。
     * <p>TODO(批次 2)：等 CorruptionPoolEntity 移植后接回；当前批次使用的生物不依赖它。</p>
     */
    public static void spawnPoolAt(Level level, Vec3 pos, double radius,
                                   int lifetime, float damage, int interval,
                                   @Nullable LivingEntity owner) {
        // TODO: port CorruptionPoolEntity in skill batch 2
    }

    /** 半径内的同族腐蚀生物数量（不含自己） */
    public static int countAllies(Mob source, double radius) {
        int count = 0;
        AABB area = source.getBoundingBox().inflate(radius);
        for (LivingEntity ally : source.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (ally == source || !ally.isAlive()) continue;
            if (isCorruptionAlly(ally)) count++;
        }
        return count;
    }

    /** 血量百分比 0~1 */
    public static float healthRatio(LivingEntity entity) {
        float max = entity.getMaxHealth();
        return max <= 0 ? 0F : Mth.clamp(entity.getHealth() / max, 0F, 1F);
    }

    /** 是否腐化阵营生物（用于友军增益 / 净化 / 计数）。 */
    public static boolean isCorruptionAlly(LivingEntity entity) {
        return entity instanceof IInfectedEntity infected
                && RottenFaction.isCorruption(infected.getInfectionType());
    }
}
