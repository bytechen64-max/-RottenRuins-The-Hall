package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ZoomRenderHelper extends AbstractItemRenderHelper {

    private final ZoomConfig config;

    public ZoomRenderHelper(ZoomConfig config) {
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
    public boolean isWrapModel() {
        return true;
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
        float scale = calculateScale();
        poseStack.scale(scale, scale, 1.0f);
    }

    @Override
    public void postRender(PoseStack poseStack) {
        poseStack.popPose();
    }

    private float calculateScale() {
        float time = (System.currentTimeMillis() % 1000000) / 1000.0f;
        return switch (config.mode) {
            case SIN_WAVE -> 1.0f + config.amplitude * Mth.sin(time * config.speed);
            case LINEAR_LOOP -> {
                float t = (time * config.speed) % 1.0f;
                float min = 1.0f - config.amplitude;
                float max = 1.0f + config.amplitude;
                yield min + (max - min) * t;
            }
            case SMOOTH_PULSE -> {
                float raw = Mth.sin(time * config.speed);
                float pulse = Math.abs(raw);
                yield 1.0f + config.amplitude * pulse * pulse * (3 - 2 * pulse);
            }
            case NONE -> 1.0f;
        };
    }

    public enum ZoomMode {
        NONE, SIN_WAVE, LINEAR_LOOP, SMOOTH_PULSE
    }

    public static class ZoomConfig {
        private boolean enabled = true;
        private ZoomMode mode = ZoomMode.SIN_WAVE;
        private float speed = 15.0f;
        private float amplitude = 0.05f;
        private boolean showInInventory = true;
        private boolean showWhenHeld = false;
        private boolean showInWorld = false;

        private ZoomConfig() {}

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final ZoomConfig config = new ZoomConfig();
            public Builder enabled(boolean enabled) { config.enabled = enabled; return this; }
            public Builder mode(ZoomMode mode) { config.mode = mode; return this; }
            public Builder speed(float speed) { config.speed = speed; return this; }
            public Builder amplitude(float amplitude) { config.amplitude = amplitude; return this; }
            public Builder showInInventory(boolean show) { config.showInInventory = show; return this; }
            public Builder showWhenHeld(boolean show) { config.showWhenHeld = show; return this; }
            public Builder showInWorld(boolean show) { config.showInWorld = show; return this; }
            public ZoomConfig build() { return config; }
        }

        // Getters
        public boolean isEnabled() { return enabled; }
        public ZoomMode getMode() { return mode; }
        public float getSpeed() { return speed; }
        public float getAmplitude() { return amplitude; }
        public boolean isShowInInventory() { return showInInventory; }
        public boolean isShowWhenHeld() { return showWhenHeld; }
        public boolean isShowInWorld() { return showInWorld; }
    }

    public static void registerZoomForItem(ItemRenderManager manager, Item item, ZoomConfig config) {
        manager.addRenderer(stack -> stack.getItem() == item, new ZoomRenderHelper(config));

    }

    //静态
    public static ZoomRenderHelper.ZoomConfig zoomHeartConfig = ZoomRenderHelper.ZoomConfig.builder()
            .mode(ZoomRenderHelper.ZoomMode.SMOOTH_PULSE)
            .speed(8.0f)
            .amplitude(0.08f)
            .showInInventory(true)
            .showWhenHeld(true)
            .showInWorld(true)
            .build();
}