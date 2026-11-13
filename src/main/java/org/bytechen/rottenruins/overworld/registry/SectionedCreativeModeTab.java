package org.bytechen.rottenruins.overworld.registry;

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
 * 带「分区隔断行」的创造模式物品栏标签：分区只记录尺寸，隔断行由客户端 mixin 补 {@link ItemStack#EMPTY} 排版；
 * 物品按 {@link Item} 去重（先声明的分区赢），{@link #getDisplayItems()} 对外仍是干净扁平表。
 */
public class SectionedCreativeModeTab extends CreativeModeTab {

    /** 一个分区：标题 + 生成器 + 上次构建的物品数（size 由 buildContents 时写回，客户端据此切分）。 */
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

        /** 上一次构建时这个分区真正贡献了几个物品（被去重吃掉的、特性开关关掉的都不算）。 */
        public int size() { return this.size; }
    }

    private final List<Section> sections;

    protected SectionedCreativeModeTab(TabBuilder builder) {
        super(builder);
        this.sections = List.copyOf(builder.sections);
    }

    /** 分区表，顺序即网格里的顺序。 */
    public List<Section> sections() { return this.sections; }

    /** 没声明过分区时客户端排版整体退化成原版的扁平列表。 */
    public boolean isSectioned() { return !this.sections.isEmpty(); }

    /** {@code CreativeModeTab.builder()} 的分区版。 */
    public static TabBuilder builder() { return new TabBuilder(); }

    /** 分区版 Builder：覆写只是为了收窄返回类型，好让 {@code .title(...).icon(...).section(...)} 能一路链下去。 */
    public static final class TabBuilder extends CreativeModeTab.Builder {

        private final List<Section> sections = new ArrayList<>();

        public TabBuilder() {
            super(Row.TOP, 0);
            // 必须在这里设工厂：父类 build() 以 tabFactory.apply(this) 收尾，而注册代码只拿到 Builder 接口、看不到子类。
            withTabFactory(b -> new SectionedCreativeModeTab((TabBuilder) b));
        }

        /** 声明一个分区；跨分区重复的物品由本类按 {@link Item} 去掉，最后一个分区可以放心当兜底。 */
        public TabBuilder section(Component title, DisplayItemsGenerator generator) {
            this.sections.add(new Section(title, generator));
            return this;
        }

        /** 禁掉扁平物品表：它设的生成器会在 {@link #build()} 里被分区生成器覆盖，写了没反应不如直接抛。 */
        @Override
        public TabBuilder displayItems(DisplayItemsGenerator generator) {
            throw new UnsupportedOperationException(
                    "SectionedCreativeModeTab 请用 section(title, generator) 声明内容，不要用 displayItems()");
        }

        @Override
        public CreativeModeTab build() {
            // 先快照分区表：build() 之后再改 builder 不该影响已建好的标签。
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

        /** 包一层原版 {@code Output}：先按 {@link Item} 去重再数这一区贡献了几个（放这里让兜底分区天然安全）。 */
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
