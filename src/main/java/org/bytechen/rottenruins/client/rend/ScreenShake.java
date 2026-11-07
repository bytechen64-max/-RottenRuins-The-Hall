package org.bytechen.rottenruins.client.rend;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 屏幕抖动 —— <b>整幅画面平移</b>，不是相机旋转。
 *
 * <h3>它和 {@code VerdictCameraEffects} 那一套不是一回事</h3>
 * <p>{@code VerdictCameraEffects} 改的是相机<b>角度</b>（俯仰/偏航/滚转），
 * 世界会绕着镜头转，近处物体比远处位移大 —— 那是"镜头被撞了一下"。
 * 这里改的是<b>投影矩阵</b>：把整幅已渲染的画面在 NDC 里平移几个像素，
 * 相机没动、准星不动、画面里的透视关系也不变，读起来是"整个世界被砸得一颤"。</p>
 *
 * <p>两者叠在一起才是完整的打击感：角度抖动给"方向与惯性"，画面平移给"重量"。
 * 只做前者，远处的东西几乎不动，砸地的感觉会发飘；只做后者，画面像贴纸一样
 * 整体滑，没有透视变化。所以这一层是<b>叠加</b>，不是替换。</p>
 *
 * <h3>为什么改投影矩阵，而不是别的做法</h3>
 * <ul>
 *   <li><b>不去移动相机位置</b>：那会改变视点，贴墙时可能穿进方块，而且
 *       位移量与距离成反比，远处的抖几乎看不见；</li>
 *   <li><b>不做全屏后处理拷贝</b>：一次整屏 copy + 重绘在光影包下代价和风险
 *       都太大（还要处理 FBO 绑定、scissor、与 pack 的顺序）；</li>
 *   <li><b>改投影</b>：一次 2 个元素的加法，零额外 draw call，HUD/GUI 用的是
 *       正交矩阵，完全不受影响 —— 准星与快捷栏稳稳不动，只有世界在抖。</li>
 * </ul>
 *
 * <h3>为什么用"时间"驱动，而不是每帧累加</h3>
 * <p>{@code getProjectionMatrix()} 一帧里会被调用多次（世界、手持物），
 * 若用"每帧叠加一次状态"的写法，第二次调用就会拿到被推进过的值，画面会跳。
 * 这里把偏移写成 {@code f(当前时间)} 的<b>纯函数</b>：同一帧里的所有调用
 * 拿到完全相同的偏移，不需要任何帧标识或去重。</p>
 *
 * <h3>包络</h3>
 * <pre>
 *   env = (1 - e^(-age·55)) · e^(-age·9)
 *          └ 极快的起手（约 20ms）  └ 约 0.5s 收干净
 * </pre>
 * <p>两条指数而不是 smoothstep：砸地那一下必须<b>瞬间</b>到位（起手慢半拍就
 * 成了"晃了一下"），衰减则要拖一点尾巴。横向扰动用两个互质频率叠加，
 * 避免读出"规律的正弦"。</p>
 */
public final class ScreenShake {

    private ScreenShake() {}

    /**
     * 满强度时的 NDC 位移。
     *
     * <p>0.020 在 1920 宽上约 19 像素、1080p 全屏约 11 像素（包络峰值 ~0.56）。
     * 再大就开始影响读画面，再小就只剩"好像动了一下"。</p>
     */
    private static final float NDC_PER_STRENGTH = 0.020f;

    /** 衰减速率（1/秒）：0.5 秒后基本归零。 */
    private static final float DECAY = 9.0f;
    /** 起手速率（1/秒）：约 20ms 到位。 */
    private static final float ATTACK = 55.0f;
    /** 超过这个时间直接当没有（省掉每帧的指数运算）。 */
    private static final float DURATION = 0.6f;

    private static long startMillis = Long.MIN_VALUE;
    private static float strength = 0f;

    /**
     * 触发一次屏幕抖动。
     *
     * @param s 0..1。同时只保留一次抖动：新的一下若明显更弱，不打断正在衰减的
     *          那一下（否则连击时画面会变成持续的细碎抖动，反而没有"一下"）
     */
    public static void impact(float s) {
        float v = Mth.clamp(s, 0f, 1f);
        if (v <= 0.02f) return;
        // 只在"当前这一下还在跑"时才比较强弱。已经衰减完的旧强度必须让位，
        // 否则一次 1.0 之后，0.5 的一击会被永久压住（strength 不清零的坑）。
        if (active() && v < strength * 0.55f) return;
        strength = v;
        startMillis = Util.getMillis();
    }

    /** 这一帧是否还有可施加的抖动。 */
    public static boolean active() {
        return startMillis != Long.MIN_VALUE
                && (Util.getMillis() - startMillis) / 1000f <= DURATION;
    }

    /**
     * 把当前帧的偏移写进投影矩阵。
     *
     * <p>⚠️ 用"新建平移矩阵再左乘"而不是原地改投影：1.20.1 的
     * {@code GameRenderer#getProjectionMatrix} 目前每次 {@code new} 一个，
     * 但万一哪天改成缓存复用，原地加法会<b>每帧累加</b>、画面越飘越远。
     * 左乘一份新的则无论被调用多少次都只作用一次。</p>
     *
     * @return 传入的矩阵（没有抖动时原样返回，调用方可以直接用返回值）
     */
    public static Matrix4f applyTo(Matrix4f projection) {
        if (!active()) return projection;
        float age = (Util.getMillis() - startMillis) / 1000f;
        float env = (1f - (float) Math.exp(-age * ATTACK)) * (float) Math.exp(-age * DECAY);
        // 两个互质频率叠加：单频会读成"节拍器在摆"
        float nx = Mth.sin(age * 96f) * 0.68f + Mth.sin(age * 61f + 1.7f) * 0.32f;
        float ny = Mth.sin(age * 83f + 2.3f) * 0.68f + Mth.sin(age * 47f + 0.6f) * 0.32f;
        float amp = env * strength * NDC_PER_STRENGTH;

        // 行 0 = (m00, m10, m20, m30)：clip.x += dx * w ⇒ NDC 平移 dx（与深度无关）
        Matrix4f shift = new Matrix4f();
        shift.m30(nx * amp);
        shift.m31(ny * amp);
        return shift.mul(projection);
    }
}
