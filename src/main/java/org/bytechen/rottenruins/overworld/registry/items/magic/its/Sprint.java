package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

/**
 * <b>疾行</b> —— 立刻释放型法术：<b>直接朝玩家朝向</b>（视线方向）甩出一股冲刺动量，
 * 伴随电火花。
 *
 * <h3>动量：一个方向 + 一个大小</h3>
 * <pre>
 *   方向 = 玩家的视线方向（getLookAngle，向上/向下都原样保留）
 *   大小 = 等效于"垂直抬升 N 格"的初速度 × {@value #MOMENTUM_MULTIPLIER}
 *   N    = 5 × 有效法强      （上限 {@value #MAX_LIFT} 格）
 * </pre>
 * <p>有效法强 = 通用法强 + 光明系法强（相加，见 {@code MagicStats.spellPower}）。
 * 默认法强 1.0 → N = 5，标定速度 ≈ {@code 1.008} 格/tick，乘上动量倍率后实际给
 * <b>≈ {@code 3.02} 格/tick</b> —— 抬头时能抬的格数也远不止 5 格。</p>
 *
 * <h3>为什么"大小"是拿竖直方向标定的</h3>
 * <p>需求的口径是"动量大小等效于垂直抬升 5 格"，所以这里就把"能抬升 5 格所需的初速度"
 * 当作动量的<b>标定尺度</b>，方向完全交给视线；{@link #MOMENTUM_MULTIPLIER} 在这个尺度上
 * 再整体放大（它只放大真正给出的速度，不改标定）。</p>
 *
 * <p><b>注意</b>：竖直与水平的原版阻力不同（竖直 {@code 0.98}/tick，水平 {@code 0.91}/tick），
 * 所以同一个速度水平飞出的距离会比等效抬升高度短。
 * 这是"同一个速度 + 真实物理"的必然结果；如果要"水平也刚好 N 格"，就得按轴分别反解
 * —— 那等于给水平/竖直各定一个速度，也就回到"固定角度"的做法了，所以这里没做。</p>
 *
 * <h3>方向不钳制</h3>
 * <p>视线往哪就冲哪：抬头就上升、平视就平移、<b>低头就往下冲</b>（会砸向地面）。
 * 这是"直接朝朝向"的字面结果。若想禁止往地下冲，把 {@link #cast} 里的
 * {@code look} 换成 {@code look.y < 0 ? new Vec3(look.x, 0, look.z).normalize() : look} 即可。</p>
 *
 * <h3>其它</h3>
 * <ul>
 *   <li>给的是速度而不是瞬移：相机连续、撞墙会被原版碰撞吃掉、落下来照常吃摔落伤害
 *       （施法瞬间只清掉<b>已经累积</b>的下落距离，不含"免摔"）。</li>
 *   <li>{@code hurtMarked = true} 让客户端同步这份速度，否则会被本地预测拽回去。</li>
 *   <li>本次释放算"特殊使用"（同流派法杖 / 同流派饰品）时，<b>冷却减半</b>
 *       —— 见 {@link #cooldownMultiplier}，只影响这一次。</li>
 * </ul>
 */
public class Sprint extends CMagicBaseItem {

    /** 位移基值（格）：法强 1.0 时的等效抬升高度，也是动量大小的标定基准。 */
    public static final double BASE_LIFT = 5.0D;

    /** 位移上限（格）。防呆：法强是可叠倍率属性，不封顶会把人送出世界。 */
    public static final double MAX_LIFT = 30.0D;

    /** 原版玩家重力（格/tick²）。 */
    private static final double GRAVITY = 0.08D;

    /** 原版竖直方向每 tick 的阻力（{@code LivingEntity#travel} 里的 0.98）。 */
    private static final double VERTICAL_DRAG = 0.98D;

    /** 二分求解的初速度上界（格/tick）—— 30 格实际只需要 2.67。 */
    private static final double MAX_INITIAL_VELOCITY = 8.0D;

    /**
     * <b>动量倍率</b>：算出来的初速度整体乘这个数。
     *
     * <p>它只放大<b>实际给出的速度</b>，不改 {@link #lift} 的标定 ——
     * {@code lift} 仍然是"等效竖直抬升多少格"的衡量尺度（用来缩放特效与音调），
     * 而乘 3 之后真正飞出去的距离远大于 lift 所对应的抛物线
     * （水平/竖直都会按比例变长，且拖得更久）。</p>
     */
    public static final double MOMENTUM_MULTIPLIER = 3.0D;

    public Sprint() {
        //               属性              流派         使用方式            蓝  CD  蓄力 每秒 伤害
        super(defaultProperties(), MagicType.LIGHT, UsingType.IMMEDIATELY,
                30,     // 启动耗蓝
                100,    // 冷却 tick（5 秒；特殊使用时减半 → 2.5 秒）
                0,      // 蓄力 tick —— 非 CHARGE，基类会归零
                0,      // 每秒维持耗蓝 —— 非 KEEP，基类会归零
                0.0F);  // 不造成伤害
    }

