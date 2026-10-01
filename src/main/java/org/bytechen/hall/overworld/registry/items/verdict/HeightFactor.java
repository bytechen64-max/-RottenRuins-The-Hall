package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 天穹裁决 —— 高度系数。
 *
 * <h3>为什么数值设计里没有伤害这一项</h3>
 * <p>这把剑的伤害已经在<b>普攻</b>上定死了（见 {@link VerdictDamage}：25 + 5 = 30），
 * 所以三个技能<b>一律不吃高度加成</b>。高度系数只喂给"覆盖"：</p>
 * <table border="1">
 *   <tr><th>维度</th><th>地表系数</th><th>天顶系数</th><th>效果</th></tr>
 *   <tr><td>光柱半径</td><td>×1.00</td><td>×1.60</td><td>越高打的是一条越宽的竖线</td></tr>
 *   <tr><td>突进距离</td><td>×1.00</td><td>×1.30</td><td>越高冲得越远</td></tr>
 *   <tr><td>领域半径</td><td>×1.00</td><td>×1.85</td><td>越高管得越宽</td></tr>
 * </table>
 *
 * <p>这样"爬高"的收益从"数字膨胀"变成"一次能罩住多少个 30"，
 * 曲线可预期、可平衡，也不会和毕业件撞车；同时低空阶段技能不会变成废招
 * （上一版把系数挂在伤害上会导致低 Y 时技能远不如平砍，玩家会直接无视它）。</p>
 *
 * <h3>映射区间</h3>
 * <p>刻意与 {@code DomeriteStatsHelper} 的刻度对齐（-64 → 62 → 320），
 * 但<b>两端都往中间收</b>：工具本身是 0.50×~2.00×，这里只给 0.90×~1.60×。
 * 因为伤害那块已经由工具吃掉了 4 倍落差，覆盖再吃 2 倍会变成双曲线失控。</p>
 */
public final class HeightFactor {

    /** 系数下界对应的 Y（与 DomeriteStatsHelper.MIN_Y 一致）。 */
    public static final double MIN_Y = -64.0;
    /** 系数上界对应的 Y（与 DomeriteStatsHelper.MAX_Y 一致）。 */
    public static final double MAX_Y = 320.0;

    /** 地表 / 基准档的系数，任何 Y ≤ 这个点的附近都近似 1.00。 */
    public static final double MIN_FACTOR = 0.90;
    /** 最高空档的系数。 */
    public static final double MAX_FACTOR = 1.60;

    // ── 各技能的开方/插值形态 ──────────────────────────────────────
    //  光柱：线性，直接乘。半径本来就是"宽度"，线性最直观。
    private static final double BEAM_EXP = 1.0;
    //  突进：开四次方——位移过头比范围过头更容易失控（会把人甩进虚空/卡进地形），
    //  所以把系数压扁到 1.00 ~ 1.13 附近再乘一个固定幅度。
    private static final double DASH_EXP = 0.25;
    //  领域：平方——半径是"面积"的代理，半径 ×1.85 已经是 ×3.4 的面积，
    //  用平方更贴近"这一下管住了多大一片战场"的直觉。
    private static final double FIELD_EXP = 1.0;

    private HeightFactor() {}

    /**
     * 原始 0..1 的高度进度。
     * <p>Y ≤ -64 恒为 0，Y ≥ 320 恒为 1。</p>
     */
    public static float progress(double y) {
        return (float) Mth.clamp((y - MIN_Y) / (MAX_Y - MIN_Y), 0.0, 1.0);
    }

    /** 实体所在位置的原始高度进度。 */
    public static float progress(Entity entity) {
        return entity == null ? 0f : progress(entity.getY());
    }

    /** 基准系数 k ∈ [0.90, 1.60]，线性插值。 */
    public static float factor(double y) {
        return (float) (MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * progress(y));
    }

    /** 实体位置处、按指数 exp 压形后的系数。 */
    private static float shaped(double y, double exp) {
        float k = factor(y);
        // 以基准档 1.0 为锚做幂变形：k=1 时结果恒为 1，两端各自被压/拉。
        return (float) Math.pow(k, exp);
    }

    // ── 对外：三个技能各自的覆盖系数 ────────────────────────────────

    /** 光柱半径系数（线性 0.90 ~ 1.60）。 */
    public static float beamRadius(Entity caster) {
        return shaped(caster.getY(), BEAM_EXP);
    }

    /** 突进距离系数（开四次方，压到 0.90 ~ 1.13 附近）。 */
    public static float dashDistance(Entity caster) {
        return shaped(caster.getY(), DASH_EXP);
    }

    /** 领域半径系数（线性 0.90 ~ 1.60）。 */
    public static float fieldRadius(Entity caster) {
        return shaped(caster.getY(), FIELD_EXP);
    }

    // ── 基准值 × 系数 ────────────────────────────────────────────────

    /** 光柱半径基准（格，地表值）。 */
    public static final float BEAM_RADIUS_BASE = 2.6f;
    /** 突进距离基准（格，地表值）。 */
    public static final float DASH_DISTANCE_BASE = 12.0f;
    /** 领域半径基准（格，地表值）。 */
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

    /** 光柱最大长度（格），与抬头角 90° 对应。 */
    public static final float BEAM_MAX_LENGTH = 44.0f;

    /**
     * 抬头角 → 光柱长度。
     * <p>pitch 在 MC 里是"正数朝下、负数朝上"，所以这里取负号。</p>
     */
    public static float beamLengthFromPitch(float pitchDegrees) {
        float up = Mth.clamp(-pitchDegrees, 0f, 90f);
        return BEAM_MAX_LENGTH * (up / 90f);
    }
}
