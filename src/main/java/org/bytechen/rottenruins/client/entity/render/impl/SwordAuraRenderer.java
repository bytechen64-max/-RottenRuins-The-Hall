package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 剑气自定义渲染器 —— 两个圆锥底面贴合，逐渐缩小并淡出。
 *
 * <h3>样式</h3>
 * <ul>
 *   <li>{@link SwordAuraEntity#STYLE_DEFAULT}：原有样式 —— 单个白色发光圆锥对，
 *       加法混合，用在虚空剑/碎骨/畸骸玩家上，<b>行为与之前完全一致</b>。</li>
 *   <li>{@link SwordAuraEntity#STYLE_APOSTLE}：使徒斩击样式，三层叠加：
 *       <ol>
 *         <li><b>白色外层</b>：把同一组圆锥按 {@code outerScale}（默认 1.45×）放大，
 *             用普通 alpha 混合画成实心白 —— 它是轮廓的"白边"；</li>
 *         <li><b>黑色内芯</b>：原尺寸的圆锥对，颜色纯黑，画完白色之后再用
 *             {@code GL_LEQUAL} 深度函数（不写深度）绘制，于是它<b>永远压在白色之上</b>，
 *             白色只剩下一圈轮廓 —— 也就是 outline 观感；</li>
 *         <li><b>空间扭曲</b>：以剑气为中心的一层屏幕空间折射，由
 *             {@link ApostleSlashRenderHandler} 在实体全部画完之后单独绘制
 *             （必须晚于实体的原因见该类注释）。</li>
 *       </ol>
 *   </li>
 *   <li>{@link SwordAuraEntity#STYLE_FROST}：<b>寒霜斩击</b> —— 外层仍然穿透方块、内层仍然
 *       被地形遮挡（这两条与使徒样式相同），但视觉上做了三件使徒样式没有的事：
 *       <ol>
 *         <li><b>渐变配色</b>：不再是"冰白 + 深蓝"两层硬切，而是<b>10 层同心壳</b>
 *             叠出的连续过渡 —— 冰白轮廓 → 浅蓝 → 中蓝 → 深蓝 → 近黑，
 *             每层再沿轴向做一次 tip→mid 插值（见 {@link FrostShell}）；</li>
 *         <li><b>弧形弯曲</b>：环心按 {@code (1-t)²} 横向推开（{@link #FROST_BEND}），
 *             于是这条刀光读作一道<b>弧</b>而不是一根针；</li>
 *         <li><b>流动高光</b>：一条窄亮带沿刀光从头扫到尾（{@link #pulseAt}），
 *             刀光活的、有能量在里面跑；</li>
 *         <li><b>方向性溶解</b>：收场不是整体缩小，而是一条溶解前沿<b>从来袭端</b>
 *             扫到另一头（{@link #dissolveKeep}）—— 读作"这一刀扫过去、尾迹散掉"。</li>
 *       </ol>
 *       以上全部只改顶点色与顶点位置，<b>不增加任何顶点</b>。</li>
 * </ul>
 *
 * <p>几何结构：上圆锥（顶点在上，底面在 y=0） + 下圆锥（顶点在下，底面在 y=0）。
 * 底面（y=0 处的圆盘）不渲染，仅渲染两侧锥面。
 */
public class SwordAuraRenderer extends EntityRenderer<SwordAuraEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆周分段数 */
    private static final int RING_SEGMENTS = 48;
    /** 每个圆锥的纵向环数（从 apex 到 base） */
    private static final int VERTICAL_RINGS = 16;

    // ═══════════════════════════════════════════════════════════════
    // 寒霜斩击的配色壳层
    // ═══════════════════════════════════════════════════════════════

    /**
     * <b>一层壳</b>：同一个双锥几何按 {@link #radiusScale} 缩小后叠在一起，
     * 由外到内逐层加深，于是从边缘到中轴是一段<b>连续的</b>颜色过渡（而不是两块硬色）。
     *
     * <p>每层自己还带一组 {@code tip → mid} 的颜色与透明度：{@code t=0} 是锥尖（刀光两端），
     * {@code t=1} 是中段最粗处。两端 alpha 压到接近 0，刀光就自然"淡出"而不是被截断；
     * 中段 alpha 高，负责撑起轮廓。插值用 smoothstep，所以两端的变化是缓的。</p>
     *
     * @param radiusScale 相对最外层半径的倍数（1.0 = 最外侧那层）
     * @param tipColor    锥尖处的颜色（0xRRGGBB）
     * @param midColor    中段最粗处的颜色（0xRRGGBB）
     * @param tipAlpha    锥尖处的 alpha 倍数
     * @param midAlpha    中段处的 alpha 倍数（会再乘实体自身的生命周期 alpha）
     * @param segments    圆周分段数（内层可以更少，因为它的边界藏在渐变里）
     * @param rings       纵向环数（决定轴向渐变的细腻度）
     */
    private record FrostShell(float radiusScale, int tipColor, int midColor,
                              float tipAlpha, float midAlpha,
                              int segments, int rings) {
    }

    /** 最外层的镶嵌密度：它是可见轮廓，圆周要给足。 */
    private static final int OUTER_SEGMENTS = 48;
    private static final int OUTER_RINGS = 12;
    /** 内层的镶嵌密度：边界被外层渐变盖住，16 段就够圆，省下的顶点用来多叠几层颜色。 */
    private static final int INNER_SEGMENTS = 16;
    private static final int INNER_RINGS = 8;

    /**
     * 最外层：<b>冰白轮廓</b>，也是唯一一层穿透方块的
     * （用它替代原来那层"放大 1.45 倍的实心白"）。
     */
    private static final FrostShell FROST_OUTER =
            new FrostShell(1.00f, 0xF6FEFF, 0xC9ECFF, 0.02f, 0.92f,
                    OUTER_SEGMENTS, OUTER_RINGS);

    /** 造一层内壳（镶嵌密度统一用内层那档）。 */
    private static FrostShell inner(float radiusScale, int tipColor, int midColor,
                                    float tipAlpha, float midAlpha) {
        return new FrostShell(radiusScale, tipColor, midColor, tipAlpha, midAlpha,
                INNER_SEGMENTS, INNER_RINGS);
    }

    /**
     * 内层，由外到内：冰白 → 浅蓝 → 中蓝 → 深蓝 → 近黑的海军蓝。
     *
     * <h3>为什么是 9 层、而不是 2 层或 20 层</h3>
     * <p>渐变的"台阶感"来自相邻两层之间的<b>颜色跳变 × 该层 alpha</b>。
     * 层数太少（原来 2 层）就是白↔深蓝硬切；层数太多则顶点数翻倍而看不出差别。
     * 所以这里做了两件事：层数加到 9，<b>每层 alpha 压到 0.42~0.56</b> ——
     * 单层不再盖满前一层，相邻两层的颜色差被 alpha 稀释掉大半，台阶就读不出来了。</p>
     *
     * <p>最内层 {@code midColor} 是 {@code 0x030814}：越靠近中轴越暗，
     * 这就是"内部颜色加深"——轮廓还是亮的，芯子几乎读作黑蓝。</p>
     *
     * <p>半径从 0.88 匀到 0.24（每层约 0.08），所以整条渐变带的宽度是稳定的；
     * 想整体调粗细只改 {@code outerScale} 或 {@code SwordAuraEntity} 的 radius，不必动这张表。</p>
     */
    private static final FrostShell[] FROST_INNER = {
            inner(0.88f, 0xEAFBFF, 0xC4EBFF, 0.03f, 0.42f),
            inner(0.80f, 0xE0F7FF, 0xAFE0FF, 0.04f, 0.44f),
            inner(0.72f, 0xD4F1FF, 0x94CFFA, 0.05f, 0.46f),
            inner(0.64f, 0xC6E9FF, 0x7CB6EC, 0.05f, 0.48f),
            inner(0.56f, 0xB6DEFF, 0x5E9AE0, 0.06f, 0.50f),
            inner(0.48f, 0xA2CFFF, 0x3F7BC8, 0.07f, 0.52f),
            inner(0.40f, 0x8AB8F2, 0x23539E, 0.08f, 0.54f),
            inner(0.32f, 0x6E92D4, 0x0F2C63, 0.09f, 0.55f),
            inner(0.24f, 0x4A5E9C, 0x030814, 0.10f, 0.56f),
    };

    // ── 动态视觉 ──

    /**
     * <b>弧形弯曲量</b>（格）：锥尖相对中段沿本地 +X 偏移多少。
     *
     * <p>直的双锥读起来是"一根针"；把环心按 {@code (1-t)²} 推出去之后，
     * 两端弯、中间直，整条读作<b>一道弧光</b>（挥砍轨迹本来就是弧）。</p>
     *
     * <p>数值要跟着刀光的<b>长宽比</b>走：刀光拉长拉细之后（14 格长 / 外半径约 0.28 格），
     * 同样的偏移量相对刀身会显得更弯。按等比例叠图重新比过 0.70 / 0.90 / 1.10 三档，
     * 取 {@code 0.90}：看得出是弧，又不至于在这个长度下变成回旋镖。</p>
     */
    private static final float FROST_BEND = 0.90f;

    /**
     * 高光脉冲：亮带沿轴向的宽度（σ，0~1 的轴向比例）。
     * 0.11 大约对应"亮带覆盖整条刀光的 1/4 长度"，够亮但不会把整条冲成白色。
     */
    private static final float PULSE_WIDTH = 0.11f;

    /**
     * 脉冲流速：每 tick 前进的轴向比例。0.075 ⇒ 约 13 tick 从一头扫到另一头，
     * 与刀光 14 tick 的寿命基本对齐 —— 也就是"一条刀光里恰好有一道光扫过"。
     */
    private static final float PULSE_SPEED = 0.075f;

    /** 脉冲把颜色往纯白提的强度（1.0 = 亮带中心变纯白）。 */
    private static final float PULSE_STRENGTH = 0.85f;

    // ── 收场：从来袭方向溶解 ──

    /**
     * 溶解边缘的宽度（0~1 的轴向比例）：前沿不是一条硬线，而是一段
     * {@value #DISSOLVE_EDGE} 长的过渡带，看起来像"烧穿 / 散掉"而不是被切断。
     *
     * <p>0.2 相当于沿着 14 格的刀光有约 2.8 格的过渡带 —— 够柔，又不至于整条都在半透明里。</p>
     */
    private static final float DISSOLVE_EDGE = 0.20f;

    public SwordAuraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    // ═══════════════════════════════════════════════════════════════
    // 主渲染入口
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(SwordAuraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float scale = entity.getCurrentScale(partialTick);
        float alpha = entity.getCurrentAlpha(partialTick);
        // 生命周期极早期/晚期跳过
        if (scale < 0.005f || alpha < 0.01f) return;

        float halfHeight = entity.getAuraHeight() / 2f;
        float baseRadius = entity.getAuraRadius();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();

        // ── 应用旋转 ──
        //  STYLE_VERDICT（天穹裁决）走一条<b>受控朝向</b>的路：
        //  俯仰由 setVerdictOrientation 给定（0 = 竖直劈砍 / 1 = 横向扫击），
        //  而不是按 entity id 派生一个随机倾斜。
        //  理由：裁决的剑光必须读得出方向 —— 领域落剑是"竖着钉下来"、
        //  突进拖尾是"顺着冲势铺开"，随机歪斜会把这两个语义都糊掉。
        if (entity.getStyle() == SwordAuraEntity.STYLE_VERDICT) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f * entity.getVerdictOrientation()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        } else {
            // ── 原有：先绕 Z 倾斜（Y 对称锥必须这样才有视觉变化），再绕 Y 转向 ──
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getPitch()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYaw()));
        }

        Matrix4f matrix = poseStack.last().pose();

        if (entity.getStyle() == SwordAuraEntity.STYLE_FROST) {
            float progress = entity.getLifeProgress(partialTick);
            // 寒霜样式<b>不</b>跟着全局那条 (1-p)² 淡出：它的收场交给"从来袭方向溶解"，
            // 用原来的曲线会还没溶解就已经淡得看不见了。这里只轻轻压一点。
            alpha = entity.getAlpha() * (1f - 0.55f * progress);
            // 流动高光的相位：按实体年龄推进（带 partialTick 插值，所以是连续滑动而不是每 tick 跳一格）
            float pulsePhase = ((entity.getAge() + Math.max(0f, partialTick)) * PULSE_SPEED) % 1f;
            renderFrost(buffer, tesselator, matrix, halfHeight, baseRadius,
                    scale, entity.getOuterScale(), alpha, pulsePhase, progress);
        } else if (entity.getStyle() == SwordAuraEntity.STYLE_APOSTLE) {
            // 使徒样式仍然是「白色外层 + 黑色内芯」两层硬切，行为与之前完全一致：
            //   · 白色外层：放大 outerScale 倍画实心白，作为轮廓外沿；
            //   · 黑色内芯：原尺寸画纯黑，后画且深度 LEQUAL ⇒ 白色只剩一圈轮廓。
            float outerR = 1f, outerG = 1f, outerB = 1f;
            float coreR = 0f, coreG = 0f, coreB = 0f;

            // ── ① 外色层：放大后画实心，作为轮廓外沿 ──
            //    「仅外色穿透方块」：这一层<b>关掉深度测试</b>且不写深度，
            //    于是它无视地形遮挡、永远可见（长刀劈过墙面的观感来源就是这一层）。
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();          // SRC_ALPHA / ONE_MINUS_SRC_ALPHA
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale * entity.getOuterScale(), outerR, outerG, outerB, alpha);
            tesselator.end();

            // ── ② 内芯：不穿透方块，且永远压在①之上 ──
            //    内芯开回正常深度测试（LEQUAL）但不写深度：
            //      · 它对①→ ①没写深度，LEQUAL 必然通过，且它后画 ⇒ ①只剩一圈轮廓（outline）；
            //      · 它对地形→ 墙体/方块的真实深度会正常挡住它 ⇒ 内芯不穿透方块。
            //    用条件深度测试而不是 GL_ALWAYS，正是为了把"内芯穿透"这条去掉；
            //    两层的层级关系仍然由绘制顺序保证，不存在共面 z-fighting。
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius,
                    scale, coreR, coreG, coreB, alpha);
            tesselator.end();

            // 恢复 GL 状态（不恢复会污染后续所有实体渲染）
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        } else {
            // ── 原有样式：白色 + 加法混合 = 发光 ──
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            emitCones(buffer, matrix, halfHeight, baseRadius, scale, 1f, 1f, 1f, alpha);
            tesselator.end();
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ═══════════════════════════════════════════════════════════════
    // 寒霜斩击：多层渐变
    // ═══════════════════════════════════════════════════════════════

    /**
     * 画寒霜斩击：多层同心壳叠出由外到内的平滑渐变 + 弧形弯曲 + 流动高光 + 方向性溶解。
     *
     * <p>深度规则与使徒样式一致，只是"轮廓"由一层变成了整条渐变带：</p>
     * <ol>
     *   <li><b>最外层</b>（{@link #FROST_OUTER}）关掉深度测试、不写深度 —— 它是唯一
     *       穿透方块的层，负责"隔着墙也看得见一道寒芒"；</li>
     *   <li><b>内层</b>（{@link #FROST_INNER}，9 层）开回 {@code GL_LEQUAL} 且不写深度 ——
     *       会被地形正常遮挡，同时因为后画、而前面的层没写深度，它们必然叠在上一层之上。</li>
     * </ol>
     *
     * <p>为什么用"叠壳"而不是加一套渐变着色器：这个几何只用到
     * {@code POSITION_COLOR} 顶点色，没有 UV、没有光照，颜色梯度只能落在顶点上。
     * 同心壳天然给出了"径向"那一段梯度，每层内部再用 smoothstep 给"轴向"那一段 ——
     * 两段拼起来就是连续的过渡，且不引入任何新着色器 / 纹理。</p>
     *
     * @param outerScale 最外层相对内层的放大倍数（{@code SwordAuraEntity#getOuterScale()}）
     * @param progress   生命进度 0→1，驱动"从来袭方向溶解"
     */
    private void renderFrost(BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix,
                             float halfHeight, float baseRadius, float scale,
                             float outerScale, float alpha, float pulsePhase, float progress) {
        // 所有壳层都以"最外层半径"为基准，内层按各自的 radiusScale 收进去，
        // 这样外轮廓的粗细只由 outerScale 决定，和层数无关。
        float shellBase = baseRadius * outerScale;
        // 弧度跟着实体缩放走（刀光收缩时弧也一起收），否则临近消失时会出现"弧还在、刀没了"
        float bend = FROST_BEND * scale;

        // ── ① 最外层：唯一穿透方块的一层 ──
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitFrostShell(buffer, matrix, halfHeight, shellBase, scale, bend, pulsePhase, progress,
                FROST_OUTER, alpha);
        tesselator.end();

        // ── ② 内层：由外到内逐层加深，全都不穿透方块 ──
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (FrostShell shell : FROST_INNER) {
            emitFrostShell(buffer, matrix, halfHeight, shellBase, scale, bend, pulsePhase, progress,
                    shell, alpha);
        }
        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** 发出一层壳的上下两个锥面（带轴向渐变 + 弯曲 + 流动高光 + 方向性溶解）。 */
    private void emitFrostShell(BufferBuilder buffer, Matrix4f matrix,
                                float halfHeight, float shellBaseRadius, float scale,
                                float bend, float pulsePhase, float progress,
                                FrostShell shell, float alpha) {
        float radius = shellBaseRadius * shell.radiusScale() * scale;
        // 上锥：顶点在 +halfHeight，底面在 y=0（t: 0=锥尖 → 1=中段）
        renderConeSideShaded(buffer, matrix, halfHeight, 0f, radius, bend, pulsePhase, progress,
                true, shell, alpha, false);
        // 下锥：顶点在 -halfHeight，底面错开 0.001，避免与上锥底环精确重合形成亮环
        renderConeSideShaded(buffer, matrix, -halfHeight, -0.001f, radius, bend, pulsePhase,
                progress, false, shell, alpha, true);
    }

    /**
     * 方向性溶解的保留度：<b>从刀光的"来袭端"开始一段段消失</b>。
     *
     * <p>{@code u} 是沿整条刀光的位置（0 = 上锥尖 = 来袭端，1 = 下锥尖）。
     * 随生命进度推进，一条"溶解前沿"从 {@code u<0} 扫到 {@code u>1}：
     * 前沿<b>之前</b>（{@code u < front}）的部分已经被吃掉，之后的部分原样保留。
     * 边缘用 {@link #DISSOLVE_EDGE} 宽度的 smoothstep，所以是"烧穿"而不是硬切。</p>
     *
     * <p>为什么不继续用"整体缩小"收场：缩小是把整条刀光一起吸向中心，读起来像
     * "刀被抽走了"；从来袭方向渐次消失才像"这一刀扫过去、尾迹从源头散掉"。</p>
     *
     * @return 该处的保留度 0~1（0 = 已经消失）
     */
    private static float dissolveKeep(float u, float progress) {
        float front = -DISSOLVE_EDGE + progress * (1f + 2f * DISSOLVE_EDGE);
        float k = (u - front) / DISSOLVE_EDGE;
        if (k <= 0f) {
            return 0f;
        }
        if (k >= 1f) {
            return 1f;
        }
        return k * k * (3f - 2f * k);          // smoothstep：边缘是"烧穿"，不是硬切
    }

    /**
     * 沿整条刀光（锥尖 A → 中段 → 锥尖 B）流动的高光强度。
     *
     * <p>用一条窄高斯窗口沿轴向扫过去：`exp(-d²/2σ²)`。窗口外是 0（不变亮）、
     * 窗口中心是 1（提到接近纯白）。分布按 {@code d - round(d)} 折回
     * {@code [-0.5, 0.5]}，于是脉冲循环流动，且两端无缝衔接。</p>
     *
     * @param u     该顶点在整条刀光上的位置（0 = 上锥尖，0.5 = 中段，1 = 下锥尖）
     * @param phase 当前脉冲位置（0~1，随年龄推进）
     */
    private static float pulseAt(float u, float phase) {
        float d = u - phase;
        d -= Math.round(d);
        return (float) Math.exp(-(d * d) / (2f * PULSE_WIDTH * PULSE_WIDTH));
    }

    /**
     * 渲染单个圆锥的侧面，颜色沿轴向（锥尖 → 中段）从 {@code tipColor} 插值到 {@code midColor}。
     *
     * <p>插值权重用 smoothstep（{@code t²(3-2t)}）而不是线性：线性在两端会有"折角"，
     * 在这么细长的刀光上会被放大成一条明显的分界；smoothstep 两端导数为 0，过渡是缓的。</p>
     *
     * <p>三个视觉层在这里一起算完（都只是顶点色，不增加任何顶点）：</p>
     * <ul>
     *   <li><b>轴向渐变</b>：tip → mid；</li>
     *   <li><b>弧形弯曲</b>：环心沿本地 +X 偏移 {@code bend × (1-t)²}，锥尖偏得最多、中段为 0 ——
     *       于是这条"针"读作一道<b>弧</b>，两半在中间严丝合缝地接上；</li>
     *   <li><b>流动高光</b>：{@link #pulseAt} 给出的亮带把该环颜色往纯白提；</li>
     *   <li><b>方向性溶解</b>：{@link #dissolveKeep} 沿轴向吃掉已经"扫过去"的部分。</li>
     * </ul>
     *
     * @param upper    {@code true} = 上锥（t 从顶点开始算）；用来把 {@code t} 映射成整条刀光上的
     *                 {@code u}，让高光与溶解都是一条完整地从一头扫到另一头的带
     * @param progress 生命进度 0→1
     */
    private void renderConeSideShaded(BufferBuilder buffer, Matrix4f matrix,
                                      float apexY, float baseY, float radius,
                                      float bend, float pulsePhase, float progress, boolean upper,
                                      FrostShell shell, float alpha, boolean flipWinding) {
        final int rings = Math.max(1, shell.rings());
        final int segments = Math.max(3, shell.segments());

        float[] ringRadii = new float[rings + 1];
        float[] ringXs = new float[rings + 1];
        float[] ringYs = new float[rings + 1];
        float[] ringR = new float[rings + 1];
        float[] ringG = new float[rings + 1];
        float[] ringB = new float[rings + 1];
        float[] ringA = new float[rings + 1];

        float tipR = channel(shell.tipColor(), 16), tipG = channel(shell.tipColor(), 8),
                tipB = channel(shell.tipColor(), 0);
        float midR = channel(shell.midColor(), 16), midG = channel(shell.midColor(), 8),
                midB = channel(shell.midColor(), 0);

        for (int ring = 0; ring <= rings; ring++) {
            float t = ring / (float) rings;
            float w = t * t * (3f - 2f * t);              // smoothstep
            ringRadii[ring] = radius * t;
            ringYs[ring] = apexY + (baseY - apexY) * t;
            // 弧：锥尖（t=0）偏得最多，中段（t=1）为 0 —— 两半在中间自然接上
            float bow = 1f - t;
            ringXs[ring] = bend * bow * bow;

            float r = lerp(tipR, midR, w);
            float g = lerp(tipG, midG, w);
            float b = lerp(tipB, midB, w);
            // 该环在整条刀光上的位置：0 = 上锥尖（来袭端），0.5 = 中段，1 = 下锥尖
            float u = upper ? 0.5f * t : 1f - 0.5f * t;
            // 流动高光：把这一环的颜色往纯白提
            float pulse = pulseAt(u, pulsePhase) * PULSE_STRENGTH;
            ringR[ring] = lerp(r, 1f, pulse);
            ringG[ring] = lerp(g, 1f, pulse);
            ringB[ring] = lerp(b, 1f, pulse);
            // 方向性溶解：前沿扫过的地方直接透明
            ringA[ring] = lerp(shell.tipAlpha(), shell.midAlpha(), w) * alpha
                    * dissolveKeep(u, progress);
        }

        for (int ring = 0; ring < rings; ring++) {
            float rCurr = ringRadii[ring];
            float rNext = ringRadii[ring + 1];
            float xCurr = ringXs[ring];
            float xNext = ringXs[ring + 1];
            float yCurr = ringYs[ring];
            float yNext = ringYs[ring + 1];
            float cr = ringR[ring], cg = ringG[ring], cb = ringB[ring], ca = ringA[ring];
            float nr = ringR[ring + 1], ng = ringG[ring + 1], nb = ringB[ring + 1], na = ringA[ring + 1];

            for (int seg = 0; seg < segments; seg++) {
                double a0 = 2.0 * Math.PI * seg / segments;
                double a1 = 2.0 * Math.PI * (seg + 1) / segments;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                float cx0 = xCurr + rCurr * c0, cz0 = rCurr * s0;
                float cx1 = xCurr + rCurr * c1, cz1 = rCurr * s1;

                float nx0 = xNext + rNext * c0, nz0 = rNext * s0;
                float nx1 = xNext + rNext * c1, nz1 = rNext * s1;

                if (flipWinding) {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, cr, cg, cb, ca);
                } else {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, nr, ng, nb, na);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, cr, cg, cb, ca);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, nr, ng, nb, na);
                }
            }
        }
    }

    /** 从 0xRRGGBB 里取一个 0~1 的通道值。 */
    private static float channel(int color, int shift) {
        return ((color >> shift) & 0xFF) / 255f;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    // ═══════════════════════════════════════════════════════════════
    // 圆锥几何
    // ═══════════════════════════════════════════════════════════════

    /** 一次性发出上下两个圆锥的侧面（不含底面圆盘）。 */
    private void emitCones(BufferBuilder buffer, Matrix4f matrix,
                           float halfHeight, float baseRadius, float scale,
                           float r, float g, float b, float alpha) {
        // ── 上圆锥：顶点 (0, +halfHeight, 0) → 底面 y=0 半径 baseRadius ──
        renderConeSide(buffer, matrix,
                0, halfHeight, 0,
                0, 0, baseRadius,
                scale, r, g, b, alpha, false);

        // ── 下圆锥：顶点 (0, -halfHeight, 0) → base 环 y=-0.001，避免与上锥 base 环精确重合形成亮环 ──
        renderConeSide(buffer, matrix,
                0, -halfHeight, 0,
                0, -0.001f, baseRadius,
                scale, r, g, b, alpha, true);
    }

    /**
     * 渲染单个圆锥的侧面（不含底面圆盘）。
     */
    private void renderConeSide(BufferBuilder buffer, Matrix4f matrix,
                                 float apexX, float apexY, float apexZ,
                                 float baseX, float baseY, float baseRadius,
                                 float scale, float r, float g, float b, float alpha,
                                 boolean flipWinding) {
        float[] ringRadii = new float[VERTICAL_RINGS + 1];
        float[] ringYs = new float[VERTICAL_RINGS + 1];

        for (int ring = 0; ring <= VERTICAL_RINGS; ring++) {
            float t = ring / (float) VERTICAL_RINGS;
            ringRadii[ring] = baseRadius * t * scale;
            ringYs[ring] = apexY + (baseY - apexY) * t;
        }
        float scaledApexX = apexX * scale;
        float scaledApexZ = apexZ * scale;
        float scaledBaseX = baseX * scale;

        for (int ring = 0; ring < VERTICAL_RINGS; ring++) {
            float rCurr = ringRadii[ring];
            float rNext = ringRadii[ring + 1];
            float yCurr = ringYs[ring];
            float yNext = ringYs[ring + 1];

            for (int seg = 0; seg < RING_SEGMENTS; seg++) {
                double a0 = 2.0 * Math.PI * seg / RING_SEGMENTS;
                double a1 = 2.0 * Math.PI * (seg + 1) / RING_SEGMENTS;

                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                float cx0 = scaledBaseX + rCurr * c0, cz0 = scaledApexZ + rCurr * s0;
                float cx1 = scaledBaseX + rCurr * c1, cz1 = scaledApexZ + rCurr * s1;

                float nx0 = scaledBaseX + rNext * c0, nz0 = scaledApexZ + rNext * s0;
                float nx1 = scaledBaseX + rNext * c1, nz1 = scaledApexZ + rNext * s1;

                if (flipWinding) {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, r, g, b, alpha);
                } else {
                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx0, yNext, nz0, r, g, b, alpha);

                    bufferVertex(buffer, matrix, cx0, yCurr, cz0, r, g, b, alpha);
                    bufferVertex(buffer, matrix, cx1, yCurr, cz1, r, g, b, alpha);
                    bufferVertex(buffer, matrix, nx1, yNext, nz1, r, g, b, alpha);
                }
            }
        }
    }

    private static void bufferVertex(BufferBuilder buffer, Matrix4f matrix,
                                      float x, float y, float z,
                                      float r, float g, float b, float alpha) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, alpha).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 纹理位置（占位）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResourceLocation getTextureLocation(SwordAuraEntity entity) {
        return DUMMY_TEXTURE;
    }
}
