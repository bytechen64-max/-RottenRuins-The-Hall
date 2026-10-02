package org.bytechen.hall.client.rend.glint;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.bytechen.hall.utils.ModUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品描边管线 —— 屏幕空间「剪影遮罩 + 环形膨胀」。
 *
 * <h3>它替换掉了什么</h3>
 * 旧实现把物品模型整体放大 6% 再重画一遍当作"描边"。那等于把整件物品
 * （包括内部）都盖了一层颜色，真正的边只有最外那一圈；而且它声明的是
 * {@code NO_DEPTH_TEST} 的 RenderType，于是既会穿墙，又把宇宙星空层
 * 整片糊住。详见 docs/item-shader-outline.md。
 *
 * <h3>现在的做法</h3>
 * 分两步，两步的产物完全不重叠：
 * <ol>
 *   <li><b>剪影遮罩</b>（{@code outline_mask}）——把"这件物品占了哪些像素、
 *       该用什么颜色描边"画进一张离屏贴图。画的时候带 {@code LEQUAL} 深度测试、
 *       且把当前场景的深度附件挂到遮罩 FBO 上，所以被墙挡住的物品自然不进遮罩。</li>
 *   <li><b>环形合成</b>（{@code outline_ring}）——全屏 pass 算
 *       {@code ring = dilate(mask, r) − mask}。这个减法就是全部关键：
 *       剪影内部 mask=1，膨胀后还是 1，相减得 0 —— <b>描边永远只出现在物品外侧</b>。
 *       宇宙星空层画在物品表面上，于是两者连一个像素都不重叠。</li>
 * </ol>
 *
 * <h3>三个刻意的设计决定</h3>
 * <ul>
 *   <li><b>颜色烘进遮罩的 rgb</b>，而不是当 uniform 传。这样同一帧里不同物品
 *       可以各自有不同的描边颜色，却只需要一次全屏合成；也避免了旧实现里
 *       "共享 ShaderInstance 的 uniform 被同批次后一个物品覆盖"的问题。</li>
 *   <li><b>等宽靠像素半径</b>，不是模型缩放系数。所以 GUI 图标、一手、三手、
 *       掉落物在任何距离下描边一样粗。</li>
 *   <li><b>硬边、无羽化、无外发光</b>，对齐原版发光实体描边的观感。唯一的柔和
 *       来自 smoothstep 提供的约 1 像素抗锯齿。</li>
 * </ul>
 *
 * <h3>时机</h3>
 * 遮罩一律在物品渲染的当帧立刻捕获（那一刻场景深度才是对的）；合成的时机分三种：
 * <ul>
 *   <li>GUI / FIXED：立刻合成。GUI 画在世界之上，不需要遮挡。</li>
 *   <li>世界（掉落物、三手、物品展示框）：没有光影包时立刻合成；有光影包时
 *       推迟到 {@code renderLevel} 完成后，直接写主 RT，绕开 GBuffer。</li>
 *   <li>一手：一律推迟到 {@code renderLevel} 的 TAIL，让描边压在手之后。</li>
 * </ul>
 */
public final class ItemOutlinePipeline {

    /**
     * 一件待描边物品的完整快照。
     *
     * <p>之所以要快照这么多东西：非 GUI 上下文里我们<b>一个 GL 调用都不发</b>，
     * 全部攒到 {@code renderLevel} TAIL 再统一处理。</p>
     *
     * <p>为什么非要这样：世界物品是在「实体通道」中间渲染的，那一刻我们切 framebuffer、
     * 画全屏四边形、改 viewport / scissor，会影响到**之后**才提交/绘制的所有实体
     * （实测：世界上所有实体变透明）。TAIL 时场景已经画完，同样的操作就完全无害 ——
     * 一手描边一直是在 TAIL 合成的，从来没出过这个问题，这就是最好的对照。</p>
     */
    private record Entry(Rect rect,
                         Matrix4f pose, Matrix3f normal,
                         Matrix4f modelView, Matrix4f projection,
                         BakedModel model,
                         @Nullable ICustomOutline custom,
                         int light, int overlay,
                         float red, float green, float blue,
                         float secondaryRed, float secondaryGreen, float secondaryBlue,
                         float colorMode, float scrollSpeed,
                         float[] palette, int paletteSize,
                         float pixelWidth, float opacity, float alphaTest,
                         boolean additive, boolean gui, boolean occlude,
                         int depthType, int depthName) { }

    /** 屏幕像素包围盒（GL 约定：原点左下）。 */
    private record Rect(int x, int y, int width, int height) { }

    /** 一帧里攒下的、还没画进遮罩的条目。 */
    private static final List<Entry> PENDING = new ArrayList<>(32);

