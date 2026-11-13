package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.StaffBase;
import org.bytechen.rottenruins.overworld.registry.items.magic.its.SuppressionField;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 抑制场正方体的棱线视觉（同 {@code SilentDaylightWardRenderer} 画法）：只画棱、
 * 辉光+芯两层、加法混合。判据全用客户端已有信息（{@code isUsingItem} + 手上法杖 +
 * 选中法术），无需同步；半径走 {@link SuppressionField#radiusOf}，与判定同一方法。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SuppressionFieldRenderer {

    /** 辉光层棱宽（格，半宽）：场很大，棱得粗一点才看得见。 */
    private static final float GLOW_HALF_WIDTH = 0.075f;

    /** 芯层棱宽（格，半宽）。 */
    private static final float CORE_HALF_WIDTH = 0.030f;

    /** 辉光层颜色：抑制场尘的紫 (0.36, 0.18, 0.65) 提亮一档，做加法混合。 */
    private static final float GLOW_R = 0.42f;
    private static final float GLOW_G = 0.22f;
    private static final float GLOW_B = 0.85f;

    /** 芯层颜色：淡紫白，让棱芯在辉光里亮出来。 */
    private static final float CORE_R = 0.86f;
    private static final float CORE_G = 0.74f;
    private static final float CORE_B = 1.00f;

    /** 每秒自转角度（度）：缓慢转动才读得出这是立体的场。 */
    private static final float SPIN_DEG_PER_SECOND = 12.0f;

    /** 脉冲周期（秒）：对齐法术 10 tick 的结算节奏，每跳一次亮一下。 */
    private static final double PULSE_PERIOD_SECONDS = 0.5D;

    /** 正方体的 12 条棱（顶点索引 0..7）。 */
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private SuppressionFieldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        boolean any = false;
        for (Player player : mc.level.players()) {
            if (fieldOf(player) != null) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        for (Player player : mc.level.players()) {
            SuppressionField field = fieldOf(player);
            if (field == null) {
                continue;
            }
            renderCube(buffer, tesselator, matrix, player, partialTick);
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** 玩家此刻是否在维持抑制场；是就返回那个法术物品（三条件全部来自客户端已有信息）。 */
    private static SuppressionField fieldOf(Player player) {
        if (player == null || !player.isUsingItem()) {
            return null;
        }
        if (!(player.getUseItem().getItem() instanceof StaffBase)) {
            return null;
        }
        ItemStack spell = MagicHandle.selectedSpell(player);
        return spell.getItem() instanceof SuppressionField field ? field : null;
    }

    /** 画一个玩家的正方体：算尺寸 + 自转姿态，再画 12 条棱（辉光 + 芯两层）。 */
    private static void renderCube(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix,
                                   Player player, float partialTick) {
        ItemStack staff = player.getUseItem();
        // 与判定共用同一个静态方法，保证"看到的范围 = 打到的范围"
        float radius = (float) SuppressionField.radiusOf(player, staff);

        // 中心取碰撞箱中心，和服务端判定用的 center 一致
        Vec3 center = player.getBoundingBox().getCenter();

        double seconds = (player.level().getGameTime() + partialTick) / 20.0D;
        float spin = (float) Math.toRadians(seconds * SPIN_DEG_PER_SECOND);

        double phase = (seconds % PULSE_PERIOD_SECONDS) / PULSE_PERIOD_SECONDS;
        float pulse = 0.55f + 0.45f * (float) Math.pow(1.0D - phase, 1.6D);

        Vec3[] vertices = cubeVertices(center, radius, spin);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : EDGES) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    GLOW_HALF_WIDTH, GLOW_R, GLOW_G, GLOW_B, 0.30f * pulse);
        }
        tesselator.end();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : EDGES) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    CORE_HALF_WIDTH, CORE_R, CORE_G, CORE_B, 0.75f * pulse);
        }
        tesselator.end();
    }

    /** 8 个顶点（世界坐标）：局部是 ±R 立方体（面心到中心 = R）绕 Y 轴自转；0..3 底面、4..7 顶面同序。 */
    private static Vec3[] cubeVertices(Vec3 center, float radius, float spin) {
        float cos = (float) Math.cos(spin);
        float sin = (float) Math.sin(spin);
        int[][] ring = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

        Vec3[] out = new Vec3[8];
        for (int i = 0; i < 4; i++) {
            float x = ring[i][0] * radius;
            float z = ring[i][1] * radius;
            float rx = x * cos - z * sin;
            float rz = x * sin + z * cos;
            out[i] = center.add(rx, -radius, rz);
            out[i + 4] = center.add(rx, radius, rz);
        }
        return out;
    }

    /** 一条棱画成两片互相垂直的四边形：DEBUG_LINES 只有 1 像素，glLineWidth 在 1.20 已移除。 */
    private static void emitEdge(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b,
                                 float halfWidth, float r, float g, float bl, float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-9D) {
            return;
        }
        axis = axis.normalize();

        Vec3 helper = Math.abs(axis.y) < 0.99D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = axis.cross(helper);
        if (u.lengthSqr() < 1.0E-9D) {
            u = axis.cross(new Vec3(0.0D, 0.0D, 1.0D));
        }
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();

        quad(buffer, matrix, a, b, u.scale(halfWidth), r, g, bl, alpha);
        quad(buffer, matrix, a, b, v.scale(halfWidth), r, g, bl, alpha);
    }

    /** 沿 a→b、沿 offset 方向张开的四边形。 */
    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 offset,
                             float r, float g, float bl, float alpha) {
        vertex(buffer, matrix, a.subtract(offset), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(offset), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(offset), r, g, bl, alpha);
        vertex(buffer, matrix, a.add(offset), r, g, bl, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                               float r, float g, float b, float alpha) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}
