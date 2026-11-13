package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerProvider;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * mask 效果层注册表：物品 → 该物品的层。所有匹配项都贡献层（靠 order() 定序，不先到先得），
 * 物品接口与外部注册表合并而非互相顶替；resolve 在渲染热路径每帧调用，无注册时返回共享 List.of()。
 */
public final class MaskLayerRegistry {

    private record Entry(Predicate<ItemStack> predicate, List<MaskLayerSpec> layers) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void register(Predicate<ItemStack> predicate, List<MaskLayerSpec> layers) {
        if (predicate == null || layers == null || layers.isEmpty()) return;
        ENTRIES.add(new Entry(predicate, List.copyOf(layers)));
    }

    public static void register(Predicate<ItemStack> predicate, MaskLayerSpec... layers) {
        if (layers == null || layers.length == 0) return;
        register(predicate, List.of(layers));
    }

    public static void registerForItem(Item item, List<MaskLayerSpec> layers) {
        if (item == null) return;
        register(stack -> stack.getItem() == item, layers);
    }

    public static void registerForItem(Item item, MaskLayerSpec... layers) {
        if (layers == null || layers.length == 0) return;
        registerForItem(item, List.of(layers));
    }

    /**
     * 解析物品栈的全部层：物品接口 + 所有匹配的注册项，原样返回（未排序、未过滤）。
     * 排序与效果可用性过滤在 MaskLayerResolver#resolve 里做，方便排查「注册了却没出现」。
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

    /** 清空注册表：给客户端资源重载 / 开发期热调使用；正常运行期不应调用（注册只在 setup 里做一次）。 */
    public static void clear() {
        ENTRIES.clear();
        HallMod.LOGGER.debug("[MaskLayer] 注册表已清空");
    }

    private MaskLayerRegistry() {}
}
