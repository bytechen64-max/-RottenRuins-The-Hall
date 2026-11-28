package org.bytechen.rottenruins.client.rend.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * tooltip "标签 + 正文" 拼一行。渐变是逐字上色、会把整行拍平，所以标签不能拼进正文模板，只能做成两个兄弟组件。
 */
public final class TooltipLines {

    private static final String GAP = "  ";

    private TooltipLines() {}

    public static MutableComponent feature(String labelKey, String bodyKey, Level level,
                                           int labelFrom, int labelTo,
                                           int bodyFrom, int bodyTo) {
        return feature(labelKey, Component.translatable(bodyKey), level,
                labelFrom, labelTo, bodyFrom, bodyTo);
    }

    public static MutableComponent feature(String labelKey, Component body, Level level,
                                           int labelFrom, int labelTo,
                                           int bodyFrom, int bodyTo) {
        return FlowingNameColors.line(Component.translatable(labelKey), labelFrom, labelTo, level)
                .append(Component.literal(GAP))
                .append(FlowingNameColors.line(body, bodyFrom, bodyTo, level));
    }
}
