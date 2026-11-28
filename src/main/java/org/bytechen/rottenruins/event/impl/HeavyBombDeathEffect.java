package org.bytechen.rottenruins.event.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.anomaly.AnomalyDamageSource;
import org.bytechen.rottenruins.content.anomaly.HallAnomalyAttacks;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological.HeavyBombEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.ShockwaveEntity;

/**
 * HeavyBomb 死亡效果：巨大冲击波 + 原地留下小型黑洞。挂 LivingDeathEvent 而非重写 die()，
 * 以绕开 AbstractRottenEntity 的假死分支（fakeDeathEnabled 时 die() 直接 return）。
 */
public final class HeavyBombDeathEffect {

    public static final float SHOCKWAVE_RADIUS = 20.0F;
    /** 冲击波速度（格/秒）：{@code speed/20*lifetime} 必须等于 radius，否则波会中途消失或没铺满。 */
    public static final float SHOCKWAVE_SPEED = 26.6F;
    public static final int SHOCKWAVE_LIFE = 15;
    public static final float SHOCKWAVE_RING_WIDTH = 1.2F;

    public static final float EXPLOSION_RADIUS = 5.0F;

    /** 固定 false —— 用 {@link Level.ExplosionInteraction#NONE} 只造成伤害与击退，不改地形。 */
    public static final boolean EXPLOSION_BREAKS_BLOCKS = false;

    public static final int BLACK_HOLE_LIFE =20;

    public static final float BLACK_HOLE_RADIUS = 0.65F;

    /** 事件视界半径（格）：进入后每 tick 受 20 点物理伤害；引力仍覆盖 40 格。 */
    public static final float BLACK_HOLE_KILL_RADIUS = 1.5F;

    private static final String FX_DONE_KEY = "HeavyBombDeathFx";

    private HeavyBombDeathEffect() {}

    public static void trigger(HeavyBombEntity bomb) {
        if (bomb.level().isClientSide()) return;

        CompoundTag data = bomb.getPersistentData();
        if (data.getBoolean(FX_DONE_KEY)) return;
        data.putBoolean(FX_DONE_KEY, true);

        Vec3 center = bomb.position();
        HallMod.LOGGER.debug("[HeavyBomb] death effect at {} {} {}", center.x, center.y, center.z);

        Level level = bomb.level();

        ShockwaveEntity.spawn(level, center,
                SHOCKWAVE_SPEED, SHOCKWAVE_RADIUS, SHOCKWAVE_LIFE, 1.0F,
                0.10F, SHOCKWAVE_RING_WIDTH);

        // 单独包 try：半径 20 的爆炸最易出错，不能让异常吞掉后面的黑洞生成。
        try {
            DamageSource source = bomb.getLastDamageSource() != null
                    ? bomb.getLastDamageSource()
                    : level.damageSources().magic();
            level.explode(bomb, new AnomalyDamageSource(source, bomb.getUUID(), HallAnomalyAttacks.HEAT_HEAVY), null,
                    center.x, center.y, center.z,
                    EXPLOSION_RADIUS, false,
                    EXPLOSION_BREAKS_BLOCKS
                            ? Level.ExplosionInteraction.MOB
                            : Level.ExplosionInteraction.NONE);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[HeavyBomb] explosion failed; black hole will still spawn", t);
        }

        // 引力与伤害由 BlackHolePhysicsHandler 计算，实体自身不承载任何逻辑。
        try {
            BlackHoleEntity hole = BlackHoleEntity.spawnSmall(
                    level, center, BLACK_HOLE_LIFE,
                    BLACK_HOLE_RADIUS, BLACK_HOLE_KILL_RADIUS);
            hole.setAnomalyAttacker(bomb.getUUID());
            HallMod.LOGGER.debug("[HeavyBomb] black hole spawned: id={} mass={}",
                    hole.getId(), hole.getColliderVolume());
        } catch (Throwable t) {
            HallMod.LOGGER.error("[HeavyBomb] black hole spawn FAILED", t);
        }
    }
}
