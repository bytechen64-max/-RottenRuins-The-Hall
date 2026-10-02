package org.bytechen.hall.mixin;

import org.bytechen.hall.api.IBlockingWeapon;
import org.bytechen.hall.client.rend.CrimsonVowBlockRig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 低版本（1.8 式）举剑格挡的客户端接入点。
 *
 * <h3>为什么不再"取消原版"</h3>
 * <p>参考模组（ArcaneVortex）在 {@code renderArmWithItem} 头部
 * {@code ci.cancel()}，然后自己重画整套。这个做法<b>依赖它的武器没有自定义
 * first-person display 变换</b> —— 一旦有，{@code ItemRenderer.render} 会把那组
 * 变换再套一遍，与格挡姿态叠加，剑刃就被拧向镜头（实机症状："整个剑往摄像机戳"）。</p>
 *
 * <p>所以这里改为：<b>原版照常渲染</b>（模型自己的 display 变换按设计生效），
 * 只在传给物品渲染的 PoseStack 上插一层增量。见 {@link CrimsonVowBlockRig}。</p>
 *
 * <h3>两个注入各自的职责</h3>
 * <ul>
 *   <li>{@link #crimsonvow$modifyItemPose} —— {@code @ModifyArg} 改
 *       {@code ItemRenderer.render(...)} 的第 5 个参数（PoseStack），
 *       叠加格挡增量。用 {@code @ModifyArg} 而不是 inject，是因为它拿到的是
 *       "原版已经摆好的那个 PoseStack"，语义最准。</li>
 *   <li>{@link #crimsonvow$markCapture} —— 让描边捕获知道这只手已经画过了。</li>
 * </ul>
 */
@Mixin(value = ItemInHandRenderer.class, priority = 890)
public abstract class CrimsonVowBlockMixin {

    /**
     * 在 {@code ItemInHandRenderer.renderItem(...)} 被调用的那一刻之前，
     * 把格挡增量叠加到 {@code poseStack} 上。
     *
     * <p><b>为什么用 {@code @Inject} 而不是 {@code @ModifyArg}</b>：{@code @ModifyArg}
     * 的回调签名要求（"目标方法签名 + 各参数 + CallbackInfo" 还是别种形式）在不同
     * Mixin 版本下不一致，实际报错为
     * {@code targets a method with an invalid signature}。而 {@code @Inject} 的规则
     * 简单明确：<b>参数表与注入目标方法完全一致</b>，所以这里插在 INVOKE 之前、
     * 就地修改传入的 {@code poseStack}（它是引用类型，改的就是原版正要用的那份）。</p>
     *
     * <p>位置用 {@code INVOKE}（默认 {@code At.Shift.BEFORE}）而不是 {@code HEAD}：
     * 此刻原版已经完成了 arm transform、挥动/攻击变换与 display context 的处理，
     * 栈上的变换就是"正常手持"，我们只在其上叠增量。</p>
     *
     * <p>注入目标核实过：{@code renderArmWithItem} 字节码里调的是
     * {@code ItemInHandRenderer.renderItem}（常量池 #749，SRG {@code m_269530_}），
     * <b>不是</b> {@code ItemRenderer.render} —— 后者只在 {@code renderItem} 内部被调用。</p>
     */
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
                            + "renderItem(Lnet/minecraft/world/entity/LivingEntity;"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0)
    private void crimsonvow$applyBlockDelta(AbstractClientPlayer player, float partialTick, float pitch,
                                            InteractionHand hand, float swingProgress, ItemStack stack,
                                            float equipProgress, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            CallbackInfo ci) {
        if (!CrimsonVowBlockRig.shouldApply(player, stack, hand)) return;
        CrimsonVowBlockRig.applyBlockDelta(poseStack);
    }

    /**
     * 描边捕获：举剑时原版照常渲染，所以捕获逻辑本身不受影响；
     * 这里只在捕获态下补一次标记，保证物品描边在格挡时也存在。
     */
    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void crimsonvow$markCapture(AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight,
                                        CallbackInfo ci) {
        if (!CrimsonVowBlockRig.shouldApply(player, stack, hand)) return;
        if (org.bytechen.hall.client.rend.glint.HeldItemOutlineRenderer.isCaptureActive()
                && !org.bytechen.hall.client.rend.glint.HeldItemOutlineRenderer.shouldSkipHand(hand)) {
            org.bytechen.hall.client.rend.glint.HeldItemOutlineRenderer.markHandCaptured();
        }
    }

    /** 供调试：当前是否命中格挡（未使用，保留给后续排查）。 */
    @SuppressWarnings("unused")
    private static boolean isBlockingWeapon(ItemStack stack) {
        return stack.getItem() instanceof IBlockingWeapon;
    }
}
