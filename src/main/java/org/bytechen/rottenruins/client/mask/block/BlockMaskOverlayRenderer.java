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
 * 方块模型视角的 mask 效果层渲染器 —— 世界里的方块（BER）与方块物品（ItemRenderer 注入）共用这一套。
 * UV 在 CPU 侧逐 quad 从自身 sprite 矩形线性重映射到遮罩 sprite（方块六面各不同，uniform 没法 per-face）。
 * 深度/混合由 {@link MaskEffectPass#immediateType()} 决定：本体先画且已写深度，这里 LEQUAL + polygon offset 只写颜色。
 * v1 在光影包激活时不画：方块侧还没有延迟回放队列，写进 GBuffer 被当成场景材质比不画更糟。
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

    /** 世界里的方块：由 BER 调用，模型按 {@link BlockState} 从 BlockRenderDispatcher 取。 */
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

    /** 方块物品：block item 的模型就是方块模型，display 变换已在 poseStack 上，直接同栈绘制。 */
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

        // v1：光影包激活时跳过；只说一次，避免刷屏。
        if (CosmicItemShaderCompat.isOculusShaderPackActive()) {
            warnOnce("shader_pack",
                    "检测到光影包：方块/方块物品的 mask 层暂时跳过（v1 还没有延迟回放队列）");
            return false;
        }

        return Minecraft.getInstance().level != null;
    }

    /** 六个方向各取一次：null 方向只返回 unculled faces，普通方块的六个面都在 culledFaces 里。 */
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

            // 一次性诊断：能区分「没被调用 / 层被过滤」和「层在画但看着不对」这两类故障。
            logFirstLayer(owner, layer, quads.size());

            VertexConsumer consumer = buffer.getBuffer(type);
            for (BakedQuad quad : quads) {
                // 7 参数版 putBulkData：rgb 固定白，alpha 恒 1；UV/位置/法线/光贴图取 quad 自身。
                consumer.putBulkData(pose, remapUvToMask(quad, mask),
                        1.0F, 1.0F, 1.0F, light, overlay);
            }

            // 每层单独 flush：uniform 在 endBatch -> apply() 才真正上传，攒着会让后一层盖掉前一层。
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(type);
            }
        }
    }

    /** 把一个 quad 的 UV 从它自己的 sprite 矩形线性映射到遮罩 sprite 矩形；只改 UV0，位置/法线/光贴图原样。 */
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

    // 一次性诊断日志：与物品侧 MaskLayerResolver 同目的 —— 从画面上看「没被调用 / 层被过滤」和「层在画但画错了」是一样的。

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
