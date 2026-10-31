package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

/**
 * 法术流派。
 *
 * <p>一个流派同时决定三件事：<b>数值</b>（{@code spell_power_<流派>} 加成属性）、
 * <b>法杖匹配</b>（同流派才算"特殊使用"）、<b>展示颜色</b>（{@link #rgb()}，
 * 用在法术物品的介绍里 —— 不同流派的法术一眼能分开）。</p>
 *
 * <p>加新流派时要同步三处：本枚举、{@code RegisterAttributes}（属性 + switch）、
 * 两个语言文件里的 {@code magic_type.rotten_ruins.<小写名>} 译名。
 * 前两处编译器会盯着（switch 没有 default），译名漏了只会显示原始键名。</p>
 */
public enum MagicType {

    /** 寒冰：{@code #8AD8FF}。 */
    ICE(0x8AD8FF),
    /** 火焰：{@code #FF6B3D}。 */
    FIRE(0xFF6B3D),
    /** 死亡：{@code #9C5BD1}。 */
    DEATH(0x9C5BD1),
    /** 自然：{@code #86D45C}。 */
    NATURE(0x86D45C),
    /** 光明：{@code #FFD75E}。 */
    LIGHT(0xFFD75E),
    /** 虚空：{@code #5A4FCF}。 */
    VOID(0x5A4FCF),
    /** 神秘：{@code #E07BFF}。 */
    MYSTERIOUS(0xE07BFF);

    private final int rgb;

    MagicType(int rgb) {
        this.rgb = rgb;
    }

    /** 展示色（{@code 0xRRGGBB}）。给 {@code Component} 上色时用。 */
    public int rgb() {
        return rgb;
    }

    /** 语言键：{@code magic_type.<命名空间>.<小写名>}，与语言文件里写死的字符串一致。 */
    public String translationKey(String namespace) {
        return "magic_type." + namespace + "." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
