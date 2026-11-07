package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDegenerateEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.AcidBallProjectile;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 腐化村民（"法师位"）。
 *
 * <ul>
 *   <li><b>C1 酸液瘴气弹</b> —— 抛物线酸弹，落点留下一片持续腐蚀的酸池（区域封锁）；</li>
 *   <li><b>C2 硬化召唤</b> —— 生成血肉墙替自己挡弹道并顺便回血（自我保护）。</li>
 * </ul>
 */
public class DegenerateVillagerEntity extends BaseDegenerateEntity {

    // =========================================================================
    // C1 酸液瘴气弹
    // =========================================================================
    private static final int BILE_COOLDOWN = 90;
    private static final double BILE_MIN_RANGE = 3.5;
    private static final double BILE_MAX_RANGE = 16.0;
    private static final float BILE_IMPACT_DAMAGE = 7.0F;
    private static final float BILE_POOL_RADIUS = 2.8F;
    private static final int BILE_POOL_LIFETIME = 120;
    private static final float BILE_POOL_DAMAGE = 4.0F;

    private int bileCooldown = 40;

    // =========================================================================
    // C2 硬化召唤
    // =========================================================================
    private static final int WALL_COOLDOWN = 220;
    private static final int WALL_COUNT = 4;
    private static final float WALL_SIZE = 1.6F;
    private static final int WALL_LIFETIME = 100;
    private static final float WALL_HEAL = 4.0F;
    private static final float WALL_TRIGGER_HEALTH_RATIO = 0.7F;

    private int wallCooldown = 120;

    public DegenerateVillagerEntity(EntityType<? extends DegenerateVillagerEntity> entityType, Level level,
                                    Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    // =========================================================================
    // 技能主循环
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        // 原版的 15 tick 酸球保留（近程骚扰），叠加新的技能节奏
        if (this.tickCount % 15 == 0) {
            throwAcidBall();
        }

        tickBileGrenade();
        tickHardenedWall();
    }

    /** 原有酸球攻击（抽成方法，逻辑不变）。 */
    private void throwAcidBall() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        AcidBallProjectile acidBall = new AcidBallProjectile(
                CorruptionEntityTypes.ACID_BALL.get(), this, this.level());
        double spawnX = this.getX();
        double spawnY = this.getY() + this.getEyeHeight() - 0.1D;
        double spawnZ = this.getZ();
        acidBall.setPos(spawnX, spawnY, spawnZ);

        double dx = target.getX() - spawnX;
        double dy = target.getEyeY() - acidBall.getY();
        double dz = target.getZ() - spawnZ;
        acidBall.shoot(dx, dy, dz, 1.5F, 0);
        this.level().addFreshEntity(acidBall);
        this.playSound(SoundEvents.LAVA_POP, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    /**
     * [C1] 酸液瘴气弹：抛出抛物线酸弹，落点留下酸池。
     * <p>先在落点画预警圈并固定落点，再发射到该点——玩家看到圈就能跑，
     * 但酸池会持续封住那片区域，形成"要么绕路要么硬吃"的战术选择。</p>
     */
    private void tickBileGrenade() {
        if (bileCooldown > 0) {
            bileCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < BILE_MIN_RANGE || dist > BILE_MAX_RANGE) return;

        Vec3 landing = CorruptionSkillUtil.telegraphGround(this.level(), target, BILE_POOL_RADIUS);
        if (landing == null) return;

        CorruptionSkillFx.breath(this.level(),
                this.position().add(0, this.getEyeHeight() * 0.8, 0),
                landing.subtract(this.position()).normalize(), 3.0, 0xFF7CFF3A, 10);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.LLAMA_SPIT, 1.0F, 0.7F, 0.15F);

        CorruptionDart dart = CorruptionDart
                .shoot(this.level(), this, target, 0.9F, CorruptionDart.ACID, 0.35F, true)
                .setDamage(BILE_IMPACT_DAMAGE)
                .setMaxLifetime(100)
                .withPool(BILE_POOL_RADIUS, BILE_POOL_LIFETIME, BILE_POOL_DAMAGE);
        dart.setHitEffect(CorruptionEffects.LYSIS.get(), 120, 0);
        this.level().addFreshEntity(dart);

        bileCooldown = BILE_COOLDOWN;
    }

    /**
     * [C2] 硬化召唤：血量下降到阈值以下时，在自身周围立起一圈血肉墙。
     * <p>墙用 {@code CuboidEntity} 实现——它在 onHit 之前就会伤害触碰者，
     * 因此天然具备"挡近战突进"的效果，同时配合回血让村民能撑到下一轮施法。</p>
     */
    private void tickHardenedWall() {
        if (wallCooldown > 0) {
            wallCooldown--;
            return;
        }

        if (CorruptionSkillUtil.healthRatio(this) > WALL_TRIGGER_HEALTH_RATIO) return;

        // 表现：由地面向上顶起的红色粒子环
        CorruptionSkillFx.pillar(this.level(), this.position(), 2.2,
                CorruptionSkillUtil.CORRUPTION_RED, 20);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 2.5,
                CorruptionSkillUtil.CORRUPTION_RED, 24);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_ROAR, 0.9F, 1.4F, 0.1F);

        double radius = 2.4;
        for (int i = 0; i < WALL_COUNT; i++) {
            double angle = i * (Math.PI * 2 / WALL_COUNT);
            double wx = this.getX() + Math.cos(angle) * radius;
            double wz = this.getZ() + Math.sin(angle) * radius;

            var wall = CorruptionEntityTypes.CUBOID.get().create(this.level());
            if (wall == null) continue;

            wall.setPos(wx, this.getY() + 0.4, wz);
            wall.setSize(WALL_SIZE, 1.8F, 0.5F);
            wall.setOwner(this);
            wall.setMaxAge(WALL_LIFETIME);
            wall.setOnHit((self, hitTarget) -> {
                if (hitTarget == this) return;
                CorruptionSkillUtil.strike(this, hitTarget, 3.0F, 0.4);
            });
            this.level().addFreshEntity(wall);
        }

        // 硬化代价换来回复：让"打掉墙再打人"成为必要流程
        this.heal(WALL_HEAL);
        CorruptionSkillFx.impact(this.level(), this.position(), 0xFFB0202F, 0.8F);

        wallCooldown = WALL_COOLDOWN;
    }

    /** 禁止被其他实体推动。 */
    @Override
    public void push(Entity entity) {
    }
}
