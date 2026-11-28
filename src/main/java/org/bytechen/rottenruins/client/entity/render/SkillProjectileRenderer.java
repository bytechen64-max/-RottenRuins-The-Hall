package org.bytechen.rottenruins.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class SkillProjectileRenderer extends EntityRenderer<Entity> {

    public SkillProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Rendering is handled by BallRendUtils and IcosahedronRendUtils
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return null;
    }
}
