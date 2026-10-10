package org.bytechen.hall.overworld.registry.items.verdict;

import me.shedaniel.autoconfig.ConfigHolder;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;

/**
 * 天穹裁决技能组的配置读取点。
 *
 * <h3>为什么要单独包一层，而不是各处直接读 ConfigHelper</h3>
 * <p>三个理由，都是这个项目里已经吃过亏的：</p>
 * <ol>
 *   <li><b>配置可能没就绪。</b>{@code ConfigHelper.configHolder} 在
 *       {@code ConfigHelper.registry()} 之前是 null；物品的 {@code use()} 与
 *       实体渲染器的 {@code render()} 都可能在那之前被碰到（尤其是数据生成与
 *       渲染线程首帧）。这里所有读取都做空值兜底，返回默认值而不是抛 NPE。</li>
 *   <li><b>解析可能抛异常。</b>配置 DLL 是外部类（cloth-autoconfig），
 *       它抛出来的 {@code Throwable} 如果一路冒到渲染线程，
 *       会把整个帧搞崩。所以每条读取都包了 try/catch。</li>
 *   <li><b>想让"关掉"这件事是可靠的。</b>总开关 {@code verdictSkillsEnabled}
 *       为 false 时，三个技能都必须彻底不触发 —— 而不是"试着触发然后失败"。</li>
 * </ol>
 *
 * <p>这也是 {@code ShockwaveRenderer.refractionScale()} 已经在用的写法，
 * 这里只是把它统一到一处，免得三个技能各自抄一遍。</p>
 */
public final class VerdictTuning {

    // ── 默认值：配置不可用时一律回落到"标准档" ──
    private static final boolean DEFAULT_SKILLS = true;
    private static final boolean DEFAULT_BEAM = true;
    private static final boolean DEFAULT_DASH = true;
    private static final boolean DEFAULT_FIELD = true;
    private static final float DEFAULT_BRIGHTNESS = 1.0f;
    private static final float DEFAULT_DASH_DISTANCE = 1.0f;
    private static final float DEFAULT_FIELD_RADIUS = 1.0f;
    private static final float DEFAULT_FIELD_RATE = 1.0f;
    private static final float DEFAULT_COOLDOWN_SECONDS = 1.5f;
    private static final float DEFAULT_STACK_SCALE = 1.0f;

    /** 冷却秒数的合理区间：0.5 秒（近乎无冷却）~ 30 秒。 */
    private static final float MIN_COOLDOWN_SECONDS = 0.5f;
    private static final float MAX_COOLDOWN_SECONDS = 30.0f;
    /** 倍率类配置的区间：0（关闭该效果）~ 5（极端加强）。 */
    private static final float MAX_SCALE = 5.0f;

    private VerdictTuning() {}

    /** 取配置快照；任何环节不可用都返回 null，由各 getter 回落默认值。 */
    private static SplendidingConfig cfg() {
        try {
            ConfigHolder<SplendidingConfig> holder = ConfigHelper.configHolder;
            if (holder == null) return null;
            return holder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 读一个布尔开关。
     * <p>泛型签名用 {@code Function<..., Boolean>} 而不是 {@code Predicate}：
     * Java 的自动装箱在 {@code test()} 里会静默拆箱，而配置文件解析可能给出 null，
     * 那就成了一个 {@code NullPointerException} 而不是"回落到默认值"。</p>
     */
    private static boolean flag(java.util.function.Function<SplendidingConfig, Boolean> get,
                                boolean fallback) {
        SplendidingConfig c = cfg();
        if (c == null) return fallback;
        try {
            Boolean v = get.apply(c);
            return v == null ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static float scale(java.util.function.Function<SplendidingConfig, Float> get,
                               float fallback, float min, float max) {
        SplendidingConfig c = cfg();
        if (c == null) return fallback;
        try {
            Float v = get.apply(c);
            if (v == null || !Float.isFinite(v)) return fallback;
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ── 总开关与三个技能开关 ────────────────────────────────────────

    /** 整套技能是否启用。false 时右键退回原版剑行为。 */
    public static boolean skillsEnabled() {
        return flag(c -> c.verdictSkillsEnabled, DEFAULT_SKILLS);
    }

    public static boolean beamEnabled() {
        return skillsEnabled() && flag(c -> c.verdictBeamEnabled, DEFAULT_BEAM);
    }

    public static boolean dashEnabled() {
        return skillsEnabled() && flag(c -> c.verdictDashEnabled, DEFAULT_DASH);
    }

    public static boolean fieldEnabled() {
        return skillsEnabled() && flag(c -> c.verdictFieldEnabled, DEFAULT_FIELD);
    }

    // ── 数值 ────────────────────────────────────────────────────────

    /** 光柱亮度倍率（1.0 = 默认）。 */
    public static float beamBrightness() {
        return scale(c -> c.verdictBeamBrightness, DEFAULT_BRIGHTNESS, 0f, MAX_SCALE);
    }

    /** 突进距离倍率（1.0 = 地表 12 格）。 */
    public static float dashDistanceScale() {
        return scale(c -> c.verdictDashDistanceScale, DEFAULT_DASH_DISTANCE, 0f, MAX_SCALE);
    }

    /** 领域半径倍率（1.0 = 地表 6 格）。 */
    public static float fieldRadiusScale() {
        return scale(c -> c.verdictFieldRadiusScale, DEFAULT_FIELD_RADIUS, 0f, MAX_SCALE);
    }

    /**
     * 领域出剑提速倍率（&gt;1 更快）。
     * <p>它不改伤害数值（伤害恒定 25+5），只改出剑间隔 —— 见
     * {@code VerdictFieldEntity.SWORD_INTERVAL}。</p>
     */
    public static float fieldRate() {
        return scale(c -> c.verdictFieldRate, DEFAULT_FIELD_RATE, 0f, MAX_SCALE);
    }

    /** 共享冷却池的基础冷却（tick）。 */
    public static int baseCooldownTicks() {
        float seconds = scale(c -> c.verdictCooldownSeconds, DEFAULT_COOLDOWN_SECONDS,
                MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS);
        return Math.max(1, Math.round(seconds * 20f));
    }

    /** 裁决层数的每层增益倍率（0 = 完全关掉层数机制）。 */
    public static float stackScale() {
        return scale(c -> c.verdictStackScale, DEFAULT_STACK_SCALE, 0f, MAX_SCALE);
    }

    /** 领域每次出剑同时锁定的目标数上界（1 ~ 6）。 */
    public static final int DEFAULT_FIELD_TARGETS = 3;
    public static final int MIN_FIELD_TARGETS = 1;
    public static final int MAX_FIELD_TARGETS = 6;

    /**
     * 领域每次出剑锁定几个目标。
     *
     * <p>读的是 int 而不是 float 倍率，所以不能复用 {@link #scale}：
     * 它带了 {@code Float} 装箱与范围钳制那套逻辑。这里单独写一遍，
     * 同样保证"配置没就绪 / 解析抛异常 / 值非法"三种情况下都回落到默认值 ——
     * 这个类存在的理由就是把那三种情况挡在调用方之外。</p>
     */
    public static int fieldMaxTargets() {
        SplendidingConfig c = cfg();
        if (c == null) return DEFAULT_FIELD_TARGETS;
        try {
            int v = c.verdictFieldMaxTargets;
            return Math.max(MIN_FIELD_TARGETS, Math.min(MAX_FIELD_TARGETS, v));
        } catch (Throwable t) {
            return DEFAULT_FIELD_TARGETS;
        }
    }
}
