package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * 截空 · 凌空斩的位移执行器 —— <b>每 tick 重申一次速度</b>。
 *
 * <h3>为什么一次性给速度不行</h3>
 * <p>直觉上"给一个初速让原版物理去积分"就够了，原版三叉戟的激流也是这么写的。
 * 但实测（诊断日志证实服务端确实执行了 {@code setDeltaMovement}、速度 1.556、
 * 目标位移 11.71 格）玩家<b>几乎不动</b>。原因有两层：</p>
 *
 * <ol>
 *   <li><b>原版激流其实配了一个"接管移动"的开关。</b>
 *       {@code TridentItem#releaseUsing} 在 {@code push()} 之后立刻调了
 *       {@code player.startAutoSpinAttack(20)}，而旋转攻击状态会让
 *       {@code travel()} 走另一条分支（免疫重力、忽略地面摩擦）。
 *       没有那个状态时，下一 tick 开始玩家就回到普通地面物理：
 *       地面摩擦系数是 0.546（草方块 0.6 × 0.91），一 tick 就把 1.556 打成 0.85，
 *       再下一 tick 0.46 —— 位移被吃掉大半。</li>
 *   <li><b>客户端是位置的权威方。</b>玩家自己的移动预测每 tick 都在算位置并上报，
 *       服务端给的单次速度若与客户端预测冲突，很快会被拉回去。
 *       持续重申速度能让两端的意图收敛到同一个方向。</li>
 * </ol>
 *
 * <h3>做法</h3>
 * <p>把"突进中"这个状态存进 {@link Player#getPersistentData()}，
 * 由物品的 {@code inventoryTick} 每 tick 调一次 {@link #tick}，
 * 在 {@link #DURATION} tick 内持续重申水平速度。</p>
 *
 * <p>撞墙即中止：{@code horizontalCollision} 为真说明玩家已经贴到方块上，
 * 继续重申只会让他贴着墙抖，不如干脆停掉，让"撞墙的突进只有一小段"成为可预期的规则。</p>
 *
 * <h3>为什么不用 {@code startAutoSpinAttack}</h3>
 * <p>它会带来旋转攻击的全部症状：身体自旋动画、周围粒子、
 * 以及"必须再次右键才能提前结束"。这个技能的定位是"一步踏出去"，
 * 不是"变成一颗旋转的炮弹"。所以宁可自己实现 12 tick 的速度重申。</p>
 */
public final class VerdictDash {

    /** 突进持续（tick）。 */
    public static final int DURATION = 12;

    private static final String ROOT = "HallVerdictDash";
    private static final String K_TICKS = "T";
    private static final String K_DIR_X = "Dx";
    private static final String K_DIR_Z = "Dz";
    private static final String K_SPEED = "S";

    /** 中止条件：撞到方块（水平碰撞）。 */
    private static boolean stopOnCollision = true;

    private VerdictDash() {}

    /**
     * 开始一次突进。
     *
     * @param dirX  水平方向 x（单位向量）
     * @param dirZ  水平方向 z（单位向量）
     * @param speed 每 tick 的水平速度
     */
    public static void start(Player player, double dirX, double dirZ, double speed) {
        CompoundTag t = player.getPersistentData();
        CompoundTag d = new CompoundTag();
        d.putInt(K_TICKS, DURATION);
        d.putDouble(K_DIR_X, dirX);
        d.putDouble(K_DIR_Z, dirZ);
        d.putDouble(K_SPEED, speed);
        t.put(ROOT, d);
    }

    /** 是否正在突进。 */
    public static boolean isActive(Player player) {
        CompoundTag d = player.getPersistentData().getCompound(ROOT);
        return d.contains(K_TICKS) && d.getInt(K_TICKS) > 0;
    }

    /** 主动中止（例如被攻击、上了坐骑）。 */
    public static void stop(Player player) {
        player.getPersistentData().remove(ROOT);
    }

    /**
     * 每 tick 由物品的 {@code inventoryTick} 调用。
     *
     * <p>只在服务端有效 —— 客户端的位置由服务端同步，这里重申没有意义，
     * 而且客户端没有权威的 {@code horizontalCollision}。</p>
     */
    public static void tick(Player player) {
        Level level = player.level();
        CompoundTag t = player.getPersistentData();
        if (!t.contains(ROOT)) return;

        CompoundTag d = t.getCompound(ROOT);
        int left = d.getInt(K_TICKS);
        if (left <= 0) {
            t.remove(ROOT);
            return;
        }

        // 撞墙 / 骑乘 / 死亡：立刻结束，别让玩家贴着墙抖
        if (player.isPassenger() || !player.isAlive()
                || (stopOnCollision && player.horizontalCollision)) {
            t.remove(ROOT);
            VerdictDebug.log("  dash 中止 left=%d collision=%b passenger=%b",
                    left, player.horizontalCollision, player.isPassenger());
            return;
        }

        // 重申水平速度；纵向保持原版物理（地面给一点抬升，让连按能升空）
        double speed = d.getDouble(K_SPEED);
        double lift = player.onGround() ? 0.42 : player.getDeltaMovement().y;
        player.setDeltaMovement(d.getDouble(K_DIR_X) * speed, lift, d.getDouble(K_DIR_Z) * speed);
        player.hurtMarked = true;
        player.hasImpulse = true;

        d.putInt(K_TICKS, left - 1);
        if (left - 1 <= 0) {
            t.remove(ROOT);
            VerdictDebug.log("  dash 完成");
        }
    }
}
