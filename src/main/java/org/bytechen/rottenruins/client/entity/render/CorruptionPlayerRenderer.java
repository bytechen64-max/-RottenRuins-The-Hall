package org.bytechen.rottenruins.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.client.entity.model.GeoBaseModel;
import org.bytechen.rottenruins.client.entity.skin.OnlinePlayerSkins;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionPlayerEntity;

public final class CorruptionPlayerRenderer extends GeoBaseRender<CorruptionPlayerEntity> {
    public CorruptionPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel());
    }

    @Override
    public void render(CorruptionPlayerEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        // Snapshot once per render so an async result cannot mix slim UVs with a wide model.
        ((PlayerModel) getGeoModel()).skin = OnlinePlayerSkins.get(entity.getSkinPlayerId());
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private static final class PlayerModel extends GeoBaseModel<CorruptionPlayerEntity> {
        private OnlinePlayerSkins.Skin skin;

        @Override
        public ResourceLocation getModelResource(CorruptionPlayerEntity entity) {
            return skin == null ? entity.model() : skin.slim()
                    ? CorruptionPlayerEntity.MODEL_SLIM : CorruptionPlayerEntity.MODEL_WIDE;
        }

        @Override
        public ResourceLocation getTextureResource(CorruptionPlayerEntity entity) {
            return skin == null ? entity.texture() : skin.texture();
        }
    }
}
