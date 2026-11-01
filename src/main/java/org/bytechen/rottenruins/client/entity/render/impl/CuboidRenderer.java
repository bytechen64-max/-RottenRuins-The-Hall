package org.bytechen.rottenruins.client.entity.render.impl;

import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CuboidEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CuboidEntity;

public class CuboidRenderer extends EntityRenderer<CuboidEntity> {

    private static final RenderType GLOWING_CUBOID = RenderType.create(
            "cuboid_glowing",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "translucent_transparency",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(
                                        GlStateManager.SourceFactor.SRC_ALPHA,
                                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                        GlStateManager.SourceFactor.ONE,
                                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
                    .createCompositeState(false)
    );

    public CuboidRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(CuboidEntity entity) {
        return null;
    }

    @Override
    public void render(CuboidEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float hw = entity.getBoxWidth() * 0.5f;
        float hh = entity.getBoxHeight();
        float hd = entity.getBoxDepth() * 0.5f;

        Vec3 renderPos = entity.getRenderPos(partialTicks);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double rx = renderPos.x - cam.x;
        double ry = renderPos.y - cam.y;
        double rz = renderPos.z - cam.z;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        GLOWING_CUBOID.setupRenderState();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        PoseStack.Pose pose = poseStack.last();
        float r = 1f, g = 1f, b = 1f, a = 1f;

        quadOffset(pose, buffer, rx, ry, rz, -hw, 0, -hd, hw, 0, -hd, hw, 0, hd, -hw, 0, hd, r, g, b, a);
        quadOffset(pose, buffer, rx, ry, rz, -hw, hh, hd, hw, hh, hd, hw, hh, -hd, -hw, hh, -hd, r, g, b, a);
        quadOffset(pose, buffer, rx, ry, rz, -hw, 0, hd, hw, 0, hd, hw, hh, hd, -hw, hh, hd, r, g, b, a);
        quadOffset(pose, buffer, rx, ry, rz, hw, 0, -hd, -hw, 0, -hd, -hw, hh, -hd, hw, hh, -hd, r, g, b, a);
        quadOffset(pose, buffer, rx, ry, rz, hw, 0, hd, hw, 0, -hd, hw, hh, -hd, hw, hh, hd, r, g, b, a);
        quadOffset(pose, buffer, rx, ry, rz, -hw, 0, -hd, -hw, 0, hd, -hw, hh, hd, -hw, hh, -hd, r, g, b, a);

        tesselator.end();
        GLOWING_CUBOID.clearRenderState();
    }

    private static void quadOffset(PoseStack.Pose pose, BufferBuilder buffer,
                                   double ox, double oy, double oz,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4,
                                   float r, float g, float b, float a) {
        buffer.vertex(pose.pose(), (float)(ox + x1), (float)(oy + y1), (float)(oz + z1)).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), (float)(ox + x2), (float)(oy + y2), (float)(oz + z2)).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), (float)(ox + x3), (float)(oy + y3), (float)(oz + z3)).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), (float)(ox + x1), (float)(oy + y1), (float)(oz + z1)).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), (float)(ox + x3), (float)(oy + y3), (float)(oz + z3)).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), (float)(ox + x4), (float)(oy + y4), (float)(oz + z4)).color(r, g, b, a).endVertex();
    }
}
