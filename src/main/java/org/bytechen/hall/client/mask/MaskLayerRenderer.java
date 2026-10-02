package org.bytechen.hall.client.mask;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.mask.render.MaskLayerRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 逐层绘制 mask 效果。
 *
 * <h3>每一层做四件事</h3>
 * <ol>
 *   <li>向效果实现要这个上下文下的 RenderType（即时 / 延迟 / 延迟一手）；</li>
 *   <li>把遮罩 sprite 的三个几何 uniform 与该层参数交给效果实现去设；</li>
 *   <li>用遮罩烘出的物品 quad 填进该 RenderType 的缓冲；</li>
 *   <li><b>立刻 flush 这一层</b>。</li>
 * </ol>
 *
 * <h3>为什么必须每层单独 flush</h3>
 * <p>{@code Uniform.set()} 只是把值记在 {@code Uniform} 对象上，真正上传发生在
 * {@code ShaderInstance.apply()}，也就是 {@code endBatch} 的时候。如果把好几个层
 * 攒到一起再 flush，后一个层设的 uniform 会把前一个层的覆盖掉 —— 症状是
 * 「同一物品上两个泛光层出现了同一个颜色/同一个宽度」。所以顺序只能是
 * 「设 A → 画 A → flush A → 设 B → 画 B → flush B」，代价是每层多一次 draw call，
 * 而这正是 cosmic 那条路已经在付的成本。</p>
 *
 * <h3>为什么传 {@code ItemStack.EMPTY}</h3>
 * <p>{@code ItemRenderer.renderQuadList} 在收到非空且带附魔光泽的物品时，会顺手
 * 往同一个缓冲里再塞一个 glint quad。我们的层是叠在物品之上的独立效果，不需要
 * 那份光泽（而且它会被算进本层的 RenderType 里，颜色状态全不对）。传空栈把这一步
 * 关掉，也让「层」的语义干净。</p>
 */
public final class MaskLayerRenderer {

    /**
     * 画完一批已解析的层。
     *
     * @param layers     已由 {@link MaskLayerResolver} 过滤并排序的层
     * @param baseSprite 物品本体贴图的 sprite：既是各层的<b>光源</b>，也是各层 quad 的
     *                   <b>几何来源</b>（见下）。取不到时传 {@code null}
     * @param lateRender true = 光影下的延迟回放（走 MAIN_TARGET / 一手不测深度）
     *
     * <h3>为什么几何取自本体贴图</h3>
     * <p>{@code ItemModelGenerator} 按 alpha 裁几何：本体贴图轮廓外是透明的，所以用它
     * 烘出来的 quad 精确等于武器轮廓。而遮罩图为了不漏掉轮廓外的像素，通常画成
     * 「整张不透明」—— 拿它烘几何就会得到一整块矩形（虚空之剑的 32×32 遮罩正是如此）。</p>
     */
    public static void render(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay,
                              List<MaskLayerResolver.Resolved> layers, boolean lateRender,
                              @Nullable TextureAtlasSprite baseSprite) {
        if (layers.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        // 时间源与 cosmic 保持一致：游戏 tick 数。具体快慢由每个效果的 speed 决定。
        float time = mc.level != null
                ? (float) (mc.level.getGameTime() % Integer.MAX_VALUE)
                : 0.0F;
        boolean firstPerson = isFirstPerson(ctx);

        for (MaskLayerResolver.Resolved layer : layers) {
            MaskEffectPass pass = layer.pass();

            RenderType renderType = lateRender
                    ? (firstPerson ? pass.handAfterLevelType() : pass.afterLevelType())
                    : pass.immediateType();
            if (renderType == null) continue;

            TextureAtlasSprite maskSprite = atlas.getSprite(layer.spec().mask());
            pass.apply(layer.spec(), maskSprite, baseSprite, time);

            // 几何优先用本体贴图（= 武器轮廓）。只有拿不到本体贴图时才退回遮罩，
            // 那种情况下如果遮罩是整张不透明的，会看到一块矩形 —— 这是可以接受的
            // 降级，但日志里会点名，免得当成渲染 bug 查。
            TextureAtlasSprite geometry = baseSprite != null ? baseSprite : maskSprite;
            if (baseSprite == null) {
                warnMissingBaseOnce(layer.spec());
            }

            VertexConsumer consumer = buffer.getBuffer(renderType);
            mc.getItemRenderer().renderQuadList(poseStack, consumer,
                    MaskLayerRenderUtils.quadsFor(geometry), ItemStack.EMPTY, light, overlay);

            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(renderType);
            }
        }
    }

    /** 拿不到本体贴图只每次都警告会很吵，按效果 id 各说一次。 */
    private static final Set<String> WARNED_NO_BASE = new HashSet<>();

    private static void warnMissingBaseOnce(MaskLayerSpec spec) {
        if (WARNED_NO_BASE.add(spec.effectId())) {
            HallMod.LOGGER.warn(
                    "[MaskLayer] 效果 '{}' 取不到本体贴图，该层退回用遮罩烘几何"
                            + "（遮罩若是整张不透明，会出现矩形；请检查模型与图集）",
                    spec.effectId());
        }
    }

    private static boolean isFirstPerson(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private MaskLayerRenderer() {}
}
