package org.bytechen.rottenruins.client.mask.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.overworld.registry.blockentity.DeathCoreBlockEntity;

/**
 * 死亡核心的方块实体渲染器 —— 方块版 mask 层的接入点。
 *
 * <p>它自己不做任何绘制，只把「当前状态 + 层列表 + 本帧的 PoseStack / 缓冲」
 * 交给 {@link BlockMaskOverlayRenderer}。这样下一个想挂 mask 层的方块只要：
 * 加一个 BlockEntity（实现 {@code BlockMaskLayerProvider}）+ 一个同样三行的 BER，
 * 就自动获得全部效果实现（glow / death_core / 以后新增的）。</p>
 *
 * <p>{@code getViewDistance} 提到 96：方块很小，默认 64 会让远处的核心在
 * 光晕还看得见的时候先被剔除，观感上是"闪一下消失"。</p>
 */
public class DeathCoreRenderer implements BlockEntityRenderer<DeathCoreBlockEntity> {

    public DeathCoreRenderer(BlockEntityRendererProvider.Context context) {
        // 没有纹理/模型需要从 context 取。
    }

    @Override
    public void render(DeathCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        BlockMaskOverlayRenderer.renderBlock(poseStack, buffer, packedLight, packedOverlay,
                state, blockEntity.maskLayers(state));
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