    /** 允许子类换流派/数值。 */
    protected Sprint(@NotNull Properties properties, @NotNull MagicType magicType,
                     @NotNull UsingType usingType, int manaCost, int cooldownTicks,
                     int chargeTicks, int keepCostPerSecond, float damage) {
        super(properties, magicType, usingType, manaCost, cooldownTicks, chargeTicks,
                keepCostPerSecond, damage);
    }

    // ══════════════════════════════════════════════════════════════
    // 释放
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        double spellPower = MagicStats.spellPower(player, stack);
        double lift = Math.min(MAX_LIFT, BASE_LIFT * spellPower);
        if (lift <= 0.0D) {
            return false;
        }

        // 动量：标定出来的初速度整体乘 MOMENTUM_MULTIPLIER
        double magnitude = magnitudeForLift(lift) * MOMENTUM_MULTIPLIER;
        Vec3 look = player.getLookAngle();

        // 方向：视线的单位向量，原样使用（不拆轴、不钳制俯仰）
        player.setDeltaMovement(look.scale(magnitude));
        // 速度是服务端算的：不标 hurtMarked，客户端会按本地预测把人拽回去
        player.hurtMarked = true;
        // 清掉施法前已累积的下落距离；之后真正的下落照常结算
        player.resetFallDistance();

        if (player.level() instanceof ServerLevel serverLevel) {
            // 视觉长度跟着<b>实际动量</b>走：动量乘 3 之后飞得远得多，
            // 轨迹还按 lift 那么短就会读成"雷只放了一小截、人已经飞出去了"
            spawnLightningEffects(serverLevel, player, look, lift * MOMENTUM_MULTIPLIER);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
    }

    /**
     * 特殊使用 → 这一次的冷却减半。
     *
     * <p>返回 {@code 0.5} 之后，框架写入的冷却就是
     * {@code MagicStats.cooldownTicks(...) × 0.5}（那个值本身已经被
     * {@code cooldown_reduction} 属性缩过一次，所以两者是相乘关系）。</p>
     */
    @Override
    public float cooldownMultiplier(@NotNull MagicCastContext castContext) {
        return castContext.specialUse() ? 0.5F : 1.0F;
    }

    // ══════════════════════════════════════════════════════════════
    // 动量大小
    // ══════════════════════════════════════════════════════════════

    /**
     * 求"竖直抬升 {@code lift} 格"所需的初速度 —— 这就是动量的<b>大小</b>。
     *
     * <p>原版每 tick 的竖直速度递推是（{@code LivingEntity#travel}）：
     * {@code v = (v − 0.08) × 0.98}，位移累加用的是递推<b>之后</b>的速度。
     * 直接套 {@code sqrt(2gh)} 会飞不到（5 格公式给 0.894，实际只到 3.95 格），
     * 所以这里对递推式二分：区间 {@code [0, 8]}，40 次二分 × 每次数百 tick 的模拟，
     * 单次施法的代价可以忽略。</p>
     */
    private static double magnitudeForLift(double lift) {
        double low = 0.0D;
        double high = MAX_INITIAL_VELOCITY;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) * 0.5D;
            if (simulatePeak(mid) < lift) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    /** 用给定初速度模拟到竖直速度归零，返回累计上升高度（格）。 */
    private static double simulatePeak(double initialVelocity) {
        double velocity = initialVelocity;
        double height = 0.0D;
        for (int tick = 0; tick < 400; tick++) {
            velocity = (velocity - GRAVITY) * VERTICAL_DRAG;
            if (velocity <= 0.0D) {
                break;
            }
            height += velocity;
        }
        return height;
    }

    // ══════════════════════════════════════════════════════════════
    // 表现
    // ══════════════════════════════════════════════════════════════

    /**
     * 闪电粒子：起点炸一圈 + 沿<b>冲刺方向</b>拉一条电火花轨迹 + 末端再炸一下。
     *
     * <p>轨迹长度取等效抬升高度（既有的强度尺度），沿视线方向铺开 —— 平视时是水平的一道雷，
     * 抬头时是竖直的一道雷。间距按长度自适应（{@code length/24}，最少 0.6 格），
     * 30 格也只有几十个粒子。</p>
     */
    private void spawnLightningEffects(@NotNull ServerLevel level, @NotNull ServerPlayer player,
                                       @NotNull Vec3 look, double length) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 起点：身边炸一圈
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.8D, z,
                28, 0.6D, 0.4D, 0.6D, 0.35D);

        // 轨迹：沿视线铺电火花
        double step = Math.max(0.6D, length / 24.0D);
        for (double d = 0.4D; d <= length; d += step) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x + look.x * d, y + 0.8D + look.y * d, z + look.z * d,
                    2, 0.18D, 0.18D, 0.18D, 0.05D);
        }

        // 末端：再炸一下，读作"冲刺的尽头"
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x + look.x * length, y + 0.8D + look.y * length, z + look.z * length,
                10, 0.3D, 0.3D, 0.3D, 0.15D);
        // 少量白色火花：暗处也能看清这道"雷"
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0D, z,
                8, 0.35D, 0.5D, 0.35D, 0.02D);
    }
}
