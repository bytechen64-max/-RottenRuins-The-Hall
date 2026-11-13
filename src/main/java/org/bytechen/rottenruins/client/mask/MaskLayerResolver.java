package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把「这个物品有哪些层」收敛成「这一帧、这个渲染上下文实际该画哪些层」：合并 → 按 order 升序 → 过滤上下文开关。
 * 排序是稳定的且 JSON 层先入列，所以同 order 时 JSON 在前。着色器未加载必须显式跳过并点名一次：
 * 否则照常烘 quad、照常塞缓冲，画出来是空白或另一套状态，症状像「物品变透明了」，极难反查。
 */
public final class MaskLayerResolver {

    /** 解析结果：配置 + 已确认就绪的效果实现。 */
    public record Resolved(MaskLayerSpec spec, MaskEffectPass pass) {}

    /** 已警告过的原因，避免每帧刷屏。 */
    private static final Set<String> WARNED = new HashSet<>();

    /** @return 按 order 升序、已过滤的层；没有可画的层时返回共享空列表（不分配） */
    public static List<Resolved> resolve(ItemStack stack, ItemDisplayContext ctx,
                                         List<MaskLayerSpec> jsonLayers) {
        List<MaskLayerSpec> registered = MaskLayerRegistry.resolve(stack);
        if (jsonLayers.isEmpty() && registered.isEmpty()) return List.of();

        List<MaskLayerSpec> all = new ArrayList<>(jsonLayers.size() + registered.size());
        all.addAll(jsonLayers);
        all.addAll(registered);
        if (all.size() > 1) {
            all.sort(Comparator.comparingDouble(MaskLayerSpec::order));
        }

        List<Resolved> out = new ArrayList<>(all.size());
        for (MaskLayerSpec spec : all) {
            if (!spec.shouldRender(ctx)) continue;

            MaskEffectPass pass = MaskEffectRegistry.get(spec.effectId());
            if (pass == null) {
                warnOnce("effect:" + spec.effectId(),
                        "mask 层引用了未注册的效果 id '" + spec.effectId() + "'，该层被跳过");
                continue;
            }
            if (!pass.ready()) {
                warnOnce("shader:" + spec.effectId(),
                        "效果 '" + spec.effectId() + "' 的着色器未加载，该层被跳过"
                                + "（检查 assets/hall/shaders/core/ 与注册日志）");
                continue;
            }
            out.add(new Resolved(spec, pass));
            logFirstResolution(stack, spec);
        }
        return out.isEmpty() ? List.of() : out;
    }

    /** 每个「物品 + 效果 + 遮罩」只记一次：这一层到底解析到了没有、用的是哪张遮罩。
     *  故意用 INFO 且不挂 isDebugEnabled —— 开发环境 DEBUG 没开，第一版一次都没打出来。 */
    private static void logFirstResolution(ItemStack stack, MaskLayerSpec spec) {
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String key = item + "|" + spec.effectId() + "|" + spec.mask();
        if (!LOGGED_RESOLUTIONS.add(key)) return;
        HallMod.LOGGER.info("[MaskLayer] 层生效：物品={} effect={} mask={} order={} "
                        + "（同一组合只记一次）",
                item, spec.effectId(), spec.mask(), spec.order());
    }

    private static final Set<String> LOGGED_RESOLUTIONS = new HashSet<>();

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            HallMod.LOGGER.warn("[MaskLayer] {}", message);
        }
    }

    public static void resetWarnings() {
        WARNED.clear();
    }

    private MaskLayerResolver() {}
}
