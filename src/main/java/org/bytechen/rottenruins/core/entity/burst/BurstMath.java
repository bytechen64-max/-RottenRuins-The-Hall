package org.bytechen.rottenruins.core.entity.burst;

/** Pure visual calculations. Keep operation order stable to preserve existing pulse/twitch curves. */
public final class BurstMath {
    private static final float BURST_PULSE_RISE = 0.3f;

    private BurstMath() {}

    public static int resolvedDuration(int scaleDuration, int deathDuration) {
        return scaleDuration > 0 ? scaleDuration : Math.max(1, deathDuration);
    }

    public static int pulseCount(float rate, int duration) {
        if (rate <= 0.0f) return 1;
        return Math.max(1, Math.round(rate * duration / 20.0f));
    }

    public static float progress(float elapsed, int duration) {
        return duration <= 0 ? 1.0f : clamp(elapsed / (float) duration, 0.0f, 1.0f);
    }

    /** Collision boxes and random generators are intentionally not involved. */
    public static float scale(float progress, float maxScaleInput, int pulses, float twitch,
                              int entityId, long gameTime) {
        float maxScale = Math.max(1.0f, maxScaleInput);
        float scale;

        if (progress >= 1.0f) {
            scale = maxScale;                    // 窗口走完：停在最大倍率（抖动继续到爆体结束）
        } else {
            float pulsePos = progress * pulses;  // 0..N
            int index = (int) pulsePos;
            if (index >= pulses) {
                scale = maxScale;
            } else {
                float local = pulsePos - index;  // 当前脉冲内的 0..1
                float step = (maxScale - 1.0f) / pulses;
                float baseline = 1.0f + step * index;
                scale = baseline + step * burstPulseShape(local);
            }
        }

        // 鬼畜抖动：逐 tick 变化（partialTick 不参与），同一 tick 的 3 帧共用同一个值。
        if (twitch > 0.0f) {
            long seed = (long) entityId * 0xBF58476D1CE4E5B9L
                    + gameTime * 0x9E3779B97F4A7C15L;
            float noise = burstTwitchNoise(seed) * 2.0f - 1.0f;   // -1..1
            scale += noise * twitch * (0.5f + progress);          // 幅度 0.5×→1.5×
        }
        // 硬上限：抖动的正向峰值也不许超过 maxScale（默认 1.4 倍）。
        return clamp(scale, 1.0f, maxScale);
    }

    private static float burstTwitchNoise(long seed) {
        seed ^= seed >>> 30;
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= seed >>> 27;
        seed *= 0x94D049BB133111EBL;
        seed ^= seed >>> 31;
        return (seed >>> 40) / (float) (1 << 24);
    }

    /**
     * 单个脉冲的归一化形状：0 → 1 → 0。
     * <p>前 {@value #BURST_PULSE_RISE} 的时间快速鼓起（smoothstep），
     * 余下时间缓缓落回（smoothstep）。两端都取 0，所以脉冲内部不会出现
     * 缩放跳变；帧与帧之间的"跳一级"只发生在脉冲边界。</p>
     */
    private static float burstPulseShape(float t) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t < BURST_PULSE_RISE) {
            float x = t / BURST_PULSE_RISE;
            return x * x * (3.0f - 2.0f * x);
        }
        float x = (t - BURST_PULSE_RISE) / (1.0f - BURST_PULSE_RISE);
        return 1.0f - x * x * (3.0f - 2.0f * x);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