    /**
     * 剪影已经画进遮罩、等着被合成的条目。
     *
     * <p>为什么要分两份：一手物品是在 {@code renderItemInHand} 里渲染的，而遮罩捕获点在
     * 它**之前**（`renderHand` 字段读取处，即 vanilla 清深度之前）—— 那一刻一手的条目
     * 还不存在。所以一手要在 TAIL 补一次捕获。分两份之后，两批剪影可以共存于同一张遮罩，
     * 最后一次性合成，互相不会被清掉。</p>
     */
    private static final List<Entry> READY = new ArrayList<>(32);

    /** 本帧遮罩里是否已经有内容 —— 决定下一批捕获前要不要清整张遮罩。 */
    private static boolean maskHasContent;

    private static final int MAX_PENDING = 256;

    /**
     * 重放剪影时临时用的 PoseStack。
     * {@code PoseStack.Pose} 的构造器不是 public，所以拿一个恒等 PoseStack 出来改写它的矩阵。
     */
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    /** 世界坐标下模型包围盒要按比例裁掉的极端值，避免相机贴脸时算出天文数字的矩形。 */
    private static final float MAX_NDC = 1.35F;

    /**
     * 深度比较容差。剪影自己必然已经在场景深度里，同深度必须算通过。
     *
     * <p>我们的重放用的是与物品渲染当时完全相同的矩阵和几何、同样的视口尺寸，
     * 所以光栅化出来的窗口深度应该逐位一致；窗口深度的量化步长约 6e-8，
     * 取 3e-5（约 500 个量化步长）足够吸收任何舍入差异，又远小于墙体与物品之间
     * 实际的深度差，不会让描边穿墙。</p>
     */
    private static final float DEPTH_BIAS = 3.0E-5F;

    /**
     * 遮挡判定用的深度。
     *
     * <p>做法：在画剪影之前，把**这件物品当时画进去的那个 framebuffer 的深度附件**
     * 临时挂到遮罩 FBO 上，靠**硬件深度测试**（{@code LEQUAL} + 不写深度，
     * 这一对状态 {@link OutlineMaskRenderType} 本来就开着）剔除被挡住的部分。</p>
     *
     * <p>为什么不做"把深度当纹理采样"那一套：Oculus 的 GBuffer 深度可能是
     * <b>renderbuffer</b>（根本不能绑成采样器）、格式也可能和主 RT 不同。
     * 硬件测试只要求"把它挂上去"，纹理 / renderbuffer / 什么格式都行，
     * 更关键的是<b>它天然用的是同一套深度约定</b> —— 这份深度本来就是用同一个投影
     * 写出来的，比较由 GPU 完成，不需要我们猜 reversed-Z 或 far plane 之类的东西。</p>
     *
     * <p>时机也关键：捕获放在 {@code renderLevel} 里首次读 {@code renderHand} 字段那一刻
     * （世界和实体都画完、vanilla 还没清深度、也还没开始画一手），是完全干净的分界点；
     * 只有最后的环形合成留在 TAIL。</p>
     */
    private record DepthSource(int type, int name) {
        static final int NONE = 0;
        boolean present() { return name != 0 && (type == GL11.GL_TEXTURE || type == GL30.GL_RENDERBUFFER); }
    }

    @Nullable
    private static TextureTarget maskTarget;

    /**
     * 遮罩专用的一沓缓冲区。
     *
     * <p>刻意不复用 {@code Tesselator.getInstance().getBuilder()}：那一个是全局共享的，
     * 而我们是在任意的 {@code ItemRenderer.render} 上下文里插入，万一它正好在
     * building 状态就会直接抛 "Already building!"。自己拿一份互不干扰。</p>
     */
    @Nullable
    private static RenderBuffers maskBuffers;

    private ItemOutlinePipeline() { }

    // ──────────────────────────────────────────────────────────────
    //  入口：物品渲染时调用
    // ──────────────────────────────────────────────────────────────

