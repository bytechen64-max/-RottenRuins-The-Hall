package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.SpearOfJudgmentEntity;
import org.joml.Matrix4f;
import com.mojang.math.Axis;

/** A directional, full-bright magical lance built from crossed blades and a solid shaft. */
public class SpearOfJudgmentRenderer extends EntityRenderer<SpearOfJudgmentEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public SpearOfJudgmentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpearOfJudgmentEntity spear, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, spear.yRotO, spear.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, spear.xRotO, spear.getXRot())));
        float pulse = 1.0f + 0.05f * Mth.sin((spear.tickCount + partialTick) * 0.7f);
        pose.scale(pulse, pulse, pulse);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());

        // Three crossed planes keep the silhouette legible from above and from the side.
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 60.0f));
            Matrix4f plane = pose.last().pose();
            blade(vertices, plane, 0.0f, 0.6f, 1.85f, 0.12f, 92, 217, 255, 225);
            blade(vertices, plane, 0.0f, 0.7f, 1.62f, 0.055f, 240, 255, 255, 255);
            blade(vertices, plane, 0.0f, -1.45f, -1.75f, 0.09f, 45, 107, 179, 180);
            pose.popPose();
        }
        // Dark shaft, bright central spine and a broad crossguard.
        blade(vertices, matrix, 0.0f, -1.6f, 0.75f, 0.105f, 28, 39, 81, 255);
        blade(vertices, matrix, 0.0f, -1.58f, 0.75f, 0.035f, 137, 226, 255, 255);
        quad(vertices, matrix, -0.58f, 0.38f, 0.58f, 0.38f, 75, 175, 245, 235);
        quad(vertices, matrix, -0.37f, 0.49f, 0.37f, 0.49f, 230, 251, 255, 255);
        pose.popPose();
        super.render(spear, yaw, partialTick, pose, buffers, 0xF000F0);
    }

    private static void blade(VertexConsumer out, Matrix4f pose, float x, float base,
                              float tip, float width, int r, int g, int b, int a) {
        vertex(out, pose, x - width, 0, base, r, g, b, a);
        vertex(out, pose, x + width, 0, base, r, g, b, a);
        vertex(out, pose, x, 0, tip, r, g, b, a);
        vertex(out, pose, x, 0, tip, r, g, b, a);
    }

    private static void quad(VertexConsumer out, Matrix4f pose, float left, float z,
                             float right, float z2, int r, int g, int b, int a) {
        vertex(out, pose, left, 0, z - 0.07f, r, g, b, a);
        vertex(out, pose, right, 0, z2 - 0.07f, r, g, b, a);
        vertex(out, pose, right, 0, z2 + 0.07f, r, g, b, a);
        vertex(out, pose, left, 0, z + 0.07f, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
                               int r, int g, int b, int a) {
        out.vertex(pose, x, y, z).color(r, g, b, a).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SpearOfJudgmentEntity spear) {
        return TEXTURE;
    }
}
