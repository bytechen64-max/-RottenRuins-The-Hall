package org.bytechen.hall.client.gui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;

/**
 * 难度选择界面「印记」的程序化几何。
 *
 * <h3>顶点格式</h3>
 * <p>用 {@code DefaultVertexFormat.POSITION_TEX_COLOR}，每个顶点塞三样东西：
 * <ul>
 *   <li>{@code Position.xy} —— 已经绕卡片中心转过的屏幕坐标</li>
 *   <li>{@code UV0 = (环序号, 图案相位)} —— 片元阶段出角度图案用。
 *       <b>相位而不是原始角度</b>：角度在 0/2π 接缝处是硬跳变，
 *       插值会跨过整个圆、在接缝那个四边形上算出假扇区；
 *       相位（角度 × 该 profile 的角频率）在接缝处连续，插值才是对的。</li>
 *   <li>{@code Color.r = 到中线的靠近程度}（内缘 1.0、外缘 0.40），
 *       {@code Color.a = 环权重}</li>
 * </ul>
 * 之所以要把这些塞进 UV 和 Color：Tesselator 这条路径只能配
 * {@code DefaultVertexFormat} 的预设格式，注册不了自定义顶点属性。
 * （1.20.1 里并没有 {@code POSITION_TEX_COLOR_UV2}，只有 10 个 {@code POSITION_*} 常量。）
 * <p>注意顶点色是 RGBA 顺序的 ubyte，强度走 alpha、边缘走红色，
 * 顶点阶段直接按通道取用，不必重建 vec4。
 *
 * <h3>几何结构</h3>
 * <p>每圈光环是一条环带：角度采样点沿径向往外放一个顶点、往里放一个顶点，
 * 相邻两组拼成一个四边形。
 * <p>注意 {@link BufferBuilder} 是<b>立即写顶点</b>的（没有索引缓冲），
 * 所以不能"先发顶点、再按索引补四边形" —— 必须按四边形顺序直接写。
 * 因此这里先把整圈顶点的位置算进数组，再按顺序发射。
 *
 * <h3>职责划分</h3>
 * <p>径向位移（呼吸 / 尖刺 / 跳变）在 CPU 侧烘进半径；片元阶段只管角度图案与上色。
 * 这么分是因为位移需要逐 profile 的确定性与哈希随机，
 * 在 Java 里算一次比在 GLSL 里再复刻一套更不容易和 CPU 侧对不上。
 */
public final class DifficultySigilMesh {

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    //  线型
    // ══════════════════════════════════════════════════════════════

    /** 半径随角度变化的线型。 */
    public interface RadialProfile {
        /**
         * @param angleRad 角度（弧度）
         * @return 半径系数，1.0 = 基准
         */
        float at(float angleRad);

        /** 一圈建议的采样段数。 */
        int segments();
    }

    /** 真圆，用 {@code sides} 段直线逼近。 */
    public static final class Circle implements RadialProfile {
        private final int sides;

        public Circle(int sides) {
            this.sides = Math.max(3, sides);
        }

        @Override
        public float at(float angleRad) {
            return 1.0F;
        }

        @Override
        public int segments() {
            return sides;
        }
    }

    /**
     * 正多边形：顶点朝外、边中点朝内。
     * <p>六边形（{@code sides = 6}）在 6 个方向上是纯直线段 —— 这正是"秩序感"的来源，
     * 另外三个 profile 全是曲线/锯齿，靠这点把普通档和它们区分开。
     *
     * @param sharpness 0 = 退化成圆，1 = 最锐利
     */
    public static final class Polygon implements RadialProfile {
        private final int sides;
        private final float sharpness;
        private final float half;

        public Polygon(int sides, float sharpness) {
            this.sides = Math.max(3, sides);
            this.sharpness = Mth.clamp(sharpness, 0.0F, 1.0F);
            this.half = TAU / (2.0F * this.sides);
        }

        @Override
        public float at(float angleRad) {
            float folded = Math.abs(wrapPi(angleRad)) % (TAU / sides);
            float distanceToEdgeMiddle = Math.abs(folded - half);
            // 0（顶点处）→ 1（边中点）
            float inward = Mth.clamp(distanceToEdgeMiddle / half, 0.0F, 1.0F);
            return 1.0F - sharpness * inward;
        }

        @Override
        public int segments() {
            // 每条边至少 8 段，直线段本身不需要很多，但转角要够密才不糊
            return sides * 8;
        }
    }

    /**
     * 不等长尖刺：{@code tips} 根，长度按稳定哈希在 1 与 {@code 1 - depth} 之间取值。
     * <p>用哈希而不是随机数：长度必须逐帧稳定，否则尖刺会变成噪点抖动，
     * 而不是"长短不一"。
     */
    public static final class Spikes implements RadialProfile {
        private final int tips;
        private final float depth;
        private final float seed;
        private final int perTip;