    /**
     * 由 {@code ItemRendererMixin} 在 {@code ItemRenderer.render} 的 popPose 之前调用。
     *
     * <p>调用点必须仍在物品自己的 pose 之内 —— 遮罩要把物品的模型变换重放一遍。</p>
     */
    public static void submit(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay, BakedModel model) {
        if (ShaderPackDetector.isShadowPass()) return;
        if (!shaderRenderingEnabled()) return;

        Style style = resolveStyle(stack, context);
        if (style == null) return;

        if (SplendidingShaders.outlineMaskShader == null
                || SplendidingShaders.outlineRingShader == null
                || SplendidingShaders.getOutlineMaskRenderType() == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;
        if (minecraft.level == null) return;

        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(42L));
        if (quads.isEmpty()) return;

        boolean gui = context == ItemDisplayContext.GUI;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack.Pose pose = poseStack.last();

        // GUI 里再收一道环宽：物品栏图标本来就 16px，4.5px 的环太厚，
        // 黑的半圈会把图标本身的内容压掉。倍率见 guiOutlineWidthScale。
        float pixelWidth = style.pixelWidth();
        if (gui) {
            SplendidingConfig cfg = config();
            pixelWidth = Mth.clamp(pixelWidth * sanitize(cfg == null ? 1F : cfg.guiOutlineWidthScale, 1F),
                    0.5F, 24.0F);
        }

        Rect rect = computeScreenRect(pose, modelView, projection, quads, main, pixelWidth);
        if (rect == null) return;

        if (PENDING.size() >= MAX_PENDING) {
            // 极端情况（几百个描边掉落物同屏）下宁可少描几个，也不要无限增长。
            return;
        }

        // 遮挡判定对「摆在世界里的物品」和「三手手里的物品」都开着：
        // 不开的话描边会穿透方块和实体（这是实测踩过的）。
        //
        // 唯独一手和 GUI 不开：
        //   * 一手物品是画在相机的眼皮底下的（vanilla 甚至在画手之前把深度清掉专门
        //     保证它不被挡），本来就谈不上"被遮挡"，拿任何深度去判都是自找麻烦；
        //   * GUI 画在世界之上，正交深度跟世界深度不是一回事。
        boolean occlude = !gui && !firstPerson;

        // 记一下"这件物品当时画进了哪个 framebuffer 的深度附件"。
        //
        // 无光影包时它是主 RT 的深度；Oculus 激活时它是 GBuffer 的深度
        // （物品本体就是画在那里的）。**纯只读查询，不改任何 GL 状态** ——
        // 在实体通道中间我们是绝对不能碰 GL 状态的（那正是"世界里所有实体变透明"
        // 的成因）。真正用它是在世界画完之后、vanilla 清深度之前的那个干净分界点。
        DepthSource depth = occlude ? queryBoundDepthSource() : new DepthSource(DepthSource.NONE, 0);

        PENDING.add(new Entry(rect,
                new Matrix4f(pose.pose()), new Matrix3f(pose.normal()),
                modelView, projection,
                model,
                stack.getItem() instanceof ICustomOutline c ? c : null,
                light, overlay,
                style.red(), style.green(), style.blue(),
                style.secondaryRed(), style.secondaryGreen(), style.secondaryBlue(),
                style.colorMode(), style.scrollSpeed(),
                style.palette(), style.paletteSize(),
                pixelWidth, style.opacity(), style.alphaTest(),
                style.additive(), gui, occlude, depth.type(), depth.name()));

        debugOnce("submit/" + context,
                "submit ctx=" + context
                        + " buffer=" + buffer.getClass().getSimpleName()
                        + " gui=" + gui
                        + " occlude=" + occlude
                        + " packActive=" + ShaderPackDetector.shouldUseShaderPackPipeline()
                        + " boundFbo=" + GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                        + " depthSrc=" + (depth.present()
                                ? (depth.type() == GL11.GL_TEXTURE ? "tex" : "rbo") + "#" + depth.name()
                                : "none")
                        + " quads=" + quads.size()
                        + " rect=" + rect.width() + "x" + rect.height()
                        + " widthPx=" + pixelWidth);

        // GUI 是在 renderLevel 之后单独的一趟，主 RT / 深度都是自洽的，而且此刻
        // 没有别的东西会被我们打扰 —— 所以只有 GUI 就地处理，其余一律攒到 TAIL。
        if (gui) {
            drain();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  样式解析
    // ──────────────────────────────────────────────────────────────

    private record Style(float red, float green, float blue,
                         float secondaryRed, float secondaryGreen, float secondaryBlue,
                         float colorMode, float scrollSpeed,
                         float[] palette, int paletteSize,
                         float pixelWidth, float opacity, float alphaTest, boolean additive) { }

    /**
     * 决定这个物品在这种上下文下要不要描边、用什么样式。
     *
     * <p>优先级与旧实现一致：{@link ICustomOutline} 接口优先于 {@link GlintRenderManager} 注册。</p>
     */
    @Nullable
    private static Style resolveStyle(ItemStack stack, ItemDisplayContext context) {
        ICustomOutline custom = stack.getItem() instanceof ICustomOutline c ? c : null;
        if (custom != null && custom.outlineEnabled(context)) {
            int argb = custom.outlineColor();
            float r = ((argb >> 16) & 0xFF) / 255F;
            float g = ((argb >> 8) & 0xFF) / 255F;
            float b = (argb & 0xFF) / 255F;

            int secondary = custom.outlineSecondaryColor();
            boolean hasSecondary = ((secondary >>> 24) & 0xFF) != 0;
            float sr = hasSecondary ? ((secondary >> 16) & 0xFF) / 255F : r;
            float sg = hasSecondary ? ((secondary >> 8) & 0xFF) / 255F : g;
            float sb = hasSecondary ? (secondary & 0xFF) / 255F : b;

            return new Style(r, g, b, sr, sg, sb,
                    colorModeForShaderKey(custom.outlineShaderKey()), 1.0F,
                    new float[]{r, g, b}, 1,
                    scaledWidth(custom.outlinePixelWidth()), custom.outlineOpacity(), custom.outlineAlphaCutoff(),
                    custom.outlineBlend() == ICustomOutline.BlendMode.ADDITIVE);
        }

        GlintEffectProfile profile = GlintRenderManager.getProfile(stack);
        if (profile == null || !profile.isWorldOutlineEnabled() || !profile.shouldRender(context)) return null;

        int paletteSize = Math.max(1, Math.min(8, profile.getPaletteSize()));
        float[] palette = new float[paletteSize * 3];
        if (profile.getPaletteSize() > 0) {
            for (int i = 0; i < paletteSize; i++) {
                palette[i * 3] = profile.getPaletteR(i);
                palette[i * 3 + 1] = profile.getPaletteG(i);
                palette[i * 3 + 2] = profile.getPaletteB(i);
            }
        } else {
            palette[0] = profile.getRed();
            palette[1] = profile.getGreen();
            palette[2] = profile.getBlue();
        }

        return new Style(profile.getRed(), profile.getGreen(), profile.getBlue(),
                profile.getSecondaryRed(), profile.getSecondaryGreen(), profile.getSecondaryBlue(),
                profile.getColorMode().shaderValue, profile.getSpeed(),
                palette, paletteSize,
                scaledWidth(profile.getWorldOutlinePixelWidth()), profile.getWorldOutlineOpacity(), 0.10F,
                false);
    }

    /**
     * 旧的 {@code outlineShaderKey} 现在只决定颜色怎么算，不再对应独立的着色器 /
     * RenderType —— 三条"描边着色器"收敛成一个几何算法 + 一个颜色模式。
     */
    private static float colorModeForShaderKey(@Nullable String key) {
        if (key == null) return 0F;
        return switch (key) {
            case SplendidingShaders.KEY_GRADIENT -> 1F;
            case SplendidingShaders.KEY_WARP_FBM -> 2F;
            default -> 0F;
        };
    }

    // ──────────────────────────────────────────────────────────────
    //  第一步：剪影遮罩
    // ──────────────────────────────────────────────────────────────

    private static void ensureMaskTarget(RenderTarget main) {
        if (maskTarget == null) {
            // useDepth=false：遮罩自己的深度从没用过 —— 要么挂场景深度，
            // 要么干脆没有深度附件（等于"不遮挡"）。这样也就绝不会在
            // clear 的时候误伤共享的场景深度缓冲。
            maskTarget = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            clearMaskTarget();
        } else if (maskTarget.width != main.width || maskTarget.height != main.height) {
            maskTarget.resize(main.width, main.height, Minecraft.ON_OSX);
            clearMaskTarget();
        }
    }

    /** 底色全清一遍。建立"遮罩在批次之外一定是干净的"这条不变量。 */
    private static void clearMaskTarget() {
        maskHasContent = false;
        if (maskTarget == null) return;
        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        maskTarget.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.clearColor(0F, 0F, 0F, 0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
    }

    /**
     * 把队列里所有物品的剪影画进遮罩。
     *
     * <p>矩阵要逐条重放：这里（{@code renderLevel} TAIL）已经不是物品渲染当时的状态了，
     * 而遮罩必须用物品当时的投影 + 模型变换才能落在和画面一致的像素上。</p>
     */
    /**
     * 第一阶段：把这一帧攒下的剪影画进遮罩。
     *
     * <p>由 {@code OutlineReplayMixin} 在 {@code GameRenderer.renderLevel} 首次读
     * {@code renderHand} 字段那一刻调用。选这个点是因为它是**唯一**同时满足下面三条的位置：</p>
     *
     * <ol>
     *   <li>世界和实体都画完了 —— 场景深度是完整的；</li>
     *   <li>vanilla 还<b>没有</b> {@code RenderSystem.clear(GL_DEPTH_BUFFER_BIT)} ——
     *       再晚一步（清完深度、画一手之后）一手视图下深度就一片空了；</li>
     *   <li>不是实体通道中间 —— 这个分界点上做 FBO / viewport / scissor 操作不会
     *       打扰任何还没 flush 的批次。</li>
     * </ol>
     *
     * <p>遮挡判定靠硬件深度测试：把 Entry 记下的那份深度附件临时挂到遮罩 FBO 上。</p>
     */
    public static void captureSceneSilhouettes() {
        if (ShaderPackDetector.isShadowPass()) {
            PENDING.clear();
            READY.clear();
            maskHasContent = false;
            return;
        }
        // 兜底：上一帧要是没走到合成（例如世界还没加载完），遮罩里可能留着上帧的剪影。
        // 不清的话它会被当成本帧的内容参与合成，看起来就是残留的鬼影。
        if (maskHasContent) {
            READY.clear();
            clearMaskTarget();
        }
        drainCapture();
    }

    /**
     * 第二阶段：补齐剩下没捕获的剪影，然后把环合成到主 RT。
     * 由 {@code OutlineReplayMixin} 在 {@code renderLevel} TAIL 调用。
     *
     * <p>这里要再调一次 {@code drainCapture()}：<b>一手物品是在 {@code renderItemInHand}
     * 里渲染的，晚于第一阶段那个捕获点</b>，所以它的条目到现在才存在。补捕获时它
     * {@code occlude=false}（一手物品不被任何东西遮挡），因此不会去借那份已经被 vanilla
     * 清空的场景深度 —— 不带深度渲染，剪影完整，正好。</p>
     */
    public static void composite() {
        if (ShaderPackDetector.isShadowPass()) {
            PENDING.clear();
            READY.clear();
            maskHasContent = false;
            return;
        }
        drainCapture();
        drainComposite();
    }

    /** GUI 专用：GUI 是 renderLevel 之后独立的一趟，就地做完捕获 + 合成。 */
    private static void drain() {
        drainCapture();
        drainComposite();
    }

    private static void drainCapture() {
        if (PENDING.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        ShaderInstance maskShader = SplendidingShaders.outlineMaskShader;
        RenderType maskType = SplendidingShaders.getOutlineMaskRenderType();
        if (main == null || maskShader == null || maskType == null) {
            PENDING.clear();
            return;
        }
        ensureMaskTarget(main);
        captureAll(maskShader, maskType);
        // 剪影已经进遮罩了，转成"待合成"。留在 PENDING 里会害得下一批把同一件物品重画一遍。
        READY.addAll(PENDING);
        PENDING.clear();
        maskHasContent = true;
    }

    private static void drainComposite() {
        if (READY.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        ShaderInstance ringShader = SplendidingShaders.outlineRingShader;
        if (main == null || ringShader == null) {
            READY.clear();
            maskHasContent = false;
            return;
        }
        if (maskTarget == null) {
            READY.clear();
            maskHasContent = false;
            return;
        }
        compositeAll(ringShader, main);
    }

    // ──────────────────────────────────────────────────────────────
    //  遮挡：把场景深度临时挂成遮罩 FBO 的深度附件
    // ──────────────────────────────────────────────────────────────

    /**
     * 纯只读地查一下「当前绑定的 framebuffer 的深度附件是谁」。
     *
     * <p>在 {@code submit}（实体通道中间）里调用，所以这里<b>绝对不能</b>改任何 GL 状态；
     * 只是两个 {@code glGetFramebufferAttachmentParameteri}。</p>
     */
    private static DepthSource queryBoundDepthSource() {
        try {
            if (GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0) return new DepthSource(DepthSource.NONE, 0);
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE && type != GL30.GL_RENDERBUFFER) {
                // 有些光影包把深度挂在 GL_DEPTH_STENCIL_ATTACHMENT 上，再问一次。
                type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            }
            if (type != GL11.GL_TEXTURE && type != GL30.GL_RENDERBUFFER) return new DepthSource(DepthSource.NONE, 0);
            int name = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            return new DepthSource(type, name);
        } catch (Throwable t) {
            return new DepthSource(DepthSource.NONE, 0);
        }
    }

    /**
     * 把队列里第一条需要遮挡的 Entry 记下的深度附件挂到遮罩 FBO 上。
     *
     * <p>尺寸不一致（光影包 resolution scale 与主 RT 不同）会让 FBO 不完整，
     * 那时就摘掉、退化成不遮挡 —— 不会错误剔除剪影，也不会崩。</p>
     */
    private static boolean attachSceneDepth() {
        if (maskTarget == null) return false;

        DepthSource source = new DepthSource(DepthSource.NONE, 0);
        for (Entry entry : PENDING) {
            if (!entry.occlude()) continue;
            DepthSource candidate = new DepthSource(entry.depthType(), entry.depthName());
            if (candidate.present()) {
                source = candidate;
                break;
            }
        }
        if (!source.present()) {
            debugOnce("depthOff", "没有可用的深度附件，剪影不做遮挡判定");
            return false;
        }

        maskTarget.bindWrite(false);
        if (source.type() == GL11.GL_TEXTURE) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, source.name(), 0);
        } else {
            GlStateManager._glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, source.name());
        }

        boolean ok = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        if (!ok) {
            // 换个挂法再试一次：有些影包用 DEPTH_STENCIL 附件。
            if (source.type() == GL11.GL_TEXTURE) {
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, source.name(), 0);
            } else {
                GlStateManager._glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                        GL30.GL_RENDERBUFFER, source.name());
            }
            ok = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }

