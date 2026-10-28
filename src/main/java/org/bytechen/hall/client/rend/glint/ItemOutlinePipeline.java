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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.api.ItemFxParams;
import org.bytechen.hall.api.ItemFxProvider;
import org.bytechen.hall.client.fx.ItemFxFieldPool;
import org.bytechen.hall.client.fx.ItemFxFieldSpec;
import org.bytechen.hall.client.fx.ItemFxFormat;
import org.bytechen.hall.client.fx.ItemFxRegistry;
import org.bytechen.hall.client.fx.effects.ArcFxEffect;
import org.bytechen.hall.client.fx.effects.FractureFxEffect;
import org.bytechen.hall.client.fx.effects.FlameFxEffect;
import org.bytechen.hall.client.fx.effects.OutlineFxEffect;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.bytechen.hall.utils.ModUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li><b>宽度靠像素半径</b>，不是模型缩放系数。近处（物品在屏幕上够大）保持恒定像素宽，
 *       远处按「物品屏幕长边 × 比例」收细 —— 否则远处的环相对物品过粗，会糊成一圈外扩的厚边。
 *       见 {@link #adaptPixelWidth}。</li>
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
                         /**
                          * 物品投影到屏幕上的<b>浮点像素框</b>（未外扩、未裁到 RT）。
                          * fire_2 用它当坐标系原点与尺寸 —— {@link #rect} 是给 scissor 用的，
                          * 带 pad（描边宽度 + 光雾 52px 外扩），拿它定火焰底部会整体偏移。
                          */
                         PnxelBox itemBox,
                         Matrix4f pose, Matrix3f normal,
                         Matrix4f modelView, Matrix4f projection,
                         BakedModel model,
                         @Nullable ICustomOutline custom,
                         int light, int overlay,
                         float red, float green, float blue,
                         float secondaryRed, float secondaryGreen, float secondaryBlue,
                         float colorMode, float scrollSpeed,
                         float[] palette, int paletteSize,
                         float pixelWidth, float opacity, float alphaTeet,
                         boolean additive, boolean gui, boolean occlude,
                         int depthType, int depthName,
                         /** 这件物品是否被声明为"会燃烧"（决定要不要把它画进火焰燃料场）。 */
                         boolean flameFuel,
                         /** 电弧强度（0 = 不画电弧）；见 outline_ring.fsh 末尾的"电弧"一节。 */
                         float arcStrength,
                         /** 是否挂"裂纹"效果（框架效果 fracture）。 */
                         boolean fracture,
                         /** 裂纹种子：让每件物品的碎裂图案不同。 */
                         float fractureSeed,
                         /** 物品局部基（屏幕 UV ← 物品模型坐标）；null = 拿不到，退回屏幕坐标。 */
                         float[] itemUv,
                         /**
                          * 贴图里<b>不透明内容</b>在这张方片上的归一化包围盒 {u0, v0, w, h}（v 向上）。
                          * 只有 fire_2 用：参考 shader 的火要从"物品可见底边"烧起来，而物品方片
                          * 往往带透明留白（法力板的贴图就是中间一条）。null = 退回整张方片。
                          */
                         @Nullable float[] spriteBox,
                         /**
                          * 是否挂「火焰光环」（fire_2）：轮廓当火源、火在轮廓外一片放大区域里烧。
                          * 与描边不是一回事 —— 它的绘制区比物品大数倍（见 {@link #fireReachPx}）。
                          */
                         boolean fire,
                         /** 火焰光环的绘制半径（屏幕像素）；0 = 不是火。 */
                         float fireReachPx,
                         /** 第一人称手持：体积火焰要到 renderLevel TAIL 才画（那时深度里是手）。 */
                         boolean firstPerson,
                         /**
                          * 物品在<b>视图空间</b>的 AABB {minX,minY,minZ,maxX,maxY,maxZ}。
                          * 体积火焰用它算物品在世界里的位置与尺寸；null = 拿不到（不画火）。
                          */
                         @Nullable float[] viewBox) { }

    /** 屏幕像素包围盒（GL 约定：原点左下）。 */
    private record Rect(int x, int y, int width, int height) { }

    /**
     * 物品包围盒投到屏幕上的<b>浮点像素</b>框（GL 约定：原点左下）。
     *
     * <p>刻意不裁到 RT、也不外扩 pad：合成用的 scissor 矩形要外扩，而这个框是它的输入。
     * 注意它<b>不能</b>用来判断「物品在屏幕上有多大」—— 相机贴脸时顶点会落到近平面附近，
     * 除以 w 之后 NDC 飞到天上并被 {@link #MAX_NDC} 夹成整屏（实测出现过
     * {@code rect=2560x1600 apparentPx=3456}）。尺寸基准走 {@link #apparentSizePx}。</p>
     */
    private record PnxelBox(float x0, float y0, float x1, float y1) { }

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

    /** 最近一次从场池拿到的遮罩尺寸，用来判断"池把图重建了"。 */
    private static int maskWidth = -1;
    private static int maskHeight = -1;

    /**
     * 光雾用的两级降采样模糊图（1/2、1/4）。每级两张，做 X/Y 分离模糊的乒乓。
     *
     * <p>只有 {@link #buildHaloChain} 会碰它们，尺寸跟随主 RT。雾是低频的，降到 1/4 后
     * 面积只剩 1/16，而且整屏一次，同屏所有描边物品共用同一份。</p>
     */
    @Nullable
    private static TextureTarget haloHalfA;
    @Nullable
    private static TextureTarget haloHalfB;
    @Nullable
    private static TextureTarget haloQuarterA;
    @Nullable
    private static TextureTarget haloQuarterB;

    /**
     * 光雾一次能扩散多远（屏幕像素）。
     *
     * <p>= 四级模糊的核半径之和：H(2 源纹素 × 4) + V(4 半纹素) + H(2 半纹素 × 4) + V(4 四分之一纹素)
     * ≈ 8 + 8 + 16 + 16。scissor 矩形必须按它外扩，否则雾会被自己的剪裁框切出一条直边。</p>
     */
    private static final int AURA_REACH_PX = 52;

    /** 本帧平流是否已推过：世界批在捕获点推，手持批在 TAIL 复用（一帧只推一次）。 */
    private static boolean flameSteppedThisFrame;
    /** 本帧的 dt：捕获点与 TAIL 两批合成必须用**同一个**值（各算一次会把时钟推快）。 */
    private static float flameDtThisFrame = 1F / 60F;

    /**
     * 贴图不透明内容的归一化包围盒缓存（key = sprite 名）。
     *
     * <p>fire_2 要按"物品可见剪影"取坐标，而物品方片通常带透明留白（法力板就是中间一条）。
     * 扫描一次 16×16 的 alpha 只要几百次读取，但它在每帧的渲染路径上，所以按 sprite 缓存。</p>
     */
    private static final Map<ResourceLocation, float[]> OPAQUE_BOX_CACHE = new ConcurrentHashMap<>();

    /**
     * 电弧算子的绑定 id（见 {@code ICustomOutline} 之外的那条效果绑定路径）。
     *
     * <p>它没有独立的 {@code ItemFxEffect} 类，因为电弧是<b>无状态</b>的：不需要自己的场、
     * 不需要 ping-pong，只是环形合成里多加一层算子（共用剪影遮罩与光雾场）。
     * 等它需要自己的半分辨率暂存场时，再提升成一个正式的 effect。</p>
     */
    public static final String ARC_EFFECT_ID = "arc";

    /** 裂纹效果的绑定 id（见 {@link FractureFxEffect}：Voronoi 单元边界，纯几何）。 */
    public static final String FRACTURE_EFFECT_ID = "fracture";

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

        PnxelBox box = projectItemBox(pose, modelView, projection, quads, main);
        if (box == null) return;

        // 非 GUI：按物品在屏幕上的占比收细环宽。
        //
        // GUI 不做：它是正交投影，「距离」没有意义，而且图标宽度已经由 guiOutlineWidthScale
        // 单独调过一轮。一手 / 三手 / 手持物品也不用担心被误伤 —— 它们占屏极大，
        // 占比远高于上限比例，算出来就是 style.pixelWidth()，逐位不变。
        float apparentPx = -1F;
        if (!gui) {
            apparentPx = apparentSizePx(quads, pose.pose(), modelView, projection, main);
            float basePx = pixelWidth;
            pixelWidth = adaptPixelWidth(style.widthMode(), basePx, apparentPx);
            if (pixelWidth < basePx - 0.01F) {
                // 只在"真的被收细了"时打，且每秒最多一条 —— 这条日志是距离自适应的唯一证据来源。
                logScaledWidth(context, stack, apparentPx, basePx, pixelWidth);
            }
        }

        // 物品局部基（屏幕 UV ← 物品模型坐标）：所有"贴在物品表面上的图案"都要用它，
        // 否则镜头一动图案就会从物品上滑过去（裂纹已经实测到这个问题）。
        // 只在真的挂了这类效果时才算 —— 它是三次矩阵乘，没必要给每件物品都付。


        Rect rect = rectFromBox(box, main, pixelWidth);
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

        // 框架效果绑定：物品侧声明"我要挂哪些效果"。目前只接了火焰一条 ——
        // 显式声明的意义在于：燃料场是共享的，只有声明过的物品才会被画进燃料，
        // 否则同屏所有描边物品都会被一起点着。
        //
        // ★ GUI 一律排除：物品栏图标只有 16 像素，而火焰高度是按屏幕高算的
        //   （默认 0.14~0.5 屏高）—— 图标上冒出一屏高的火，而且会跟着图标位置到处跑。
        //   体积类效果本来就不属于 GUI，这条在管线里兜死，不指望物品自己记得。
        boolean flameFuel = false;
        float arcStrength = 0F;
        boolean fracture = false;
        float fractureSeed = 0F;
        if (!gui && stack.getItem() instanceof ItemFxProvider provider) {
            for (ItemFxProvider.ItemFxBinding binding : provider.itemFxLayers(stack, context)) {
                ItemFxParams params = binding.params();
                if (FRACTURE_EFFECT_ID.equals(binding.effectId())) {
                    // 裂纹：种子取自物品显示名 —— 每把剑的碎裂图案稳定且互不相同
                    fracture = true;
                    fractureSeed = Math.abs(stack.getHoverName().getString().hashCode() % 97);
                } else if (FlameFxEffect.ID.equals(binding.effectId())) {
                    flameFuel = true;
                    // 染色：让物品用自己的颜色决定冷尾与主体（核心仍推向白）
                    if (params.has("tintR") || params.has("tintG") || params.has("tintB")) {
                        FlameFxEffect.setTint(params.getFloat("tintR", 1.0F),
                                params.getFloat("tintG", 0.42F),
                                params.getFloat("tintB", 0.12F));
                    }
                } else if (ARC_EFFECT_ID.equals(binding.effectId())) {
                    // 电弧是 outline 合成里的一层算子（无状态、共用剪影/光雾场），
                    // 所以这里只取一个强度，跟着条目走到环形合成那一趟去设 uniform。
                    arcStrength = params.getFloat("strength", 1.0F);
                }
            }
        }
        if (flameFuel) {
            FlameFxEffect.markBurning();
        }
        // 诊断：火焰是"按名字"触发的，而名字这条链最容易出问题（铁砧/输入法/大小写）。
        // 每个不同的显示名只打一行，一眼就能看出游戏到底读到什么、有没有点火。
        if (!gui && stack.getItem() instanceof ICustomOutline) {
            String hover = stack.getHoverName().getString();
            if (LOGGED.add("name:" + hover)) {
                ModUtils.LOGGER.info("[ItemOutlnne] 物品名=\"{}\" → 火焰={} 电弧={}",
                        hover, flameFuel, arcStrength > 0F);
            }
        }

        // 物品局部基（屏幕 UV ← 物品模型坐标）：贴在物品表面上的图案必须用它，
        // 否则镜头一动图案就会从物品上滑过去（裂纹已实测到）。只给真的挂了这类效果的物品算。
        float[] uvBasis = fracture ? itemUvBasis(pose, modelView, projection) : null;

        // fire_2 需要"可见剪影"的包围盒来定火焰底部；别的颜色模式不用付这次扫描/查表。
        float[] spriteBox = style.colorMode() == 5F ? spriteOpaqueBox(quads) : null;

        // 火焰光环：绘制区比物品大数倍（"覆盖物品几倍"），半径按物品的表观高度给，
        // 并夹住上下限 —— 太小的物品（16px 图标）也要有能看的火苗，手持物品也不能铺满屏幕。
        boolean fire = style.colorMode() == 5F;
        float fireReachPx = fire
                ? Mth.clamp(Math.abs(box.y1() - box.y0()) * 2.5F, 40F, 320F)
                : 0F;
        // 体积火焰需要物品在视图空间的 AABB（只给火条目算）。
        float[] viewBox = fire ? viewSpaceBox(pose, modelView, quads) : null;
        if (style.colorMode() == 5F && LOGGED.add("fire2/" + stack.getHoverName().getString())) {
            // 排查"火烧不起来"时先看这一行：ItemRect/ItemOpaque 不对的话，火会锚在空气上。
            // 用 LOGGER 的惰性参数，正常帧不拼字符串。
            ModUtils.LOGGER.info("[ItemOutlnne] fire_2 物品名=\"{}\" itemBox={},{} {}x{} spriteOpaque={}",
                    stack.getHoverName().getString(),
                    box.x0(), box.y0(), box.x1() - box.x0(), box.y1() - box.y0(),
                    spriteBox == null ? "null(整张方片)" : java.util.Arrays.toString(spriteBox));
        }

        PENDING.add(new Entry(rect, box,
                new Matrix4f(pose.pose()), new Matrix3f(pose.normal()),
                modelView, projection,
                model,
                stack.getItem() instanceof ICustomOutline c ? c : null,
                light, overlay,
                style.red(), style.green(), style.blue(),
                style.secondaryRed(), style.secondaryGreen(), style.secondaryBlue(),
                style.colorMode(), style.scrollSpeed(),
                style.palette(), style.paletteSize(),
                pixelWidth, style.opacity(), style.alphaTeet(),
                style.additive(), gui, occlude, depth.type(), depth.name(), flameFuel, arcStrength,
                fracture, fractureSeed, uvBasis, spriteBox, fire, fireReachPx, firstPerson, viewBox));

        debugOnce("submit/" + context,
                "submit ctx=" + context
                        + " buffer=" + buffer.getClass().getSimpleName()
                        + " gui=" + gui
                        + " occlude=" + occlude
                        + " packActnve=" + ShaderPackDetector.shouldUseShaderPackPipeline()
                        + " boundFbo=" + GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                        + " depthSrc=" + (depth.present()
                                ? (depth.type() == GL11.GL_TEXTURE ? "tex" : "rbo") + "#" + depth.name()
                                : "none")
                        + " quads=" + quads.size()
                        + " rect=" + rect.width() + "x" + rect.height()
                        + " apparentPx=" + (gui ? "n/a" : (int) apparentPx)
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
                         float pixelWidth, ICustomOutline.OutlineWidthMode widthMode,
                         float opacity, float alphaTeet, boolean additive) { }

    /**
     * 决定这个物品在这种上下文下要不要描边、用什么样式。
     *
     * <p>优先级与旧实现一致：{@link ICustomOutline} 接口优先于 {@link GlintRenderManager} 注册。</p>
     */
    @Nullable
    private static Style resolveStyle(ItemStack stack, ItemDisplayContext context) {
        ICustomOutline custom = stack.getItem() instanceof ICustomOutline c ? c : null;
        if (custom != null && custom.outlineEnabled(context)) {
            // 用 *For(ItemStack) 这一组：它允许"同一件物品因为改名/NBT 而表现不同"。
            // 默认实现直接退回不带 stack 的老方法，所以既有武器一行都不用改。
            int argb = custom.outlineColorFor(stack);
            float r = ((argb >> 16) & 0xFF) / 255F;
            float g = ((argb >> 8) & 0xFF) / 255F;
            float b = (argb & 0xFF) / 255F;

            int secondary = custom.outlineSecondaryColorFor(stack);
            boolean haeSecondary = ((secondary >>> 24) & 0xFF) != 0;
            float sr = haeSecondary ? ((secondary >> 16) & 0xFF) / 255F : r;
            float sg = haeSecondary ? ((secondary >> 8) & 0xFF) / 255F : g;
            float sb = haeSecondary ? (secondary & 0xFF) / 255F : b;

            return new Style(r, g, b, sr, sg, sb,
                    colorModeForShaderKey(custom.outlineShaderKeyFor(stack)), 1.0F,
                    new float[]{r, g, b}, 1,
                    scaledWidth(custom.outlinePixelWidth()), custom.outlineWidthMode(),
                    custom.outlineOpacity(), custom.outlineAlphaCutoff(),
                    custom.outlineBlendFor(stack) == ICustomOutline.BlendMode.ADDITIVE);
        }

        GlintEffectProfile profile = GlintRenderManager.getProfile(stack);
        if (profile == null || !profile.isWorldOutlineEnabled() || !profile.shouldRender(context)) return null;

        int paletteSize = Math.max(1, Math.min(8, profile.getPaletteSize()));
        float[] palette = new float[paletteSize * 3];
        if (profile.getPaletteSize() > 0) {
            for (int n = 0; n < paletteSize; n++) {
                palette[n * 3] = profile.getPaletteR(n);
                palette[n * 3 + 1] = profile.getPaletteG(n);
                palette[n * 3 + 2] = profile.getPaletteB(n);
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
                scaledWidth(profile.getWorldOutlinePixelWidth()), ICustomOutline.OutlineWidthMode.DISTANCE,
                profile.getWorldOutlineOpacity(), 0.10F,
                false);
    }

    /**
     * 旧的 {@code outlineShaderKey} 现在只决定颜色怎么算，不再对应独立的着色器 /
     * RenderType —— 几条"描边着色器"收敛成一个几何算法 + 一个颜色模式。
     */
    private static float colorModeForShaderKey(@Nullable String key) {
        if (key == null) return 0F;
        return switch (key) {
            case SplendidingShaders.KEY_GRADIENT -> 1F;
            case SplendidingShaders.KEY_WARP_FBM -> 2F;
            case SplendidingShaders.KEY_FIRE -> 4F;
            case SplendidingShaders.KEY_FIRE_2 -> 5F;
            default -> 0F;
        };
    }

    // ──────────────────────────────────────────────────────────────
    //  fire_2：贴图不透明内容的包围盒
    // ──────────────────────────────────────────────────────────────

    /**
     * 这件物品贴图里<b>不透明内容</b>占方片的哪一块（归一化 0~1，v 向上）。
     *
     * <p>为什么要它：参考 shader 的火是从画面底部烧起来的，而"底部"对描边来说应当是
     * <b>物品可见剪影的底边</b>。物品方片通常带透明留白（火焰法力板就是中间一条金属板），
     * 直接用方片包围盒的话火会从一片空气里烧起来 —— 实测就是整圈只剩烟雾灰。
     * 扫描 alpha 一次后按 sprite 缓存；扫描失败（非 RGBA / 动画帧异常）退回整张方片。</p>
     *
     * @return {u0, v0, w, h}；拿不到返回 {@code null}（着色器按整张方片处理）
     */
    @Nullable
    private static float[] spriteOpaqueBox(List<BakedQuad> quads) {
        if (quads.isEmpty()) return null;
        TextureAtlasSprite sprite = quads.get(0).getSprite();
        if (sprite == null) return null;

        ResourceLocation key;
        try {
            key = sprite.contents().name();
        } catch (Throwable ignored) {
            return null;
        }
        float[] cached = OPAQUE_BOX_CACHE.get(key);
        if (cached != null) return cached;

        float[] box = scanOpaqueBox(sprite);
        if (box != null) OPAQUE_BOX_CACHE.put(key, box);
        return box;
    }

    /** 扫一遍 sprite 的 alpha（mip0），返回可见内容的归一化包围盒；失败返回 null。 */
    @Nullable
    private static float[] scanOpaqueBox(TextureAtlasSprite sprite) {
        int width;
        int height;
        try {
            width = sprite.contents().width();
            height = sprite.contents().height();
        } catch (Throwable ignored) {
            return null;
        }
        if (width <= 0 || height <= 0) return null;

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        try {
            // 阈值和 outlineAlphaCutoff 的默认值（0.1 → 25/255）对齐：描边认为"算物品"的像素，
            // 就是这里认为"可见"的像素，否则火底边会和剪影底边错开一格。
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // NativeImage 按 RGBA 存字节、小端读成 int → alpha 在最高字节（SpriteContents 也是这么判透明的）
                    int alpha = (sprite.getPixelRGBA(0, x, y) >>> 24) & 0xFF;
                    if (alpha < 25) continue;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        } catch (Throwable ignored) {
            // 非 RGBA 格式的贴图会抛；退化成整张方片，不影响别的东西
            return null;
        }
        if (maxX < minX || maxY < minY) return null;

        float u0 = minX / (float) width;
        float uW = (maxX - minX + 1) / (float) width;
        // 图片行 0 是顶；屏幕 y 向上 → 翻过来
        float v0 = 1F - (maxY + 1) / (float) height;
        float vH = (maxY - minY + 1) / (float) height;
        return new float[]{u0, v0, uW, vH};
    }

    // ──────────────────────────────────────────────────────────────
    //  第一步：剪影遮罩
    // ──────────────────────────────────────────────────────────────

    /**
     * 保证遮罩场存在（尺寸跟随主 RT）。
     *
     * <p>S1 起这张图的<b>生死交给 {@link ItemFxFieldPool}</b>：它的尺寸/格式/过滤来自
     * {@link OutlineFxEffect#fields()} 里的声明，而不是写在这里。管线只保留一个"最近一次
     * 拿到的那张图"的引用，于是下面几十处 {@code maskTarget.xxx()} 一行都不用改。</p>
     */
    private static void ensureMaskTarget(RenderTarget main) {
        TextureTarget target = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.full(OutlineFxEffect.FIELD_MASK, ItemFxFormat.RGBA8), main);
        if (target == null) return;
        // 换了一张图、或者尺寸变了 → 内容和"有没有内容"的标记都不能信，清一遍。
        // （原实现是 if (maskTarget == null) / else if (尺寸变了) 两个分支各自 clear，
        //   这里合成一句：判断依据从"我自己的字段"改成"池给的这张图"，语义不变。）
        if (target != maskTarget || target.width != maskWidth || target.height != maskHeight) {
            maskTarget = target;
            maskWidth = target.width;
            maskHeight = target.height;
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
        // 体积火焰要用的世界深度：这里（vanilla 清深度之前）blit 一次到纹理，
        // 具体的火焰绘制与合成全部留到 TAIL，避免在世界渲染中间改 GL 状态。
        captureFireDepth();
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
        // 火焰的帧结算必须**无条件**跑完：
        //   * 没有描边目标时（物品不可见 / 全被剔除）compositeAll 会提前 return，
        //     若把 endFrame 放在里面，burningThisFrame 会一直是 true、燃料场永远不清，
        //     于是火既不会熄灭也会残留上次的密度；
        //   * dt 也在这里算一次，两条路（sim 推进 / 只结算）共用同一个值。
        flameSteppedThisFrame = false;   // 每帧只推一次平流（世界批在捕获点推，手持批在 TAIL 复用）
        float flameDt = FlameFxEffect.nextDeltaSeconds();
        flameDtThisFrame = flameDt;   // 捕获点那批合成要复用同一个 dt
        try {
            drainCapture();
            // 体积火焰：世界批 + 一手都在 TAIL、离屏渲染后加法合成。
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main != null) {
                renderFireVolumes(main);
            }
            drainComposite(true, flameDt);
        } finally {
            FlameFxEffect.endFrame(flameDt);
        }
    }

    /** GUI 专用：GUI 是 renderLevel 之后独立的一趟，就地做完捕获 + 合成。 */
    private static void drain() {
        drainCapture();
        // GUI 不推进火焰 sim（世界那一趟已经推过了；这里再推一次会让火快一倍），
        // 也不做帧结算 —— 结算是世界那一趟 TAIL 的职责，一帧只能结算一次。
        drainComposite(false, 0F);
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
        // 火焰燃料：一帧只在第一批之前清一次（第二批是一手，要叠上去）
        FlameFxEffect.beginFuelFrame(main);
        captureAll(maskShader, maskType, main);
        // 剪影已经进遮罩了，转成"待合成"。留在 PENDING 里会害得下一批把同一件物品重画一遍。
        READY.addAll(PENDING);
        PENDING.clear();
        // 世界物品的火焰必须**在捕获点**就合成：这里主 RT 的深度附件里还是完整的世界深度，
        // 四边形带上物品的深度平面 + LEQUAL，挡在前面的方块才会真的把火挡住（TAIL 时深度已被清）。
        compositeFlameGroup(main, true, flameDtThisFrame, true, config());
        maskHasContent = true;
    }

    /**
     * @param fromTail true = 这一趟是 {@code renderLevel} 的 TAIL（可以推进火焰 sim）；
     *                 false = GUI 那条就地合成路径（不推 sim，否则 GUI 与世界同帧时
     *                 火焰会被推进两次，速度翻倍）。
     */
    private static void drainComposite(boolean fromTail, float flameDt) {
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
        compositeAll(ringShader, main, fromTail, flameDt);
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

        debugOnce(ok ? "depthOn" : "depthAttachFanled",
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

    private static void captureAll(ShaderInstance shader, RenderType maskType, RenderTarget main) {
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
                    captureOne(shader, maskType, entry, main);
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

    private static void captureOne(ShaderInstance shader, RenderType maskType, Entry entry,
                                   @Nullable RenderTarget main) {
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

        // 火焰的燃料：同一批几何、同一个着色器再画一遍，但画进 1/4 分辨率的小图。
        // 只有"声明了火焰"的物品才画 —— 否则同屏其它描边物品会被一起点着（燃料场是共享的）。
        // 复用 maskType 就够了：那个着色器输出 vec4(color, 1)，alpha 天然是"覆盖 = 1 / 空 = 0"，
        // 而 sim 只读它的 .a，颜色写了也不看。
        if (!entry.flameFuel() || main == null) return;
        TextureTarget fuel = FlameFxEffect.fuelTarget(main);
        if (fuel == null) return;

        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        fuel.bindWrite(true);
        MultiBufferSource.BufferSource fuelSource = maskBuffers.bufferSource();
        VertexConsumer fuelConeumer = fuelSource.getBuffer(maskType);
        for (BakedQuad quad : entry.model().getQuads(null, null, RandomSource.create(42L))) {
            fuelConeumer.putBulkData(pose, quad, 1F, 1F, 1F, entry.light(), entry.overlay());
        }
        fuelSource.endBatch(maskType);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    private static void applyMaskUniforms(ShaderInstance shader, Entry entry) {
        setUniform(shader, "MaskColor", entry.red(), entry.green(), entry.blue(), 1F);
        setUniform(shader, "SecondaryColor", entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue(), 1F);
        // fire_2（参考 shader 版火焰）按"该物品的屏幕包围盒"取坐标：参考实现用的是
        // fragCoord/resolution，这里换成 ItemRect，于是 16px 的板和 600px 的手持物
        // 得到同比例的火焰。用**未外扩**的投影框（不是 scissor 的 rect —— 那个带 52px 光雾 pad）。
        PnxelBox box = entry.itemBox();
        setUniform(shader, "ItemRect",
                Math.min(box.x0(), box.x1()), Math.min(box.y0(), box.y1()),
                Math.abs(box.x1() - box.x0()), Math.abs(box.y1() - box.y0()));
        // fire_2 的"可见剪影"包围盒（贴图里不透明内容占方片的哪一块）；拿不到就当整张方片。
        float[] spriteBox = entry.spriteBox();
        if (spriteBox != null) {
            setUniform(shader, "ItemOpaque", spriteBox[0], spriteBox[1], spriteBox[2], spriteBox[3]);
        } else {
            setUniform(shader, "ItemOpaque", 0F, 0F, 1F, 1F);
        }
        setFloat(shader, "ColorMode", entry.colorMode());
        setFloat(shader, "ColorScrollSpeed", entry.scrollSpeed());
        setFloat(shader, "Time", resolveAnimationTime());
        setFloat(shader, "PaletteSize", entry.paletteSize());
        setFloat(shader, "AlphaCutoff", entry.alphaTeet());

        // 渐变的频率/速度/暗端：GUI 单独一套。
        //
        // 描边颜色是按模型坐标算的，频率 18/12 —— 一手/世界里物品占屏大，那是柔和的宽带；
        // 物品栏图标只有 16px，同一个波长就变成「一圈描边过 3~4 次黑白」的粗黑边，
        // 反而把渐变盖住了。所以 GUI 把波段放宽、流速放慢、暗端抬起来。
        SplendidingConfig config = config();
        boolean gui = entry.gui();
        float freqScale = gui ? sanitize(config == null ? 1F : config.guiOutlineGradientScale, 1F) : 1F;
        float epeedScale = gui ? sanitize(config == null ? 1F : config.guiOutlineSpeedScale, 1F) : 1F;
        float floor = gui ? Mth.clamp(config == null ? 0F : config.guiOutlineMinMix, 0F, 1F) : 0F;
        setUniform2(shader, "ColorFreq", 18F * freqScale, 12F * freqScale);
        setFloat(shader, "ColorSpeed", 4F * epeedScale);
        setFloat(shader, "ColorFloor", floor);

        for (int n = 0; n < 8; n++) {
            int index = Math.max(0, Math.min(n, entry.paletteSize() - 1));
            float r = entry.palette()[index * 3];
            float g = entry.palette()[index * 3 + 1];
            float b = entry.palette()[index * 3 + 2];
            setUniform(shader, "PaletteColor" + n, r, g, b, 1F);
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

    /**
     * 合成一批火焰（世界批 / 手持批）。
     *
     * <p><b>为什么必须分两批</b>：vanilla 在画手之前会清掉世界深度，所以 TAIL 那一步没有
     * 可比的深度 —— 这正是火焰一直"穿墙"的根因。世界物品的火因此改到**捕获点**合成：
     * 那里主 RT 的深度附件里还是完整的世界深度，四边形带上物品所在深度平面的 z
     * （由 modelView × projection 算出 NDC z），开 LEQUAL 之后挡在物品前面的方块就会
     * **真的**把火挡住，而羽流高度不受任何限制（不同于之前那张只有 52px 支撑域的
     * "可见性包络"）。手持物品就在最前面、不需要遮挡，留在 TAIL 合成。</p>
     *
     * @param world     true = 世界批（开深度测试、每条目用各自的深度平面）；false = 手持批
     * @param allowStep 是否允许在这一批里推进平流（一帧只推一次）
     */
    private static void compositeFlameGroup(RenderTarget main, boolean world, float dt,
                                            boolean allowStep, @Nullable SplendidingConfig cfg) {
        if (main == null || maskTarget == null) return;

        boolean any = false;
        for (Entry entry : READY) {
            if (entry.flameFuel() && entry.occlude() == world && !entry.gui()) {
                any = true;
                break;
            }
        }
        if (!any) return;

        if (allowStep && FlameFxEffect.wantsSimulation()) {
            int[] box = unionRectOfBurningReady(main, 0);
            int[] padded = unionRectOfBurningReady(main, FlameFxEffect.SCISSOR_PAD_PX);
            float sizeScale = Mth.clamp((box == null ? 0F : box[3]) / 500F, 0.45F, 1.35F);
            float anchorU = padded == null ? 0.5F : (padded[0] + padded[2] * 0.5F) / Math.max(1, main.width);
            float anchorV = padded == null ? 0.5F : (padded[1] + padded[3] * 0.5F) / Math.max(1, main.height);
            FlameFxEffect.step(main, dt, anchorU, anchorV, sizeScale);
            flameSteppedThisFrame = true;
        }

        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableDepthTest();
        if (world) {
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
        RenderSystem.depthMask(false);

        int debugView = cfg == null ? 0 : Mth.clamp(cfg.flameDebugView, 0, 2);
        if (debugView > 0) {
            RenderSystem.disableBlend();
        }

        for (Entry entry : READY) {
            if (!entry.flameFuel() || entry.occlude() != world || entry.gui()) continue;

            Rect r = entry.rect();
            int pad = FlameFxEffect.SCISSOR_PAD_PX;
            int sx = Mth.clamp(r.x() - pad, 0, main.width);
            int sy = Mth.clamp(r.y() - pad, 0, main.height);
            int sr = Mth.clamp(r.x() + r.width() + pad, 0, main.width);
            int st = Mth.clamp(r.y() + r.height() + pad, 0, main.height);
            RenderSystem.enableScissor(sx, sy, Math.max(1, sr - sx), Math.max(1, st - sy));

            float itemTopV = Mth.clamp((r.y() + r.height()) / (float) Math.max(1, main.height) - 0.01F, 0F, 1F);
            float scissorTopV = Mth.clamp(st / (float) Math.max(1, main.height), 0F, 1F);

            float ndcZ = 0F;
            if (world) {
                Vector4f clip = new Vector4f(0F, 0F, 0F, 1F);
                entry.modelView().transform(clip);
                entry.projection().transform(clip);
                if (Math.abs(clip.w) > 1.0E-6F) {
                    ndcZ = clip.z / clip.w;
                }
            }
            FlameFxEffect.composite(main, maskTarget, null, itemTopV, scissorTopV, debugView, ndcZ);
        }

        RenderSystem.disableScissor();
        if (debugView > 0) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.defaultBlendFunc();
    }
    private static void compositeAll(ShaderInstance ringShader, RenderTarget main, boolean fromTail, float flameDt) {
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

        // 光雾：先把遮罩做成两级降采样模糊图（整屏一次，同屏所有物品共用）。
        // 强度为 0 / 模糊着色器没加载 / 模糊图建不起来 → aura=false，下面退化成纯硬环。
        float auraStrength = auraStrength();
        float auraSpread = auraSpread();
        boolean aura = auraStrength > 0F && buildHaloChain(main, auraSpread);
        // buildHaloChain 会把 framebuffer 切到降采样图上再切回来；这里再显式绑一次主 RT，
        // 让"环形合成画进主 RT"这件事不依赖于那个函数有没有正确还原状态。
        // （踩过：少还原一次 → 描边被画进 1/4 的模糊图 → 屏幕上一条描边都没有。）
        main.bindWrite(true);
        float animationTime = resolveAnimationTime();

        // ── 火焰（框架效果 flame）：先推进一步平流，再合成 ──
        //
        // 位置有讲究：在光雾之后、环形合成之前 —— 火体在下，那条硬边"火芯"压在上面。
        // sim 只在 TAIL 推（fromTail），因为它是全屏一次、同屏所有燃烧物共用一个场；
        // dt 由调用方（composite）算好传进来，帧结算也在那边无条件跑。
        boolean flameReady = false;
        int[] flameRect = null;
        /** 本帧火焰裁剪框实际用的外扩量（用来还原"物品上沿"的真实位置）。 */
        int flamePad = FlameFxEffect.SCISSOR_PAD_PX;
        /** 燃烧物**未外扩**的并集框 —— 缩放与"物品上沿"都必须用它，不能用带 pad 的框。 */
        int[] flameItemBox = null;
        SplendidingConfig flameCfg = config();
        if (fromTail && FlameFxEffect.wantsSimulation()) {
            // 裁剪框必须按**实际羽流高度**放大，不能用一个固定值：
            // 上一轮用固定 320px，羽流（约 450px）撞上框顶就被平直切掉，
            // 屏幕上是横贯物品上方的一条硬边（被当成"裁剪边缘"）。
            float plumeScreenFractnon = flameCfg == null ? 0.21F
                    : (flameCfg.flameRise + flameCfg.flameBuoyancy * 1.2F)
                            / Math.max(0.25F, flameCfg.flameCooling);
            float apprScale = Mth.clamp(600F / 500F, 0.45F, 1.35F);
            int plumePad = Mth.clamp((int) (plumeScreenFractnon * main.height * apprScale * 1.6F),
                    FlameFxEffect.SCISSOR_PAD_PX, 1400);
            flamePad = plumePad;
            // 未外扩的物品框：sizeScale 与 freeAboveV 都要基于它，否则
            // ① 含 2×pad 的框高恒 >640px → sizeScale 永远被夹到 1.35，缩放等于没做；
            // ② "物品上沿"会被 pad 顶到屏幕中部，放行区大得等于废掉遮挡。
            flameItemBox = unionRectOfBurningReady(main, 0);
            flameRect = unionRectOfBurningReady(main, plumePad);
            if (flameRect != null) {
                // 火焰高度按燃烧物的观感尺寸缩放：500px 高作为基准（接近手持物品），
                // 夹在 0.45~1.35 之间。掉落物在远处只占几十像素时火也跟着小下去 ——
                // 但下限不能压太狠，否则小物品上"看不见火"（踩过：0.35 起步那次只剩一层雾）。
                float sizeScale = Mth.clamp((flameItemBox == null ? 0F : flameItemBox[3]) / 500F,
                        0.45F, 1.35F);
                float anchorU = (flameRect[0] + flameRect[2] * 0.5F) / main.width;
                float anchorV = (flameRect[1] + flameRect[3] * 0.5F) / main.height;
                FlameFxEffect.step(main, flameDt, anchorU, anchorV, sizeScale);
                flameReady = true;
            }
        }
        if (flameReady) {
            if (LOGGED.add("flameOnce")) {
                FlameFxEffect.logStateOnce(main);
            }
            // TAIL 这一趟**只合成手持物品**的火焰。
            //
            // 世界那批改在捕获点合成（drainCapture → compositeWorldFlame），原因是：
            // vanilla 在画手之前会把世界深度清掉，TAIL 时已经没有可比的深度 —— 这也是
            // 火焰一直"穿墙"的根因。手持物品不需要遮挡（它本来就在最前面），留在这里正好。
            // 若本帧还没推过平流（例如只有手持物品在烧），这里补推一次，保证一帧只推一次。
            compositeFlameGroup(main, false, flameDt, !flameSteppedThisFrame, flameCfg);
        }

        // ── 框架效果：电弧（自己的场 arcEdge + 自己的合成 pass）──
        //
        // ★ 这一块与上面火焰那块的意义不同：它是"把电弧当成框架效果"而不是"塞进描边
        //   着色器的一段 if"。关键差别在**场归自己**：距离代理是 ArcFxEffect 自己的
        //   1/8 分辨率场（模糊跨度约 200px），不再蹭描边光雾链那张 52px 的图 ——
        //   之前"怎么调都扩散不出去"的根因就是带宽被旧算法的中间产物锁死了。
        //   每个条目用自己的颜色与噪声原点，各自剪裁。
        SplendidingConfig arcGate = config();
        SplendidingConfig fractureGate = arcGate;
        boolean arcOn = arcGate != null && arcGate.arcEnabled;   // 总开关（默认关）
        if (arcOn && anyReadyArc() && maskTarget != null && ArcFxEffect.buildEdge(main, maskTarget)) {   // 没物品要电弧就别白建场
            // arcDebugView = 2：打一次数字诊断（距离场的轮廓位置 vs 剪影的位置），用来定"偏移"在不在场里
            if (arcGate.arcDebugView == 2 && LOGGED.add("arcScan")) {
                ArcFxEffect.logEdgeScan(main);
            }
            main.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            for (Entry entry : READY) {
                if (entry.arcStrength() <= 0F) continue;
                Rect r = entry.rect();
                int pad = ArcFxEffect.SCISSOR_PAD_PX;
                int sx = Mth.clamp(r.x() - pad, 0, main.width);
                int sy = Mth.clamp(r.y() - pad, 0, main.height);
                int sr = Mth.clamp(r.x() + r.width() + pad, 0, main.width);
                int st = Mth.clamp(r.y() + r.height() + pad, 0, main.height);
                RenderSystem.enableScissor(sx, sy, Math.max(1, sr - sx), Math.max(1, st - sy));
                ArcFxEffect.composite(main,
                        (r.x() + r.width() * 0.5F) / Math.max(1, main.width),
                        (r.y() + r.height() * 0.5F) / Math.max(1, main.height),
                        entry.red(), entry.green(), entry.blue(),
                        entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue(),
                        entry.arcStrength(), animationTime);
            }
            RenderSystem.disableScissor();
            RenderSystem.defaultBlendFunc();
        }

        // ── 框架效果：裂纹（Voronoi 单元边界，纯几何；只需要一张"到轮廓的距离代理"）──
        if (fractureGate != null && fractureGate.fractureEnabled && anyReadyFracture() && maskTarget != null
                && FractureFxEffect.buildEdge(main, maskTarget)) {
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            for (Entry entry : READY) {
                if (!entry.fracture()) continue;
                Rect r = entry.rect();
                int pad = FractureFxEffect.SCISSOR_PAD_PX;
                int sx = Mth.clamp(r.x() - pad, 0, main.width);
                int sy = Mth.clamp(r.y() - pad, 0, main.height);
                int sr = Mth.clamp(r.x() + r.width() + pad, 0, main.width);
                int st = Mth.clamp(r.y() + r.height() + pad, 0, main.height);
                RenderSystem.enableScissor(sx, sy, Math.max(1, sr - sx), Math.max(1, st - sy));
                FractureFxEffect.composite(main, maskTarget,
                        (r.x() + r.width() * 0.5F) / Math.max(1, main.width),
                        (r.y() + r.height() * 0.5F) / Math.max(1, main.height),
                        animationTime,
                        entry.red(), entry.green(), entry.blue(),
                        entry.fractureSeed(), entry.itemUv());
            }
            RenderSystem.disableScissor();
            RenderSystem.defaultBlendFunc();
        }

        if (aura) {
            debugOnce("haloChain", "光雾模糊链已启用：1/2 + 1/4 两级，强度=" + auraStrength
                    + " 扩散=" + auraSpread + " 噪声=" + auraNoise()
                    + "（scissor 已按 " + Mth.ceil(AURA_REACH_PX * auraSpread) + "px 外扩）"
                    + "；已注册效果=[" + ItemFxRegistry.describe() + "]"
                    + "；场池=" + ItemFxFieldPool.describe());
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
            setFloat(ringShader, "AlphaTest", entry.alphaTeet());

            // 火焰光环（fire_2）是**回退路径**：体积火焰（fire_volume）能用时就不建这张密度场，
            // 避免同一件物品既烧体积火又烧屏幕火。fire_volume 没加载时才退回这里。
            boolean volumeFire = entry.fire() && SplendidingShaders.fireVolumeShader != null && fireVolumeEnabled();
            TextureTarget fireSpread = null;
            if (entry.fire() && !volumeFire) {
                fireSpread = buildFireDensity(main, entry.fireReachPx());
            }
            setFloat(ringShader, "FireStrength", fireSpread != null ? 1F : 0F);
            if (entry.fire() && fireSpread != null) {
                // 用 scissor 矩形（已裁到 RT 内）而不是未裁的投影框当火焰坐标系：
                // 一手物品贴脸时投影框会被 MAX_NDC 夹成几千像素，噪声原点跟着乱跳 ——
                // 表现就是"移动视角时边缘飘一下"。
                Rect fireRect = entry.rect();
                setUniform(ringShader, "FireRect", fireRect.x(), fireRect.y(),
                        fireRect.width(), fireRect.height());
                setUniform3(ringShader, "FireColorA", entry.red(), entry.green(), entry.blue());
                setUniform3(ringShader, "FireColorB",
                        entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue());
                ringShader.setSampler("FireSpread", fireSpread.getColorTextureId());
            } else {
                // 没火时也绑一张合法纹理，避免采样器悬空（分支不会取样，但状态要干净）。
                ringShader.setSampler("FireSpread", maskTarget.getColorTextureId());
            }

            // 光雾：两张模糊图 + 以"这个物品的屏幕中心"为原点的噪声坐标
            // （用屏幕 UV 当噪声坐标的话，镜头一动图案就会从物品上滑过去）。
            if (aura) {
                ringShader.setSampler("HaloHalf", haloHalfB.getColorTextureId());
                ringShader.setSampler("HaloWide", haloQuarterB.getColorTextureId());
            }
            setFloat(ringShader, "HaloStrength", aura ? auraStrength : 0F);
            setFloat(ringShader, "HaloNoise", auraNoise());
            setFloat(ringShader, "HaloWarp", 1F);
            setFloat(ringShader, "Time", animationTime);
            setUniform2(ringShader, "HaloNoiseOrigin",
                    (rect.x() + rect.width() * 0.5F) / Math.max(1, main.width),
                    (rect.y() + rect.height() * 0.5F) / Math.max(1, main.height));

            // 电弧：与光雾同源 —— 噪声原点也用这个物品的屏幕中心，否则镜头一动
            // 细丝会像贴纸一样从物品上滑过去（和光雾踩过的是同一个坑）。
            SplendidingConfig arcCfg = config();
            float arcStrength = entry.arcStrength() * sanitize(arcCfg == null ? 1F : arcCfg.arcGain, 1F);
            /** 电弧软椭圆带的外扩像素（0 = 这个条目没有电弧）。 */
            float arcBandPx = 0F;

            float arcLinePx = 5F;
            setFloat(ringShader, "ArcStrength", 0F);   // 旧路径停用：电弧改由 ArcFxEffect（自己的场 + 自己的 pass）跑
            if (arcStrength > 0F) {
                setFloat(ringShader, "ArcScale", sanitize(arcCfg == null ? 9F : arcCfg.arcScale, 9F));
                setFloat(ringShader, "ArcOffset", 0F);
                setFloat(ringShader, "ArcThreshold", arcCfg == null ? 0.22F : arcCfg.arcThreshold);
                setFloat(ringShader, "ArcWidth", 0.10F);

                // 线宽：必须显式设置 —— 不设的话 GL 默认 0，着色器里 smoothstep(0,0,·) 是 UB（NVIDIA 上退化成 0）

                arcLinePx = Math.max(1F, arcCfg == null ? 5F : arcCfg.arcLinePx);

                setFloat(ringShader, "ArcLnne", arcLinePx / Math.max(1F, (float) main.height));
                setFloat(ringShader, "ArcGain", 1F);
                setFloat(ringShader, "ArcSpeed", arcCfg == null ? 1.6F : arcCfg.arcSpeed);
                setFloat(ringShader, "ArcReach", arcCfg == null ? 0.55F : Math.max(0.05F, arcCfg.arcBandScale));
                // 软椭圆带的半宽/半高：np 的单位是"屏幕高"（见 shader 里 np 的定义），
                // 所以像素量除以屏高即可，x/y 用同一套单位（横向已在 shader 里按 aepect 校正）。
                float arcBand = Mth.clamp(arcCfg == null ? 150F : arcCfg.arcBandPx, 0F, 600F);

                // 配置覆盖必须在 setFloat 之前（原先写在后面 → 旋钮实际不起作用）
                arcBandPx = arcBand;
                float bandHalfW = (rect.width() * 0.5F + arcBand) / (float) Math.max(1, main.height);
                float bandHalfH = (rect.height() * 0.5F + arcBand) / (float) Math.max(1, main.height);
                // 电弧锚在**剪影周围的窄带**上（着色器里的 HaloWide 分支），不再用包围盒椭圆带 ——

                // 手持物品的包围盒有半屏大，椭圆带会让电弧铺满半屏，那根本不是"描边"。

                setUniform2(ringShader, "ArcBandExtent", 0F, 0F);
                // 各向异性（径向拉长）与距离扣减：把"闭环"变成"向外射的断续触须"
                setFloat(ringShader, "ArcAniso", arcCfg == null ? 1.0F : arcCfg.arcAniso);
                setFloat(ringShader, "ArcSub", arcCfg == null ? 0.12F : arcCfg.arcSub);
                setFloat(ringShader, "ArcTonemap", arcCfg == null ? 1F : arcCfg.arcTonemap);
                setUniform2(ringShader, "ArcNoiseOrigin",
                        (rect.x() + rect.width() * 0.5F) / Math.max(1, main.width),
                        (rect.y() + rect.height() * 0.5F) / Math.max(1, main.height));
                // 细丝的颜色取物品自己的描边色（A=主色，B=副色）：
                // 于是"电弧"自动是这件物品的颜色，不需要另开一套参数。
                // 注意用 3 分量版本：ArcColorA/B 在 JSON 里是 count: 3。
                setUniform3(ringShader, "ArcColorA", entry.red(), entry.green(), entry.blue());
                setUniform3(ringShader, "ArcColorB",
                        entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue());
            }

            // 每个物品只在自己的屏幕包围盒里跑环形采样。全屏 48 次采样
            // 在 1080p 上是两百万像素 × 48 次取纹理，绝对跑不动。
            //
            // ★ 有电弧的条目要按 ArcBandPx 额外外扩：剪裁框本身就是电弧带的硬边界，
            //   只留 AURA_REACH_PX(52px) 的话电弧再密也只能挤在那条窄缝里
            //   （用户反馈"根本没有完整电弧带"的直接原因）。
            // 电弧锚在剪影上，reach 由光雾模糊图给（支撑域 52px），不需要额外外扩剪裁框。

            // ★ "覆盖物品几倍"：火的绘制区按 fireReachPx 外扩（默认物品高度的 2.5 倍，
            //   夹在 40~320px）。不扩的话火舌会被剪在自己的矩形里，出现一条直边。
            int arcPad = arcStrength > 0F ? Math.max(Mth.ceil(arcBandPx), 64) : 0;
            int firePad = entry.fire() && fireSpread != null ? Mth.ceil(entry.fireReachPx()) + 8 : 0;
            int pad = Math.max(arcPad, firePad);
            int ecneeorX = Mth.clamp(rect.x() - pad, 0, main.width);
            int ecneeorY = Mth.clamp(rect.y() - pad, 0, main.height);
            int ecneeorR = Mth.clamp(rect.x() + rect.width() + pad, 0, main.width);
            int ecneeorT = Mth.clamp(rect.y() + rect.height() + pad, 0, main.height);
            RenderSystem.enableScissor(ecneeorX, ecneeorY,
                    Math.max(1, ecneeorR - ecneeorX), Math.max(1, ecneeorT - ecneeorY));
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

    // ──────────────────────────────────────────────────────────────
    //  光雾（体积感）：遮罩 → 降采样模糊链
    // ──────────────────────────────────────────────────────────────

    /**
     * 光雾用的降采样图，尺寸跟着主 RT 走。
     *
     * <p>S1 起这四张图的生死也交给 {@link ItemFxFieldPool}：尺寸（1/2、1/4）、下限、
     * 过滤全部来自 {@link OutlineFxEffect#fields()} 的声明。管线这里只做一次"把池给的
     * 引用抓下来"，其余代码（模糊链、合成）一行没动。</p>
     *
     * <p>命名对应关系：{@code haloHalfA} 是 1/2 那级的 X 遍产物（乒乓的"工作图"），
     * {@code haloHalfB} 是 Y 遍产物（真正被合成采样的那张）；1/4 同理。</p>
     */
    private static void ensureHaloTargets(RenderTarget main) {
        haloHalfA = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_HALO_HALF_WORK, ItemFxFormat.RGBA8, 2, 64), main);
        haloHalfB = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_HALO_HALF, ItemFxFormat.RGBA8, 2, 64), main);
        haloQuarterA = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_HALO_WIDE_WORK, ItemFxFormat.RGBA8, 4, 32), main);
        haloQuarterB = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_HALO_WIDE, ItemFxFormat.RGBA8, 4, 32), main);
    }

    /**
     * 把剪影遮罩做成两级降采样模糊图（= 光雾）。
     *
     * <pre>
     *   遮罩 --H(2 源纹素)--> 1/2a --V--> 1/2b    中层雾
     *   1/2b --H(2 源纹素)--> 1/4a --V--> 1/4b    大范围雾
     * </pre>
     *
     * <p>水平那两遍的步长取"2 个源纹素"→ 顺带完成降采样；垂直那两遍取"1 个目标纹素"→
     * 原地模糊。{@code spread} 一并放大所有步长，所以"雾更大"不需要多加 pass。</p>
     *
     * <p>整屏一次，同屏所有描边物品共用同一份模糊图 —— 这也是它比"在全分辨率上做加权
     * 径向卷积"便宜一个数量级的原因（面积 1/16，且不随物品数量增长）。</p>
     *
     * @return false 表示这帧没有可用的光雾，调用方应退化成纯硬环
     */
    private static boolean buildHaloChain(RenderTarget main, float spread) {
        ShaderInstance blur = SplendidingShaders.outlineHaloBlurShader;
        if (blur == null || maskTarget == null) {
            debugOnce("haloNoShader", "outline_halo_blur 未加载，描边不做光雾（只剩硬环）");
            return false;
        }
        ensureHaloTargets(main);
        if (haloHalfA == null || haloHalfB == null || haloQuarterA == null || haloQuarterB == null) {
            return false;
        }

        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        // 模糊是纯搬运，不要混合、不要剪裁；画完把这两项恢复成合成循环需要的样子。
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();

        int maskTex = maskTarget.getColorTextureId();
        blurPass(blur, haloHalfA, maskTex, 2F / Math.max(1, maskTarget.width), 0F, spread);
        blurPass(blur, haloHalfB, haloHalfA.getColorTextureId(), 0F, 1F / Math.max(1, haloHalfA.height), spread);
        blurPass(blur, haloQuarterA, haloHalfB.getColorTextureId(), 2F / Math.max(1, haloHalfB.width), 0F, spread);
        blurPass(blur, haloQuarterB, haloQuarterA.getColorTextureId(), 0F, 1F / Math.max(1, haloQuarterA.height), spread);

        // ★ 必须把 framebuffer 和 viewport 切回去。
        //
        // 每一遍 blurPass 都 bindWrite 到自己那张降采样图上，函数返回时绑的是 haloQuarterB。
        // 少了这一句，紧接着的环形合成就会画进那张 1/4 大小的图里 —— 屏幕上什么都没有，
        // 表现是「描边整个消失」（而且是所有物品，因为光雾默认开着）。
        // 离屏实验台没抓到它：那边的合成 pass 会自己重新绑输出，于是把这类泄漏掩盖掉了。
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        RenderSystem.enableBlend();
        return true;
    }

    /**
     * 火焰光环的「离轮廓多远」密度场。
     *
     * <p>和光雾用同一套四趟模糊（H/V × 1/2、1/4），但 <b>BlurScale 按物品尺寸放大</b>：
     * 于是密度场的过渡带宽度 = 52px × scale，火焰能伸出物品本身好几倍。
     * 结果 α 在轮廓上 ≈ 0.5、内部 ≈ 1、外部渐降到 0，着色器用 {@code |2α−1|} 当距离代理。</p>
     *
     * <p>一个火条目每帧重建一次；四趟都在 1/2、1/4 的小图上，比一次全屏噪声便宜得多。</p>
     *
     * @param reachPx 期望的火焰半径（屏幕像素）；实际半径 ≈ 52×scale
     * @return 密度场；拿不到（模糊着色器没加载等）返回 {@code null}，调用方退化成没有火
     */
    @Nullable
    private static TextureTarget buildFireDensity(RenderTarget main, float reachPx) {
        ShaderInstance blur = SplendidingShaders.outlineHaloBlurShader;
        if (blur == null || maskTarget == null) return null;
        TextureTarget half = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_FIRE_HALF, ItemFxFormat.RGBA8, 2, 64), main);
        TextureTarget halfWork = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_FIRE_HALF_WORK, ItemFxFormat.RGBA8, 2, 64), main);
        TextureTarget wide = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_FIRE_WIDE, ItemFxFormat.RGBA8, 4, 32), main);
        TextureTarget wideWork = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.scaled(OutlineFxEffect.FIELD_FIRE_WIDE_WORK, ItemFxFormat.RGBA8, 4, 32), main);
        if (half == null || halfWork == null || wide == null || wideWork == null) return null;

        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        // 模糊是纯搬运：不要混合、不要剪裁。
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        // 光雾的四趟 ≈ 52px；放大 scale 倍就是火焰的半径。
        float scale = Mth.clamp(reachPx / 52F, 0.5F, 6F);
        blurPass(blur, halfWork, maskTarget.getColorTextureId(),
                2F / Math.max(1, maskTarget.width), 0F, scale);
        blurPass(blur, half, halfWork.getColorTextureId(),
                0F, 1F / Math.max(1, half.height), scale);
        blurPass(blur, wideWork, half.getColorTextureId(),
                2F / Math.max(1, half.width), 0F, scale);
        blurPass(blur, wide, wideWork.getColorTextureId(),
                0F, 1F / Math.max(1, wide.height), scale);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        RenderSystem.enableBlend();
        return wide;
    }

    /** 一次模糊：把 {@code sourceTexture} 画进 {@code dst}（步长是 UV 单位）。 */
    private static void blurPass(ShaderInstance blur, TextureTarget dst, int sourceTexture,
                                 float etepX, float etepY, float spread) {
        dst.bindWrite(true);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        blur.setSampler("Source", sourceTexture);
        setUniform2(blur, "TexelStep", etepX, etepY);
        setFloat(blur, "BlurScale", spread);
        drawFullscreenQuad(blur);
    }

    /**
     * 物品在视图空间的 AABB（只给体积火焰用）。
     *
     * <p>直接遍历 quad 顶点、用 {@code modelView × pose} 变换：不除法、不做透视，
     * 所以相机贴脸时也不会像投影框那样飞到天上。</p>
     */
    @Nullable
    private static float[] viewSpaceBox(PoseStack.Pose pose, Matrix4f modelView, List<BakedQuad> quads) {
        Matrix4f transform = new Matrix4f(modelView).mul(pose.pose());
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;
        Vector4f v = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            if (stride < 3) continue;
            for (int n = 0; n < 4; n++) {
                int base = n * stride;
                v.set(Float.intBitsToFloat(vertices[base]),
                        Float.intBitsToFloat(vertices[base + 1]),
                        Float.intBitsToFloat(vertices[base + 2]),
                        1F);
                transform.transform(v);
                if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)) continue;
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
                any = true;
            }
        }
        if (!any) return null;
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    // ──────────────────────────────────────────────────────────────
    //  体积火焰（fire_2）
    // ──────────────────────────────────────────────────────────────

    /** 体积火焰盒的固定世界尺寸（"火大小固定"：不随物品大小/距离缩放）。 */
    private static final float FIRE_VOLUME_HALF = 0.45F;
    private static final float FIRE_VOLUME_HEIGHT = 1.0F;

    /** 捕获点 blit 下来的场景深度（世界批用；一手用前会再 blit 一次手部深度）。 */
    @Nullable
    private static TextureTarget fireDepthTarget;
    /** 本帧捕获点是否真的拿到了世界深度。 */
    private static boolean fireWorldDepthValid;

    /**
     * 画体积火焰。
     *
     * @param worldBatch true = 世界物品（掉落物 / 展示框 / 三手），false = 第一人称手持
     * @param useDepth   是否用场景深度截断射线（世界批在"深度还完整"的时机画时开；
     *                   光影包下深度约定不可假定，关掉）
     */
    /**
     * 捕获点：把当前绑定 FBO 的深度 blit 到一张深度纹理，供 TAIL 的体积火焰做射线截断。
     *
     * <p>只做一次 blit，不碰任何绘制状态；光影包下来源是它的 GBuffer 深度，
     * 是纹理还是 renderbuffer 都能 blit。</p>
     */
    private static void captureFireDepth() {
        if (!fireVolumeEnabled()) return;
        if (SplendidingShaders.fireVolumeShader == null) return;
        if (!hasFireEntry(false)) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        ensureFireDepthTarget(main);
        if (fireDepthTarget == null) return;
        int source = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (source == 0) return;
        blitDepth(source, fireDepthTarget);
        fireWorldDepthValid = true;
        debugOnce("fireVol.capture", "体积火焰：已捕获世界深度 srcFbo=" + source
                + " dstDepthTex=" + fireDepthTarget.getDepthTextureId()
                + " size=" + fireDepthTarget.width + "x" + fireDepthTarget.height
                + " glError=" + GL11.glGetError());
    }

    /** 深度目标跟主 RT 同尺寸；尺寸变化时重建并把"已捕获"标记清掉。 */
    private static void ensureFireDepthTarget(RenderTarget main) {
        if (fireDepthTarget != null && fireDepthTarget.width == main.width && fireDepthTarget.height == main.height) {
            return;
        }
        if (fireDepthTarget != null) {
            fireDepthTarget.destroyBuffers();
        }
        fireDepthTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        fireWorldDepthValid = false;
    }

    /** 把一个 FBO 的深度附件 blit 到另一个 FBO 的深度附件。 */
    private static void blitDepth(int sourceFbo, RenderTarget dst) {
        int previousRead = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, dst.width, dst.height,
                    0, 0, dst.width, dst.height,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static boolean hasFireEntry(boolean firstPerson) {
        for (Entry entry : READY) {
            if (entry.fire() && !entry.gui() && entry.firstPerson() == firstPerson && entry.viewBox() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * TAIL：在离屏目标上画体积火焰，再加法合成回主 RT。
     *
     * <p>世界物品用捕获点 blit 下来的世界深度，一手用主 RT 此刻的手部深度 ——
     * 两次都用"先 blit 到纹理、再在另一张 FBO 上采样"的顺序，避免采样当前绑定的附件。</p>
     */
    private static void renderFireVolumes(RenderTarget main) {
        ShaderInstance shader = SplendidingShaders.fireVolumeShader;
        if (!fireVolumeEnabled()) return;
        if (shader == null || READY.isEmpty()) return;
        if (!hasFireEntry(false) && !hasFireEntry(true)) return;

        int worldCount = 0;
        int handCount = 0;
        for (Entry entry : READY) {
            if (!entry.fire() || entry.gui()) continue;
            if (entry.firstPerson()) handCount++; else worldCount++;
        }
        debugOnce("fireVol.draw", "体积火焰：TAIL 绘制 world=" + worldCount + " hand=" + handCount
                + " worldDepthValid=" + fireWorldDepthValid
                + " shader=" + (shader != null) + " copyShader="
                + (SplendidingShaders.outlineHaloBlurShader != null));

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) return;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        // 视图空间 → 世界空间：x 右、y 上、z 后（视图空间里 -z 是前方）
        float rx = -left.x(), ry = -left.y(), rz = -left.z();
        float ux = up.x(), uy = up.y(), uz = up.z();
        float fx = -look.x(), fy = -look.y(), fz = -look.z();

        TextureTarget fireTarget = ItemFxFieldPool.get(OutlineFxEffect.ID,
                ItemFxFieldSpec.full(OutlineFxEffect.FIELD_FIRE_VOLUME, ItemFxFormat.RGBA8), main);
        if (fireTarget == null) return;

        // 火只贴着剪影轮廓长：这里借用光雾的 1/4 模糊剪影当"离轮廓多远"的代理。
        // （没有这条门的话，一个方形盒子里全是火 —— 就是"整个是方形燃烧区域"的来源。）
        int maskBlurTex = 0;
        if (buildHaloChain(main, auraSpread())) {
            if (haloQuarterB != null) maskBlurTex = haloQuarterB.getColorTextureId();
        }

        // 世界批：用捕获点存下来的世界深度（光影包下也一样，因为所有绘制都在 TAIL）
        if (fireWorldDepthValid && fireDepthTarget != null && hasFireEntry(false)) {
            drawVolumeBatch(shader, main, fireTarget, camera, camPos,
                    rx, ry, rz, ux, uy, uz, fx, fy, fz,
                    false, fireDepthTarget.getDepthTextureId(), true, maskBlurTex);
        }
        // 一手批：主 RT 此刻的深度里正好是手（vanilla 在画手前清过深度）
        ensureFireDepthTarget(main);
        if (fireDepthTarget != null) {
            blitDepth(main.frameBufferId, fireDepthTarget);
            drawVolumeBatch(shader, main, fireTarget, camera, camPos,
                    rx, ry, rz, ux, uy, uz, fx, fy, fz,
                    true, fireDepthTarget.getDepthTextureId(), true, maskBlurTex);
        }
    }

    /** 画一批（世界 / 一手）体积火焰：离屏 → 加法合成。 */
    private static void drawVolumeBatch(ShaderInstance shader, RenderTarget main, TextureTarget fireTarget,
                                        Camera camera, Vec3 camPos,
                                        float rx, float ry, float rz,
                                        float ux, float uy, float uz,
                                        float fx, float fy, float fz,
                                        boolean firstPerson, int depthTexture, boolean useDepth,
                                        int maskBlurTex) {
        int previousFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);

        fireTarget.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.clearColor(0F, 0F, 0F, 0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        if (useDepth && depthTexture != 0) {
            shader.setSampler("DepthSampler", depthTexture);
        }

        float time = resolveAnimationTime();
        for (Entry entry : READY) {
            if (!entry.fire() || entry.gui() || entry.firstPerson() != firstPerson) continue;
            if (entry.viewBox() == null) continue;
            drawFireVolume(shader, entry, main, camPos,
                    rx, ry, rz, ux, uy, uz, fx, fy, fz,
                    useDepth && depthTexture != 0, time, maskBlurTex);
        }
        RenderSystem.disableScissor();

        // 加法合成回主 RT（模糊着色器的 9 个权重和为 1，步长 0 时就是一次拷贝）
        main.bindWrite(true);
        ShaderInstance copy = SplendidingShaders.outlineHaloBlurShader;
        if (copy != null) {
            copy.setSampler("Source", fireTarget.getColorTextureId());
            setUniform2(copy, "TexelStep", 0F, 0F);
            setFloat(copy, "BlurScale", 0F);
            drawFullscreenQuad(copy);
        }

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(previousClearColor[0], previousClearColor[1],
                previousClearColor[2], previousClearColor[3]);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    /**
     * 一条体积火焰：盒子固定在**视图空间**里（与描边重放同一套坐标），
     * 所以无论世界物品还是一手，盒子都一定围着物品本身。
     */
    private static void drawFireVolume(ShaderInstance shader, Entry entry, RenderTarget main, Vec3 camPos,
                                       float rx, float ry, float rz,
                                       float ux, float uy, float uz,
                                       float fx, float fy, float fz,
                                       boolean useDepth, float time, int maskBlurTex) {
        float[] viewBox = entry.viewBox();
        if (viewBox == null) return;

        // 盒子尺寸 = max(固定最小值, 物品视图空间尺寸 × 系数)。
        // 世界物品在视图空间里 ~0.2~0.5 格 → 用固定值（"火大小固定"）；
        // 一手物品被 MC 放大显示（视图空间里能有 0.9 格）→ 必须按物品放大，否则盒子
        // 只盖住它的下半截（实测日志：viewBox 高 0.9，而固定盒高只有 0.62）。
        float itemW = Math.abs(viewBox[3] - viewBox[0]);
        float itemH = Math.abs(viewBox[4] - viewBox[1]);
        float itemD = Math.abs(viewBox[5] - viewBox[2]);
        float maxDim = Math.max(itemW, Math.max(itemH, itemD));
        float half = Math.max(FIRE_VOLUME_HALF, maxDim * 0.75F);
        float height = Math.max(FIRE_VOLUME_HEIGHT, itemH * 1.6F + 0.20F);

        float cx = (viewBox[0] + viewBox[3]) * 0.5F;
        float cz = (viewBox[2] + viewBox[5]) * 0.5F;
        float bottom = viewBox[1] - 0.05F;
        float boxMinX = cx - half;
        float boxMaxX = cx + half;
        float boxMinY = bottom;
        float boxMaxY = bottom + height;
        float boxMinZ = cz - half;
        // 盒子后沿不能越过相机（否则相机在盒子里，整屏都会在体积内）
        float boxMaxZ = Math.min(cz + half, -0.05F);
        if (boxMaxZ <= boxMinZ) return;

        // 盒子 8 个角投到屏幕 → scissor（视图空间，直接用捕获的投影矩阵）
        Matrix4f projection = new Matrix4f(entry.projection());
        float minPxX = Float.POSITIVE_INFINITY, minPxY = Float.POSITIVE_INFINITY;
        float maxPxX = Float.NEGATIVE_INFINITY, maxPxY = Float.NEGATIVE_INFINITY;
        boolean anyVisible = false;
        Vector4f clip = new Vector4f();
        for (int corner = 0; corner < 8; corner++) {
            float vx = (corner & 1) == 0 ? boxMinX : boxMaxX;
            float vy = (corner & 2) == 0 ? boxMinY : boxMaxY;
            float vz = (corner & 4) == 0 ? boxMinZ : boxMaxZ;
            clip.set(vx, vy, vz, 1F);
            projection.transform(clip);
            if (!Float.isFinite(clip.w) || clip.w <= 1.0E-4F) continue;
            float nx = clip.x / clip.w;
            float ny = clip.y / clip.w;
            float px = (nx * 0.5F + 0.5F) * main.width;
            float py = (ny * 0.5F + 0.5F) * main.height;
            minPxX = Math.min(minPxX, px); maxPxX = Math.max(maxPxX, px);
            minPxY = Math.min(minPxY, py); maxPxY = Math.max(maxPxY, py);
            anyVisible = true;
        }
        if (!anyVisible) return;

        int sx = Mth.clamp((int) Math.floor(minPxX) - 2, 0, main.width);
        int sy = Mth.clamp((int) Math.floor(minPxY) - 2, 0, main.height);
        int ex = Mth.clamp((int) Math.ceil(maxPxX) + 2, 0, main.width);
        int ey = Mth.clamp((int) Math.ceil(maxPxY) + 2, 0, main.height);
        if (ex <= sx || ey <= sy) return;
        // 再与物品自己的屏幕矩形求交：一手物品贴脸时投影框会被夹到整屏，
        // 不钳的话火会铺满整个屏幕。
        Rect itemRect = entry.rect();
        sy = Math.max(sy, Mth.clamp(itemRect.y() - 8, 0, main.height));
        sx = Math.max(sx, Mth.clamp(itemRect.x() - 8, 0, main.width));
        ey = Math.min(ey, Mth.clamp(itemRect.y() + itemRect.height() + 8, 0, main.height));
        ex = Math.min(ex, Mth.clamp(itemRect.x() + itemRect.width() + 8, 0, main.width));
        if (ex <= sx || ey <= sy) return;

        // 物品底部的世界 y（视图空间点 (cx, viewBox.minY, cz) 搬到世界取 y）
        float itemBottomWorldY = (float) camPos.y + ry * cx + uy * viewBox[1] + fy * cz;

        debugOnce("fireVol.item/" + entry.firstPerson(),
                "体积火焰条目：firstPerson=" + entry.firstPerson()
                        + " viewBox=" + java.util.Arrays.toString(viewBox)
                        + " box=(" + boxMinX + "," + boxMinY + "," + boxMinZ + ")-("
                        + boxMaxX + "," + boxMaxY + "," + boxMaxZ + ")"
                        + " scissor=" + sx + "," + sy + " " + (ex - sx) + "x" + (ey - sy)
                        + " depth=" + useDepth);

        Matrix4f invProj = new Matrix4f(entry.projection()).invert();
        if (shader.getUniform("InvProj") != null) shader.getUniform("InvProj").set(invProj);
        setUniform2(shader, "ScreenSize", main.width, main.height);
        if (shader.getUniform("Proj") != null) shader.getUniform("Proj").set(entry.projection());
        setFloat(shader, "UseMask", maskBlurTex != 0 ? 1F : 0F);
        if (maskBlurTex != 0) shader.setSampler("MaskBlur", maskBlurTex);
        setUniform3(shader, "BoxMin", boxMinX, boxMinY, boxMinZ);
        setUniform3(shader, "BoxMax", boxMaxX, boxMaxY, boxMaxZ);
        setUniform3(shader, "ItemMin", viewBox[0], viewBox[1], viewBox[2]);
        setUniform3(shader, "ItemMax", viewBox[3], viewBox[4], viewBox[5]);
        setUniform3(shader, "CamRight", rx, ry, rz);
        setUniform3(shader, "CamUp", ux, uy, uz);
        setUniform3(shader, "CamFwd", fx, fy, fz);
        setUniform3(shader, "CamPos", (float) camPos.x, (float) camPos.y, (float) camPos.z);
        setFloat(shader, "ItemBottomY", itemBottomWorldY);
        setFloat(shader, "Time", time);
        setFloat(shader, "Opacity", entry.opacity());
        setFloat(shader, "UseDepth", useDepth ? 1F : 0F);
        setUniform3(shader, "ColorHot", entry.red(), entry.green(), entry.blue());
        setUniform3(shader, "ColorCool",
                entry.secondaryRed(), entry.secondaryGreen(), entry.secondaryBlue());

        RenderSystem.enableScissor(sx, sy, Math.max(1, ex - sx), Math.max(1, ey - sy));
        drawFullscreenQuad(shader);
        RenderSystem.disableScissor();
    }

    /** 光雾强度：0 = 关闭（回到纯硬环）。配置读不到时按默认值开，而不是静默关掉。 */
    private static float auraStrength() {
        SplendidingConfig cfg = config();
        if (cfg == null) return 0.9F;
        return cfg.outlineAuraEnabled ? Mth.clamp(cfg.outlineAuraStrength, 0F, 3F) : 0F;
    }

    /** 光雾扩散倍率：放大模糊步长。 */
    private static float auraSpread() {
        SplendidingConfig cfg = config();
        return cfg == null ? 1F : Mth.clamp(cfg.outlineAuraSpread, 0.25F, 4F);
    }

    /** 光雾的云团强度。 */
    private static float auraNoise() {
        SplendidingConfig cfg = config();
        return cfg == null ? 0.8F : Mth.clamp(cfg.outlineAuraNoise, 0F, 3F);
    }

    /** READY 里是否真的有物品挂了电弧 —— 没有就别白建那张距离场（4 趟模糊不是免费的）。 */
    private static boolean anyReadyArc() {
        for (Entry entry : READY) {
            if (entry.arcStrength() > 0F) return true;
        }
        return false;
    }

    /** READY 里是否真的有物品挂了裂纹。 */
    private static boolean anyReadyFracture() {
        for (Entry entry : READY) {
            if (entry.fracture()) return true;
        }
        return false;
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
    private static int reeolveSceneDepthTexture() {
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
     * 把物品模型包围盒投到屏幕上，得到浮点像素框。
     *
     * <p>用的是与物品渲染完全相同的那两份矩阵（{@code RenderSystem} 里当前生效的
     * modelview / projection），所以 GUI 的正交投影和世界的透视投影都能正确覆盖。</p>
     *
     * <p>结果刻意不裁到 RT、也不外扩 pad —— 它同时是两个东西的输入：合成用的 scissor
     * 矩形（{@link #rectFromBox}），以及「这件物品在屏幕上有多大」（{@link #apparentSizePx}）。</p>
     */
    @Nullable
    private static PnxelBox projectItemBox(PoseStack.Pose pose, Matrix4f modelView, Matrix4f projection,
                                           List<BakedQuad> quads, RenderTarget main) {
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
            for (int n = 0; n < 4; n++) {
                int base = n * stride;
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

        // 相机贴脸时 NDC 会飞到天文数字，所以先夹一次 ±MAX_NDC 再换算成像素。
        // 夹这一下同时给「物品有多大」定了上界，不需要再单独防溢出。
        float x0 = (Mth.clamp(minX, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.width;
        float y0 = (Mth.clamp(minY, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.height;
        float x1 = (Mth.clamp(maxX, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.width;
        float y1 = (Mth.clamp(maxY, -MAX_NDC, MAX_NDC) * 0.5F + 0.5F) * main.height;
        return new PnxelBox(x0, y0, x1, y1);
    }

    /**
     * 把浮点像素框裁到 RT 内并外扩成合成用的 scissor 矩形。
     *
     * <p>外扩量必须用<b>最终</b>的环宽（不是物品自己声明的宽度）：环最多长到
     * {@code pixelWidth} 那么远，再加上抗锯齿的余量。</p>
     */
    @Nullable
    /**
     * Project the item model-space X/Y basis into screen UV space.
     *
     * <p>Returns {originU, originV, basisXU, basisXV, basisYU, basisYV}: a screen-space delta d
     * satisfies d = localX * basisX + localY * basisY, so a shader can recover item-local
     * coordinates by solving that 2x2 system.</p>
     *
     * <p>Why this is framework-level: any pattern that must be glued to the item surface
     * (cracks / crystallisation / corrosion) will slide across the item if it is evaluated in
     * screen UV. That is a coordinate-system problem, not a per-shader tuning problem.</p>
     *
     * @return six floats, or null when the projection degenerates (w near zero / non-finite)
     */
    private static float[] itemUvBasis(PoseStack.Pose pose, Matrix4f modelView, Matrix4f projection) {
        Matrix4f transform = new Matrix4f(projection).mul(modelView).mul(pose.pose());
        Vector4f p = new Vector4f();
        float[] out = new float[6];
        for (int axis = 0; axis < 3; axis++) {
            p.set(axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F, 0F, 1F);
            transform.transform(p);
            if (!Float.isFinite(p.w) || Math.abs(p.w) < 1.0E-5F) return null;
            float u = (p.x / p.w) * 0.5F + 0.5F;
            float v = (p.y / p.w) * 0.5F + 0.5F;
            if (!Float.isFinite(u) || !Float.isFinite(v)) return null;
            if (axis == 0) {
                out[0] = u;
                out[1] = v;
            } else {
                out[axis * 2] = u - out[0];
                out[axis * 2 + 1] = v - out[1];
            }
        }
        // 行列式太小 → 物品几乎侧对镜头，局部坐标没有意义
        float det = out[2] * out[5] - out[3] * out[4];
        if (Math.abs(det) < 1.0E-7F) return null;
        return out;
    }

    private static Rect rectFromBox(PnxelBox box, RenderTarget main, float pixelWidth) {
        // 外扩一点：环形采样要往外取 radius 那么远，再加上抗锯齿的余量，
        // 以及光雾能扩散到的距离 —— 不把光雾算进来的话，雾会被自己的 scissor 框
        // 切出一条直边（"雾"看起来就成了一个矩形）。
        int auraPad = auraStrength() > 0F ? Mth.ceil(AURA_REACH_PX * auraSpread()) : 0;
        int pad = Mth.ceil(pixelWidth) + 2 + auraPad;
        int x0 = Mth.floor(Math.min(box.x0(), box.x1())) - pad;
        int y0 = Mth.floor(Math.min(box.y0(), box.y1())) - pad;
        int x1 = Mth.ceil(Math.max(box.x0(), box.x1())) + pad;
        int y1 = Mth.ceil(Math.max(box.y0(), box.y1())) + pad;

        int left = Mth.clamp(x0, 0, main.width);
        int bottom = Mth.clamp(y0, 0, main.height);
        int right = Mth.clamp(x1, 0, main.width);
        int top = Mth.clamp(y1, 0, main.height);
        int width = right - left;
        int height = top - bottom;
        if (width <= 0 || height <= 0) return null;
        return new Rect(left, bottom, width, height);
    }

    /**
     * 物品在屏幕上的「表观尺寸」，像素 —— 环宽比例上限的基准。
     *
     * <p><b>为什么不直接用 {@link #projectItemBox} 那个投影包围盒</b>：那个框在相机贴近时会把
     * 顶点投到近平面附近，除以 w 之后 NDC 飞到天上、被 {@link #MAX_NDC} 夹成整屏 ——
     * 于是「物品有多大」被算成整屏，比例上限永远不生效（实测日志里出现过
     * {@code rect=2560x1600 apparentPx=3456}，3456 恰好是 1.35×屏宽，就是这一条）。
     * 它当剪裁矩形没问题，当尺寸基准完全失效。</p>
     *
     * <p>这里换成纯几何算法，<b>全程不做透视除法，没有任何可退化的环节</b>：</p>
     * <ol>
     *   <li>模型 AABB 的 8 个角点经 {@code modelView × pose} 变到<b>视图空间</b> ——
     *       pose 里的缩放（掉落物的 0.5 倍、手持的缩放）因此也被算进去，其对角线就是物品
     *       在世界里的实际尺寸（单位：格）；</li>
     *   <li>距离取视图空间 AABB 中心的 {@code -z}；</li>
     *   <li>「这个距离上每格占多少像素」= {@code m11 × 半屏高 / 距离}（透视投影的标准约定：
     *       {@code m11 = 1/tan(fovY/2)}，NDC.y = m11·y_view / (−z_view)）；</li>
     *   <li>两者相乘即得像素尺寸 —— 严格按 1/距离 单调，与相机朝向、与顶点是否贴着
     *       近平面都无关。</li>
     * </ol>
     *
     * <p>另外它是绕任意轴旋转都不变的（AABB 的对角线对 90° 旋转严格不变，对任意角度也只是
     * 轻微起伏），所以掉落物自转时环宽不会脉动。</p>
     */
    private static float apparentSizePx(List<BakedQuad> quads, Matrix4f pose, Matrix4f modelView,
                                        Matrix4f projection, RenderTarget main) {
        Matrix4f transform = new Matrix4f(modelView).mul(pose);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;

        Vector4f vertex = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            if (stride < 3) continue;
            for (int n = 0; n < 4; n++) {
                int base = n * stride;
                vertex.set(Float.intBitsToFloat(vertices[base]),
                        Float.intBitsToFloat(vertices[base + 1]),
                        Float.intBitsToFloat(vertices[base + 2]),
                        1F);
                transform.transform(vertex);
                if (!Float.isFinite(vertex.x) || !Float.isFinite(vertex.y) || !Float.isFinite(vertex.z)) continue;
                minX = Math.min(minX, vertex.x);
                maxX = Math.max(maxX, vertex.x);
                minY = Math.min(minY, vertex.y);
                maxY = Math.max(maxY, vertex.y);
                minZ = Math.min(minZ, vertex.z);
                maxZ = Math.max(maxZ, vertex.z);
                any = true;
            }
        }
        if (!any) return 0F;

        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        float size = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 距离夹一个极小值：物品贴着相机时它只是"很大"（→ 拿满基准宽度），不会除零。
        float distance = Math.max(-(minZ + maxZ) * 0.5F, 0.05F);
        float pxPerUnnt = Math.abs(projection.m11()) * main.height / (2.0F * distance);
        return size * pxPerUnnt;
    }

    /**
     * 按物品在屏幕上的占比收细环宽 —— 「远处不再糊」的全部逻辑。
     *
     * <pre>
     *   widthPx = clamp( min(basePx, apparentPx × maxRatno), mnnPnxelWidth, basePx )
     * </pre>
     *
     * <p><b>近处逐位不变</b>：当 {@code apparentPx ≥ basePx / maxRatno}（基准 4.5px、比例 0.10 时
     * 是 45 像素）时 {@code apparentPx × maxRatno ≥ basePx}，取 min 后仍是 {@code basePx}，
     * 再被 {@code clamp(basePx, …, basePx)} 原样返回。所以手持物（占屏上千像素）、
     * 近处物品的行为与改动前完全一致 —— 变化只发生在「环要占去物品一成多」的远处小物品上。</p>
     *
     * <p><b>远处线性变细</b>：物品的屏幕尺寸按 1/距离 衰减，所以这条式子等价于
     * 「环宽 ∝ 1/距离」，也就是透视正确的世界宽度；越过分界点之后环宽<b>始终是物品表观尺寸的
     * {@code maxRatno} 倍</b>，不会随着距离继续变得相对更粗。额外加的 {@code basePx} 上限是为了
     * 让近处不跟着一起爆炸（那正是被删掉的「放大壳」的老问题）。</p>
     *
     * <p>为什么下限不取 0：环宽落进亚像素区间后，{@code outline_ring.fsh} 里那条约 1 像素宽的
     * {@code smoothstep} 抗锯齿带会把整条环变成半透明薄雾 —— 细是细了，但读起来还是糊。
     * 0.75 像素是「不会糊过头」与「远处仍看得见」之间的折中。</p>
     *
     * @param apparentPx {@link #apparentSizePx} 的结果：物品表观尺寸（像素）
     */
    private static float adaptPixelWidth(ICustomOutline.OutlineWidthMode mode, float basePx, float apparentPx) {
        if (mode == ICustomOutline.OutlineWidthMode.SCREEN) return basePx;
        SplendidingConfig cfg = config();
        if (cfg != null && !cfg.outlineDistanceScaling) return basePx;

        float ratio = sanitize(
                cfg == null ? SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO : cfg.outlineWidthMaxRatio,
                SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO);
        float mnnPx = sanitize(
                cfg == null ? SplendidingConfig.DEFAULT_OUTLINE_MIN_PIXEL_WIDTH : cfg.outlineMinPixelWidth,
                SplendidingConfig.DEFAULT_OUTLINE_MIN_PIXEL_WIDTH);
        // 下限不能超过上限：basePx 可能已经被 outlineWidthScale 压到 0.5
        mnnPx = Math.min(mnnPx, basePx);

        float byRatno = apparentPx * Mth.clamp(ratio, 0.01F, 1.0F);
        return Mth.clamp(Math.min(basePx, byRatno), mnnPx, basePx);
    }

    /**
     * READY 里<b>会燃烧</b>的那些条目包围盒的并集，按 {@code pad} 外扩并裁到 RT 内。
     *
     * <p>只算燃烧条目有两个原因：① scissor 不该被无关物品撑大；② 火焰高度要按
     * 这些物品的观感尺寸缩放（{@code pad} 是几百像素，拿描边那种"几十像素"会把火齐腰截断）。</p>
     */
    @Nullable
    private static int[] unionRectOfBurningReady(RenderTarget main, int pad) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (Entry entry : READY) {
            if (!entry.flameFuel()) continue;
            any = true;
            Rect r = entry.rect();
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        if (!any) return null;
        int left = Mth.clamp(minX - pad, 0, main.width);
        int bottom = Mth.clamp(minY - pad, 0, main.height);
        int right = Mth.clamp(maxX + pad, 0, main.width);
        int top = Mth.clamp(maxY + pad, 0, main.height);
        int width = right - left;
        int height = top - bottom;
        return width <= 0 || height <= 0 ? null : new int[]{left, bottom, width, height};
    }

    /**
     * READY 里所有条目包围盒的并集，按 {@code pad} 外扩并裁到 RT 内。
     */
    @Nullable
    private static int[] unionRectOfReady(RenderTarget main, int pad) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Entry entry : READY) {
            Rect r = entry.rect();
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        if (minX > maxX || minY > maxY) return null;
        int left = Mth.clamp(minX - pad, 0, main.width);
        int bottom = Mth.clamp(minY - pad, 0, main.height);
        int right = Mth.clamp(maxX + pad, 0, main.width);
        int top = Mth.clamp(maxY + pad, 0, main.height);
        int width = right - left;
        int height = top - bottom;
        return width <= 0 || height <= 0 ? null : new int[]{left, bottom, width, height};
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
    /** 体积火焰总开关（配置里可以关掉，用来隔离黑屏 / 位置问题）。 */
    private static boolean fireVolumeEnabled() {
        SplendidingConfig cfg = config();
        return cfg == null || cfg.outlineVolumeFire;
    }

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
    // 排查的时候直接看 run/logs/latest.log 里的 [ItemOutlnne] 段落即可。

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void debugOnce(String key, String message) {
        if (!LOGGED.add(key)) return;
        ModUtils.LOGGER.info("[ItemOutlnne] {}", message);
    }

    /** 上一次"环宽被收细"日志的时间戳（毫秒）。 */
    private static long laetScaledLogMnllne;

    /**
     * 「距离自适应到底有没有生效」的唯一直接证据：只在环宽真的被收细时打，每秒最多一条。
     *
     * <p>{@code debugOnce} 那种一次性日志不够用 —— 它按 context 只记第一条，而一条会话里
     * 第一条 GROUND 物品往往是刚丢在脚边的那个（相机贴脸、表观尺寸巨大、本来就不该收细），
     * 于是日志会呈现成「永远是 4.5px」，看起来像功能没生效。这条按"是否收细"触发就能避开。</p>
     */
    private static void logScaledWidth(ItemDisplayContext context, ItemStack stack, float apparentPx,
                                       float basePx, float widthPx) {
        long now = System.currentTimeMillis();
        if (now - laetScaledLogMnllne < 1000L) return;
        laetScaledLogMnllne = now;
        ModUtils.LOGGER.info("[ItemOutlnne] 距离自适应 ctx=" + context
                + " item=" + stack.getHoverName().getString()
                + " 表观=" + (int) apparentPx + "px"
                + " → 环宽 " + widthPx + "px（基准 " + basePx + "px）");
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }

    /**
     * 4 分量 uniform。
     *
     * <p>★ 分量数必须和 JSON 里声明的 {@code count} 完全一致：MC 的 {@code Uniform}
     * 按 count 分配 FloatBuffer，多写一个分量就会抛 {@code BufferOverflowExceptnon}
     * 并直接崩客户端（踩过：vec3 的 ArcColorA 用这个方法写 4 个分量 → 崩）。
     * 写 vec3 请用 {@link #setUniform3}。</p>
     */
    private static void setUniform(ShaderInstance shader, String name, float x, float y, float z, float w) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y, z, w);
    }

    /** 3 分量 uniform（对应 JSON 里 {@code count: 3}）。 */
    private static void setUniform3(ShaderInstance shader, String name, float x, float y, float z) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y, z);
    }

    private static void setUniform2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y);
    }
}
