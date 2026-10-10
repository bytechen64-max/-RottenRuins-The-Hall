package org.bytechen.hall.client.mask;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerProvider;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * mask 效果层的注册表：{@code 物品 → 该物品的层}。
 *
 * <p>对应 {@link MaskEffectRegistry} 的另一半 —— 那边登记「有哪些效果」，
 * 这边登记「哪个物品用哪些效果」。</p>
 *
 * <h3>与 CosmicLayerRegistry 的两处有意的不同</h3>
 * <ol>
 *   <li><b>所有匹配项都贡献层，而不是「第一条命中就返回」。</b>
 *       cosmic 一个物品只可能有一层星空，先到先得合理；但层是可以叠加的，
 *       如果沿用先到先得，后来注册的效果就永远挂不上去 —— 那正是本系统要
 *       避免的事。层与层之间靠 {@link MaskLayerSpec#order()} 定序，不靠注册顺序。</li>
 *   <li><b>物品接口与注册表<b>合并</b>而不是互相顶替。</b>
 *       物品自描述的是「我这件东西天生就有的效果」，外部注册的往往是「某个
 *       玩法系统额外加上的效果」，两者并不冲突。cosmic 那边接口优先是因为
 *       它只能有一层，这里没有这个约束。</li>
 * </ol>
 *
 * <h3>热路径</h3>
 * <p>{@link #resolve} 每帧对每个被渲染的物品调用一次。没有任何注册、物品也没
 * 实现接口时它直接返回 {@link List#of()}（共享单例，不分配）—— 也就是没接入
 * 本系统的物品付出的是「一次 {@code instanceof}」的代价。</p>
 */
public final class MaskLayerRegistry {

    private record Entry(Predicate<ItemStack> predicate, List<MaskLayerSpec> layers) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** 按谓词注册：匹配到的物品都会获得这些层。 */
    public static void register(Predicate<ItemStack> predicate, List<MaskLayerSpec> layers) {
        if (predicate == null || layers == null || layers.isEmpty()) return;
        ENTRIES.add(new Entry(predicate, List.copyOf(layers)));
    }

    /** 按谓词注册（变长参数版）。 */
    public static void register(Predicate<ItemStack> predicate, MaskLayerSpec... layers) {
        if (layers == null || layers.length == 0) return;
        register(predicate, List.of(layers));
    }

    /** 按具体物品注册。 */
    public static void registerForItem(Item item, List<MaskLayerSpec> layers) {
        if (item == null) return;
        register(stack -> stack.getItem() == item, layers);
    }

    /** 按具体物品注册（变长参数版）。 */
    public static void registerForItem(Item item, MaskLayerSpec... layers) {
        if (layers == null || layers.length == 0) return;
        registerForItem(item, List.of(layers));
    }

    /**
     * 解析某个物品栈的全部层：物品接口 + 所有匹配的注册项，原样返回（未排序、未过滤）。
     *
     * <p>排序与效果可用性过滤在 {@link MaskLayerResolver#resolve} 里做 ——
     * 这样「注册表里有什么」和「这一帧该画什么」是两件事，方便排查
     * 「层注册了却没出现」这类问题。</p>
     */
    public static List<MaskLayerSpec> resolve(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        boolean isProvider = stack.getItem() instanceof MaskLayerProvider;
        if (ENTRIES.isEmpty() && !isProvider) return List.of();

        List<MaskLayerSpec> out = null;
        if (isProvider) {
            List<MaskLayerSpec> fromItem = ((MaskLayerProvider) stack.getItem()).maskLayers(stack);
            if (fromItem != null && !fromItem.isEmpty()) {
                out = new ArrayList<>(fromItem);
            }
        }
        for (Entry e : ENTRIES) {
            if (e.predicate().test(stack)) {
                if (out == null) out = new ArrayList<>(e.layers());
                else out.addAll(e.layers());
            }
        }
        return out == null ? List.of() : out;
    }

    public static boolean isEmpty() {
        return ENTRIES.isEmpty();
    }

    /**
     * 清空注册表。给客户端资源重载 / 开发期热调使用；
     * 正常运行期不应该调用它（注册一般在 {@code FMLClientSetupEvent} 里只做一次）。
     */
    public static void clear() {
        ENTRIES.clear();
        HallMod.LOGGER.debug("[MaskLayer] 注册表已清空");
    }

    private MaskLayerRegistry() {}
}
