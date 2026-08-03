package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 抽象物品渲染辅助类，定义统一的渲染流程。
 * 所有自定义特效渲染（如 CD、六芒星、光晕等）均需继承此类并实现具体渲染逻辑。
 */
public abstract class AbstractItemRenderHelper {

    /**
     * 判断当前物品与渲染上下文是否应当执行渲染。
     * 子类可根据配置灵活决定显示条件。
     *
     * @param stack   待渲染的物品堆
     * @param context 当前渲染上下文（GUI、手持、地面等）
     * @return true 表示应当渲染
     */
    public abstract boolean shouldRender(ItemStack stack, ItemDisplayContext context);

    /**
     * 执行具体的渲染逻辑。
     * 调用前会确保渲染状态已适当保存，子类无需额外调用 push/pop。
     *
     * @param poseStack       矩阵堆栈
     * @param stack           物品堆
     * @param context         渲染上下文
     * @param buffer          缓冲区源（可用于获取顶点构建器）
     * @param combinedLight   光照值
     * @param combinedOverlay 叠加层值
     */
    public abstract void render(PoseStack poseStack,
                                ItemStack stack,
                                ItemDisplayContext context,
                                MultiBufferSource buffer,
                                int combinedLight,
                                int combinedOverlay);

    /**
     * 是否为包裹模型的效果（需要在模型渲染前应用变换，渲染后恢复）
     * 默认 false 表示是附加效果（在模型外部绘制）
     */
    public boolean isWrapModel() {
        return false;
    }

    /**
     * 恢复变换（仅对 isWrapModel() == true 的 Helper 有效）
     */
    public void postRender(PoseStack poseStack) {
    }
}