        if (!ok) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }

        debugOnce(ok ? "depthOn" : "depthAttachFailed",
                ok ? "剪影遮挡判定开启（把场景深度临时挂成遮罩 FBO 的深度附件，走硬件深度测试；"
                        + "type=" + (source.type() == GL11.GL_TEXTURE ? "texture" : "renderbuffer")
                        + " name=" + source.name() + "）"
                   : "深度附件挂不上（FBO 不完整，多半是尺寸不一致），剪影不做遮挡判定");
        return ok;
    }

    /** 摘掉借来的深度附件，让遮罩 FBO 回到"只有颜色附件"。 */
    private static void detachSceneDepth() {
        if (maskTarget == null) return;
        maskTarget.bindWrite(false);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL11.GL_TEXTURE_2D, 0, 0);
    }

    private static void captureAll(ShaderInstance shader, RenderType maskType) {
        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        // 把场景深度临时挂到遮罩 FBO 上，剪影就由硬件深度测试来剔除被挡住的部分。
        // 挂的是"这件物品当时画进去的那个 FBO"的深度附件 —— 无光影包时是主 RT 的，
        // Oculus 激活时是它 GBuffer 的。纹理 / renderbuffer 都支持。
        boolean depthOk = attachSceneDepth();

        maskTarget.bindWrite(true);
        RenderSystem.disableScissor();
        // 只在这一帧的第一批捕获前清整张遮罩。第二批（TAIL 补的一手）要叠上去，
        // 清掉的话第一批的剪影就没了。
        if (!maskHasContent) {
            RenderSystem.clearColor(0F, 0F, 0F, 0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        // 深度比较要有容差：光影包普遍会给投影加亚像素抖动（TAA），而我们是拿
        // "同一套矩阵重放"去和它写进 GBuffer 的深度比 —— 两者不可能逐位相等。
        // 不做补偿的话，LEQUAL 会随机剔掉一层碎斑，而且抖动每帧都在变，
        // 表现就是描边一直在闪。
        //
        // glPolygonOffset 负数 = 把片元往相机方向拉一点，等于给深度测试加容差。
        // 和项目里 cosmic 延迟回放用的是同一招（见 CosmicRenderType.SHADER_LAYER_DEPTH_BIAS）。
        RenderSystem.polygonOffset(-1.0F, -64.0F);
        RenderSystem.enablePolygonOffset();
        try {
            for (Entry entry : PENDING) {
                RenderSystem.setProjectionMatrix(entry.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
                modelViewStack.pushPose();
                modelViewStack.setIdentity();
                modelViewStack.mulPoseMatrix(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                try {
                    captureOne(shader, maskType, entry);
                } finally {
                    modelViewStack.popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);

            detachSceneDepth();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            RenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
        }
    }

    private static void captureOne(ShaderInstance shader, RenderType maskType, Entry entry) {
        applyMaskUniforms(shader, entry);

        // 兼容旧接口：仍然给物品一次调 uniform 的机会（传的是剪影遮罩着色器）。
        // 它跑在标准 uniform 之后，所以物品自己设的值优先。
        if (entry.custom() != null) {
            entry.custom().configureOutlineShader(shader);
        }

        // 状态全部交给 RenderType（LEQUAL 深度 + 不写深度 + 不混合 + 不剔面），
        // 所以这里不手改 GL —— 手改一定会在批次 flush 时被 setupRenderState 覆盖掉，
        // 那正是旧实现"想要的混合模式和深度测试从来没生效过"的原因。
        // FBO 没有深度附件时深度测试恒通过，正好省掉一次 depthFunc 切换。
        if (maskBuffers == null) {
            maskBuffers = new RenderBuffers();
        }
        PoseStack.Pose pose = SCRATCH_POSE.last();
        pose.pose().set(entry.pose());
        pose.normal().set(entry.normal());

        MultiBufferSource.BufferSource source = maskBuffers.bufferSource();
        VertexConsumer consumer = source.getBuffer(maskType);
        for (BakedQuad quad : entry.model().getQuads(null, null, RandomSource.create(42L))) {
            consumer.putBulkData(pose, quad, 1F, 1F, 1F, entry.light(), entry.overlay());
        }
        source.endBatch(maskType);
    }

    private static void applyMaskUniforms(ShaderInstance shader, Entry entry) {
        setUniform(shader, "MaskColor", entry.red(), entry.green(), entry.blue(), 1F);
        setUniform(shader, "SecondaryColor", entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue(), 1F);
        setFloat(shader, "ColorMode", entry.colorMode());
        setFloat(shader, "ColorScrollSpeed", entry.scrollSpeed());
        setFloat(shader, "Time", resolveAnimationTime());
        setFloat(shader, "PaletteSize", entry.paletteSize());
        setFloat(shader, "AlphaCutoff", entry.alphaTest());

        // 渐变的频率/速度/暗端：GUI 单独一套。
        //
        // 描边颜色是按模型坐标算的，频率 18/12 —— 一手/世界里物品占屏大，那是柔和的宽带；
        // 物品栏图标只有 16px，同一个波长就变成「一圈描边过 3~4 次黑白」的粗黑边，
        // 反而把渐变盖住了。所以 GUI 把波段放宽、流速放慢、暗端抬起来。
        SplendidingConfig config = config();
        boolean gui = entry.gui();
        float freqScale = gui ? sanitize(config == null ? 1F : config.guiOutlineGradientScale, 1F) : 1F;
        float speedScale = gui ? sanitize(config == null ? 1F : config.guiOutlineSpeedScale, 1F) : 1F;
        float floor = gui ? Mth.clamp(config == null ? 0F : config.guiOutlineMinMix, 0F, 1F) : 0F;
        setUniform2(shader, "ColorFreq", 18F * freqScale, 12F * freqScale);
        setFloat(shader, "ColorSpeed", 4F * speedScale);
        setFloat(shader, "ColorFloor", floor);

        for (int i = 0; i < 8; i++) {
            int index = Math.max(0, Math.min(i, entry.paletteSize() - 1));
            float r = entry.palette()[index * 3];
            float g = entry.palette()[index * 3 + 1];
            float b = entry.palette()[index * 3 + 2];
            setUniform(shader, "PaletteColor" + i, r, g, b, 1F);
        }
    }

    /** 配置值兜底：非正数或 NaN/Inf 都退回 {@code fallback}。 */
    private static float sanitize(float value, float fallback) {
        return value > 0F && Float.isFinite(value) ? value : fallback;
    }

    private static float resolveAnimationTime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 0F;
        long wrapped = Math.floorMod(minecraft.level.getGameTime(), 240000L);
        return (wrapped + minecraft.getFrameTime()) * 0.05F;
    }

    // ──────────────────────────────────────────────────────────────
    //  第二步：环形合成
    // ──────────────────────────────────────────────────────────────

    private static void compositeAll(ShaderInstance ringShader, RenderTarget main) {
        if (READY.isEmpty()) {
            maskHasContent = false;
            return;
        }
        if (maskTarget == null) {
            READY.clear();
            maskHasContent = false;
            return;
        }

        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);

        main.bindWrite(true);

        // 描边不参与深度：遮挡关系已经在遮罩那一步用场景深度判定完了。
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        maskTarget.setFilterMode(GL11.GL_LINEAR);
        if (ringShader.getUniform("ScreenSize") != null) {
            ringShader.getUniform("ScreenSize").set((float) main.width, (float) main.height);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        debugOnce("composite", "合成 " + READY.size() + " 个描边条目 → 主 RT "
                + main.width + "x" + main.height
                + "，之前绑定的 FBO=" + previousFramebuffer
                + "（主 RT 的 FBO 已重新绑定）");

        for (Entry entry : READY) {
            Rect rect = entry.rect();
            minX = Math.min(minX, rect.x());
            minY = Math.min(minY, rect.y());
            maxX = Math.max(maxX, rect.x() + rect.width());
            maxY = Math.max(maxY, rect.y() + rect.height());

            // 每次画之前重设采样器：ShaderInstance.clear() 会解除采样绑定，
            // 放在循环外只在第一件物品上是可靠的。
            ringShader.setSampler("MaskSampler", maskTarget.getColorTextureId());
            setFloat(ringShader, "OutlineWidth", entry.pixelWidth());
            setFloat(ringShader, "Opacity", entry.opacity());
            setFloat(ringShader, "AlphaTest", entry.alphaTest());

            // 每个物品只在自己的屏幕包围盒里跑环形采样。全屏 48 次采样
            // 在 1080p 上是两百万像素 × 48 次取纹理，绝对跑不动。
            RenderSystem.enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
            if (entry.additive()) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            drawFullscreenQuad(ringShader);
            RenderSystem.disableScissor();
            RenderSystem.defaultBlendFunc();
        }

        PENDING.clear();
        READY.clear();
        maskHasContent = false;

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 合成完就把遮罩上刚用过的区域清掉。这样下一批（下一件物品 / 下一帧）
        // 开始时遮罩一定是干净的：既不会串色，也不用每件物品都全屏 clear 一次。
        int unionWidth = maxX - minX;
        int unionHeight = maxY - minY;
        if (unionWidth > 0 && unionHeight > 0) {
            maskTarget.bindWrite(true);
            // 并集够小就只清这一块，够大就直接全屏清（省掉一次 scissor 的意义）。
            if ((long) unionWidth * unionHeight * 2 < (long) maskTarget.width * maskTarget.height) {
                RenderSystem.enableScissor(minX, minY, unionWidth, unionHeight);
            }
            RenderSystem.clearColor(0F, 0F, 0F, 0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableScissor();
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        RenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
    }

    private static void drawFullscreenQuad(ShaderInstance shader) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1D, -1D, 0D).uv(0F, 0F).endVertex();
        builder.vertex(1D, -1D, 0D).uv(1F, 0F).endVertex();
        builder.vertex(1D, 1D, 0D).uv(1F, 1F).endVertex();
        builder.vertex(-1D, 1D, 0D).uv(0F, 1F).endVertex();
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    // ──────────────────────────────────────────────────────────────
    //  工具
    // ──────────────────────────────────────────────────────────────

    /**
     * 当前绑定的 draw framebuffer 的深度附件纹理 id。取不到就返回 0。
     *
     * <p>不写死"主 RT 的深度"是因为光影包激活时正在绑定的是它的 GBuffer，
     * 那才是有意义的场景深度。</p>
     */
    private static int resolveSceneDepthTexture() {
        try {
            int bound = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (bound == 0) {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                return main != null ? main.getDepthTextureId() : 0;
            }
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE) return 0;
            return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 把物品模型包围盒投到屏幕上，得到合成时用的 scissor 矩形。
     *
     * <p>用的是与物品渲染完全相同的那两份矩阵（{@code RenderSystem} 里当前生效的
     * modelview / projection），所以 GUI 的正交投影和世界的透视投影都能正确覆盖。</p>
     */
    @Nullable
    private static Rect computeScreenRect(PoseStack.Pose pose, Matrix4f modelView, Matrix4f projection,
                                          List<BakedQuad> quads, RenderTarget main, float pixelWidth) {
        Matrix4f transform = new Matrix4f(projection).mul(modelView).mul(pose.pose());

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean any = false;

        Vector4f corner = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            if (stride < 3) continue;
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                corner.set(Float.intBitsToFloat(vertices[base]),
                        Float.intBitsToFloat(vertices[base + 1]),
                        Float.intBitsToFloat(vertices[base + 2]),
                        1F);
                transform.transform(corner);
                float w = corner.w;
                if (!Float.isFinite(w) || Math.abs(w) < 1.0E-4F) continue;
                float nx = corner.x / w;
                float ny = corner.y / w;
                if (!Float.isFinite(nx) || !Float.isFinite(ny)) continue;
                minX = Math.min(minX, nx);
                maxX = Math.max(maxX, nx);
                minY = Math.min(minY, ny);
                maxY = Math.max(maxY, ny);
                any = true;
            }
        }
        if (!any) return null;

        int x0 = Mth.floor((Mth.clamp(minX, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.width);
        int y0 = Mth.floor((Mth.clamp(minY, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.height);
        int x1 = Mth.ceil((Mth.clamp(maxX, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.width);
        int y1 = Mth.ceil((Mth.clamp(maxY, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.height);

        // 外扩一点：环形采样要往外取 radius 那么远，再加上抗锯齿的余量。
        int pad = Mth.ceil(pixelWidth) + 2;
        x0 -= pad;
        y0 -= pad;
        x1 += pad;
        y1 += pad;

        int left = Mth.clamp(Math.min(x0, x1), 0, main.width);
        int bottom = Mth.clamp(Math.min(y0, y1), 0, main.height);
        int right = Mth.clamp(Math.max(x0, x1), 0, main.width);
        int top = Mth.clamp(Math.max(y0, y1), 0, main.height);
        int width = right - left;
        int height = top - bottom;
        if (width <= 0 || height <= 0) return null;
        return new Rect(left, bottom, width, height);
    }

    /**
     * 配置里的着色器总开关。配置未就绪时按"开启"处理 ——
     * 读不到配置不应该表现为"描边突然消失"。
     */
    private static boolean shaderRenderingEnabled() {
        SplendidingConfig config = config();
        return config == null || config.use_shader;
    }

    /**
     * 把物品自己声明的像素宽度过一遍全局倍率。
     *
     * <p>这样"整体加厚/变细"是一次配置改动，不用改每个物品、也不用重新编译。
     * 下限 0.5 像素保证倍率再小也还看得见边。</p>
     */
    private static float scaledWidth(float base) {
        SplendidingConfig config = config();
        float scale = config == null ? 1.0F : config.outlineWidthScale;
        if (!(scale > 0F) || !Float.isFinite(scale)) scale = 1.0F;
        return Mth.clamp(base * scale, 0.5F, 24.0F);
    }

    @Nullable
    private static SplendidingConfig config() {
        try {
            ConfigHolder<SplendidingConfig> holder = ConfigHelper.configHolder;
            return holder == null ? null : holder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  诊断
    // ──────────────────────────────────────────────────────────────
    //
    // 光影兼容这条链路有太多"静默失败"的环节：绑定目标不对、深度附件挂不上、
    // 剪影被深度剔干净…… 全都是"结果就是没有描边"，光看画面无从判断。
    // 所以每个环节各留一条一次性日志。每个 key 只打一次，不会刷屏；
    // 排查的时候直接看 run/logs/latest.log 里的 [ItemOutline] 段落即可。

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void debugOnce(String key, String message) {
        if (!LOGGED.add(key)) return;
        ModUtils.LOGGER.info("[ItemOutline] {}", message);
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }

    private static void setUniform(ShaderInstance shader, String name, float x, float y, float z, float w) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y, z, w);
    }

    private static void setUniform2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y);
    }
}
