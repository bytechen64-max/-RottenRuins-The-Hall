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
 * 疾行 —— 立刻释放型法术：朝视线方向甩出一股冲刺动量，伴随电火花。
 * <p>动量 = 方向（视线单位向量，不钳制俯仰，低头就往地面冲）× 大小；大小取「竖直抬升 N 格
 * 所需的初速度」，N = {@value #BASE_LIFT} × 有效法强（通用 + 光明系，上限 {@value #MAX_LIFT}），
 * 再整体乘 {@link #MOMENTUM_MULTIPLIER}。
 * <p>竖直阻力 0.98/tick、水平 0.91/tick，所以同一个速度水平飞出的距离比等效抬升高度短。
 * <p>给的是速度而非瞬移：{@code hurtMarked} 让客户端同步，否则会被本地预测拽回去。
 */
public class Sprint extends CMagicBaseItem {

    /** 位移基值（格）：法强 1.0 时的等效抬升高度，也是动量大小的标定基准。 */
    public static final double BASE_LIFT = 5.0D;

    /** 位移上限（格）。防呆：法强是可叠倍率属性，不封顶会把人送出世界。 */
    public static final double MAX_LIFT = 30.0D;

    private static final double GRAVITY = 0.08D;

    private static final double VERTICAL_DRAG = 0.98D;

    /** 二分求解的初速度上界（格/tick）—— 30 格实际只需要 2.67。 */
    private static final double MAX_INITIAL_VELOCITY = 8.0D;

    /** 动量倍率：只放大实际给出的速度，不改「等效抬升 lift 格」这个标定尺度。 */
    public static final double MOMENTUM_MULTIPLIER = 3.0D;

    public Sprint() {
        super(defaultProperties(), MagicType.LIGHT, UsingType.IMMEDIATELY,
                30,
                60,
                0,
                0,
                0.0F);
    }

    /** 允许子类换流派/数值。 */
    protected Sprint(@NotNull Properties properties, @NotNull MagicType magicType,
                     @NotNull UsingType usingType, int manaCost, int cooldownTicks,
                     int chargeTicks, int keepCostPerSecond, float damage) {
        super(properties, magicType, usingType, manaCost, cooldownTicks, chargeTicks,
                keepCostPerSecond, damage);
    }

    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        double spellPower = MagicStats.spellPower(player, stack);
        double lift = Math.min(MAX_LIFT, BASE_LIFT * spellPower);
        if (lift <= 0.0D) {
            return false;
        }

        double magnitude = magnitudeForLift(lift) * MOMENTUM_MULTIPLIER;
        Vec3 look = player.getLookAngle();

        player.setDeltaMovement(look.scale(magnitude));
        // 速度是服务端算的：不标 hurtMarked，客户端会按本地预测把人拽回去
        player.hurtMarked = true;
        player.resetFallDistance();

        if (player.level() instanceof ServerLevel serverLevel) {
            // 视觉长度跟实际动量走（乘过 MOMENTUM_MULTIPLIER），否则读成"雷只放了一小截、人已经飞出去"
            spawnLightningEffects(serverLevel, player, look, lift * MOMENTUM_MULTIPLIER);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
    }

    /** 特殊使用（同流派法杖 / 饰品）→ 这一次的冷却减半，与 {@code cooldown_reduction} 属性相乘生效。 */
    @Override
    public float cooldownMultiplier(@NotNull MagicCastContext castContext) {
        return castContext.specialUse() ? 0.5F : 1.0F;
    }

    /** 求「竖直抬升 lift 格」所需的初速度（格/tick），这就是动量的大小。
     *  原版竖直递推 {@code v = (v − 0.08) × 0.98} 且位移用递推后的速度，直接套 sqrt(2gh)
     *  会飞不到（5 格公式给 0.894，实际只到 3.95 格），故对递推式二分：区间 [0, 8]、40 次。 */
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

    /** 闪电粒子：起点炸一圈 + 沿冲刺方向拉一条电火花轨迹 + 末端再炸一下。
     *  间距按长度自适应（{@code length/24}，最少 0.6 格），30 格也只有几十个粒子。 */
    private void spawnLightningEffects(@NotNull ServerLevel level, @NotNull ServerPlayer player,
                                       @NotNull Vec3 look, double length) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.8D, z,
                28, 0.6D, 0.4D, 0.6D, 0.35D);

        double step = Math.max(0.6D, length / 24.0D);
        for (double d = 0.4D; d <= length; d += step) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x + look.x * d, y + 0.8D + look.y * d, z + look.z * d,
                    2, 0.18D, 0.18D, 0.18D, 0.05D);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x + look.x * length, y + 0.8D + look.y * length, z + look.z * length,
                10, 0.3D, 0.3D, 0.3D, 0.15D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0D, z,
                8, 0.35D, 0.5D, 0.35D, 0.02D);
    }
}
