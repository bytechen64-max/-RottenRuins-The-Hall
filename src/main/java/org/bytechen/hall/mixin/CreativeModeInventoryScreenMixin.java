package org.bytechen.hall.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.client.gui.creative.SectionedCreativeScreen;
import org.bytechen.hall.mixin.accessor.AccessorAbstractContainerScreen;
import org.bytechen.hall.overworld.registry.SectionedCreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给创造模式物品栏装上「分区隔断行」。
 *
 * <h3>原版那套网格是什么</h3>
 * <p>{@code ItemPickerMenu.items} 是一条**扁平**的 {@code NonNullList<ItemStack>}，
 * {@code scrollTo(float)} 把从某个整行开始的 45 个（5×9）拷进静态 {@code CONTAINER}，
 * 45 个槽只是这块 CONTAINER 的取景窗。所以「隔断行」不需要任何新的渲染机制 ——
 * 只要在那条扁平列表里插进 9 个 {@link ItemStack#EMPTY}，它就是一行空白：
 * 空栈在 {@code GuiGraphics.renderItem} / {@code renderItemDecorations} 里直接 return，
 * 不出 tooltip（{@code renderTooltip} 要求 {@code hoveredSlot.hasItem()}），
 * 也拿不走。行数、滚动条由 {@code calculateRowCount()} / {@code canScroll()}
 * 按列表长度算，插进去多少行它们自己就对。</p>
 *
 * <h3>为什么在客户端做</h3>
 * <p>创造标签的物品表**只在客户端构建**：整份 Forge+Minecraft 里
 * {@code CreativeModeTabs.tryRebuildTabContents()} 只有 {@code CreativeModeInventoryScreen}
 * 一个调用方。所以隔断行不可能跑到服务端去，也就不存在客户端/服务端不同步这回事。</p>
 *
 * <h3>三个注入点</h3>
 * <p>菜单里的物品表有三条填充路径，都得在她们填完之后重排一遍：</p>
 * <ol>
 *   <li>{@code selectTab} —— 切标签（{@code resize()} 与 {@code init()} 也走这里）</li>
 *   <li>{@code refreshCurrentTabContents} —— 特性开关 / OP 权限变化后重建标签内容</li>
 *   <li>{@code refreshSearchResults} —— 搜索框内容变化</li>
 * </ol>
 * <p>重排是**幂等**的：它只读 {@code selectedTab.getDisplayItems()}（原版那份干净的扁平表），
 * 不读已经改过的 {@code menu.items}，所以上面几个方法互相调用（{@code selectTab}
 * 内部会调 {@code refreshSearchResults}）导致重复重排也不会越插越多。</p>
 *
 * <h3>搜索时不分区</h3>
 * <p>带搜索栏的标签一旦输入关键字，结果来自 {@code SearchTree}，是一条与分区无关的
 * 命中列表 —— 那种时候隔断行没有任何意义，所以直接放弃重排，保持原版的扁平结果。</p>
 *
 * <p>渲染在 {@code ContainerScreenEvent.Render.Foreground}（见
 * {@link org.bytechen.hall.client.gui.creative.CreativeTabDividerRenderer}），
 * 那里已经画完槽位高亮、又还没画 tooltip 和手上拖着的物品，正好盖住空白槽的悬停白框。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin implements SectionedCreativeScreen {

    // 网格固定 5 行 × 9 列 = 45 个槽（槽位编号 0..44，45..53 是玩家快捷栏）。
    // 行列数不抽 static final 常量：会被 javac 就地内联，留给 mixin 合并的只是一个死字段。
    @Shadow private float scrollOffs;

    @Shadow @Nullable private EditBox searchBox;

    /** 原版是 {@code private static CreativeModeTab selectedTab;}。 */
    @Shadow private static CreativeModeTab selectedTab;

    @Unique private Map<Integer, Component> splendiding$dividerCache;

    @Override
    public Map<Integer, Component> splendiding$dividerRows() {
        if (this.splendiding$dividerCache == null) {
            this.splendiding$dividerCache = new LinkedHashMap<>();
        }
        return this.splendiding$dividerCache;
    }

    @Override
    public int splendiding$scrollRow() {
        // 复刻 ItemPickerMenu#getRowIndexForScroll(float)：
        //   max((int)(scroll * calculateRowCount()) + 0.5, 0)
        // 不直接调它是因为它是 protected，而本类不在那个包/继承链上。
        // 滚动是整行吸附的（返回 int），所以隔断行永远落在整行边界上。
        AbstractContainerMenu menu = ((AccessorAbstractContainerScreen) (Object) this).splendiding$getMenu();
        if (!(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu picker)) {
            return 0;
        }
        int rowCount = Math.max(0, (picker.items.size() + 9 - 1) / 9 - 5);
        if (rowCount <= 0) {
            return 0;
        }
        return Math.max((int) ((double) (this.scrollOffs * (float) rowCount) + 0.5D), 0);
    }

    // ──────────────────────────────────────────────────────────────
    //  排版
    // ──────────────────────────────────────────────────────────────

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void splendiding$layoutAfterSelectTab(CreativeModeTab tab, CallbackInfo ci) {
        this.splendiding$rebuildLayout();
    }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void splendiding$layoutAfterRefresh(Collection<ItemStack> items, CallbackInfo ci) {
        this.splendiding$rebuildLayout();
    }

    @Inject(method = "refreshSearchResults", at = @At("TAIL"))
    private void splendiding$layoutAfterSearch(CallbackInfo ci) {
        this.splendiding$rebuildLayout();
    }

    /**
     * 把扁平物品表重排成「补空 + 隔断行 + 本段物品」，写回菜单。
     *
     * <p>分区边界来自 {@link SectionedCreativeModeTab.Section#size()} ——
     * 那是各分区的生成器在 {@code buildContents} 时真正吐出去的物品个数
     * （已经被 {@code SectionOutput} 按物品去重过，所以和 {@code getDisplayItems()}
     * 的顺序严格对齐）。</p>
     */
    @Unique
    private void splendiding$rebuildLayout() {
        Map<Integer, Component> dividers = this.splendiding$dividerRows();
        dividers.clear();

        CreativeModeTab tab = selectedTab;
        if (!(tab instanceof SectionedCreativeModeTab sectioned) || !sectioned.isSectioned()) {
            return;
        }
        if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
            return;
        }
        EditBox box = this.searchBox;
        if (tab.hasSearchBar() && box != null && !box.getValue().isEmpty()) {
            return;
        }

        AbstractContainerMenu menu = ((AccessorAbstractContainerScreen) (Object) this).splendiding$getMenu();
        if (!(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu picker)) {
            return;
        }

        List<ItemStack> raw = new ArrayList<>(tab.getDisplayItems());
        if (raw.isEmpty()) {
            return;
        }

        List<ItemStack> laid = new ArrayList<>(raw.size() + 32);
        int cursor = 0;
        for (SectionedCreativeModeTab.Section section : sectioned.sections()) {
            int take = Math.min(section.size(), raw.size() - cursor);
            if (take <= 0) {
                continue;   // 这个分区被特性开关清空了，连标题行都不出现
            }
            // 段尾补空到整行 —— 不补的话标题行会从上一段的半行处折下去
            while (laid.size() % 9 != 0) {
                laid.add(ItemStack.EMPTY);
            }
            dividers.put(laid.size() / 9, section.title());
            for (int i = 0; i < 9; i++) {
                laid.add(ItemStack.EMPTY);
            }
            for (int i = 0; i < take; i++) {
                laid.add(raw.get(cursor++));
            }
        }
        if (dividers.isEmpty()) {
            return;   // 一个分区都没落下东西：保持原版扁平表，别白改
        }

        // 别的 mod 通过 BuildCreativeModeTabContentsEvent 插进来的物品不在任何分区里，
        // 兜在最后一段（无标题）而不是丢掉。
        if (cursor < raw.size()) {
            while (laid.size() % 9 != 0) {
                laid.add(ItemStack.EMPTY);
            }
            for (int i = cursor; i < raw.size(); i++) {
                laid.add(raw.get(i));
            }
        }

        NonNullList<ItemStack> items = picker.items;
        items.clear();
        items.addAll(laid);
        // 列表长度变了，取景窗得重新灌一次（scrollOffs 不变，行号会跟着变）
        picker.scrollTo(this.scrollOffs);
    }

    // ──────────────────────────────────────────────────────────────
    //  隔断行不是"空地"
    // ──────────────────────────────────────────────────────────────

    /**
     * 拦住落在隔断行上的点击。
     *
     * <p>原版点空白网格槽会把手上拿着的东西清掉（"点空地放下"）。这个行为对普通空槽
     * 是对的，但隔断行是一条标题栏，点它把东西弄没很突兀。</p>
     *
     * <p>槽位判定只用 {@code index < 45}：{@code ItemPickerMenu} 先加 45 个网格槽、
     * 再加 9 个快捷栏槽，而 {@code CONTAINER} / {@code SlotWrapper} 都是包级私有，
     * 从 mixin 包里引用不到。这里已经先用 {@code selectedTab} 限定了是分区标签，
     * 所以不需要再验容器。</p>
     */
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
            at = @At("HEAD"), cancellable = true)
    private void splendiding$blockDividerClick(@Nullable Slot slot, int slotId, int mouseButton,
                                               ClickType clickType, CallbackInfo ci) {
        if (slot == null || !(selectedTab instanceof SectionedCreativeModeTab)) {
            return;
        }
        int index = slot.index;
        if (index < 0 || index >= 45) {
            return;
        }
        int row = this.splendiding$scrollRow() + index / 9;
        if (this.splendiding$dividerRows().containsKey(row)) {
            ci.cancel();
        }
    }
}
