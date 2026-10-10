package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.hall.overworld.registry.RegisterParticles;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.BaseInfectedEntity;
import org.bytechen.hall.utils.TickUtils;
import org.bytechen.hall.utils.entity.EntityParticleUtils;

import java.util.function.Consumer;

/**
 * 溃烂系生物的公共基类（斥候 {@link ScoutEntity}、巨碑 {@link MonolithEntity}）。
 * <p>
 * 目前只管一件事：<b>周期性从身上掉落「溃烂肉屑」粒子</b>。
 * <ul>
 *   <li>计时：{@link TickUtils#tickCountTimerWithoutStart(int, int)}，每 {@link #MEAT_DRIP_INTERVAL} tick 一次</li>
 *   <li>粒子：{@link EntityParticleUtils#spawnParticles(net.minecraft.world.entity.Entity,
 *       net.minecraft.core.particles.ParticleOptions, double, double, float)}
 *       —— 在碰撞箱内随机撒点，数量随体积自动缩放，服务端发送所以所有玩家都能看到</li>
 * </ul>
 * 只在服务端跑（客户端不重复生成），假死/死亡时不掉。
 */
public abstract class BaseUlceratedEntity extends BaseInfectedEntity {

    /** 掉肉屑的周期（tick） */
    private static final int MEAT_DRIP_INTERVAL = 40;
    /** 粒子密度：实际数量 = 碰撞箱体积 × 密度 + 5（上限 200，见 EntityParticleUtils） */
    private static final double MEAT_DRIP_DENSITY = 1.0D;
    /** 粒子初速度随机范围（格/tick） */
    private static final double MEAT_DRIP_SPEED = 0.06D;
    /** 生成范围系数：碰撞箱缩放，1.0 = 正好贴着碰撞箱 */
    private static final float MEAT_DRIP_RANGE_SCALE = 0.85F;

    /** 受伤溅射：密度 */
    private static final double HURT_DRIP_DENSITY = 2.0D;
    /** 受伤溅射：初速度随机范围（格/tick） */
    private static final double HURT_DRIP_SPEED = 0.12D;
    /** 受伤溅射：生成范围系数 */
    private static final float HURT_DRIP_RANGE_SCALE = 1.0F;

    protected BaseUlceratedEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                                  Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return; // 粒子由服务端发送，客户端不重复生成
        if (this.isFakeDying()) return;

        if (!TickUtils.tickCountTimerWithoutStart(this.tickCount, MEAT_DRIP_INTERVAL)) return;

        EntityParticleUtils.spawnParticles(this, RegisterParticles.ULCERATED_MEAT.get(),
                MEAT_DRIP_DENSITY, MEAT_DRIP_SPEED, MEAT_DRIP_RANGE_SCALE);
    }

    /**
     * 受伤时也溅一次肉屑。
     * <p>
     * 用 {@code hurtTime} 做节流：{@code hurtTime > 0} 说明上一个受击闪烁窗口（默认 10 tick）还没走完，
     * 这期间的连续伤害（例如陨石 AoE 每 tick 清零无敌帧）不再重复生成粒子，
     * 只有这一轮受击的<b>第一次</b>会溅肉。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean firstHitOfWindow = this.hurtTime == 0; // super.hurt 之前读，之后会被置成 hurtDuration

        boolean hurt = super.hurt(source, amount);

        if (hurt && firstHitOfWindow && !this.level().isClientSide() && !this.isFakeDying()) {
            EntityParticleUtils.spawnParticles(this, RegisterParticles.ULCERATED_MEAT.get(),
                    HURT_DRIP_DENSITY, HURT_DRIP_SPEED, HURT_DRIP_RANGE_SCALE);
        }
        return hurt;
    }
}
