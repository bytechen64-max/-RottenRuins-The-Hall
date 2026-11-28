package org.bytechen.rottenruins.client.gui.wheel;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 轮盘/装载界面的本地化入口。
 *
 * <p>所有界面文案都走 lang key（{@code rotten_ruins.wheel.*}），由 datagen 的
 * {@code LangDataEN/CN} 生成 en_us/zh_cn。这里只做一件事：按当前语言把 key 变成字符串。
 * 用 {@code getString()} 而不是 {@code Component} 是因为这些界面大量用
 * {@code font.width()} 做布局计算，需要纯文本宽度。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WheelI18n {

    /** 结果提示等需要原始 Component 的地方直接用 {@link Component#translatable}。 */
    private WheelI18n() {
    }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 只取 key 本身（没有参数/格式化时用；等价于 {@link #tr(String, Object...)}）。 */
    public static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** 页面罗马数字（I / II / III）：不翻译，但集中在这里避免散落字符串。 */
    public static String pageLabel(int page) {
        return switch (page) {
            case 1 -> "II";
            case 2 -> "III";
            default -> "I";
        };
    }
}
