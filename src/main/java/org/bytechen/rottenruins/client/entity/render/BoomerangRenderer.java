package org.bytechen.rottenruins.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.client.rend.slash.BladeGeometry;
import org.bytechen.rottenruins.client.rend.slash.BladeModelGeometry;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.bytechen.rottenruins.overworld.registry.entities.projectile.BoomerangEntity;

public final class BoomerangRenderer extends EntityRenderer<BoomerangEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public BoomerangRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
    }
    @Override public void render(BoomerangEntity entity, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        var model = items.getModel(entity.getItem(), entity.level(), null, entity.getId());
        var geometry = BladeModelGeometry.flatProfile(model, entity.getItem(), new Vector3f(1))
                .orElseGet(() -> new BladeGeometry.Profile(new BladeGeometry.Vec(1, 0, 0),
                        new BladeGeometry.Vec(0, 1, 0), new BladeGeometry.Vec(0, 0, 1),
                        new BladeGeometry.Vec(0, 0, 0), 0.5));
        var direction = net.minecraft.world.phys.Vec3.directionFromRotation(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot()),
                Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()));
        long seed = entity.getUUID().getMostSignificantBits() ^ entity.getUUID().getLeastSignificantBits();
        var rotation = BoomerangPose.rotation(geometry, direction.toVector3f(), entity.tickCount + partialTick, seed);
        PoseStack display = new PoseStack();
        model.applyTransform(ItemDisplayContext.FIXED, display, false);
        Matrix4f transform = new Matrix4f().rotate(rotation);
        // ItemRenderer reapplies the display transform. Cancel it so the measured raw model plane wins.
        if (Math.abs(display.last().pose().determinant()) > 1.0e-6F) {
            transform.mul(new Matrix4f(display.last().pose()).invert());
        }
        pose.last().pose().mul(transform);
        pose.last().normal().mul(new Matrix3f(transform).invert().transpose());
        items.render(entity.getItem(), ItemDisplayContext.FIXED, false, pose, buffers, light,
                OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(BoomerangEntity entity) { return InventoryMenu.BLOCK_ATLAS; }
}
