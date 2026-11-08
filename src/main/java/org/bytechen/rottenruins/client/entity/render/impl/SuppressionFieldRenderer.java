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
 * <b>抑制场</b>的正方体视觉 —— 画法与寂寒白日那颗三棱锥完全一致
 * （{@code SilentDaylightWardRenderer}）：<b>只画棱</b>、辉光 + 芯两层、加法混合，
 * 面全透明。区别只有两点：形状是正方体，颜色取抑制场自己的紫。
 *
 * <h3>不需要任何同步</h3>
 * <p>判据是"玩家正在用一把法杖、且当前选中的法术是抑制场"——
 * 这三个条件（{@code isUsingItem}、手上物品、魔法池选中格）客户端本来就都有
 * （法术池是同步下来的副本，见 {@code MagicSync}）。所以这里不需要新开网络包，
 * 服务端也一行都不用改。</p>
 *
 * <h3>尺寸与判定必须一致</h3>
 * <p>半径直接调 {@link SuppressionField#radiusOf}，和 {@code onKeepTick} 里算判定用的是
 * <b>同一个静态方法</b> —— 否则会出现"看起来打到了却没伤害"这种最难查的问题。</p>
 *
 * <h3>几何</h3>
 * <p>正方体取"面心到中心 = 半径"的尺寸（也就是把半径为 R 的球内切进这个立方体里）。
 * 12 条棱各自用两片互相垂直的四边形拼成十字 ribbons，从任何角度看都有厚度，
 * 不需要 {@code glLineWidth}（1.20 已移除）。</p>
 *
 * <p>它随施法者一起走，所以用 {@code AFTER_ENTITIES} 阶段、按当前帧的玩家位置现算，
 * 天然没有"实体残留"的问题。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SuppressionFieldRenderer {

    /** 辉光层棱宽（格，半宽）。场很大，棱也得跟着粗一点才看得见。 */
    private static final float GLOW_HALF_WIDTH = 0.075f;

    /** 芯层棱宽（格，半宽）。 */
    private static final float CORE_HALF_WIDTH = 0.030f;

    /** 辉光层颜色：抑制场尘的紫 {@code (0.36, 0.18, 0.65)}，提亮一档做加法混合。 */
    private static final float GLOW_R = 0.42f;
    private static final float GLOW_G = 0.22f;
    private static final float GLOW_B = 0.85f;

    /** 芯层颜色：淡紫白，让棱芯在辉光里"亮出来"。 */
    private static final float CORE_R = 0.86f;
    private static final float CORE_G = 0.74f;
    private static final float CORE_B = 1.00f;

    /** 每秒自转角度（度）：缓慢转动，读得出"这是个立体的场"。 */
    private static final float SPIN_DEG_PER_SECOND = 12.0f;

    /** 脉冲周期（秒）：与法术的 10 tick 结算节奏对齐，每跳一次亮一下。 */
    private static final double PULSE_PERIOD_SECONDS = 0.5D;

    /** 正方体的 12 条棱（顶点索引 0..7）。 */
    private static final int[][] EDGES = {
            // 底面
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            // 顶面
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            // 立柱
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

    /**
     * 这个玩家此刻是否在维持抑制场；是就返回那个法术物品。
     *
     * <p>三件套：正在使用物品 + 手上是法杖 + 选中的法术是抑制场。
     * 都是客户端已有的信息，所以判定和显示天然同步。</p>
     */
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

        // 脉冲：每 0.5 秒（10 tick）亮一次，和法术结算节奏对齐
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

    /**
     * 正方体的 8 个顶点（世界坐标）。
     *
     * <p>局部坐标是 {@code ±R} 的立方体（面心到中心 = R，正好把半径 R 的球内切进去），
     * 再绕 Y 轴慢慢自转。顶点顺序与 {@link #EDGES} 对应：
     * 0..3 是底面四角（按环序 {@code -x-z → +x-z → +x+z → -x+z}），4..7 是顶面同序。</p>
     */
    private static Vec3[] cubeVertices(Vec3 center, float radius, float spin) {
        float cos = (float) Math.cos(spin);
        float sin = (float) Math.sin(spin);
        int[][] ring = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

        Vec3[] out = new Vec3[8];
        for (int i = 0; i < 4; i++) {
            float x = ring[i][0] * radius;
            float z = ring[i][1] * radius;
            // 绕 Y 轴自转
            float rx = x * cos - z * sin;
            float rz = x * sin + z * cos;
            out[i] = center.add(rx, -radius, rz);        // 底面
            out[i + 4] = center.add(rx, radius, rz);     // 顶面
        }
        return out;
    }

    /**
     * 把一条棱画成两片互相垂直的四边形（十字截面）。
     *
     * <p>不用 {@code VertexFormat.Mode.DEBUG_LINES}：那只有 1 像素，
     * 十格开外的场根本看不见；{@code glLineWidth} 在 1.20 已被移除。</p>
     */
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
