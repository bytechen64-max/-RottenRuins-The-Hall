package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseRefactoredEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 长臂兽（重构种）。
 *
 * <p>技能：<b>C7 钩刺拖拽</b> —— 用手臂弹出一枚爪钩，命中后把目标拽回身前。
 * 这让长臂兽从"只能打近身"变成"能把逃跑的人拉回来"，与近战扇形挥击组成
 * "钩过来 → 扫一圈"的起手式。</p>
 */
public class LongArmBeastEntity extends BaseRefactoredEntity {

    // =========================================================================
    // C7 钩刺拖拽参数
    // =========================================================================
    private static final int HOOK_COOLDOWN = 140;
    private static final double HOOK_MIN_RANGE = 4.0;
    private static final double HOOK_MAX_RANGE = 13.0;
    private static final int HOOK_WINDUP = 20;
    private static final int HOOK_TRAVEL = 10;
    private static final float HOOK_DAMAGE = 7.0F;

    private int hookCooldown = 60;
    private int hookWindup = -1;
    /** 本次钩击是否已经命中（防止一次钩子反复拖拽） */
    private boolean hookLanded = false;

    private LivingEntity hookTarget;
    private int hookContactTimer = 0;

    public LongArmBeastEntity(EntityType<? extends LongArmBeastEntity> entityType, Level level,
                              Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);   // 该模型无 run 动画：始终走 walk
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    // =========================================================================
    // C7 钩刺拖拽
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (hookCooldown > 0) hookCooldown--;

        if (hookWindup >= 0) {
            hookWindup--;

            float progress = 1.0F - (float) hookWindup / HOOK_WINDUP;
            CorruptionSkillUtil.chargeIn(this, 2.0, 5, progress);

            LivingEntity target = this.getTarget();
            if (target != null) CorruptionSkillUtil.faceTarget(this, target);

            if (hookWindup < 0) {
                if (target == null || !target.isAlive()) {
                    hookCooldown = 40;
                } else {
                    launchHook(target);
                }
            }
            return;
        }

        tickHookContact();

        // 满足条件则起钩：中远距离的目标会被拽回身前
        if (hookWindup < 0 && hookCooldown <= 0) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                double dist = Math.sqrt(this.distanceToSqr(target));
                if (dist >= HOOK_MIN_RANGE && dist <= HOOK_MAX_RANGE) {
                    hookWindup = HOOK_WINDUP;
                }
            }
        }
    }

    /** 弹出爪钩：一枚细长立方体朝目标飞去。 */
    private void launchHook(LivingEntity target) {
        var hook = CorruptionEntityTypes.CUBOID.get().create(this.level());
        if (hook == null) return;

        Vec3 spawn = this.position().add(0, this.getBbHeight() * 0.7, 0);
        hook.setPos(spawn.x, spawn.y, spawn.z);
        hook.setSize(0.4F, 0.4F, 1.4F);
        hook.setOwner(this);
        hook.setMaxAge(HOOK_TRAVEL + 6);
        hook.moveTo(target.position().add(0, target.getBbHeight() * 0.5, 0), HOOK_TRAVEL);

        hook.setOnHit((self, hitTarget) -> {
            if (hitTarget == this) return;
            applyHookDrag(hitTarget);
        });

        this.level().addFreshEntity(hook);

        hookTarget = target;
        hookLanded = false;
        hookContactTimer = HOOK_TRAVEL + 4;
        hookCooldown = HOOK_COOLDOWN;

        CorruptionSkillFx.breath(this.level(), spawn,
                target.position().subtract(spawn).normalize(), 4.0,
                CorruptionSkillUtil.CORRUPTION_RED, 12);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 0.8F, 0.1F);
    }

    /**
     * 钩爪到达后的拖拽结算。
     * <p>单独的计时通道：{@code CuboidEntity} 的 onHit 只在碰撞箱相交时触发，
     * 目标在飞行期间可能走动；到点即判定，避免"明明瞄准了却什么都没发生"。</p>
     */
    private void tickHookContact() {
        if (hookContactTimer <= 0) return;
        hookContactTimer--;

        LivingEntity target = hookTarget;
        if (target == null || !target.isAlive()) {
            hookTarget = null;
            hookContactTimer = 0;
            return;
        }

        if (hookContactTimer == 0) {
            applyHookDrag(target);
            hookTarget = null;
        }
    }

    /** 命中结算：伤害 + 拉回身前 + 短暂减速。 */
    private void applyHookDrag(LivingEntity target) {
        if (hookLanded) return;
        if (!CorruptionSkillUtil.isValidTarget(this, target)) return;

        hookLanded = true;

        if (!CorruptionSkillUtil.strike(this, target, HOOK_DAMAGE, 0)) return;

        // 拉到身前 2 格，避免直接贴脸导致模型穿模
        Vec3 anchor = this.position().add(
                Vec3.directionFromRotation(0, this.getYRot()).scale(2.0));
        CorruptionSkillUtil.pull(anchor, target, 0.95, 0.35);
        CorruptionSkillUtil.slow(target, 60, 2);
        CorruptionSkillUtil.bleed(target, 60, 0);

        CorruptionSkillFx.impact(this.level(), target.position(),
                CorruptionSkillUtil.CORRUPTION_RED, 0.7F);
        CorruptionSkillFx.breath(this.level(), target.position(),
                this.position().subtract(target.position()).normalize(), 2.0,
                CorruptionSkillUtil.CORRUPTION_RED, 8);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.CHAIN_HIT, 1.0F, 0.9F, 0.1F);
    }
}
