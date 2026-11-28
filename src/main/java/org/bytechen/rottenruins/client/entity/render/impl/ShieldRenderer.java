package org.bytechen.rottenruins.client.entity.render.impl;

import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.ShieldEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.ShieldEntity;

public class ShieldRenderer extends EntityRenderer<ShieldEntity> {

    private static final RenderType GLOWING_SHIELD = RenderType.create(
            "shield_icosahedron",
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

    private static final Map<Integer, List<Vector3f>> ICOSA_CACHE = new ConcurrentHashMap<>();
    private static final int SUBDIVISION = 2;

    public ShieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(ShieldEntity entity) {
        return null;
    }

    @Override
    public void render(ShieldEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float r = entity.getRadius();

        List<Vector3f> vertices = getIcosaVertices();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        GLOWING_SHIELD.setupRenderState();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        PoseStack.Pose pose = poseStack.last();

        for (Vector3f v : vertices) {
            buffer.vertex(pose.pose(), v.x() * r, v.y() * r, v.z() * r)
                    .color(1f, 1f, 1f, 0.4f)
                    .endVertex();
        }

        tesselator.end();
        GLOWING_SHIELD.clearRenderState();
    }

    private static List<Vector3f> getIcosaVertices() {
        return ICOSA_CACHE.computeIfAbsent(SUBDIVISION, level -> {
            List<Vector3f> result = new ArrayList<>();
            float t = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);

            List<Vector3f> base = new ArrayList<>();
            addV(base, -1, t, 0); addV(base, 1, t, 0); addV(base, -1, -t, 0); addV(base, 1, -t, 0);
            addV(base, 0, -1, t); addV(base, 0, 1, t); addV(base, 0, -1, -t); addV(base, 0, 1, -t);
            addV(base, t, 0, -1); addV(base, t, 0, 1); addV(base, -t, 0, -1); addV(base, -t, 0, 1);

            int[][] faces = {
                    {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
                    {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
                    {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
                    {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
            };

            for (int[] f : faces) {
                subdivide(base.get(f[0]), base.get(f[1]), base.get(f[2]), level, result);
            }
            return result;
        });
    }

    private static void addV(List<Vector3f> l, float x, float y, float z) {
        l.add(new Vector3f(x, y, z).normalize());
    }

    private static void subdivide(Vector3f v1, Vector3f v2, Vector3f v3, int depth, List<Vector3f> result) {
        if (depth <= 0) {
            result.add(new Vector3f(v1));
            result.add(new Vector3f(v2));
            result.add(new Vector3f(v3));
            return;
        }
        Vector3f v12 = new Vector3f(v1).add(v2).mul(0.5f).normalize();
        Vector3f v23 = new Vector3f(v2).add(v3).mul(0.5f).normalize();
        Vector3f v31 = new Vector3f(v3).add(v1).mul(0.5f).normalize();
        subdivide(v1, v12, v31, depth - 1, result);
        subdivide(v2, v23, v12, depth - 1, result);
        subdivide(v3, v31, v23, depth - 1, result);
        subdivide(v12, v23, v31, depth - 1, result);
    }
}
