package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.glint.ItemOutlinePipeline;
import org.bytechen.hall.client.rend.glint.ShaderPackDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品描边的延迟合成时机。
 *
 * <h3>为什么只有一个注入点</h3>
 * 描边遮罩（"这件物品占了哪些像素"）一律在物品渲染的当帧<b>立刻</b>捕获 ——
 * 只有那一刻场景深度才是对的。真正推迟的只是最后那次全屏环形合成。
 *
 * <p>合成统一放在 {@code GameRenderer.renderLevel} 的 <b>TAIL</b>，也就是
 * {@code renderItemInHand()} 之后。这个点是被验证过的：光影包激活时，一手描边
 * 在 TAIL 显示正常。</p>
 *
 * <h3>踩过的坑：不要用 renderHand 字段那个点</h3>
 * 旧实现把世界物品拆到 {@code GameRenderer.renderLevel} 里首次读
 * {@code renderHand} 字段的位置（1.20.1 里就是 {@code if (this.renderHand)} 那一行）去合成，
 * 想的是"世界物品先合成、一手压在上面"。结果第三人称在开光影时<b>完全看不到描边</b>。
 *
 * <p>原因看 1.20.1 的方法体就清楚了：</p>
 * <pre>
 *   levelRenderer.renderLevel(...)          // 世界画完（GBuffer）
 *   dispatchRenderStage(AFTER_LEVEL)
 *   if (this.renderHand) {                  // ← 旧的合成点在这
 *       RenderSystem.clear(GL_DEPTH_BUFFER_BIT);
 *       renderItemInHand(...);              // 一手
 *   }
 *                                           // ← TAIL：Iris 的最终合成落在这附近
 * </pre>
 *
 * <p>光影包的最终合成晚于 {@code renderHand} 那一行，于是那个点写进去的描边
 * 会被整个覆盖掉；而 TAIL 的 writes 排在它之后，所以一手正常。
 * 既然遮罩捕获时遮挡关系已经用场景深度判定完了，合成这一步根本不依赖深度，
 * 也就没必要拆成两段 —— 全部放到 TAIL 既正确又简单。</p>
 *
 * <p>Priority 500 与原参照模组的 {@code CosmicAfterLevelMixin} 一致，
 * 保证我们的注入排在 Oculus/Iris 那些包装渲染管线的 mixin 之后。</p>
 */
@Mixin(value = net.minecraft.client.renderer.GameRenderer.class, priority = 500)
public abstract class OutlineReplayMixin {

    /**
     * 遮罩捕获点：在 vanilla 清掉场景深度**之前**把剪影画进遮罩。
     *
     * <p>1.20.1 的方法体：</p>
     * <pre>
     *   levelRenderer.renderLevel(...)        // 世界 + 实体，深度完整
     *   if (this.renderHand) {                // ← 我们在这（读字段那一刻）
     *       RenderSystem.clear(GL_DEPTH_BUFFER_BIT);   // ← 深度在这里被清掉
     *       renderItemInHand(...);
     *   }
     * </pre>
     *
     * <p>必须在这里：再晚一步（清完深度、画完一手）一手视图下场景深度就没了，
     * 遮挡判定会恒通过 —— 表现为描边穿透方块和实体。同时这个点也是
     * 「世界画完 / 一手未开始」的干净分界点，做 FBO 操作不会打扰实体批次。</p>
     */
    @Inject(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                    ordinal = 0))
    private void live$captureSceneSilhouettes(float partialTick, long finishTimeNano,
                                              com.mojang.blaze3d.vertex.PoseStack poseStack,
                                              CallbackInfo ci) {
        if (ShaderPackDetector.isShadowPass()) return;
        ItemOutlinePipeline.captureSceneSilhouettes();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void live$compositeOutline(float partialTick, long finishTimeNano,
                                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                                       CallbackInfo ci) {
        if (ShaderPackDetector.isShadowPass()) return;
        ItemOutlinePipeline.composite();
    }
}
