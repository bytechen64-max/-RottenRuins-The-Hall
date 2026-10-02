package org.bytechen.hall.client.mask;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerSpec;
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
 * 把「这个物品有哪些层」（注册表 / 物品接口 / 模型 JSON）收敛成
 * 「这一帧、这个渲染上下文，实际该画哪些层」。
 *
 * <h3>做三件事</h3>
 * <ol>
 *   <li><b>合并</b>三个来源：模型 JSON 的层 + {@link MaskLayerRegistry}
 *       （物品接口与谓词注册）</li>
 *   <li><b>排序</b>：按 {@link MaskLayerSpec#order()} 升序。排序是<b>稳定</b>的，
 *       而且 JSON 层排在注册表层之前 —— 所以 order 相同的层，JSON 在前。</li>
 *   <li><b>过滤</b>：上下文开关（GUI / 手持 / 世界）与效果可用性
 *       （id 没注册、着色器没加载 → 该层整层跳过）</li>
 * </ol>
 *
 * <h3>为什么要显式过滤「着色器没加载」</h3>
 * <p>着色器加载失败时 {@code shader()} 返回 null。这时若不跳过，我们会照常
 * 烘 quad、照常往缓冲里塞，最后画出来的是另一套状态下的东西或一片空白 ——
 * 排查起来像「物品变透明了」。跳过并<b>点名一次</b>，症状就直接指向着色器。</p>
 */
public final class MaskLayerResolver {

    /** 解析结果：配置 + 已确认就绪的效果实现。 */
    public record Resolved(MaskLayerSpec spec, MaskEffectPass pass) {}

    /** 已警告过的原因，避免每帧刷屏。 */
    private static final Set<String> WARNED = new HashSet<>();

    /**
     * @param stack      正在渲染的物品
     * @param ctx        渲染上下文
     * @param jsonLayers 模型 JSON 声明的层（没有就传 {@link List#of()}）
     * @return 按 order 升序、已过滤的层列表；没有可画的层时返回共享空列表（不分配）
     */
    public static List<Resolved> resolve(ItemStack stack, ItemDisplayContext ctx,
                                         List<MaskLayerSpec> jsonLayers) {
        List<MaskLayerSpec> registered = MaskLayerRegistry.resolve(stack);
        if (jsonLayers.isEmpty() && registered.isEmpty()) return List.of();

        List<MaskLayerSpec> all = new ArrayList<>(jsonLayers.size() + registered.size());
        all.addAll(jsonLayers);
        all.addAll(registered);
        // sort 是稳定排序（TimSort），加上 JSON 层先入列，于是同 order 时 JSON 在前。
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

    /**
     * 每个「物品 + 效果 + 遮罩」只记一次：这一层到底解析到了没有、用的是哪张遮罩。
     *
     * <p>这条日志是补课的产物：排查「加了层却看不出效果 / 效果糊满整把武器」时，
     * 第一件要确认的事就是「层有没有被解析、mask 指的是哪张图」。没有它就只能靠猜，
     * 而这两种症状（层没解析 / 遮罩本身盖住了整把武器）从画面上看是一样的。</p>
     *
     * <p><b>故意用 INFO、不挂 isDebugEnabled</b>：第一版挂在 isDebugEnabled 上，
     * 结果开发环境里这个 logger 的 DEBUG 并没有开，诊断日志<b>一次都没打出来</b>——
     * 一个「排查时才需要、平时最多几行」的日志，用 INFO 更安全。</p>
     */
    private static void logFirstResolution(ItemStack stack, MaskLayerSpec spec) {
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String key = item + "|" + spec.effectId() + "|" + spec.mask();
        if (!LOGGED_RESOLUTIONS.add(key)) return;
        HallMod.LOGGER.info("[MaskLayer] 层生效：物品={} effect={} mask={} order={} "
                        + "（同一组合只记一次）",
                item, spec.effectId(), spec.mask(), spec.order());
    }

    /** 已记过日志的「物品+效果+遮罩」组合。 */
    private static final Set<String> LOGGED_RESOLUTIONS = new HashSet<>();

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            HallMod.LOGGER.warn("[MaskLayer] {}", message);
        }
    }

    /** 资源重载后允许重新警告一次（比如着色器改了之后又失败）。 */
    public static void resetWarnings() {
        WARNED.clear();
    }

    private MaskLayerResolver() {}
}
