package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class ItemRenderManager {

    private static final ItemRenderManager INSTANCE = new ItemRenderManager();

    public record RenderEntry(Predicate<ItemStack> predicate, AbstractItemRenderHelper helper) {}

    private final List<RenderEntry> renderEntries = new ArrayList<>();

    private ItemRenderManager() {}

    public static ItemRenderManager getInstance() {
        return INSTANCE;
    }

    public void addRenderer(Predicate<ItemStack> predicate, AbstractItemRenderHelper helper) {
        renderEntries.add(new RenderEntry(predicate, helper));
    }

    public void registerCDRenderer(Predicate<ItemStack> itemPredicate, CDRenderHelper.CDRenderConfig config) {
        addRenderer(itemPredicate, new CDRenderHelper(config));
    }

    public List<RenderEntry> getEntries() {
        return Collections.unmodifiableList(renderEntries);
    }

    /**
     * 渲染附加效果（不包裹模型，如 CD 光效）
     */
    public void renderAttachedEffects(PoseStack poseStack,
                                      ItemStack stack,
                                      ItemDisplayContext context,
                                      MultiBufferSource buffer,
                                      int combinedLight,
                                      int combinedOverlay) {
        for (RenderEntry entry : renderEntries) {
            if (!entry.helper.isWrapModel() && entry.predicate.test(stack) && entry.helper.shouldRender(stack, context)) {
                entry.helper.render(poseStack, stack, context, buffer, combinedLight, combinedOverlay);
            }
        }
    }

    /**
     * 应用所有包裹模型的效果变换（在模型渲染前调用）
     * @return 应用的 Helper 列表，用于后续恢复
     */
    public List<AbstractItemRenderHelper> applyWrapTransforms(PoseStack poseStack,
                                                              ItemStack stack,
                                                              ItemDisplayContext context,
                                                              MultiBufferSource buffer,
                                                              int combinedLight,
                                                              int combinedOverlay) {
        List<AbstractItemRenderHelper> applied = new ArrayList<>();
        for (RenderEntry entry : renderEntries) {
            if (entry.helper.isWrapModel() && entry.predicate.test(stack) && entry.helper.shouldRender(stack, context)) {
                entry.helper.render(poseStack, stack, context, buffer, combinedLight, combinedOverlay);
                applied.add(entry.helper);
            }
        }
        return applied;
    }

    /**
     * 恢复所有包裹模型的效果变换（在模型渲染后调用）
     * 使用 Object 迭代并 instanceof 检查，防止其他模组的 mixin 将非 AbstractItemRenderHelper
     * 对象混入列表导致 ClassCastException（例如与 corruption 模组的 ZoomRenderHelper 冲突）。
     */
    public void restoreWrapTransforms(PoseStack poseStack, List<AbstractItemRenderHelper> appliedHelpers) {
        for (Object obj : appliedHelpers) {
            if (obj instanceof AbstractItemRenderHelper helper) {
                helper.postRender(poseStack);
            }
        }
    }
}