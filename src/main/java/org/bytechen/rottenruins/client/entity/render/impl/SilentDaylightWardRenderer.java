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
 * <b>寂寒白日 · 寒气领域</b>的客户端表现：一颗小而蓝的<b>正三棱锥</b>绕着玩家转。
 *
 * <h3>为什么不用实体 / 不需要同步</h3>
 * <p>领域的状态就是"玩家正在举剑"（{@code isUsingItem()} + 正在用的是本剑），
 * 这两个条件<b>原版本来就同步</b>，所以客户端自己就能判定（见
 * {@link SilentDaylight#isWarding(Player)})—— 三棱锥与举剑姿势同时出现、同时消失。
 * 再开一个护盾实体只会多一条同步链路、多一份实体列表开销，
 * 还要处理"玩家换维度/退出时实体残留"。</p>
 *
 * <h3>为什么在这个阶段画</h3>
 * <p>{@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} —— 实体已经全部画完，
 * 此时画半透明几何不会被前面实体的深度写入吃掉，也不会污染实体渲染阶段的 GL 状态
 * （混用 {@code MultiBufferSource} 的批次时改深度函数很容易把后面的实体画坏）。</p>
 *
 * <h3>轨迹：25° 倾斜的圆 + 自身多轴翻滚</h3>
 * <ul>
 *   <li><b>轨道</b>：以玩家腰为圆心、半径 {@link #ORBIT_RADIUS} 格的一个圆。
 *       圆所在平面与地面成 {@link #ORBIT_TILT_DEG}°（水平面的法线是 {@code +Y}，
 *       所以"面与地面的夹角"= 圆平面法线与 {@code +Y} 的夹角）。</li>
 *   <li><b>公转</b>：三棱锥沿这个圆匀速走，{@link #ORBIT_DEG_PER_SECOND}°/秒。</li>
 *   <li><b>进动</b>：圆的倾斜方向本身也在绕 Y 轴慢慢转（{@link #PRECESS_DEG_PER_SECOND}°/秒），
 *       于是轨道不是固定死的一块，而是缓慢扫过整个球面 —— 需求里的"多向旋转"。</li>
 *   <li><b>自转</b>：三棱锥自己同时绕 X / Y / Z 三个轴以不同角速度翻滚，
 *       三个速度互质程度越高越不容易读成"周期性的重复"。</li>
 * </ul>
 *
 * <h3>几何</h3>
 * <p>正四面体取"顶点朝上、底面水平"的那一支（局部坐标）：外接球半径 R 时顶点在
 * {@code +R}，底面在 {@code -R/3}、半径 {@code R·√8/3}。翻滚之后再平移到轨道点上，
 * 所以"顶点朝上"只是它转到的某一个姿态，不是常驻朝向。</p>
 *
 * <p>只画<b>棱</b>：六条棱各自用两片互相垂直的四边形拼成一条"十字 ribbons"，
 * 从任何角度看都有厚度，不需要 {@code glLineWidth}（1.20 已移除，且不同显卡上限不同）。
 * 面完全透明 —— 需求是"仅棱有颜色，其他透明"。</p>
 *
 * <p>配色两层叠加：外层宽一点的靛蓝（{@code #4F8CFF}）做辉光，
 * 内层细一点的冰白（{@code #E4FAFF}）做芯 —— 与这把剑的描边、名字渐变是同一组色，
 * 加法混合下读作"蓝到发白的棱"。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilentDaylightWardRenderer {

    /**
     * 三棱锥的外接球半径（格）—— 对应需求里的"大小 0.3 格"。
     *
     * <p>取 0.22 而不是 0.3：正四面体的<b>外接球直径</b>是 {@code 2R}，而"大小 0.3 格"
     * 读作"这玩意大概 0.3 格"。按顶点朝上的姿态算，它的高度是 {@code 4R/3}、
     * 底面宽度是 {@code 2R·√8/3}，取 {@code R = 0.22} 时高度 0.293、宽度 0.415，
     * 正好是"0.3 格量级"的小晶体。</p>
     */
    private static final float TETRA_RADIUS = 0.22f;

    /** 环绕圆的半径（格）：以玩家腰为圆心。 */
    private static final float ORBIT_RADIUS = 1.6f;

    /** 环绕平面与地面的夹角（度）。 */
    private static final float ORBIT_TILT_DEG = 25.0f;

    /**
     * 环绕中心相对玩家脚底的高度（格）。
     *
     * <p>不能更低：倾斜 25° 时圆的最低点在 {@code center − ORBIT_RADIUS·sin25°}
     * （≈ −0.68），再减去三棱锥自己的 0.22 半径 —— 中心给到 1.15 才能保证
     * 它全程不扎进地面（最低 ≈ 脚下 0.25 格）。</p>
     */
    private static final float CENTER_LIFT = 1.15f;

    /** 公转角速度（度/秒）：约 3.6 秒一圈。 */
    private static final float ORBIT_DEG_PER_SECOND = 100.0f;

    /** 倾斜方向绕 Y 轴的进动角速度（度/秒）。取与公转不成简单整数比的值，避免周期性顿挫。 */
    private static final float PRECESS_DEG_PER_SECOND = 37.0f;

    /** 三棱锥自转：三个轴的角速度（度/秒），互不相同。 */
    private static final float SPIN_X_DEG_PER_SECOND = 137.0f;
    private static final float SPIN_Y_DEG_PER_SECOND = 211.0f;
    private static final float SPIN_Z_DEG_PER_SECOND = 83.0f;

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

        // 先收集：一个人都没举剑就直接跳过，别碰 GL 状态
        boolean any = false;
        for (Player player : mc.level.players()) {
            if (SilentDaylight.isWarding(player)) {
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
            if (!SilentDaylight.isWarding(player)) continue;
            renderWard(buffer, tesselator, matrix, player, partialTick);
        }

        poseStack.popPose();

        // 恢复 GL 状态（不恢复会污染后续所有渲染）
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** 画一个玩家的三棱锥：算轨道点 + 翻滚姿态，再画六条棱（辉光 + 芯两层）。 */
    private static void renderWard(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix,
                                   Player player, float partialTick) {
        Vec3 center = player.getPosition(partialTick).add(0.0D, CENTER_LIFT, 0.0D);

        // 用世界时间（不是玩家 tickCount），退出重进不会跳角度
        double seconds = (player.level().getGameTime() + partialTick) / 20.0D;

        Vec3 anchor = orbitPosition(center, seconds);
        Vec3[] vertices = tetrahedronVertices(anchor, seconds);

        // 呼吸：亮度在 0.75~1.0 之间轻微起伏，静止时也不会像"贴图"
        float pulse = 0.75f + 0.25f * (float) Math.sin(seconds * 1.8D);

        // ── ① 辉光层 ──
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : EDGES) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    GLOW_HALF_WIDTH, GLOW_R, GLOW_G, GLOW_B, 0.42f * pulse);
        }
        tesselator.end();

        // ── ② 芯层 ──
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : EDGES) {
            emitEdge(buffer, matrix, vertices[edge[0]], vertices[edge[1]],
                    CORE_HALF_WIDTH, CORE_R, CORE_G, CORE_B, 0.85f * pulse);
        }
        tesselator.end();
    }

    /**
     * 三棱锥此刻所在的轨道点（世界坐标）。
     *
     * <p>圆的法线 {@code n} 与 {@code +Y} 的夹角就是"圆面与地面的夹角"，
     * 所以 {@code n = (sin(tilt)·cosφ, cos(tilt), sin(tilt)·sinφ)}；
     * 再用 {@code n} 与 {@code +Y} 叉出圆的第一条基向量 {@code u}，{@code v = n × u}，
     * 圆上一点就是 {@code R·(cosθ·u + sinθ·v)}。</p>
     *
     * <p>φ 随时间进动 → 倾斜方向在变，整个轨道缓慢扫过球面（"多向旋转"的一半）。</p>
     */
    private static Vec3 orbitPosition(Vec3 center, double seconds) {
        double tilt = Math.toRadians(ORBIT_TILT_DEG);
        double theta = Math.toRadians(seconds * ORBIT_DEG_PER_SECOND);
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

    /**
     * 正四面体的四个顶点（世界坐标）。
     *
     * <p>先在<b>局部坐标</b>建出"顶点朝上"的正四面体：顶点 {@code (0, R, 0)}，
     * 底面三点在 {@code y = -R/3} 的平面上、落在半径 {@code R·√8/3} 的圆上（间隔 120°）。
     * 这样六条棱等长 —— 是<b>正</b>三棱锥，不是随手拼的锥。
     * 再按三个轴的角速度翻滚，最后平移到轨道点。</p>
     */
    private static Vec3[] tetrahedronVertices(Vec3 anchor, double seconds) {
        float baseRadius = TETRA_RADIUS * BASE_RING_RATIO;
        float baseY = -TETRA_RADIUS / 3.0f;

        double ax = Math.toRadians(seconds * SPIN_X_DEG_PER_SECOND);
        double ay = Math.toRadians(seconds * SPIN_Y_DEG_PER_SECOND);
        double az = Math.toRadians(seconds * SPIN_Z_DEG_PER_SECOND);

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

    /**
     * 绕 X → Y → Z 依次旋转（角度为弧度）。
     *
     * <p>三个轴<b>都</b>转，所以姿态不会出现"只有一个轴在动"那种一眼假的旋转；
     * 顺序固定为 X→Y→Z 只是为了结果可复现（客户端每帧重算，不需要序列化）。</p>
     */
    private static Vec3 tumble(Vec3 v, double ax, double ay, double az) {
        // 绕 X
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double y1 = v.y * cx - v.z * sx;
        double z1 = v.y * sx + v.z * cx;
        double x1 = v.x;

        // 绕 Y
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double x2 = x1 * cy + z1 * sy;
        double z2 = -x1 * sy + z1 * cy;
        double y2 = y1;

        // 绕 Z
        double cz = Math.cos(az), sz = Math.sin(az);
        double x3 = x2 * cz - y2 * sz;
        double y3 = x2 * sz + y2 * cz;

        return new Vec3(x3, y3, z2);
    }

    /**
     * 把一条棱画成两片互相垂直的四边形（十字截面）。
     *
     * <p>为什么不用 {@code VertexFormat.Mode.DEBUG_LINES}：那只有 1 像素，
     * 既看不清、也没有"辉光"可言；而 {@code glLineWidth} 在 1.20 已被移除。
     * 十字 ribbons 无论从哪个角度看都有厚度，且总共只有 2 个四边形 / 棱。</p>
     */
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
