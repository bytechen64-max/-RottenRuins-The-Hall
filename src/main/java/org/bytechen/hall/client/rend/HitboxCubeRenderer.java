package org.bytechen.hall.client.rend;

import org.bytechen.hall.network.NetworkHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.UUID;

public class HitboxCubeRenderer extends AbstractStaticEffectRenderer<HitboxCubeRenderer.CubeInstance> {

    public static final HitboxCubeRenderer INSTANCE = new HitboxCubeRenderer();
    private static final String TYPE_ID = "hitbox_cube";

    static {
        registerRenderer(INSTANCE);
    }

    private HitboxCubeRenderer() {}

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    protected CubeInstance createInstance(UUID id, CompoundTag data) {
        return new CubeInstance(id, data);
    }

    @Override
    protected void doRender(PoseStack poseStack, CubeInstance inst, float partialTick) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        AABB box = inst.box;
        float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
        Matrix4f matrix = poseStack.last().pose();

        Vec3 v000 = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 v001 = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 v010 = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 v011 = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 v100 = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 v101 = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 v110 = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 v111 = new Vec3(box.maxX, box.maxY, box.maxZ);

        // Y- bottom
        addQuad(buffer, matrix, v000, v100, v101, v001, r, g, b, a);
        // Y+ top
        addQuad(buffer, matrix, v010, v011, v111, v110, r, g, b, a);
        // Z+ front
        addQuad(buffer, matrix, v001, v101, v111, v011, r, g, b, a);
        // Z- back
        addQuad(buffer, matrix, v000, v010, v110, v100, r, g, b, a);
        // X- left
        addQuad(buffer, matrix, v000, v001, v011, v010, r, g, b, a);
        // X+ right
        addQuad(buffer, matrix, v100, v110, v111, v101, r, g, b, a);

        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f matrix,
                                Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                                float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) v4.x, (float) v4.y, (float) v4.z).color(r, g, b, a).endVertex();
    }

    // ========== Instance ==========
    public static class CubeInstance extends EffectInstance {
        public AABB box;

        public CubeInstance(UUID id, CompoundTag tag) {
            super(id, tag.getInt("maxAge"));
            readFromNBT(tag);
        }

        @Override
        public void readFromNBT(CompoundTag tag) {
            box = new AABB(
                    tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ")
            );
        }

        @Override
        public void writeToNBT(CompoundTag tag) {
            tag.putDouble("minX", box.minX);
            tag.putDouble("minY", box.minY);
            tag.putDouble("minZ", box.minZ);
            tag.putDouble("maxX", box.maxX);
            tag.putDouble("maxY", box.maxY);
            tag.putDouble("maxZ", box.maxZ);
            tag.putInt("maxAge", maxAge);
        }
    }

    public static void handleSyncPacket(UUID instanceId, CompoundTag data) {
        INSTANCE.applySyncData(instanceId, data);
    }

    public static void spawnCube(LivingEntity entity, int ticks) {
        UUID id = UUID.randomUUID();
        AABB box = entity.getBoundingBox();
        CompoundTag data = new CompoundTag();
        data.putDouble("minX", box.minX);
        data.putDouble("minY", box.minY);
        data.putDouble("minZ", box.minZ);
        data.putDouble("maxX", box.maxX);
        data.putDouble("maxY", box.maxY);
        data.putDouble("maxZ", box.maxZ);
        data.putInt("maxAge", ticks);
        NetworkHelper.sendCubeToAll(id, data);

        if (entity.level() instanceof ServerLevel serverLevel) {
            Vec3 center = box.getCenter();
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    center.x, center.y, center.z,
                    15,
                    box.getXsize() * 0.5,
                    box.getYsize() * 0.5,
                    box.getZsize() * 0.5,
                    0.05);
        }
    }
}
