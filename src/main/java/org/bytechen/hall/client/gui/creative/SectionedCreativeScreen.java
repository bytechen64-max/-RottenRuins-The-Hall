package org.bytechen.hall.client.gui.creative;

import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * 分区创造标签的界面侧接口。
 *
 * <p>由 {@code CreativeModeInventoryScreenMixin} 混入 {@code CreativeModeInventoryScreen}，
 * 渲染器（{@link CreativeTabDividerRenderer}）通过 {@code instanceof} 认领自己该画的界面。</p>
 *
 * <p>之所以走「接口 + mixin 实现」而不是一个静态变量：静态变量在
 * 玩家用 {@code /gamemode} 切出去、或者同帧存在两个界面时容易残留，
 * 而接口是长在界面实例上的，界面没了就自然没了。</p>
 */
public interface SectionedCreativeScreen {

    /**
     * 当前布局里的隔断行 —— key 是**全局行号**（相对整份物品列表，不是屏幕上的第几行），
     * value 是那一行要显示的标题。空 map 表示这个界面没有分区。
     */
    Map<Integer, Component> splendiding$dividerRows();

    /** 当前滚动到的顶行行号（原版 {@code ItemPickerMenu#getRowIndexForScroll} 的口径）。 */
    int splendiding$scrollRow();
}
