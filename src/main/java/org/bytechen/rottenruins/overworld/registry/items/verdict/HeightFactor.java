package org.bytechen.rottenruins.overworld.registry.items.verdict;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

// 天穹裁决的高度系数。伤害已在普攻上定死（见 VerdictDamage：25 + 5 = 30），三个技能一律不吃高度加成 ——
// 系数只喂"覆盖"（光柱半径 / 突进距离 / 领域半径），于是爬高的收益是"一次能罩住多少个 30"而不是数字膨胀。
// 映射区间刻意与 DomeriteStatsHelper 的刻度对齐（-64 → 62 → 320），但两端都往中间收：
// 工具本身已是 0.50×~2.00×，这里只给 0.90×~1.60×，否则覆盖再吃 2 倍会变成双曲线失控。
public final class HeightFactor {

    public static final double MIN_Y = -64.0;
    public static final double MAX_Y = 320.0;

    public static final double MIN_FACTOR = 0.90;
    public static final double MAX_FACTOR = 1.60;

    // 指数形态：光柱与领域线性（半径本来就是宽度 / 面积的代理）；突进用四次方压扁到 1.00~1.13 附近
    // —— 位移过头比范围过头更容易把人甩进虚空或卡进地形，所以只在窄区间内放大。三段的地表基准见下面 *_BASE（单位：格）。
    private static final double BEAM_EXP = 1.0;
    private static final double DASH_EXP = 0.25;
    private static final double FIELD_EXP = 1.0;

    private HeightFactor() {}

    /** 原始 0..1 高度进度：Y ≤ -64 恒为 0，Y ≥ 320 恒为 1。 */
    public static float progress(double y) {
        return (float) Mth.clamp((y - MIN_Y) / (MAX_Y - MIN_Y), 0.0, 1.0);
    }

    public static float progress(Entity entity) {
        return entity == null ? 0f : progress(entity.getY());
    }

    /** 基准系数 k ∈ [0.90, 1.60]，线性插值。 */
    public static float factor(double y) {
        return (float) (MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * progress(y));
    }

    /** 以基准档 1.0 为锚做幂变形：k = 1 时结果恒为 1，两端各自被压 / 拉。 */
    private static float shaped(double y, double exp) {
        float k = factor(y);
        return (float) Math.pow(k, exp);
    }

    public static float beamRadius(Entity caster) {
        return shaped(caster.getY(), BEAM_EXP);
    }

    /** 突进距离系数（开四次方，压到 0.90 ~ 1.13 附近）。 */
    public static float dashDistance(Entity caster) {
        return shaped(caster.getY(), DASH_EXP);
    }

    public static float fieldRadius(Entity caster) {
        return shaped(caster.getY(), FIELD_EXP);
    }

    public static final float BEAM_RADIUS_BASE = 2.6f;
    public static final float DASH_DISTANCE_BASE = 12.0f;
    public static final float FIELD_RADIUS_BASE = 6.0f;

    public static float beamRadiusBlocks(Entity caster) {
        return BEAM_RADIUS_BASE * beamRadius(caster);
    }

    public static float dashDistanceBlocks(Entity caster) {
        return DASH_DISTANCE_BASE * dashDistance(caster);
    }

    public static float fieldRadiusBlocks(Entity caster) {
        return FIELD_RADIUS_BASE * fieldRadius(caster);
    }

    /** 光柱<b>视觉</b>最大长度（格，抬头 90° 对应）；它和判定高度是两回事，别绑在一起 —— 判定另有 {@link #BEAM_HIT_MAX_HEIGHT} 上限。 */
    public static final float BEAM_MAX_LENGTH = 440.0f;

    /** 光柱<b>判定</b>用的高度上限（格）= 64：判定必须停在玩家看得见的范围里，视觉再长也不放大。 */
    public static final float BEAM_HIT_MAX_HEIGHT = 64.0f;

    public static float beamHitHeight(float visualLength) {
        return Math.min(visualLength, BEAM_HIT_MAX_HEIGHT);
    }

    /** 抬头角 → 光柱长度（pitch 正数朝下、负数朝上，故取负号）；这条曲线只用于视觉和"是否瞄准"判定，伤害高度走 {@link #beamHitHeight}。 */
    public static float beamLengthFromPitch(float pitchDegrees) {
        float up = Mth.clamp(-pitchDegrees, 0f, 90f);
        return BEAM_MAX_LENGTH * (up / 90f);
    }
}
