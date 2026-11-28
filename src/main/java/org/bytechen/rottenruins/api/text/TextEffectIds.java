package org.bytechen.rottenruins.api.text;

/**
 * § 分节符文本特效的公共常量（<b>双端安全</b>：只用 String / int / float，不引用任何客户端类）。
 *
 * <h3>为什么所有"魔法字符串"都收在这一处</h3>
 * <p>语法本身是作者可见的契约（写进 lang / 命令 / 演示屏）。解析器、Java 构建器、
 * 文档、演示屏如果各自拼一遍字符串，改一个字母就会漏掉一半调用点 —— 项目在
 * {@code TranslateUtils} 那套翻译键上已经吃过"散落硬编码"的亏。这里把命名空间、
 * 前缀、效果名、默认调色板全部集中，任何地方都从这里引用。</p>
 *
 * <h3>语法形状（与 {@code docs/proposals/custom-section-code-text-effects.md} 一致）</h3>
 * <pre>
 *   §[rrs:&lt;效果名&gt;[:参数]…]   开始一个效果 span
 *   §[/]                        关闭最内层
 *   §[/&lt;效果名&gt;]               关闭指定效果
 *   §[rrs:reset]                清空全部自定义效果
 * </pre>
 *
 * <p>命名空间选 {@code rrs}（Rotten RuinS）而不是裸单字母：裸单字母已被 Bedrock
 * 材质色码与多个文本 mod 占用，命名空间让"别的 mod 的 § 码"原样透传。</p>
 */
public final class TextEffectIds {

    /** 命名空间（`§[rrs:...]` 里的 `rrs`）。 */
    public static final String NAMESPACE = "rrs";

    /** 打开标记的固定前缀。所有"这行有没有效果标记"的快速判断都扫它。 */
    public static final String OPEN_PREFIX = "\u00a7[" + NAMESPACE + ":";

    /** 关闭最内层。 */
    public static final String CLOSE_ALL = "\u00a7[/]";

    /** 清空全部自定义效果（不影响原版格式码）。 */
    public static final String RESET_ALL = "\u00a7[" + NAMESPACE + ":reset]";

    // ── 效果名 ───────────────────────────────────────────────────

    public static final String GRAD = "grad";
    public static final String SOLID = "solid";
    public static final String RAINBOW = "rainbow";
    public static final String PULSE = "pulse";
    public static final String SHAKE = "shake";
    public static final String WAVE = "wave";
    public static final String GLITCH = "glitch";
    public static final String RESET = "reset";

    // ── 默认调色板（王庭主题）─────────────────────────────────────

    /** 腐化绿：渐变起点。 */
    public static final int DEFAULT_GRAD_FROM = 0x6FBF4A;
    /** 王庭血红：渐变终点。 */
    public static final int DEFAULT_GRAD_TO = 0x8B1E2D;
    /** 苍白火：纯色 / 呼吸的默认色。 */
    public static final int DEFAULT_PALE = 0xE8E4D8;

    // ── 默认参数（与提案 §4.3、预览 GIF 的公式一一对应）────────────

    public static final float DEFAULT_RAINBOW_SPEED = 1.0F;
    public static final float DEFAULT_RAINBOW_SAT = 0.72F;
    public static final float DEFAULT_RAINBOW_VAL = 1.0F;
    public static final float DEFAULT_PULSE_AMP = 0.35F;
    public static final float DEFAULT_PULSE_SPEED = 1.0F;
    public static final float DEFAULT_SHAKE_AMP = 1.0F;
    public static final float DEFAULT_SHAKE_FREQ = 2.0F;
    public static final float DEFAULT_WAVE_AMP = 1.2F;
    public static final float DEFAULT_WAVE_LEN = 12.0F;
    public static final float DEFAULT_WAVE_SPEED = 1.0F;
    public static final float DEFAULT_GLITCH_AMP = 2.5F;
    public static final float DEFAULT_GLITCH_RATE = 12.0F;
    public static final float DEFAULT_GLITCH_PROB = 0.10F;

    /** 单标记长度上限（防滥用；解析器见到超长/未闭合按原版未知码处理）。 */
    public static final int MAX_MARKER_LENGTH = 256;

    private TextEffectIds() {}
}
