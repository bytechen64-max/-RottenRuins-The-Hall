package org.bytechen.hall.event.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.population.ecological.HeavyBombEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;

/**
 * HeavyBomb 的死亡效果 —— 巨大冲击波 + 原地留下小型黑洞。
 *
 * <h3>为什么不用实体自己的 {@code die()} 重写</h3>
 * {@link org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity}
 * 的 {@code hurt()} 与 {@code die()} 都会拦截一层假死逻辑
 * （{@code fakeDeathEnabled} 时 {@code die()} 直接 return、由假死计时器
 * 走 {@code remove(KILLED)} 而不是走死亡流程），从实体内部挂钩很容易
 * 被这条分支绕过。改挂 Forge 的 {@code LivingDeathEvent}：它由
 * {@code LivingEntity.die()} 的末尾触发，一次生命只会来一次，且不受
 * 任何子类重写影响。
 *
 * <h3>幂等</h3>
 * 用实体持久化数据打一个 {@code HeavyBombDeathFx} 标记，
 * 即使事件因为某种原因重复投递，效果也只会放一次。
 */
public final class HeavyBombDeathEffect {

    // ── 调参区 ──

    /** 冲击波半径（格）。 */
    public static final float SHOCKWAVE_RADIUS = 20.0F;
    /**
     * 冲击波扩散速度（格/秒）。
     * <p>{@code ShockwaveEntity} 的半径按 {@code age / 20 * speed} 增长，
     * 且波前一旦达到 {@code radius} 就立即 discard，所以
     * {@code speed / 20 * lifetime} 应当正好等于 {@code radius}，
     * 否则波会在半途突然消失（speed 偏小）或还没来得及铺满就被切掉。
     * 26.6 / 20 × 15 = 19.95 格 ≈ 20 格。</p>
     * <p>取 26.6（约 0.75 秒扫完 20 格）而不是更慢的值：冲击波是爆炸的
     * 瞬时现象，太慢会显得像气浪而不是冲击波。想更缓就同时调大
     * {@link #SHOCKWAVE_LIFE}，两者必须保持 20 格。</p>
     */
    public static final float SHOCKWAVE_SPEED = 26.6F;
    /** 冲击波寿命（tick）。0.75 秒 —— 足够看到扩散过程，又不拖沓。 */
    public static final int SHOCKWAVE_LIFE = 15;
    /** 冲击波（渲染）ringWidth，越厚波前壳越宽。 */
    public static final float SHOCKWAVE_RING_WIDTH = 1.2F;

    /** 实际破坏与伤害半径（格）。冲击波只是视觉，破坏靠爆炸。 */
    public static final float EXPLOSION_RADIUS = 5.0F;

    /**
     * 爆炸是否破坏方块。
     * <p>固定为 {@code false} —— 用 {@link Level.ExplosionInteraction#NONE}
     * 让爆炸只造成伤害与击退，不改变地形。想恢复地形破坏就把这个开关
     * 改成 {@code true}（会换成 MOB 模式，玩家自行承担后果）。</p>
     */
    public static final boolean EXPLOSION_BREAKS_BLOCKS = false;

    /** 黑洞存活时长（tick）—— 400 tick = 20 秒。 */
    public static final int BLACK_HOLE_LIFE =20;

    /**
     * 黑洞的史瓦西半径（方块）。越小盘面越小：
     * 渲染器的透镜球体半径是 6·Rs，所以 0.65 对应约 3.9 格的球。
     */
    public static final float BLACK_HOLE_RADIUS = 0.65F;

    /**
     * 事件视界半径（格）—— 进入后每 tick 受到 20 点物理伤害。
     * <p>只取 1.5 格（略大于透镜球体半径），是一个真正“贴脸才危险”的
     * 内圈；引力则照旧覆盖 40 格。</p>
     */
    public static final float BLACK_HOLE_KILL_RADIUS = 1.5F;

    /** 幂等标记的 NBT 键。 */
    private static final String FX_DONE_KEY = "HeavyBombDeathFx";

    private HeavyBombDeathEffect() {}

    /** 由 {@code LivingDeathEvent} 调用。 */
    public static void trigger(HeavyBombEntity bomb) {
        if (bomb.level().isClientSide()) return;

        // 幂等：同一只炸弹只放一次
        CompoundTag data = bomb.getPersistentData();
        if (data.getBoolean(FX_DONE_KEY)) return;
        data.putBoolean(FX_DONE_KEY, true);

        Vec3 center = bomb.position();
        HallMod.LOGGER.debug("[HeavyBomb] death effect at {} {} {}", center.x, center.y, center.z);

        Level level = bomb.level();

        // ① 巨大冲击波（纯视觉）
        ShockwaveEntity.spawn(level, center,
                SHOCKWAVE_SPEED, SHOCKWAVE_RADIUS, SHOCKWAVE_LIFE, 1.0F,
                0.10F, SHOCKWAVE_RING_WIDTH);

        // ② 实际伤害。
        //    用 ExplosionInteraction.NONE：只结算伤害与击退，**不破坏方块**。
        //    单独包 try：半径 20 的爆炸范围很大，是最容易出问题的一步，
        //    绝不能让它把后面的黑洞生成一起吞掉。
        try {
            DamageSource source = bomb.getLastDamageSource() != null
                    ? bomb.getLastDamageSource()
                    : level.damageSources().magic();
            level.explode(bomb, source, null,
                    center.x, center.y, center.z,
                    EXPLOSION_RADIUS, false,
                    EXPLOSION_BREAKS_BLOCKS
                            ? Level.ExplosionInteraction.MOB
                            : Level.ExplosionInteraction.NONE);
        } catch (Throwable t) {
            HallMod.LOGGER.error("[HeavyBomb] explosion failed; black hole will still spawn", t);
        }

        // ③ 原地留下小型黑洞。引力与伤害由 BlackHolePhysicsHandler 计算，
        //    实体自身不承载任何逻辑。
        try {
            BlackHoleEntity hole = BlackHoleEntity.spawnSmall(
                    level, center, BLACK_HOLE_LIFE,
                    BLACK_HOLE_RADIUS, BLACK_HOLE_KILL_RADIUS);
            HallMod.LOGGER.debug("[HeavyBomb] black hole spawned: id={} mass={}",
                    hole.getId(), hole.getColliderVolume());
        } catch (Throwable t) {
            HallMod.LOGGER.error("[HeavyBomb] black hole spawn FAILED", t);
        }
    }
}
