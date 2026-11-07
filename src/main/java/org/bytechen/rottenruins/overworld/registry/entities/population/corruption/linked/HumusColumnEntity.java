package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseLinkedEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 腐植柱（联结种站桩增益单位）。
 *
 * <p>每次 Tick 对范围内一个盟友施加增益、对随机一个敌人施加减益（可叠加时长/等级），
 * 并周期性释放「净化脉冲」清除范围内盟友的全部负面效果。死亡时留下放大的腐蚀领域（C9）。</p>
 */
public class HumusColumnEntity extends BaseLinkedEntity {

    public static final int GLOW_DURATION_TICKS = 30;

    private static final EntityDataAccessor<Integer> GLOW_TIMER =
            SynchedEntityData.defineId(HumusColumnEntity.class, EntityDataSerializers.INT);

    private static final int BUFF_COOLDOWN_TICKS = 100;
    private static final double BUFF_RANGE = 10.0;

    /** 净化脉冲冷却（约 8 秒） */
    private static final int PURIFY_COOLDOWN = 160;
    /** 净化半径 */
    private static final double PURIFY_RADIUS = 14.0;

    private static final List<BuffEntry> POSITIVE_BUFFS = new ArrayList<>();
    private static final List<BuffEntry> NEGATIVE_BUFFS = new ArrayList<>();

