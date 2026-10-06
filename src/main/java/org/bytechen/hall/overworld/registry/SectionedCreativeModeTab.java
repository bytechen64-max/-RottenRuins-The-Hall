package org.bytechen.hall.overworld.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 带「分区隔断行」的创造模式物品栏标签。
 *
 * <h3>它解决什么</h3>
 * <p>一个标签里塞一百多个物品时，原版只有一条扁平的物品流 —— 找东西全靠眼睛。
 * 这个子类把标签的内容声明成若干 {@link Section 分区}，每个分区在网格里以一行
 * 160×18 的流动色带（{@code hall:gui_tab_divider}）+ 居中彩色标题开头。</p>
 *
 * <h3>分区行是怎么变出来的</h3>
 * <p><b>本类不往物品列表里塞任何占位物</b>。它只记录「每个分区贡献了几个物品」
 * （{@link Section#size()}），真正的排版发生在客户端界面层：
 * {@code CreativeModeInventoryScreenMixin} 把 {@code menu.items} 重组成
 * 「补空到整行 → 9 个 {@link ItemStack#EMPTY} 当隔断行 → 本段物品」的形态。
 * 空槽在原版渲染里什么都不画、也不出 tooltip、也拿不走，所以隔断行的代价几乎为零，
 * 而且 {@link #getDisplayItems()} 对外仍然是一份干净的扁平物品表（JEI / 别的 mod
 * 读到的和以前一模一样）。</p>
 *
 * <h3>为什么要用 withTabFactory</h3>
 * <p>Forge 在 {@code CreativeModeTab.Builder#withTabFactory} 上开了口子
 * （1.20.1 就有），否则没法让 {@code DeferredRegister} 注册出一个子类实例。
 * {@link TabBuilder} 构造时就把工厂设好，注册代码不需要关心这件事。</p>
 *
 * <h3>去重</h3>
 * <p>和改造前的写法一样按 {@link Item} 去重：先声明的分区赢，后面的分区
 * （尤其是最后那个「其它」兜底分区）不会重复出现同一个物品。
 * 去重发生在转发给原版 {@code Output} 之前，所以计数和实际进网格的物品严格一致。</p>
 */
public class SectionedCreativeModeTab extends CreativeModeTab {

    /**
     * 一个分区：标题 + 声明它装哪些物品的生成器 + 上一次构建出来的物品个数。
     *
     * <p>{@link #size()} 由 {@link TabBuilder#build()} 装的那个合成生成器在
     * {@code buildContents} 时写回，客户端排版时拿它切分 {@link #getDisplayItems()}。</p>
     */
    public static final class Section {
        private final Component title;
        private final DisplayItemsGenerator generator;
        private int size;

        private Section(Component title, DisplayItemsGenerator generator) {
            this.title = title;
            this.generator = generator;
        }

        /** 隔断行上显示的标题。 */
        public Component title() { return this.title; }

        /** 上一次构建时这个分区真正贡献了几个物品（被去重吃掉的、以及特性开关关掉的都不算）。 */
        public int size() { return this.size; }
    }

    private final List<Section> sections;

    protected SectionedCreativeModeTab(TabBuilder builder) {
        super(builder);
        this.sections = List.copyOf(builder.sections);
    }

    /** 分区表，顺序即网格里的顺序。 */
    public List<Section> sections() { return this.sections; }

    /** 有没有声明过分区。没有的话客户端排版会整体退化成原版的扁平列表。 */
    public boolean isSectioned() { return !this.sections.isEmpty(); }

    /** {@code CreativeModeTab.builder()} 的分区版。 */
    public static TabBuilder builder() { return new TabBuilder(); }

    // ──────────────────────────────────────────────────────────────
    //  Builder
    // ──────────────────────────────────────────────────────────────

    /**
     * 分区版 Builder。
     *
     * <p>覆写的那一堆方法只是为了把返回类型收窄成 {@link TabBuilder}，
     * 好让 {@code .title(...).icon(...).section(...)} 能一路链下去 ——
     * 不这么做的话链到父类方法就断了，后面不能再接 {@code .section(...)}。</p>
     */
    public static final class TabBuilder extends CreativeModeTab.Builder {

        private final List<Section> sections = new ArrayList<>();

        public TabBuilder() {
            super(Row.TOP, 0);
            // 父类 build() 最后一步是 tabFactory.apply(this)，这里换成我们的子类。
            // 必须在这里设好：注册代码拿到的就是个普通 Builder 接口，看不到子类。
            withTabFactory(b -> new SectionedCreativeModeTab((TabBuilder) b));
        }

        /**
         * 声明一个分区。
         *
         * @param title     隔断行上显示的标题（走 {@code Component.translatable} 便于本地化）
         * @param generator 这个分区装哪些物品。直接照原版 {@code displayItems} 的写法写就行，
         *                  跨分区重复的物品由本类按 {@link Item} 自动去掉，
         *                  所以最后一个分区可以放心地「把所有本模组物品都倒进来」当兜底。
         */
        public TabBuilder section(Component title, DisplayItemsGenerator generator) {
            this.sections.add(new Section(title, generator));
            return this;
        }

        /**
         * 禁止直接设扁平物品表。
         *
         * <p>分区标签的物品表是由各分区的生成器拼出来的；如果还允许调这个方法，
         * 它设的生成器会在 {@link #build()} 里被覆盖掉 —— 那种「写了没反应」的坑
         * 不如直接抛出来。</p>
         */
        @Override
        public TabBuilder displayItems(DisplayItemsGenerator generator) {
            throw new UnsupportedOperationException(
                    "SectionedCreativeModeTab 请用 section(title, generator) 声明内容，不要用 displayItems()");
        }

        @Override
        public CreativeModeTab build() {
            // 把各区拼成一个生成器交给父类。列表先做快照：build() 之后再改 builder 不该影响已建好的标签。
            List<Section> snapshot = List.copyOf(this.sections);
            super.displayItems((params, output) -> {
                // claimed 的生命周期 = 一次 buildContents。先声明的分区赢。
                Set<Item> claimed = new HashSet<>();
                for (Section section : snapshot) {
                    section.size = 0;
                    section.generator.accept(params, new SectionOutput(output, section, claimed));
                }
            });
            return super.build();
        }

        // ── 返回类型收窄（纯转发） ──

        @Override public TabBuilder title(Component title) { super.title(title); return this; }
        @Override public TabBuilder icon(Supplier<ItemStack> icon) { super.icon(icon); return this; }
        @Override public TabBuilder alignedRight() { super.alignedRight(); return this; }
        @Override public TabBuilder hideTitle() { super.hideTitle(); return this; }
        @Override public TabBuilder noScrollBar() { super.noScrollBar(); return this; }
        @Override public TabBuilder withSearchBar() { super.withSearchBar(); return this; }
        @Override public TabBuilder withSearchBar(int width) { super.withSearchBar(width); return this; }
        @Override public TabBuilder withLabelColor(int color) { super.withLabelColor(color); return this; }
        @Override public TabBuilder withSlotColor(int color) { super.withSlotColor(color); return this; }
        @Override public TabBuilder withTabsImage(ResourceLocation image) { super.withTabsImage(image); return this; }
        @Override public TabBuilder withBackgroundLocation(ResourceLocation background) {
            super.withBackgroundLocation(background);
            return this;
        }
        @Override public TabBuilder withTabsBefore(ResourceLocation... tabs) { super.withTabsBefore(tabs); return this; }
        @Override public TabBuilder withTabsAfter(ResourceLocation... tabs) { super.withTabsAfter(tabs); return this; }

        /**
         * 包一层原版 {@code Output}：先把物品按 {@link Item} 去重，再去数这一区贡献了几个。
         *
         * <p>去重放在这里而不是各分区的生成器里，是为了让「兜底分区把所有物品倒进来」
         * 这种写法天然安全 —— 它不需要知道前面几个分区已经拿走了什么。</p>
         */
        private record SectionOutput(CreativeModeTab.Output delegate, Section section, Set<Item> claimed)
                implements CreativeModeTab.Output {

            @Override
            public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                if (!this.claimed.add(stack.getItem())) {
                    return;
                }
                this.section.size++;
                this.delegate.accept(stack, visibility);
            }
        }
    }
}
