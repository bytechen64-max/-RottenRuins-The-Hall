package org.bytechen.rottenruins.client.gui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;

/**
 * 难度选择界面「印记」的程序化几何。
 * 顶点格式固定 POSITION_TEX：1.20.1 的 Tesselator 只认预设格式，带第三个属性时
 * 自定义着色器在本界面画不出东西；于是 UV0.x = 径向 0..1、UV0.y = 图案相位。
 * 相位（角度×倍数）而非原始角度，才能在 0/2π 接缝处插值不出假扇区。
 * 每个角度段只发一个四边形（内缘直达外缘），避免共享边斜向光栅化的单像素断口。
 * 径向位移在 CPU 侧烘进半径，片元阶段只管角度图案与上色。
 */
public final class DifficultySigilMesh {

    private static final float TAU = (float) (Math.PI * 2.0);

    /** 半径随角度变化的线型。 */
    public interface RadialProfile {
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

    /** 正多边形：顶点朝外、边中点朝内；sharpness 0 = 退化成圆，1 = 最锐利。 */
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

    /** 不等长尖刺：长度按稳定哈希在 1 与 1-depth 之间取值（逐帧必须稳定，否则只是抖动噪点）。 */
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

    /**
     * 一圈光环的描述。
     *
     * @param patternMultiplicity 片元图案倍数；必须与 shader 里该 profile 用的倍数一致，
     *                            UV0.y 烘的是 {@code 倍数 × (θ + 自转 + phaseOffset)}
     */
    public record Ring(float baseRadius, float strokeWidth, float weight,
                       float spinSpeed, RadialProfile profile,
                       float wobbleAmplitude, float wobbleFrequency, float wobbleSpeed,
                       float patternMultiplicity, float phaseOffset) {
    }

    /**
     * 把一圈光环烘进 builder（调用方负责 begin / end / 绘制），返回实际写入的顶点数。
     * 顶点只有 Position + UV0.x(径向 0..1) + UV0.y(图案相位)；每段只发一个四边形。
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

            float iax = cx[i] - nx[i] * halfWidth;
            float iay = cy[i] - ny[i] * halfWidth;
            float ibx = cx[next] - nx[next] * halfWidth;
            float iby = cy[next] - ny[next] * halfWidth;
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