    static {
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.MOVEMENT_SPEED, 200, 1));
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.DAMAGE_BOOST, 200, 1));
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.REGENERATION, 200, 1));
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.DAMAGE_RESISTANCE, 200, 1));
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.FIRE_RESISTANCE, 200, 0));
        POSITIVE_BUFFS.add(new BuffEntry(MobEffects.ABSORPTION, 200, 2));
        POSITIVE_BUFFS.add(new BuffEntry(CorruptionEffects.SCLEROSIS.get(), 200, 2));

        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.WEAKNESS, 200, 1, 10));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, 10));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.POISON, 200, 1, 8));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.WITHER, 200, 1, 5));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.BLINDNESS, 200, 0, 5));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.HUNGER, 200, 1, 5));
        NEGATIVE_BUFFS.add(new BuffEntry(MobEffects.UNLUCK, 200, 1, 5));
        NEGATIVE_BUFFS.add(new BuffEntry(CorruptionEffects.CORRUPTION.get(), 200, 1, 8));
        NEGATIVE_BUFFS.add(new BuffEntry(CorruptionEffects.LYSIS.get(), 200, 0, 1));
        NEGATIVE_BUFFS.add(new BuffEntry(CorruptionEffects.FEAR.get(), 200, 1, 5));
        NEGATIVE_BUFFS.add(new BuffEntry(CorruptionEffects.BLEEDING.get(), 200, 1, 5));
    }

    private int buffCooldown = 0;
    private int purifyCooldown = PURIFY_COOLDOWN / 2;

    public HumusColumnEntity(EntityType<? extends HumusColumnEntity> entityType, Level level,
                             Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        // 站桩单位不需要假死演出
        this.setFakeDeathEnabled(false);
        // C9 辐照领域：作为阵营增益核心，被拆掉时留下污染区
        this.enableDeathField(4.0F, 200, 3.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(GLOW_TIMER, 0);
    }

    // =========================================================================
    // 光环
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        if (buffCooldown <= 0) {
            applySingleTargetBuffs();
            buffCooldown = BUFF_COOLDOWN_TICKS;
        } else {
            buffCooldown--;
        }

        // 净化脉冲：周期性清除盟友身上的全部负面效果（含腐蚀）
        if (purifyCooldown > 0) {
            purifyCooldown--;
        } else {
            performPurifyPulse();
            purifyCooldown = PURIFY_COOLDOWN;
        }

        if (getGlowTimer() > 0) {
            setGlowTimer(getGlowTimer() - 1);
        }
    }

    /** 净化脉冲：清掉半径内所有盟友的负面效果，并短暂回血。 */
    private void performPurifyPulse() {
        int purified = CorruptionSkillUtil.purifyAllies(this, PURIFY_RADIUS);
        if (purified <= 0) return;

        CorruptionSkillFx.pillar(this.level(), this.position(), 3.0,
                CorruptionSkillUtil.CORRUPTION_RED, 18);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 3.5,
                CorruptionSkillUtil.CORRUPTION_RED, 24);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F, 0.1F);

        CorruptionSkillUtil.buffAllies(this, PURIFY_RADIUS, MobEffects.REGENERATION, 120, 0);
        this.setGlowTimer(GLOW_DURATION_TICKS);
    }

    private void applySingleTargetBuffs() {
        AABB area = this.getBoundingBox().inflate(BUFF_RANGE);
        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);

        List<LivingEntity> allies = new ArrayList<>();
        List<LivingEntity> enemies = new ArrayList<>();

        for (LivingEntity e : entities) {
            if (CorruptionSkillUtil.isCorruptionAlly(e)) {
                allies.add(e);
            } else {
                enemies.add(e);
            }
        }

        // 只处理第一个盟友（原工程按 kind/killCount 优先级排序；合并工程暂无 kind 体系，简化为就近取首个）
        if (!allies.isEmpty()) {
            applyBuffToSingleAlly(allies.get(0));
        }

        // 随机选择一个敌人
        if (!enemies.isEmpty()) {
            LivingEntity target = enemies.get(this.random.nextInt(enemies.size()));
            if (target instanceof Player player && player.isCreative()) return;
            applyDebuffToSingleEnemy(target);
        }
    }

    /** 对单个盟友施加增益（使用可叠加效果）：先尝试移除负面效果，否则随机正面 buff。 */
    private void applyBuffToSingleAlly(LivingEntity ally) {
        boolean applied = false;

        for (MobEffectInstance inst : ally.getActiveEffects()) {
            if (isNegativeEffect(inst.getEffect())) {
                ally.removeEffect(inst.getEffect());
                applied = true;
                break;
            }
        }

        if (!applied && !POSITIVE_BUFFS.isEmpty()) {
            BuffEntry positive = POSITIVE_BUFFS.get(this.random.nextInt(POSITIVE_BUFFS.size()));
            CorruptionSkillUtil.stackEffect(ally, positive.effect, positive.duration, positive.amplifier);
            applied = true;
        }

        if (applied) {
            sendSparkTo(ally);
            this.setGlowTimer(GLOW_DURATION_TICKS);
        }
    }

    /** 对单个敌人施加减益（使用可叠加效果）。 */
    private void applyDebuffToSingleEnemy(LivingEntity enemy) {
        boolean applied = false;

        BuffEntry debuff = selectRandomDebuff();
        if (debuff != null) {
            CorruptionSkillUtil.stackEffect(enemy, debuff.effect, debuff.duration, debuff.amplifier);
            applied = true;
        } else {
            for (MobEffectInstance inst : enemy.getActiveEffects()) {
                if (isPositiveEffect(inst.getEffect())) {
                    enemy.removeEffect(inst.getEffect());
                    applied = true;
                    break;
                }
            }
        }

        if (applied) {
            sendSparkTo(enemy);
            this.setGlowTimer(GLOW_DURATION_TICKS);
        }
    }

    @Nullable
    private BuffEntry selectRandomDebuff() {
        if (NEGATIVE_BUFFS.isEmpty()) return null;
        int totalWeight = NEGATIVE_BUFFS.stream().mapToInt(e -> e.weight).sum();
        int rand = this.random.nextInt(totalWeight);
        int cum = 0;
        for (BuffEntry entry : NEGATIVE_BUFFS) {
            cum += entry.weight;
            if (rand < cum) return entry;
        }
        return null;
    }

    private boolean isPositiveEffect(MobEffect effect) {
        return POSITIVE_BUFFS.stream().anyMatch(e -> e.effect == effect);
    }

    private boolean isNegativeEffect(MobEffect effect) {
        return NEGATIVE_BUFFS.stream().anyMatch(e -> e.effect == effect);
    }

    /** 表现：红色电火花连到目标（原工程是客户端红雷渲染器，合并工程用服务端粒子替代）。 */
    private void sendSparkTo(LivingEntity target) {
        if (this.level().isClientSide()) return;
        Vec3 start = new Vec3(this.getX(), this.getY() + 2.2, this.getZ());
        Vec3 end = target.position().add(0, target.getEyeHeight() / 2, 0);
        CorruptionSkillFx.breath(this.level(), start,
                end.subtract(start).normalize(), start.distanceTo(end),
                CorruptionSkillUtil.CORRUPTION_RED, 10);
        CorruptionSkillFx.impact(this.level(), target.position(), 0xFFB0202F, 0.5F);
    }

    public int getGlowTimer() {
        return this.entityData.get(GLOW_TIMER);
    }

    public void setGlowTimer(int timer) {
        this.entityData.set(GLOW_TIMER, timer);
    }

    // ==================== 禁止移动 ====================

    @Override
    public void travel(Vec3 travelVector) {
        super.travel(Vec3.ZERO);
    }

    /** 禁止被其他实体推动。 */
    @Override
    public void push(Entity entity) {
    }

    // ==================== 内部类 ====================

    private static class BuffEntry {
        final MobEffect effect;
        final int duration;
        final int amplifier;
        final int weight;

        BuffEntry(MobEffect effect, int duration, int amplifier) {
            this(effect, duration, amplifier, 0);
        }

        BuffEntry(MobEffect effect, int duration, int amplifier, int weight) {
            this.effect = effect;
            this.duration = duration;
            this.amplifier = amplifier;
            this.weight = weight;
        }
    }
}
