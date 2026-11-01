package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.deteriorated;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDeterioratedEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 匍匐者 —— 畸变种。
 *
 * <p>技能：<b>C10 地底突袭</b> —— 钻地消失（不可选中）→ 在目标脚下破土 → 斩击并击飞。
 * 破土前地面会冒出土粒与警告圈，玩家可以据此提前位移。</p>
 */
public class GrovelerEntity extends BaseDeterioratedEntity {

    // =========================================================================
    // C10 地底突袭参数
    // =========================================================================
    private static final double BURROW_MIN_RANGE = 5.0;
    private static final double BURROW_MAX_RANGE = 20.0;
    private static final int BURROW_TICKS = 30;
    private static final int EMERGE_WARNING = 14;
    private static final float EMERGE_DAMAGE = 9.0F;
    private static final int EMERGE_RECOVERY = 30;
    private static final int BURROW_COOLDOWN = 220;

    private enum BurrowPhase { NONE, BURROWING, WARNING, RECOVERY }

    private BurrowPhase burrowPhase = BurrowPhase.NONE;
    private int burrowTimer = 0;
    private int burrowCooldown = 120;
    /** 预警期间固定下来的破土点 */
    private Vec3 emergePoint = Vec3.ZERO;
    /** 钻地期间不可被伤害/推动 */
    private boolean invulnerableWhileBurrowed = false;

    public GrovelerEntity(EntityType<? extends GrovelerEntity> entityType, Level level,
                          Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    // =========================================================================
    // C10 地底突袭
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (burrowCooldown > 0) burrowCooldown--;

        switch (burrowPhase) {
            case BURROWING -> tickBurrowing();
            case WARNING -> tickEmergeWarning();
            case RECOVERY -> tickEmergeRecovery();
            case NONE -> tickBurrowTrigger();
        }
    }

    private void tickBurrowTrigger() {
        if (burrowCooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < BURROW_MIN_RANGE || dist > BURROW_MAX_RANGE) return;
        if (!this.onGround()) return;

        // 入地：粒子下沉 + 音效，明确"它钻进去了"
        CorruptionSkillFx.impact(this.level(), this.position(), 0xFF5A3A20, 0.8F);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX(), this.getY() + 0.2, this.getZ(), 20, 0.5, 0.15, 0.5, 0.02);
        }
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.SILVERFISH_HURT, 1.2F, 0.6F, 0.1F);

        this.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, BURROW_TICKS + EMERGE_WARNING + 10, 0, false, false));
        this.setInvulnerable(true);
        this.setInvisible(true);
        invulnerableWhileBurrowed = true;

        burrowPhase = BurrowPhase.BURROWING;
        burrowTimer = BURROW_TICKS;
    }

    /** 钻地移动：以高于常规的速度贴近目标，但保留先兆窗口。 */
    private void tickBurrowing() {
        burrowTimer--;

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            CorruptionSkillUtil.lungeAt(this, target, 0.5, 0);
            CorruptionSkillFx.breath(this.level(), this.position().add(0, 0.1, 0),
                    Vec3.directionFromRotation(0, this.getYRot()), 2.0, 0xFF5A3A20, 4);
        }

        if (burrowTimer <= 0) {
            beginEmergeWarning();
        }
    }

    /**
     * 破土预警：在目标脚下画圈并把落点固定 —— 落点一旦确定就不再跟随目标，
     * 否则玩家跑了也会被打中，先兆就失去意义。
     */
    private void beginEmergeWarning() {
        LivingEntity target = this.getTarget();

        emergePoint = target != null && target.isAlive()
                ? target.position()
                : this.position();

        CorruptionSkillUtil.warningRing(this.level(), emergePoint, 2.2, 18);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.SILVERFISH_AMBIENT, 1.0F, 0.7F, 0.1F);

        burrowPhase = BurrowPhase.WARNING;
        burrowTimer = EMERGE_WARNING;
    }

    private void tickEmergeWarning() {
        burrowTimer--;

        float progress = 1.0F - (float) burrowTimer / EMERGE_WARNING;
        CorruptionSkillUtil.warningRing(this.level(), emergePoint, 2.2, 6 + (int) (progress * 12));
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    emergePoint.x, emergePoint.y + 0.1, emergePoint.z,
                    4, 0.5, 0.1, 0.5, 0.05);
        }

        if (burrowTimer <= 0) {
            emerge();
        }
    }

    /** 破土：瞬移到预警点、解除无敌、斩击并击飞范围内目标。 */
    private void emerge() {
        this.teleportTo(emergePoint.x, emergePoint.y, emergePoint.z);

        this.setInvulnerable(false);
        this.setInvisible(false);
        invulnerableWhileBurrowed = false;
        this.removeEffect(MobEffects.INVISIBILITY);

        CorruptionSkillUtil.burst(this, 2.4, 28, 0.45);
        CorruptionSkillFx.pillar(this.level(), this.position(), 1.8,
                CorruptionSkillUtil.CORRUPTION_RED, 16);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_STEP, 1.3F, 0.7F, 0.1F);
        CorruptionSkillFx.bloodMist(this.level(), this.position(), 12);

        int hits = CorruptionSkillUtil.areaTrueDamage(this, 2.6, EMERGE_DAMAGE * 0.5F, EMERGE_DAMAGE, 1.1);
        if (hits > 0) {
            for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 2.6)) {
                CorruptionSkillUtil.slow(target, 60, 1);
                CorruptionSkillUtil.bleed(target, 100, 0);
            }
        }

        burrowPhase = BurrowPhase.RECOVERY;
        burrowTimer = EMERGE_RECOVERY;
    }

    private void tickEmergeRecovery() {
        burrowTimer--;
        if (burrowTimer <= 0) {
            burrowPhase = BurrowPhase.NONE;
            burrowCooldown = BURROW_COOLDOWN;
        }
    }

    /** 无敌期仅用于钻地；正常受击照旧。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (invulnerableWhileBurrowed) return true;
        return super.isInvulnerableTo(source);
    }

    /** 钻地中不可被推动。 */
    @Override
    public boolean isPushable() {
        return !invulnerableWhileBurrowed;
    }

    @Override
    public void push(Entity entity) {
        if (invulnerableWhileBurrowed) return;
        super.push(entity);
    }
}
