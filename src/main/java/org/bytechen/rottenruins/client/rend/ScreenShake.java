package org.bytechen.rottenruins.client.rend;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** 屏幕抖动：改投影矩阵在 NDC 里平移整幅画面（相机与准星不动），与 VerdictCameraEffects 的角度抖动叠加。 */
public final class ScreenShake {

    private ScreenShake() {}

    /** 满强度时的 NDC 位移：0.020 在 1920 宽上约 19 像素（包络峰值 ~0.56）。 */
    private static final float NDC_PER_STRENGTH = 0.020f;

    /** 衰减速率（1/秒）：约 0.5 秒归零。 */
    private static final float DECAY = 9.0f;
    private static final float ATTACK = 55.0f;
    /** 超过这个时间直接当没有（省掉每帧的指数运算）。 */
    private static final float DURATION = 0.6f;

    private static long startMillis = Long.MIN_VALUE;
    private static float strength = 0f;

    /** 触发一次抖动，{@code s} 取 0..1；同时只保留一次，明显更弱的一下不打断正在衰减的那一下。 */
    public static void impact(float s) {
        float v = Mth.clamp(s, 0f, 1f);
        if (v <= 0.02f) return;
        // 只在当前这一下还在跑时才比较强弱：已衰减完的旧强度必须让位（strength 不清零的坑）。
        if (active() && v < strength * 0.55f) return;
        strength = v;
        startMillis = Util.getMillis();
    }

    public static boolean active() {
        return startMillis != Long.MIN_VALUE
                && (Util.getMillis() - startMillis) / 1000f <= DURATION;
    }

    /** 把偏移左乘进投影（不原地加，避免投影被缓存复用时每帧累加）：m30/m31 平移 clip.x/y ⇒ NDC 平移，与深度无关。 */
    public static Matrix4f applyTo(Matrix4f projection) {
        if (!active()) return projection;
        float age = (Util.getMillis() - startMillis) / 1000f;
        float env = (1f - (float) Math.exp(-age * ATTACK)) * (float) Math.exp(-age * DECAY);
        // 两个互质频率叠加：单频会读成"节拍器在摆"
        float nx = Mth.sin(age * 96f) * 0.68f + Mth.sin(age * 61f + 1.7f) * 0.32f;
        float ny = Mth.sin(age * 83f + 2.3f) * 0.68f + Mth.sin(age * 47f + 0.6f) * 0.32f;
        float amp = env * strength * NDC_PER_STRENGTH;

        Matrix4f shift = new Matrix4f();
        shift.m30(nx * amp);
        shift.m31(ny * amp);
        return shift.mul(projection);
    }
}
