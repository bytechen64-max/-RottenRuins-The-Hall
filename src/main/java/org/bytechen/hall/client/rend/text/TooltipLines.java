package org.bytechen.hall.client.rend.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * 把 tooltip 的"标签 + 正文"拼成一行的公共写法（绯红誓约 / 寂寒白日共用）。
 *
 * <h3>为什么要拆成两个兄弟组件，而不是一条 {@code "%s  …"} 模板</h3>
 * <p>渐变是<b>逐字上色</b>的：{@link FlowingNameColors#gradient} 内部走
 * {@code getString()} 把整行拍平成字符串，再一个字一个字地赋色。所以如果先把
 * 标签插进翻译模板、再对整行套渐变，<b>标签自己的颜色会一起被拍平</b> ——
 * 两者就不可能不同色了。</p>
 *
 * <p>于是语言文件里"标签"与"正文"是两个键，这里把它们拼成两个兄弟组件，
 * 各自带自己的一对渐变颜色。这也是为什么 {@code TranslateUtils} 里
 * 格挡/湍流这类行都有一对 {@code label.*} 与正文键。</p>
 *
 * <h3>为什么放在 client 包却由物品调用</h3>
 * <p>与 {@link FlowingNameColors} 同一处境：物品类在公共代码里（服务端也会加载），
 * 而这里要用到客户端才有的时间源。所以真正的"客户端才流动"判断收在
 * {@link FlowingNameColors#line} 里，本类只负责拼接与配色。</p>
 */
public final class TooltipLines {

    /** 标签与正文之间的分隔：两个空格（原版 tooltip 没有制表位，只能靠空格对位）。 */
    private static final String GAP = "  ";

    private TooltipLines() {}

    /**
     * 一行"标签 + 正文"，两半各自一对渐变颜色。
     *
     * @param labelKey  标签的翻译键
     * @param bodyKey   正文的翻译键
     * @param level     {@code appendHoverText} 拿到的 level（决定流动还是静态渐变）
     * @param labelFrom 标签渐变起色（ARGB，alpha 忽略）
     * @param labelTo   标签渐变止色
     * @param bodyFrom  正文渐变起色 —— 一般比标签暗一档，正文才不会读成第二个标题
     * @param bodyTo    正文渐变止色
     */
    public static MutableComponent feature(String labelKey, String bodyKey, Level level,
                                           int labelFrom, int labelTo,
                                           int bodyFrom, int bodyTo) {
        return FlowingNameColors.line(Component.translatable(labelKey), labelFrom, labelTo, level)
                .append(Component.literal(GAP))
                .append(FlowingNameColors.line(Component.translatable(bodyKey), bodyFrom, bodyTo, level));
    }
}
