package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * 光盘（CD）特效渲染辅助类。
 * 使用配置参数在物品上方绘制径向渐变圆环，模拟光盘反光效果。
 */
public class CDRenderHelper extends AbstractItemRenderHelper {

    private final CDRenderConfig config;

    public CDRenderHelper(CDRenderConfig config) {
        this.config = config;
    }

    @Override
    public boolean shouldRender(ItemStack stack, ItemDisplayContext context) {
        if (!config.enabled) return false;
        return switch (context) {
            case GUI, FIXED -> config.showInInventory;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> config.showWhenHeld;
            case GROUND, NONE -> config.showInWorld;
            default -> false;
        };
    }

    @Override
    public void render(PoseStack poseStack,
                       ItemStack stack,
                       ItemDisplayContext context,
                       MultiBufferSource buffer,
                       int combinedLight,
                       int combinedOverlay) {
        if (config == null) return;
        poseStack.pushPose();
        // 应用 Z 轴偏移，使 CD 效果浮于物品表面
        poseStack.translate(0, 0, config.zOffset);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        float innerRadius = config.size * (1.0f - config.ringThickness);
        float outerRadius = config.size;

        // 解析颜色分量
        float centerR = ((config.centerColor >> 16) & 0xFF) / 255.0f;
        float centerG = ((config.centerColor >> 8) & 0xFF) / 255.0f;
        float centerB = (config.centerColor & 0xFF) / 255.0f;
        float centerA = config.centerAlpha;

        float edgeR = ((config.edgeColor >> 16) & 0xFF) / 255.0f;
        float edgeG = ((config.edgeColor >> 8) & 0xFF) / 255.0f;
        float edgeB = (config.edgeColor & 0xFF) / 255.0f;
        float edgeA = config.edgeAlpha;

        // 使用 QUADS 绘制径向渐变圆盘（分段越多越平滑）
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float angleStep = (float) (2 * Math.PI / config.segments);

        for (int i = 0; i < config.segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            // 内圈点（中心色）
            float innerX1 = cos1 * innerRadius;
            float innerY1 = sin1 * innerRadius;
            float innerX2 = cos2 * innerRadius;
            float innerY2 = sin2 * innerRadius;

            // 外圈点（边缘色）
            float outerX1 = cos1 * outerRadius;
            float outerY1 = sin1 * outerRadius;
            float outerX2 = cos2 * outerRadius;
            float outerY2 = sin2 * outerRadius;

            // 四边形四个顶点：内1 → 外1 → 外2 → 内2
            builder.vertex(matrix, innerX1, innerY1, 0).color(centerR, centerG, centerB, centerA).endVertex();
            builder.vertex(matrix, outerX1, outerY1, 0).color(edgeR, edgeG, edgeB, edgeA).endVertex();
            builder.vertex(matrix, outerX2, outerY2, 0).color(edgeR, edgeG, edgeB, edgeA).endVertex();
            builder.vertex(matrix, innerX2, innerY2, 0).color(centerR, centerG, centerB, centerA).endVertex();
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /**
     * CD 渲染参数配置类，使用 Builder 模式简化构建。
     */
    public static class CDRenderConfig {
        private boolean enabled = true;
        private float size = 0.8f;
        private int centerColor = 0xFFFFFF;
        private float centerAlpha = 0.7f;
        private int edgeColor = 0xAAAAAA;
        private float edgeAlpha = 0.3f;
        private float ringThickness = 0.4f;
        private int segments = 32;
        private float zOffset = 100.0f;

        private boolean showInInventory = true;
        private boolean showWhenHeld = false;
        private boolean showInWorld = false;

        private CDRenderConfig() {}

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final CDRenderConfig config = new CDRenderConfig();

            public Builder enabled(boolean enabled) { config.enabled = enabled; return this; }
            public Builder size(float size) { config.size = size; return this; }
            public Builder centerColor(int color) { config.centerColor = color; return this; }
            public Builder centerAlpha(float alpha) { config.centerAlpha = alpha; return this; }
            public Builder edgeColor(int color) { config.edgeColor = color; return this; }
            public Builder edgeAlpha(float alpha) { config.edgeAlpha = alpha; return this; }
            public Builder ringThickness(float thickness) { config.ringThickness = thickness; return this; }
            public Builder segments(int segments) { config.segments = segments; return this; }
            public Builder zOffset(float zOffset) { config.zOffset = zOffset; return this; }
            public Builder showInInventory(boolean show) { config.showInInventory = show; return this; }
            public Builder showWhenHeld(boolean show) { config.showWhenHeld = show; return this; }
            public Builder showInWorld(boolean show) { config.showInWorld = show; return this; }

            public CDRenderConfig build() {
                return config;
            }
        }

        // Getter 方法供渲染使用
        public boolean isEnabled() { return enabled; }
        public float getSize() { return size; }
        public int getCenterColor() { return centerColor; }
        public float getCenterAlpha() { return centerAlpha; }
        public int getEdgeColor() { return edgeColor; }
        public float getEdgeAlpha() { return edgeAlpha; }
        public float getRingThickness() { return ringThickness; }
        public int getSegments() { return segments; }
        public float getZOffset() { return zOffset; }
        public boolean isShowInInventory() { return showInInventory; }
        public boolean isShowWhenHeld() { return showWhenHeld; }
        public boolean isShowInWorld() { return showInWorld; }
    }

    public static void registerCDForItem(ItemRenderManager manager, Item item, int color) {
        CDRenderHelper.CDRenderConfig config = CDRenderHelper.CDRenderConfig.builder()
                .enabled(true)
                .size(1.0f)                      // 半径 1.0（物品栏格尺寸基准）
                .centerColor(color)
                .centerAlpha(1.0f)               // 中心完全不透明
                .edgeColor(color)
                .edgeAlpha(0.0f)                 // 边缘完全透明，形成渐变消失效果
                .ringThickness(1.0f)             // 整个半径范围都参与渐变
                .segments(50)                    // 高平滑分段
                .zOffset(0.0f)                   // Z 偏移（正值浮于物品之上）
                .showInInventory(true)           // 在物品栏显示
                .showWhenHeld(false)             // 手持时不显示（可按需调整）
                .showInWorld(false)              // 掉落物不显示
                .build();

        manager.registerCDRenderer(stack -> stack.getItem() == item, config);
    }
}