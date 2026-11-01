package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bytechen.infcore.core.evolution.EvolutionManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.BoneSpikeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 腐化系 · 同化种（原 corruption host 族）薄层基类。
 *
 * <p>承载同化种共用的技能开关（对应 corruption 的 B 系技能），由各生物在构造器里按需打开：
 * B1 腐化唾液吐息 / B9 尸群践踏 / B10 濒死骨爆 / B11 血肉牵引 / B12 汲血回复。</p>
 */
public abstract class BaseHostEntity extends BaseCorruptionEntity {

    // ==================== B1 腐化唾液吐息 ====================
    protected boolean spitEnabled = false;
    protected float spitDamage = 6.0F;
    protected int spitCooldown = 50;
    protected double spitRange = 12.0;
    private int spitTimer = 20;

    // ==================== B9 尸群践踏 ====================
    protected boolean hordeChargeEnabled = false;
    protected int hordeRequired = 3;
    protected double hordeRadius = 12.0;
    private int hordeTimer = 20;

    // ==================== B10 濒死骨爆 ====================
    protected boolean boneBurstEnabled = false;
    protected int boneBurstCount = 10;
    protected float boneBurstDamage = 4.0F;

    // ==================== B11 血肉牵引 / B12 汲血回复 ====================
    protected boolean fleshPullEnabled = false;
    protected double fleshPullStrength = 0.9;
    protected boolean lifestealEnabled = false;
    protected float lifestealRatio = 0.35F;

    protected BaseHostEntity(EntityType<? extends BaseHostEntity> entityType, Level level,
                             Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }

    // ==================== 开关 ====================

    protected BaseHostEntity enableSpit(float damage, int cooldown, double range) {
        this.spitEnabled = true;
        this.spitDamage = damage;
        this.spitCooldown = cooldown;
        this.spitRange = range;
        return this;
    }

    protected BaseHostEntity enableHordeCharge(int requiredAllies, double radius) {
        this.hordeChargeEnabled = true;
        this.hordeRequired = requiredAllies;
        this.hordeRadius = radius;
        return this;
    }

    protected BaseHostEntity enableBoneBurst(int count, float damage) {
        this.boneBurstEnabled = true;
        this.boneBurstCount = count;
        this.boneBurstDamage = damage;
        return this;
    }

    protected BaseHostEntity enableFleshPull(double strength) {
        this.fleshPullEnabled = true;
        this.fleshPullStrength = strength;
        return this;
    }

    protected BaseHostEntity enableLifesteal(float ratio) {
        this.lifestealEnabled = true;
        this.lifestealRatio = ratio;
        return this;
    }

    // ==================== 技能主循环 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;
        tickSpit();
        tickHordeCharge();
    }

    /** B1：目标 2.5~range 时吐出一枚腐化弹。 */
    private void tickSpit() {
        if (!spitEnabled) return;
        if (spitTimer > 0) {
            spitTimer--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < 2.5 || dist > spitRange) return;

        CorruptionSkillUtil.faceTarget(this, target);
        Vec3 origin = this.position().add(0, this.getBbHeight() * 0.7, 0);
        CorruptionSkillFx.breath(this.level(), origin,
                target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin).normalize(),
                2.5, 0xFFB0200F, 10);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.LLAMA_SPIT, 1.0F, 0.9F, 0.1F);

        CorruptionDart dart = CorruptionDart
                .shoot(this.level(), this, target, 0.85F, CorruptionDart.RED, 0.22F, true)
                .setDamage(spitDamage)
                .setMaxLifetime(80)
                .setHitEffect(CorruptionEffects.CORRUPTION.get(), 120, 0);
        this.level().addFreshEntity(dart);

        spitTimer = spitCooldown;
    }

    /** B9：附近同族够多时，全队加速 + 增伤。 */
    private void tickHordeCharge() {
        if (!hordeChargeEnabled) return;
        if (hordeTimer > 0) {
            hordeTimer--;
            return;
        }
        hordeTimer = 20;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (CorruptionSkillUtil.countAllies(this, hordeRadius) < hordeRequired) return;

        CorruptionSkillUtil.buffAllies(this, hordeRadius, MobEffects.MOVEMENT_SPEED, 140, 1);
        CorruptionSkillUtil.buffAllies(this, hordeRadius, MobEffects.DAMAGE_BOOST, 140, 0);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.ZOMBIE_AMBIENT, 1.0F, 0.9F, 0.1F);
    }

    /** B10：爆体结束时环状发射骨刺（必须在 super.onBurstEnd 之前做）。 */
    @Override
    protected void onBurstEnd() {
        if (boneBurstEnabled && !this.level().isClientSide) {
            boneBurst();
        }
        super.onBurstEnd();
    }

    private void boneBurst() {
        CorruptionSkillUtil.burst(this, 1.6, 18, 0.25);
        CorruptionSkillFx.pillar(this.level(), this.position(), 1.6,
                CorruptionSkillUtil.CORRUPTION_RED, 16);
        CorruptionSkillFx.bloodMist(this.level(), this.position(), 14);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.SKELETON_HURT, 1.2F, 0.8F, 0.1F);

        Vec3 origin = this.position().add(0, this.getBbHeight() * 0.5, 0);
        for (int i = 0; i < boneBurstCount; i++) {
            double angle = i * (Math.PI * 2 / boneBurstCount);
            double lift = (i % 2 == 0) ? 0.12 : 0.42;
            Vec3 dir = new Vec3(Math.cos(angle), lift, Math.sin(angle)).normalize();
            float speed = 0.62F + this.random.nextFloat() * 0.18F;
            BoneSpikeEntity spike = BoneSpikeEntity.launch(
                    this.level(), this, origin, dir, speed, boneBurstDamage);
            this.level().addFreshEntity(spike);
        }
    }

    /** 近战 + B11/B12 命中附带效果。 */
    @Override
    protected boolean getAttack(LivingEntity target) {
        boolean hit = super.getAttack(target);
        if (!hit) return false;

        if (fleshPullEnabled) {
            CorruptionSkillUtil.pull(this.position(), target, fleshPullStrength, 0.25);
            CorruptionSkillUtil.bleed(target, 40, 0);
            CorruptionSkillFx.impact(this.level(), target.position(),
                    CorruptionSkillUtil.CORRUPTION_RED, 0.6F);
        }
        if (lifestealEnabled) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            CorruptionSkillUtil.lifesteal(this, damage, lifestealRatio);
            CorruptionSkillFx.impact(this.level(), this.position(), 0xFFB0202F, 0.5F);
        }
        return true;
    }

    // =========================================================================
    // 击杀进阶：同化种击杀 3 个生物后进化为对应的腐化种（degenerate）
    // =========================================================================

    /** 击杀数达到该值触发进阶 */
    public static final int EVOLVE_KILL_THRESHOLD = 3;

    @Override
    public void onKilledEntity(Entity killed) {
        super.onKilledEntity(killed);

        if (this.level().isClientSide || !this.isAlive()) return;
        if (this.getKillCount() != EVOLVE_KILL_THRESHOLD) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 只有数据里配置了对应腐化种的宿主才会真正进化（没有对应类型则返回 false）
        CorruptionSkillUtil.burst(this, 2.2, 24, 0.4);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 2.5,
                CorruptionSkillUtil.CORRUPTION_RED, 24);
        EvolutionManager.applyEvolution(serverLevel, this, this.getFaction().evolutionRuleType());
    }
}
