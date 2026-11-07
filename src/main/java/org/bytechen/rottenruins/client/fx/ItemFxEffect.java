package org.bytechen.rottenruins.client.fx;

import java.util.List;

/**
 * 一个可注册的物品效果。
 *
 * <p>框架的插拔点。<b>目标形状</b>是：加一个新效果 = 新写一个实现 + 注册一行，
 * 而不是回去改 {@code ItemOutlinePipeline} 里那个 {@code ColorMode} 分支
 * 和三个文件里的 switch。</p>
 *
 * <h3>S1 与 S1b 的边界（诚实说明）</h3>
 * <p>S1（当前）只接好了<b>声明</b>这一半：{@link #id()} / {@link #domain()} /
 * {@link #fields()} / {@link #blend()} 已经是真的被读取、被消费的（场池按
 * {@link #fields()} 开图，驱动按 {@link #domain()} 分类，合成按 {@link #blend()} 走）。
 * 真正的 {@code capture} / {@code composite} 钩子要等 S1b 把描边那几趟 pass
 * 从管线搬进 {@link org.bytechen.rottenruins.client.fx.effects.OutlineFxEffect} 时再加 ——
 * 那时骨架才算长齐，但这之前<b>画面必须是逐位不变的</b>。</p>
 */
public interface ItemFxEffect {

    /** 效果 id（注册表键，也是场名的前缀）。 */
    String id();

    /** 作用域。驱动用它决定"什么时候、拿哪些目标"来跑这个效果。 */
    ItemFxDomain domain();

    /** 这个效果需要哪些离屏场。每帧由驱动交给 {@link ItemFxFieldPool} 保证存在。 */
    List<ItemFxFieldSpec> fields();

    /**
     * 合成方式的<b>缺省值</b>。
     *
     * <p>注意它是缺省而不是最终值：描边自己就允许逐物品在 {@code ADD} 与 {@code ALPHA}
     * 之间选（{@code ICustomOutline.outlineBlend()}），所以真正合成时按物品取，
     * 这里给的是"没有逐物品指定时用什么"。</p>
     */
    default ItemFxBlend blend() {
        return ItemFxBlend.ADD;
    }

    /**
     * 同一件物品上有多个效果时的绘制顺序（升序）。
     *
     * <p>与 {@code mask_layers} 的 {@code order} 是同一个语义，将来两边会统一。</p>
     */
    default float order() {
        return 100.0f;
    }

    /** 是否需要在剪影阶段捕获几何（跑 capture pass）。S1b 起生效。 */
    default boolean needsCapture() {
        return true;
    }
}
