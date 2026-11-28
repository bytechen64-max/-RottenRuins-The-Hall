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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.items.SilentDaylight;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * <b>寂寒白日 · 寒气领域</b>：一颗小而蓝的正三棱锥绕玩家转（只画棱，十字 ribbons，面全透明）。
 * <p>不开实体、不新增同步（状态就是"正在举剑"，原版本就同步）；画在 {@code AFTER_ENTITIES}，半透明几何不被深度写入吃掉。
 * <p>{@code glLineWidth} 在 1.20 已移除，故棱用四边形拼。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilentDaylightWardRenderer {

    /** 三棱锥外接球半径（格）：需求"大小 0.3 格"指的是高度/宽度量级，{@code R = 0.22} 时高 0.293、宽 0.415。 */
    private static final float TETRA_RADIUS = 0.22f;

    /** 环绕圆的半径（格）：以玩家腰为圆心。 */
    private static final float ORBIT_RADIUS = 1.6f;

    /** 环绕平面与地面的夹角（度）。 */
    private static final float ORBIT_TILT_DEG = 25.0f;

    /** 环绕中心相对脚底的高度（格）。不能再低：倾斜 25° 时圆最低点约 −0.68，再减 0.22 半径仍要不扎进地面。 */
    private static final float CENTER_LIFT = 1.15f;

    /** 公转角速度（度/秒）：约 3.6 秒一圈。 */
    private static final float ORBIT_DEG_PER_SECOND = 100.0f;

    /** 倾斜方向绕 Y 轴的进动角速度（度/秒）。取与公转不成简单整数比的值，避免周期性顿挫。 */
    private static final float PRECESS_DEG_PER_SECOND = 37.0f;

    /** 三棱锥自转：三个轴的角速度（度/秒），互不相同。 */
    private static final float SPIN_X_DEG_PER_SECOND = 137.0f;
    private static final float SPIN_Y_DEG_PER_SECOND = 211.0f;
    private static final float SPIN_Z_DEG_PER_SECOND = 83.0f;

    /** 多颗时每颗的翻滚相位偏移（秒）：让它们姿态错开，不是同一个东西复制三份。 */
    private static final double SPIN_PHASE_PER_TETRA = 1.7D;

    /** 举剑维持寒气领域时环绕的三棱锥数量（手持时是 1 颗）。 */
    private static final int WARDING_TETRA_COUNT = 3;

    /** 辉光层棱宽（格，半宽）。棱长约 0.36 格，0.024 的半径既看得见又不会糊成一团。 */
    private static final float GLOW_HALF_WIDTH = 0.024f;
    /** 芯层棱宽（格，半宽）。 */
    private static final float CORE_HALF_WIDTH = 0.010f;

    /** 辉光层颜色 = 亮靛蓝 {@code #4F8CFF}。 */
    private static final float GLOW_R = 0x4F / 255f;
    private static final float GLOW_G = 0x8C / 255f;
    private static final float GLOW_B = 0xFF / 255f;

    /** 芯层颜色 = 冰白 {@code #E4FAFF}。 */
    private static final float CORE_R = 0xE4 / 255f;
    private static final float CORE_G = 0xFA / 255f;
    private static final float CORE_B = 0xFF / 255f;

    /** 正四面体：底面圆的半径 / 外接球半径 = √8 / 3。 */
    private static final float BASE_RING_RATIO = (float) (Math.sqrt(8.0) / 3.0);

    /** 六条棱的顶点索引对（顶点 0 → 三个底角，再加底面三边）。 */
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 3},
            {1, 2}, {2, 3}, {3, 1}
    };

    private SilentDaylightWardRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 先收集：一个人都没拿这把剑就直接跳过，别碰 GL 状态
        boolean any = false;
        for (Player player : mc.level.players()) {
            if (SilentDaylight.isHolding(player)) {
                any = true;
                break;
            }
        }
        if (!any) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        // 加法混合：棱在暗处发亮、不会把背后的画面压暗
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
            if (!SilentDaylight.isHolding(player)) continue;
            renderWard(buffer, tesselator, matrix, player, partialTick);
        }

        poseStack.popPose();

        // 恢复 GL 状态（不恢复会污染后续所有渲染）
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** 画一个玩家的三棱锥：手持 = 1 颗，举剑 = 3 颗（沿轨道均分 120°）；两层各一次 begin/end，与个数无关。 */
    private static void renderWard(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix,
                                   Player player, float partialTick) {
        Vec3 center = player.getPosition(partialTick).add(0.0D, CENTER_LIFT, 0.0D);

        // 用世界时间（不是玩家 tickCount），退出重进不会跳角度
        double seconds = (player.level().getGameTime() + partialTick) / 20.0D;

        int count = SilentDaylight.isWarding(player) ? WARDING_TETRA_COUNT : 1;

        // 呼吸：亮度在 0.75~1.0 之间轻微起伏，静止时也不会像"贴图"
        float pulse = 0.75f + 0.25f * (float) Math.sin(seconds * 1.8D);

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            Vec3[] vertices = tetrahedronVertices(orbitPosition(center, seconds, i, count), seconds, i);
            for (int[] edge : EDGES) {
                emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                        GLOW_HALF_WIDTH, GLOW_R, GLOW_G, GLOW_B, 0.42f * pulse);
            }
        }
        tesselator.end();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            Vec3[] vertices = tetrahedronVertices(orbitPosition(center, seconds, i, count), seconds, i);
            for (int[] edge : EDGES) {
                emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                        CORE_HALF_WIDTH, CORE_R, CORE_G, CORE_B, 0.85f * pulse);
            }
        }
        tesselator.end();
    }

    /**
     * 三棱锥此刻所在的轨道点：{@code u = n × +Y}、{@code v = n × u}，圆上一点 {@code R·(cosθ·u + sinθ·v)}；
     * φ 随时间进动，轨道缓慢扫过球面。
     */
    private static Vec3 orbitPosition(Vec3 center, double seconds, int index, int count) {
        double tilt = Math.toRadians(ORBIT_TILT_DEG);
        double theta = Math.toRadians(seconds * ORBIT_DEG_PER_SECOND
                + 360.0 * index / Math.max(1, count));
        double phi = Math.toRadians(seconds * PRECESS_DEG_PER_SECOND);

        Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(phi),
                Math.cos(tilt),
                Math.sin(tilt) * Math.sin(phi));

        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = normal.cross(up);
        if (u.lengthSqr() < 1.0E-9D) {
            // 退化（tilt = 0 水平圆）：随便取一条水平轴
            u = new Vec3(1.0D, 0.0D, 0.0D);
        }
        u = u.normalize();
        Vec3 v = normal.cross(u).normalize();

        return center.add(u.scale(ORBIT_RADIUS * Math.cos(theta))
                .add(v.scale(ORBIT_RADIUS * Math.sin(theta))));
    }

    /** 正四面体四个顶点：局部"顶点朝上"形态（顶点 {@code (0,R,0)}、底面 {@code y=-R/3}、半径 {@code R·√8/3}）翻滚后平移到轨道点。 */
    private static Vec3[] tetrahedronVertices(Vec3 anchor, double seconds, int index) {
        float baseRadius = TETRA_RADIUS * BASE_RING_RATIO;
        float baseY = -TETRA_RADIUS / 3.0f;

        double phase = seconds + index * SPIN_PHASE_PER_TETRA;
        double ax = Math.toRadians(phase * SPIN_X_DEG_PER_SECOND);
        double ay = Math.toRadians(phase * SPIN_Y_DEG_PER_SECOND);
        double az = Math.toRadians(phase * SPIN_Z_DEG_PER_SECOND);

        Vec3[] out = new Vec3[4];
        out[0] = anchor.add(tumble(new Vec3(0.0D, TETRA_RADIUS, 0.0D), ax, ay, az));

        for (int i = 0; i < 3; i++) {
            // 底面三点间隔 120°，从 90° 起
            double angle = Math.toRadians(90.0 + 120.0 * i);
            Vec3 local = new Vec3(baseRadius * Math.cos(angle), baseY, baseRadius * Math.sin(angle));
            out[1 + i] = anchor.add(tumble(local, ax, ay, az));
        }
        return out;
    }

    /** 绕 X → Y → Z 依次旋转（弧度）。三个轴都转，避免"只有一个轴在动"那种一眼假的旋转；顺序固定只为结果可复现。 */
    private static Vec3 tumble(Vec3 v, double ax, double ay, double az) {
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double y1 = v.y * cx - v.z * sx;
        double z1 = v.y * sx + v.z * cx;
        double x1 = v.x;

        double cy = Math.cos(ay), sy = Math.sin(ay);
        double x2 = x1 * cy + z1 * sy;
        double z2 = -x1 * sy + z1 * cy;
        double y2 = y1;

        double cz = Math.cos(az), sz = Math.sin(az);
        double x3 = x2 * cz - y2 * sz;
        double y3 = x2 * sz + y2 * cz;

        return new Vec3(x3, y3, z2);
    }

    /** 把一条棱画成两片互相垂直的四边形（十字截面）：{@code DEBUG_LINES} 只有 1 像素、无辉光，且 {@code glLineWidth} 已移除。 */
    private static void emitEdge(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b,
                                 float halfWidth, float r, float g, float bl, float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-9D) return;
        axis = axis.normalize();

        // 任取一个与 axis 不平行的向量做叉乘，得到第一个垂直方向
        Vec3 helper = Math.abs(axis.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
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
        Vec3 p0 = a.subtract(offset);
        Vec3 p1 = b.subtract(offset);
        Vec3 p2 = b.add(offset);
        Vec3 p3 = a.add(offset);

        vertex(buffer, matrix, p0, r, g, bl, alpha);
        vertex(buffer, matrix, p1, r, g, bl, alpha);
        vertex(buffer, matrix, p2, r, g, bl, alpha);
        vertex(buffer, matrix, p3, r, g, bl, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                               float r, float g, float b, float alpha) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}
