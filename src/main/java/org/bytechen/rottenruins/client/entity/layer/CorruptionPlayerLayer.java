package org.bytechen.rottenruins.client.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.entity.IGeoLayerProvider;
import org.bytechen.rottenruins.client.entity.render.GeoBaseRender;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host.CorruptionPlayerEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public final class CorruptionPlayerLayer implements IGeoLayerProvider {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HallMod.MODID, "textures/entity/corruption_player_layer.png");

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void renderAdditionalLayer(GeoBaseRender<?> renderer, LivingEntity entity, BakedGeoModel model,
                                     RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                     PoseStack poseStack, float partialTick, int light, int overlay) {
        if (!(entity instanceof CorruptionPlayerEntity player) || entity.isInvisible()) return;
        RenderType layer = RenderType.entityTranslucent(TEXTURE);
        ((GeoBaseRender) renderer).reRender(model, poseStack, buffers, player, layer,
                buffers.getBuffer(layer), partialTick, light, overlay, 1, 1, 1, 1);
    }
}
