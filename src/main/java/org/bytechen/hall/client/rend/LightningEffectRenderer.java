package org.bytechen.hall.client.rend;


import org.bytechen.hall.network.NetworkHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.UUID;

public class LightningEffectRenderer extends AbstractStaticEffectRenderer<LightningEffectRenderer.LightningInstance> {

    public static final LightningEffectRenderer INSTANCE = new LightningEffectRenderer();
    private static final String TYPE_ID = "lightning";

    static {
        registerRenderer(INSTANCE);
    }

    private LightningEffectRenderer() {}

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    protected LightningInstance createInstance(UUID id, CompoundTag data) {
        return new LightningInstance(id, data);
    }

    @Override
    protected void doRender(PoseStack poseStack, LightningInstance inst, float partialTick) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Vec3 start = inst.start;
        Vec3 end = inst.end;
        float thickness = inst.thickness;
        int color = inst.color;
        int segments = inst.segments;
        float jitter = inst.jitter;

        // 生成路径点（可抖动）
        Vec3[] points = generateLightningPath(start, end, segments, jitter);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f; // 完全不透明，无淡出

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < points.length - 1; i++) {
            Vec3 cur = points[i];
            Vec3 nxt = points[i + 1];
            Vec3 dir = nxt.subtract(cur);
            float len = (float) dir.length();
            if (len < 0.001f) continue;
            dir = dir.normalize();

            // 计算垂直向量
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = dir.cross(up).normalize();
            if (right.lengthSqr() < 0.001) {
                right = dir.cross(new Vec3(1, 0, 0)).normalize();
            }
            Vec3 upLocal = dir.cross(right).normalize();

            Vec3 thickRight = right.scale(thickness);
            Vec3 thickUp = upLocal.scale(thickness);

            // 八个顶点构成管道长方体
            Vec3 v0 = cur.add(thickRight).add(thickUp);
            Vec3 v1 = cur.subtract(thickRight).add(thickUp);
            Vec3 v2 = cur.subtract(thickRight).subtract(thickUp);
            Vec3 v3 = cur.add(thickRight).subtract(thickUp);
            Vec3 v4 = nxt.add(thickRight).add(thickUp);
            Vec3 v5 = nxt.subtract(thickRight).add(thickUp);
            Vec3 v6 = nxt.subtract(thickRight).subtract(thickUp);
            Vec3 v7 = nxt.add(thickRight).subtract(thickUp);

            // 前面
            addQuad(buffer, matrix, v0, v1, v2, v3, r, g, b, a);
            // 后面
            addQuad(buffer, matrix, v4, v5, v6, v7, r, g, b, a);
            // 顶面
            addQuad(buffer, matrix, v0, v3, v7, v4, r, g, b, a);
            // 底面
            addQuad(buffer, matrix, v1, v2, v6, v5, r, g, b, a);
            // 左面
            addQuad(buffer, matrix, v0, v1, v5, v4, r, g, b, a);
            // 右面
            addQuad(buffer, matrix, v2, v3, v7, v6, r, g, b, a);
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                                float r, float g, float b, float a) {
        // 拆分为两个三角形
        buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z).color(r, g, b, a).endVertex();
    }

    private Vec3[] generateLightningPath(Vec3 start, Vec3 end, int segments, float jitter) {
        Vec3[] points = new Vec3[segments + 1];
        points[0] = start;
        points[segments] = end;
        Vec3 dir = end.subtract(start);
        float step = 1f / segments;

        for (int i = 1; i < segments; i++) {
            float t = i * step;
            Vec3 base = start.add(dir.scale(t));
            float offsetScale = jitter * (1f - 2f * Math.abs(t - 0.5f)); // 中点抖动最大
            Vec3 offset = new Vec3(
                    (Math.random() - 0.5) * 2 * offsetScale,
                    (Math.random() - 0.5) * 2 * offsetScale,
                    (Math.random() - 0.5) * 2 * offsetScale
            );
            points[i] = base.add(offset);
        }
        return points;
    }

    // ========== 实例数据类 ==========
    public static class LightningInstance extends EffectInstance {
        public Vec3 start;
        public Vec3 end;
        public float thickness;
        public int color;
        public int segments = 8;
        public float jitter = 0.2f;

        public LightningInstance(UUID id, CompoundTag tag) {
            super(id, tag.getInt("maxAge"));
            readFromNBT(tag);
        }

        @Override
        public void readFromNBT(CompoundTag tag) {
            start = new Vec3(tag.getDouble("sx"), tag.getDouble("sy"), tag.getDouble("sz"));
            end = new Vec3(tag.getDouble("ex"), tag.getDouble("ey"), tag.getDouble("ez"));
            thickness = tag.getFloat("thickness");
            color = tag.getInt("color");
            if (tag.contains("segments")) segments = tag.getInt("segments");
            if (tag.contains("jitter")) jitter = tag.getFloat("jitter");
        }

        @Override
        public void writeToNBT(CompoundTag tag) {
            tag.putDouble("sx", start.x);
            tag.putDouble("sy", start.y);
            tag.putDouble("sz", start.z);
            tag.putDouble("ex", end.x);
            tag.putDouble("ey", end.y);
            tag.putDouble("ez", end.z);
            tag.putFloat("thickness", thickness);
            tag.putInt("color", color);
            tag.putInt("segments", segments);
            tag.putFloat("jitter", jitter);
            tag.putInt("maxAge", maxAge);
        }
    }

    // ========== 便捷生成方法（供服务端调用） ==========

    /**
     * 处理来自服务端的同步包（由 EffectSyncPacket 调用）
     */
    public static void handleSyncPacket(UUID instanceId, CompoundTag data) {
        // 直接调用基类的 applySyncData 方法（需要渲染器实例）
        INSTANCE.applySyncData(instanceId, data);
    }

    // 便捷生成方法，供服务端调用
    public static void spawnRedLightning(Vec3 start, Vec3 end, int ticks) {
        UUID id = UUID.randomUUID();
        CompoundTag data = new CompoundTag();
        data.putDouble("sx", start.x);
        data.putDouble("sy", start.y);
        data.putDouble("sz", start.z);
        data.putDouble("ex", end.x);
        data.putDouble("ey", end.y);
        data.putDouble("ez", end.z);
        data.putFloat("thickness", 0.05f);
        data.putInt("color", 0xFFFF0000); // 红色
        data.putInt("segments", 10);
        data.putFloat("jitter", 0.25f);
        data.putInt("maxAge", ticks);

        // 发送到所有客户端
        NetworkHelper.sendLightningToAll(id, data);
    }
}