package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.client.mask.render.MaskLayerRenderUtils;
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
 * 逐层绘制 mask 效果：要 RenderType → 设 uniform → 填 quad → 立刻 flush 该层。
 * 必须每层单独 flush：{@code Uniform.set()} 只是记值，上传在 {@code endBatch}，攒批会让后一层覆盖前一层
 * （症状：两个泛光层同色同宽）；传 {@code ItemStack.EMPTY} 是为了关掉 renderQuadList 顺手塞的光泽 quad。
 */
public final class MaskLayerRenderer {

    /** @param baseSprite 本体贴图 sprite：既是各层的<b>光源</b>，也是 quad 的<b>几何来源</b> ——
     *  {@code ItemModelGenerator} 按 alpha 裁几何，用它烘出的 quad 才等于武器轮廓（遮罩常整张不透明）。
     *  @param lateRender true = 光影下的延迟回放（走 MAIN_TARGET / 一手不测深度）。 */
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

            // 退回遮罩时可能看到一整块矩形（可接受的降级，日志会点名）
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
