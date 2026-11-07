package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractCorruptionProjectile;
import org.bytechen.rottenruins.utils.combat.CorruptionDamageUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

/**
 * 骨刺 —— 死亡时从尸体向外弹射的骨制飞刺。
 *
 * <p>替代原先用 {@code CuboidEntity}（白色半透明方块）充数的写法：
 * 现在它是真正的模型化投射物——一根两端收尖的锥形骨刺，
 * 带骨缝、髓腔与两块碎骨摇晃动画，飞行时按自身运动方向倾斜。
 *
 * <h2>为什么命中逻辑写在 onHitLivingEntity 里</h2>
 * 父类 {@link AbstractCorruptionProjectile} 的调用顺序是
 * {@code onHit()} → 服务端 {@code discard()} → {@code onHitEntity()}。
 * 也就是说 {@code onHitEntity} 执行时实体已经被标记移除，
 * 在里面写伤害属于"死后结算"。父类专门留出的 {@code onHitLivingEntity}
 * 才是干净的伤害接口，命中判定与伤害都放在这里。
 */
public class BoneSpikeEntity extends AbstractCorruptionProjectile {

    /** 基础伤害，由发射方通过 {@link #setDamage(float)} 覆盖 */
    private float damage = 4.0F;
    /** 最大存活 tick，避免扎在墙上后永久残留 */
    private int maxLifetime = 40;
    private int age = 0;

    public BoneSpikeEntity(EntityType<? extends AbstractCorruptionProjectile> type, Level level) {
        super(type, level);
    }

    public BoneSpikeEntity(EntityType<? extends AbstractCorruptionProjectile> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    /** 注册时用于注入 GeckoLib 资源 */
    public BoneSpikeEntity(EntityType<? extends AbstractCorruptionProjectile> type, Level level,
                           java.util.function.Consumer<BoneSpikeEntity> consumer) {
        super(type, level);
        if (consumer != null) consumer.accept(this);
    }

    // =========================================================================
    // 配置
    // =========================================================================

    public BoneSpikeEntity setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    public BoneSpikeEntity setMaxLifetime(int ticks) {
        this.maxLifetime = Math.max(1, ticks);
        return this;
    }

    /**
     * 发射一枚骨刺。
     *
     * @param level     服务端世界
     * @param owner     发射者（自身不会被打到）
     * @param origin    起点（通常是尸体中心）
     * @param direction 飞行方向（内部会归一化）
     * @param speed     初速度
     * @param damage    命中伤害
     */
    public static BoneSpikeEntity launch(Level level, LivingEntity owner, Vec3 origin,
                                         Vec3 direction, float speed, float damage) {
        BoneSpikeEntity spike = new BoneSpikeEntity(
                CorruptionEntityTypes.BONE_SPIKE.get(), owner, level);
        spike.setPos(origin.x, origin.y, origin.z);
        spike.setDamage(damage);
        spike.setMaxLifetime(30);
        spike.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        // 发射时先对齐一次朝向，避免第一帧还是默认水平姿态
        spike.aimAlongMotion();
        return spike;
    }

    // =========================================================================
    // 飞行
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        age++;
        if (age >= maxLifetime) {
            this.discard();
        }
    }

    // =========================================================================
    // 命中
    // =========================================================================

    @Override
    protected void onHitLivingEntity(LivingEntity target) {
        if (this.level().isClientSide) return;
        if (!CorruptionSkillUtil.isValidTarget(this.getOwner(), target)) return;

        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            CorruptionDamageUtil.TrueHurt(livingOwner, target, 0, damage, 1, true);
        } else {
            target.hurt(target.damageSources().magic(), damage);
        }

        CorruptionSkillFx.impact(this.level(), target.position(),
                CorruptionSkillUtil.CORRUPTION_RED, 0.3F);
    }

    @Override
    public boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner();
    }

    // =========================================================================
    // 朝向：让骨刺顺着飞行方向躺下
    // =========================================================================

    /**
     * 按当前速度向量写回 yaw/pitch。
     *
     * <p>投射物本身不会自动回写 {@code yRot}/{@code xRot}，
     * 而 GeckoLib 渲染器读取的正是这两个字段，所以必须手动对齐，
     * 否则骨刺会永远保持固定朝向横着飞。
     * 模型本身已按"指向本地 -Z"建模，因此直接用标准 yaw/pitch 即可。
     */
    private void aimAlongMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0e-6) return;

        Vec3 dir = motion.normalize();
        float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
        float pitch = (float) (Math.asin(-dir.y) * (180.0 / Math.PI));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }
}
