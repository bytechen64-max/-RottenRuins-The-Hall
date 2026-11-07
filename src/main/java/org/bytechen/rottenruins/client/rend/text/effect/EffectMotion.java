package org.bytechen.rottenruins.client.rend.text.effect;

/**
 * 位移类效果的逐字偏移（纯 Java）。
 *
 * <h3>铁律：只返回"偏移量"，不碰排版</h3>
 * <p>这里算出来的 dx/dy 只会在 {@code Font$StringRenderOutput.accept} 里加到绘制坐标上、
 * 返回前再减回去 —— 字宽（advance）与折行完全不受影响。这是方案里"抖动不会破坏
 * tooltip 宽度/面板尺寸"的根本原因，改动时不要把偏移写进宽度计算。</p>
 *
 * <h3>确定性</h3>
 * <p>同一个 {@code (ordinal, time)} 必须得到同一个偏移：一次绘制里 shadow 与主字形是两遍，
 * 描边甚至十遍；如果每次调用都随机，重影会散开。所以"随机"用整数哈希而不是
 * {@code Random}，并且按时间分桶（glitch 每 {@code 1/rate} 秒才换一次位置）。</p>
 */
public final class EffectMotion {

    /** 不可变偏移（GUI 像素，亚像素精度）。 */
    public static final class Offset {
        public final float dx;
        public final float dy;

        public Offset(float dx, float dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    private EffectMotion() {}

    /**
     * 取某个可视字符的位移。
     *
     * @return 颜色类效果返回 {@code null}（调用方不加偏移）
     */
    public static Offset offsetFor(EffectSpan span, int ordinal, float time, float speedMultiplier) {
        if (span == null) return null;
        EffectSpec spec = span.spec;
        int local = ordinal - span.visibleStart;

        switch (spec.kind) {
            case SHAKE: {
                // 双频正弦叠加：比单一正弦更像"手抖"，又保持 C1 连续（不会突然跳像素）
                double t = time * spec.speed * speedMultiplier;
                double dx = 1.3D * Math.sin(11D * t + 2.1D * local) + 0.7D * Math.sin(17.3D * t + 5.7D * local);
                double dy = 1.3D * Math.sin(13.7D * t + 1.3D * local) + 0.7D * Math.cos(19.1D * t + 3.9D * local);
                return new Offset((float) (dx * 0.6D * spec.amp), (float) (dy * 0.6D * spec.amp));
            }
            case WAVE: {
                // len 是"波长"（多少 GUI 像素走完一个完整周期）；默认 12px
                double waveNumber = 0.55D * (12D / Math.max(1D, spec.len));
                double dy = 1.6D * spec.amp * Math.sin(local * waveNumber - time * 4D * spec.speed * speedMultiplier);
                return new Offset(0F, (float) dy);
            }
            case GLITCH: {
                int bucket = (int) Math.floor(time * spec.rate);
                float roll = hash01(local, bucket, 1);
                if (roll >= spec.prob) return null;
                int dir = (hash(local, bucket, 2) & 1) == 0 ? -1 : 1;
                float frac = hash01(local, bucket, 3);
                float magnitude = (3F + 4F * frac) * (spec.amp / 2.5F);
                return new Offset(dir * magnitude, 0F);
            }
            default:
                return null;
        }
    }

    // ── 确定性哈希（同一 (ordinal, bucket) 恒定）──────────────────

    private static float hash01(int a, int b, int salt) {
        return (hash(a, b, salt) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static int hash(int a, int b, int salt) {
        int x = a * 0x9E3779B1 + b * 0x85EBCA77 + salt * 0xC2B2AE3D + 0x9E3779B9;
        x ^= x >>> 15;
        x *= 0x2545F491;
        x ^= x >>> 13;
        x *= 0x27D4EB2F;
        x ^= x >>> 16;
        return x;
    }
}
