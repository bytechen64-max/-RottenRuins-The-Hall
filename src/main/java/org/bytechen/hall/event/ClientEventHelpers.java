package org.bytechen.hall.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.client.entity.layer.GlowLayer;
import org.bytechen.hall.client.entity.model.GeoBaseModel;
import org.bytechen.hall.client.entity.IGeoModelBehavior;
import org.bytechen.hall.overworld.registry.ClientRenderRegistry;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;

@OnlyIn(Dist.CLIENT)
public class ClientEventHelpers {

    public static void setupClientRenderLayers() {
        // 注册发光图层 —— InfPlayer
        ClientRenderRegistry.registerRenderLayer(
                EntityTypeRegistry.INF_PLAYER.get(),
                new GlowLayer("textures/entity/inf_player_glow_layer.png"));
        ClientRenderRegistry.registerRenderLayer(
                EntityTypeRegistry.INF_ENDERMAN.get(),
                new GlowLayer("textures/entity/inf_enderman_glow_layer.png"));

        ClientRenderRegistry.registerRenderLayer(
                EntityTypeRegistry.BONECRUSHER.get(),
                new GlowLayer("textures/entity/bonecrusher_glow_layer.png"));


        // 注册行为时使用 createHeadTrackingBehavior()
        // ClientRenderRegistry.registerModelBehavior(..., createHeadTrackingBehavior());
    }

    public static IGeoModelBehavior createHeadTrackingBehavior() {
        // 使用匿名内部类替代 Lambda
        return new IGeoModelBehavior() {
            @Override
            public void onModelCustomAnimations(GeoBaseModel<?> model, LivingEntity entity, long uniqueId, AnimationState<?> state) {
                CoreGeoBone head = model.getAnimationProcessor().getBone("head");
                if (head == null) return;

                float relativeYaw = Mth.clamp(
                        Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot),
                        -50f, 50f
                );
                float targetRotY = -relativeYaw * Mth.DEG_TO_RAD;
                head.setRotY(Mth.lerp(0.1f, head.getRotY(), targetRotY));

                float pitch = entity.getXRot();
                float targetRotX = -pitch * Mth.DEG_TO_RAD;
                targetRotX = Mth.clamp(targetRotX, -0.5236f, 0.5236f);
                head.setRotX(Mth.lerp(0.1f, head.getRotX(), targetRotX));
            }

            // 如果接口还有 preRender 等其他抽象方法，必须一并实现（可留空）
            @Override
            public void preRender(GeoBaseModel<?> model, LivingEntity entity, PoseStack poseStack, float partialTick) {
                // 不需要处理时留空
            }
        };
    }
}