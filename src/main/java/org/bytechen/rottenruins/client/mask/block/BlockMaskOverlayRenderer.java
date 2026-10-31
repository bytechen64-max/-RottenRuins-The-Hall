package org.bytechen.rottenruins.client.mask.block;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.rottenruins.client.mask.MaskEffectPass;
import org.bytechen.rottenruins.client.mask.MaskEffectRegistry;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 方块模型视角的 mask 效果层渲染器 —— 世界里的方块（BER）和方块物品（ItemRenderer
 * 注入）共用这一套。
 *
 * <h3>两个入口</h3>
 * <ul>
 *   <li>{@link #renderBlock} —— 方块实体每帧渲染时调用；方块模型由
 *       {@code BlockRenderDispatcher} 按 {@link BlockState} 取。</li>
 *   <li>{@link #renderItem} —— 方块物品在手持/物品栏/掉落物形态下调用；
 *       模型是 {@code ItemRenderer} 已经拿到的那个 BakedModel（block item 的模型
 *       就是方块模型，所以 quad 形状与世界里一致）。</li>
 * </ul>
 *
 * <h3>和物品版 {@code MaskLayerRenderer} 的异同</h3>
 * <table>
 *   <tr><th></th><th>物品版</th><th>本类（方块模型）</th></tr>
 *   <tr><td>几何</td><td>用本体贴图烘的 16×16 平面</td><td>模型自己的 quad（六个面 / 任意形状）</td></tr>
 *   <tr><td>UV</td><td>指向本体贴图，shader 里用 BaseMin/BaseSize 换算</td>
 *       <td>CPU 侧重映射到遮罩 sprite，shader 直接采样</td></tr>
 *   <tr><td>上下文开关</td><td>GUI / 手持 / 世界</td><td>世界（方块）/ 同一套三开关（方块物品）</td></tr>
 * </table>
 *
 * <h3>为什么 UV 在 CPU 侧重映射</h3>
 * <p>方块有六张面贴图（甚至多个 sprite），uniform 没法 per-face；而这个渲染器
 * 本来就要逐 quad 写顶点，所以顺手把每个 quad 的 UV 从它自己的 sprite 矩形线性
 * 映射到遮罩 sprite 矩形 —— 着色器里 {@code UV0} 直接就是遮罩图集 UV。效果实现
 * （{@link MaskEffectPass}）完全不用知道宿主长什么样。</p>
 *
 * <h3>深度 / 混合由效果的 RenderType 决定</h3>
 * <p>本体由区块 mesh（方块）或原版物品渲染（方块物品）先画、深度已写好，所以
 * 这里统一用 {@code LEQUAL + polygon offset}、只写颜色。混合方式由
 * {@link MaskEffectPass#immediateType()} 返回的变体决定：
 * {@code glow} 是 ADDITIVE，{@code death_core} 是 TRANSLUCENT（遮罩窗口内覆盖、
 * 窗口外透明）。</p>
 *
 * <h3>光影包（Oculus / Iris）与阴影 pass</h3>
 * <p>v1 的策略是<b>不画</b>：方块侧还没有对应的「延迟回放」队列（物品侧的
 * {@code MaskLayerLateRenderQueue} 是按物品重新入队，方块需要在 BER 里存矩阵 +
 * 状态再在主帧缓冲回放，属于下一阶段）。在光影包激活时直接跳过并只提示一次，
 * 避免写进 GBuffer 被当成场景材质 —— 那比不画更糟。阴影 pass 一律跳过。</p>
 */
public final class BlockMaskOverlayRenderer {

    /** BLOCK 顶点格式：每顶点 8 个 int（32 字节）。 */
    private static final int VERTEX_STRIDE = 8;

    /** BLOCK 顶点格式里 UV0 的起始 int 下标：position(3) + color(1) = 4。 */
    private static final int UV_OFFSET = 4;

    /** 探测模型 quad 用的固定种子，避免每帧随机数导致 quad 顺序不稳定。 */
    private static final long QUAD_SEED = 42L;

    /** 已提示过的问题，避免每帧刷屏。资源重载时由 {@link #resetWarnings()} 清空。 */
    private static final Set<String> WARNED = new HashSet<>();

    /**
     * 世界里的方块：由 BER 调用。
     *
     * @param poseStack 当前 BER 的 PoseStack（已平移到方块位置）
     * @param buffer    渲染缓冲（通常就是 {@code MultiBufferSource.BufferSource}）
     * @param light     打包光照（这些层不读 UV2，透传只为满足顶点格式）
     * @param overlay   打包 overlay
     * @param state     方块状态（用来取方块模型）
     * @param layers    该方块要挂的层（未排序、未过滤）
     */
    public static void renderBlock(PoseStack poseStack, MultiBufferSource buffer,
                                   int light, int overlay, BlockState state,
                                   List<MaskLayerSpec> layers) {
        if (layers == null || layers.isEmpty()) return;
        if (!canRenderHere()) return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = collectQuads(model, state);
        renderQuads(poseStack, buffer, light, overlay, quads, layers,
                null, "block:" + blockId(state));
    }

    /**
     * 方块物品：由 {@code ItemRenderer} 注入点调用（手持 / 物品栏 / 掉落物）。
     *
     * <p>几何取物品模型自己的 quad；block item 的模型就是方块模型，所以和世界里
     * 的方块层是同一份形状与 UV 映射。物品的 display 变换已经在 {@code poseStack}
     * 上，直接同栈绘制即可。</p>
     */
    public static void renderItem(PoseStack poseStack, MultiBufferSource buffer,
                                  int light, int overlay, BakedModel model,
                                  List<MaskLayerSpec> layers, ItemDisplayContext context,
                                  ItemStack stack) {
        if (layers == null || layers.isEmpty()) return;
        if (!canRenderHere()) return;

        List<BakedQuad> quads = collectQuads(model, null);
        renderQuads(poseStack, buffer, light, overlay, quads, layers,
                context, "item:" + itemId(stack));
    }

    /** 阴影 pass / 光影包 / 无世界 三种情况统一在这里拦掉。 */
    private static boolean canRenderHere() {
        // 阴影 pass 是「从光源方向看」，这些层却按相机视角算，入内会投出错误阴影。
        if (HeldItemOutlineCompat.isOculusShadowPass()) return false;

        // v1：光影包激活时跳过（见类注释最后一节）。只说一次，避免刷屏。
        if (CosmicItemShaderCompat.isOculusShaderPackActive()) {
            warnOnce("shader_pack",
                    "检测到光影包：方块/方块物品的 mask 层暂时跳过（v1 还没有延迟回放队列）");
            return false;
        }

        return Minecraft.getInstance().level != null;
    }

    /**
     * 收集一个模型的全部 quad。
     *
     * <p><b>不能只调 {@code getQuads(state, null, rand)}</b>：{@code SimpleBakedModel}
     * 在 null 方向只返回「unculled faces」，而普通方块模型的六个面全部按方向存进
     * culledFaces —— 那条路拿到的是空列表（症状：方块本体正常、层永远不出现）。
     * 必须六个方向各取一次。</p>
     */
    private static List<BakedQuad> collectQuads(BakedModel model, @Nullable BlockState state) {
        List<BakedQuad> quads = new ArrayList<>(7);
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(state, direction, RandomSource.create(QUAD_SEED)));
        }
        quads.addAll(model.getQuads(state, null, RandomSource.create(QUAD_SEED)));
        // putBulkData 需要方向来算法线；无方向的 unculled quad 留到后续版本再支持。
        quads.removeIf(quad -> quad.getDirection() == null);
        return quads;
    }

    private static void renderQuads(PoseStack poseStack, MultiBufferSource buffer,
                                    int light, int overlay, List<BakedQuad> quads,
                                    List<MaskLayerSpec> layers,
                                    @Nullable ItemDisplayContext context, String owner) {
        if (quads.isEmpty()) {
            warnOnce("quads:" + owner, owner + " 取不到模型 quad，mask 层无法绘制");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        // 时间源与物品侧 / cosmic 一致：游戏 tick 数。
        float time = (float) (mc.level.getGameTime() % Integer.MAX_VALUE);
        PoseStack.Pose pose = poseStack.last();

        // 层序：按 order 升序（与物品侧一致）。单层时不复制。
        List<MaskLayerSpec> ordered = layers;
        if (layers.size() > 1) {
            ordered = new ArrayList<>(layers);
            ordered.sort(Comparator.comparingDouble(MaskLayerSpec::order));
        }

        for (MaskLayerSpec layer : ordered) {
            // 方块（context == null）只有世界上下文；方块物品用物品那套三开关。
            boolean visible = context == null
                    ? layer.showInWorld()
                    : layer.shouldRender(context);
            if (!visible) continue;

            MaskEffectPass pass = MaskEffectRegistry.get(layer.effectId());
            if (pass == null) {
                warnOnce("effect:" + layer.effectId(),
                        "mask 层引用了未注册的效果 id '" + layer.effectId() + "'，该层被跳过");
                continue;
            }
            if (!pass.ready()) {
                warnOnce("shader:" + layer.effectId(),
                        "效果 '" + layer.effectId() + "' 的着色器未加载，该层被跳过");
                continue;
            }

            RenderType type = pass.immediateType();
            if (type == null) continue;

            TextureAtlasSprite mask = atlas.getSprite(layer.mask());
            // 方块/方块模型没有「本体贴图光源」这个概念，base 传 null；效果实现自己决定要不要用。
            pass.apply(layer, mask, null, time);

            // 一次性诊断（与物品侧「层生效」同款）：能区分「没被调用 / 层被过滤」
            // 和「层在画但看着不对」这两类完全不同的故障。
            logFirstLayer(owner, layer, quads.size());

            VertexConsumer consumer = buffer.getBuffer(type);
            for (BakedQuad quad : quads) {
                // 7 参数版 putBulkData：rgb 固定白，alpha 恒 1；UV/位置/法线/光贴图取 quad 自身。
                consumer.putBulkData(pose, remapUvToMask(quad, mask),
                        1.0F, 1.0F, 1.0F, light, overlay);
            }

            // 每层单独 flush：Uniform.set() 的值在 endBatch -> ShaderInstance.apply()
            // 才真正上传，攒到一起会让后一层的 uniform 盖掉前一层（与物品侧同一条约定）。
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(type);
            }
        }
    }

    /**
     * 把一个 quad 的 UV 从它自己的 sprite 矩形线性映射到遮罩 sprite 矩形。
     *
     * <p>只改 UV0，位置/法线/光贴图原样保留，所以表面层精确贴合宿主形状
     * （cube、台阶、方块物品模型都成立）。</p>
     */
    private static BakedQuad remapUvToMask(BakedQuad quad, TextureAtlasSprite mask) {
        TextureAtlasSprite source = quad.getSprite();
        if (source == null) return quad;

        float su0 = source.getU0(), sv0 = source.getV0();
        float suSpan = source.getU1() - su0;
        float svSpan = source.getV1() - sv0;
        if (suSpan <= 0.0F || svSpan <= 0.0F) return quad;

        float mu0 = mask.getU0(), mv0 = mask.getV0();
        float muSpan = mask.getU1() - mu0;
        float mvSpan = mask.getV1() - mv0;

        int[] src = quad.getVertices();
        int[] dst = src.clone();
        for (int v = 0; v < 4; v++) {
            int uv = v * VERTEX_STRIDE + UV_OFFSET;
            float u = Float.intBitsToFloat(dst[uv]);
            float w = Float.intBitsToFloat(dst[uv + 1]);
            float localU = (u - su0) / suSpan;
            float localV = (w - sv0) / svSpan;
            dst[uv] = Float.floatToRawIntBits(mu0 + localU * muSpan);
            dst[uv + 1] = Float.floatToRawIntBits(mv0 + localV * mvSpan);
        }
        return new BakedQuad(dst, quad.getTintIndex(), quad.getDirection(), mask, quad.isShade());
    }

    // ── 一次性诊断日志 ──────────────────────────────────────────────
    //
    // 与物品侧 MaskLayerResolver 的「层生效」日志同一个目的：从画面上看
    // 「没被调用 / 层被过滤掉」和「层在画但画错了」是一样的，日志能把它们分开。

    /** 已记录过的「宿主 + 效果 + 遮罩」组合。 */
    private static final Set<String> LOGGED = new HashSet<>();

    private static String blockId(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? String.valueOf(state.getBlock()) : key.toString();
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? String.valueOf(stack.getItem()) : key.toString();
    }

    private static void logFirstLayer(String owner, MaskLayerSpec layer, int quadCount) {
        String key = owner + "|" + layer.effectId() + "|" + layer.mask();
        if (!LOGGED.add(key)) return;
        HallMod.LOGGER.info("[MaskLayer] 表面层生效：宿主={} effect={} mask={} quads={} order={}"
                        + "（同一组合只记一次）",
                owner, layer.effectId(), layer.mask(), quadCount, layer.order());
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            HallMod.LOGGER.warn("[MaskLayer] {}", message);
        }
    }

    /** 资源重载后允许重新警告/记录一次（与物品侧同一套约定）。 */
    public static void resetWarnings() {
        WARNED.clear();
        LOGGED.clear();
    }

    private BlockMaskOverlayRenderer() {}
}
