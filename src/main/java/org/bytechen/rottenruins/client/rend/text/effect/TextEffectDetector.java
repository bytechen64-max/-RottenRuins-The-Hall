package org.bytechen.rottenruins.client.rend.text.effect;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.text.TextEffectIds;

import java.util.Optional;

/**
 * "这段文本里有没有我们的标记"的<b>快速探测</b>。
 *
 * <h3>为什么探测要这么抠</h3>
 * <p>L1（{@code ClientLanguage.getVisualOrder}）对<b>所有</b>组件文本都会调用；如果每次先
 * {@code getString()} 拼出完整字符串再判断，普通 tooltip/聊天也会多一次分配。
 * 这里用 {@link FormattedText#visit} 逐段扫，命中就借助 {@code STOP_ITERATION} 提前停止，
 * 全程零字符串分配（内容串本身是 Component 里现成的）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TextEffectDetector {

    private TextEffectDetector() {}

    /** 原文（String 路径）里是否有打开标记。 */
    public static boolean hasMarkers(String text, int from) {
        return text != null && text.indexOf(TextEffectIds.OPEN_PREFIX, Math.max(0, from)) >= 0;
    }

    /** 组件路径：逐段扫，找到就停。 */
    public static boolean hasMarkers(FormattedText text) {
        if (text == null) return false;
        try {
            Optional<net.minecraft.util.Unit> found = text.visit((style, content) -> {
                if (content != null && content.indexOf(TextEffectIds.OPEN_PREFIX) >= 0) {
                    return FormattedText.STOP_ITERATION;
                }
                return Optional.empty();
            }, Style.EMPTY);
            return found.isPresent();
        } catch (Throwable t) {
            // 探测失败按"没有标记"处理：最坏是这行不显示效果，而不是把文字画坏。
            return false;
        }
    }
}