        public Spikes(int tips, float depth, float seed, int perTip) {
            this.tips = Math.max(3, tips);
            this.depth = Mth.clamp(depth, 0.0F, 1.0F);
            this.seed = seed;
            this.perTip = Math.max(2, perTip);
        }

        @Override
        public float at(float angleRad) {
            float u = wrapPi(angleRad) / TAU + 0.5F;          // 0..1
            float scaled = u * tips;
            int index = (int) Math.floor(scaled);
            float frac = scaled - index;

            // 扇区内从谷底升到尖顶再落回：|cos| 的 0.55 次幂让尖顶更尖
            float lobe = (float) Math.pow(Math.abs(Math.cos((frac - 0.5F) * Math.PI)), 0.55);
            float length = 1.0F - depth * (1.0F - hash11(index * 1.37F + seed));
            return Mth.lerp(lobe, 1.0F - depth, length);
        }

        @Override
        public int segments() {
            return tips * perTip;
        }
    }

    /** 多档跳变：半径按扇区阶梯式变化，扇区内恒定（刻意的<b>不连续</b>）。 */
    public static final class Steps implements RadialProfile {
        private final int steps;
        private final float variance;
        private final float seed;
        private final float base;

        public Steps(int steps, float variance, float seed) {
            this.steps = Math.max(2, steps);
            this.variance = Mth.clamp(variance, 0.0F, 1.0F);
            this.seed = seed;
            this.base = 1.0F - this.variance;
        }

        @Override
        public float at(float angleRad) {
            float u = wrapPi(angleRad) / TAU + 0.5F;
            int index = (int) Math.floor(u * steps);
            return base + hash11(index * 3.11F + seed) * 2.0F * variance;
        }

