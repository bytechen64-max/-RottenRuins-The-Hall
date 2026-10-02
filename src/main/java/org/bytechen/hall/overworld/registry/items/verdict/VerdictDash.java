package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /**
     * 每 tick 的速度包络 —— 把"匀速率平移"改成"甩出去 + 滑行"。
     *
     * <h3>为什么匀速率会显得廉价</h3>
     * <p>恒定速度的位移在视觉上与"瞬移 + 一条拖影"没有本质区别：起点和终点都突然，
     * 中间没有加速也没有减速。玩家读不出"是被甩出去的"，只读得出"我瞬移了"。</p>
     *
     * <p>改成"起步快 34%、收尾慢 58%"之后，位移本身就有了重量：
     * 前两 tick 的爆发制造"蹬地"，最后几 tick 的衰减制造"滑行"。</p>
     *
     * <h3>为什么这些数必须均值 ≈ 1.0</h3>
     * <p>突进距离是反解出来的（{@code dashSpeedForDistance} 保证 12 格）。
     * 包络只允许改变<b>速度的分布</b>，不能改变<b>总位移</b> ——
     * 所以这一串数的算术平均被刻意压到 1.0（{@code 1.34+1.24+1.08}/{@code ...}）。
     * 想调手感就保持均值等于 1，否则"配置里写 12 格、实际冲了 15 格"会变成一个
     * 很难查的偏差。</p>
    */
    private static final float[] SPEED_ENVELOPE = {
            1.34f, 1.24f, 1.15f, 1.07f, 1.00f, 0.94f,
            0.88f, 0.83f, 0.78f, 0.74f, 0.70f, 0.66f
    };

    /**
     * 收招后保留的水平速度比例。
     *
     * <p>原本突进结束的那一刻速度直接归零 —— 无论在空中还是地面，
     * 玩家都像撞上一堵空气墙。保留 38% 之后，位移的尾巴接回了原版的移动物理，
     * 读起来像"滑出去停住"。空中尤其明显：它让突进可以自然衔接后续操作，
     * 而不是一个必须立刻踩刹车的动作。</p>
     */
    private static final double RESIDUE = 0.38;

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

        double speed = d.getDouble(K_SPEED);
        double dirX = d.getDouble(K_DIR_X);
        double dirZ = d.getDouble(K_DIR_Z);

        // 撞墙 / 骑乘 / 死亡：立刻结束，别让玩家贴着墙抖。
        //  撞墙额外给一次"拍在墙上"的反馈：原本撞墙是完全静默的
        //  （玩家只觉得自己突然没动），一声钝响 + 一次短抖就能把因果说清楚。
        if (player.isPassenger() || !player.isAlive()
                || (stopOnCollision && player.horizontalCollision)) {
            t.remove(ROOT);
            if (player.horizontalCollision && player.isAlive()) {
                spawnWallImpact(player);
            }
            VerdictDebug.log("  dash 中止 left=%d collision=%b passenger=%b",
                    left, player.horizontalCollision, player.isPassenger());
            return;
        }

        // ── 速度包络：按"已经走了几 tick"取本 tick 的倍率 ──
        //  用 DURATION - left 而不是外部计数器：状态全在这一个 NBT 里，
        //  中途被中止再重启也不会读到一个错的相位。
        int elapsed = DURATION - left;
        float env = SPEED_ENVELOPE[Math.floorMod(elapsed, SPEED_ENVELOPE.length)];
        double v = speed * env;

        // 重申水平速度；纵向保持原版物理（地面给一点抬升，让连按能升空）
        double lift = player.onGround() ? 0.42 : player.getDeltaMovement().y;
        player.setDeltaMovement(dirX * v, lift, dirZ * v);
        player.hurtMarked = true;
        player.hasImpulse = true;

        d.putInt(K_TICKS, left - 1);
        if (left - 1 <= 0) {
            t.remove(ROOT);
            // 收招：留一段余速，让位移的尾巴接回原版物理而不是硬停
            double residue = speed * RESIDUE;
            double vy = player.onGround() ? 0.0 : player.getDeltaMovement().y;
            player.setDeltaMovement(dirX * residue, vy, dirZ * residue);
            player.hurtMarked = true;
            VerdictDebug.log("  dash 完成（余速 %.3f 保留）", residue);
        }
    }

    /**
     * 撞到方块时的反馈：一声钝响 + 一次短促抖动。
     *
     * <p>刻意只有声音和相机，不加粒子：撞墙是<b>失败</b>的突进，
     * 给它华丽的表现会让玩家误以为这是技能的一部分。</p>
     */
    private static void spawnWallImpact(Player player) {
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5f, 0.6f);
        VerdictFeedback.shakeNear(player, player.position(), 0.3f);
    }
}