        @Override
        public int segments() {
            // 阶梯要密：每个扇区多切几段，跳变才够"硬"
            return steps * 4;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  一圈光环
    // ══════════════════════════════════════════════════════════════

    /**
     * 一圈光环的描述。
     *
     * @param baseRadius          基准半径（像素，未乘 {@code radiusUnit}）
     * @param strokeWidth         环带全宽（像素）
     * @param weight              该环的亮度权重
     * @param spinSpeed           自转角速度（弧度/秒，正负决定方向）
     * @param profile             径向线型
     * @param wobbleAmplitude     半径波动幅度（比例）
     * @param wobbleFrequency     波动频率
     * @param wobbleSpeed         波动相位速度（弧度/秒）
     * @param patternMultiplicity 片元阶段角度图案的倍数（3 = 三段缺口、5 = 五段撕裂、7 = 七扇区）。
     *                            烘进 {@code UV0.y} 的相位就是 {@code 倍数 × (θ + 自转 + phaseOffset)}，
     *                            片元阶段除以它就能把"已经转过自转的角"还原出来。
     *                            <b>必须与 shader 里该 profile 用的倍数一致</b>，
     *                            否则缺口数量会对不上。
     * @param phaseOffset         该环的图案相位偏移（弧度）—— 让三环的缺口错开，
     *                            否则三环的图案会完全重合，"多层"就白做了
     */
    public record Ring(float baseRadius, float strokeWidth, float weight,
                       float spinSpeed, RadialProfile profile,
                       float wobbleAmplitude, float wobbleFrequency, float wobbleSpeed,
                       float patternMultiplicity, float phaseOffset) {
    }

    // ══════════════════════════════════════════════════════════════
    //  烘焙
    // ══════════════════════════════════════════════════════════════

    /**
     * 把一圈光环烘进 {@code builder}。
     *
     * <h3>顶点只有 Position 与 UV0</h3>
     * <p>顶点格式固定为 {@code POSITION_TEX}。调试中确认：格式带第三个属性
     * （{@code POSITION_TEX_COLOR} / {@code POSITION_COLOR_NORMAL}）时，自定义着色器
     * 在这个界面里画不出任何东西；而只有 Position + UV0 时一切正常。
     * 所以逐顶点参数一律编进 UV0：
     * <ul>
     *   <li>{@code UV0.x} —— 径向坐标：0 = 环带内缘，1 = 环带外缘</li>
     *   <li>{@code UV0.y} —— 图案相位 = {@code 倍数 × (θ + 自转 + 该环偏移)}</li>
     * </ul>
     *
     * <h3>每个角度段只发一个四边形</h3>
     * <p>曾经把一条环带拆成「中线→内缘」「中线→外缘」两组四边形，
     * 好处是片元能直接用 {@code UV0.x} 当衰减系数。但那两组在<b>中线处共享一条边</b>，
     * 共享边在斜向光栅化下容易出现单像素丢样的缝隙 —— 表现就是细环上东一处西一处
     * 的断口，半径越大越明显（采样点越密，共享边越接近水平/垂直以外的角度）。
     * <p>现在改成每个角度段只发一个四边形、径向直接从内缘通到外缘，
     * 不再有任何共享边，断口随之消失。径向柔化改由片元按
     * 「{@code UV0.x} 到 0.5 的距离」计算，不再依赖顶点色。
     *
     * @param builder    目标缓冲（调用方负责 {@code begin} / {@code end} / 绘制）
     * @param centerX    中心 X（屏幕坐标）
     * @param centerY    中心 Y（屏幕坐标）
     * @param radiusUnit 半径基准（像素）：所有环半径都乘它，用来实现"选中放大"
     * @param ring       环描述
     * @param time       动画时间（秒）
     * @return 本次实际写入的顶点数
     */
    public static int bakeRing(BufferBuilder builder, float centerX, float centerY,
                               float radiusUnit, Ring ring, float time) {
        int steps = Mth.clamp(ring.profile().segments(), 6, 256);

        float spin = ring.spinSpeed() * time;
        float drift = ring.wobbleSpeed() * time;
        // 下限抬到 1.0：亚像素宽的细带在光栅化时本身就会被丢样本
        float halfWidth = Math.max(ring.strokeWidth() * 0.5F, 1.0F);
        float multiplicity = Math.max(ring.patternMultiplicity(), 1.0F);

        // 中线上的采样点、径向单位向量、相位
        float[] cx = new float[steps + 1];
        float[] cy = new float[steps + 1];
        float[] nx = new float[steps + 1];
        float[] ny = new float[steps + 1];
        float[] phase = new float[steps + 1];

        for (int i = 0; i <= steps; i++) {
            float theta = (float) i / steps * TAU;

            float displacement = ring.profile().at(theta);
            float wobble = 1.0F + ring.wobbleAmplitude()
                    * Mth.sin(theta * ring.wobbleFrequency() + drift);

            float magnitude = ring.baseRadius() * radiusUnit * displacement * wobble;
            float angle = theta + spin;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            cx[i] = centerX + cos * magnitude;
            cy[i] = centerY + sin * magnitude;
            // 相位用"转过自转的角"乘倍数：接缝处连续，插值不会跨圆
            phase[i] = (angle + ring.phaseOffset()) * multiplicity;
        }

        // 径向单位向量：用相邻采样点差分求切线再转 90°，比"从圆心指向采样点"稳
        for (int i = 0; i <= steps; i++) {
            int ringSize = steps;                        // 首尾点重合，故环长为 steps
            int prev = (i - 1 + ringSize) % ringSize;
            int next = (i + 1) % ringSize;

            float tx = cx[next] - cx[prev];
            float ty = cy[next] - cy[prev];
            float len = Mth.sqrt(tx * tx + ty * ty);

            if (len < 1.0E-4F) {
                float rx = cx[i] - centerX;
                float ry = cy[i] - centerY;
                float rl = Mth.sqrt(rx * rx + ry * ry);
                nx[i] = rl < 1.0E-4F ? 1.0F : rx / rl;
                ny[i] = rl < 1.0E-4F ? 0.0F : ry / rl;
            } else {
                nx[i] = -ty / len;
                ny[i] = tx / len;
            }
        }

        int written = 0;
        for (int i = 0; i < steps; i++) {
            int next = (i + 1) % steps;

            // 末段外推相位，避免回卷到 phase[0] 造成接缝跨圆
            float phaseA = phase[i];
            float phaseB = phaseA + multiplicity * (TAU / steps);

            // 内缘两点
            float iax = cx[i] - nx[i] * halfWidth;
            float iay = cy[i] - ny[i] * halfWidth;
            float ibx = cx[next] - nx[next] * halfWidth;
            float iby = cy[next] - ny[next] * halfWidth;
            // 外缘两点
            float oax = cx[i] + nx[i] * halfWidth;
            float oay = cy[i] + ny[i] * halfWidth;
            float obx = cx[next] + nx[next] * halfWidth;
            float oby = cy[next] + ny[next] * halfWidth;

            // 顺序：内(i) → 外(i) → 外(i+1) → 内(i+1)，绕序一致
            vertex(builder, iax, iay, 0.0F, phaseA);
            vertex(builder, oax, oay, 1.0F, phaseA);
            vertex(builder, obx, oby, 1.0F, phaseB);
            vertex(builder, ibx, iby, 0.0F, phaseB);
            written += 4;
        }
        return written;
    }

    private static void vertex(BufferBuilder builder, float x, float y,
                               float radialCoord, float phase) {
        builder.vertex(x, y, 0.0F)
                .uv(radialCoord, phase)
                .endVertex();
    }

    // ══════════════════════════════════════════════════════════════
    //  辅助
    // ══════════════════════════════════════════════════════════════

    /** 把角度折到 [-π, π]。 */
    private static float wrapPi(float angleRad) {
        float wrapped = angleRad % TAU;
        if (wrapped > (float) Math.PI) wrapped -= TAU;
        if (wrapped < -(float) Math.PI) wrapped += TAU;
        return wrapped;
    }

    /** 稳定的 1 维哈希，取值 0..1。与 shader 里的 hash11 同构。 */
    private static float hash11(float p) {
        float v = fract(p * 0.1031F);
        v *= v + 33.33F;
        v *= v + v;
        return fract(v);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private DifficultySigilMesh() {}
}
