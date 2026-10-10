package org.bytechen.hall.client.rend.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.Optional;

/**
 * 坍缩使徒的自定义 boss 血条。
 *
 * <h3>材料</h3>
 * <ul>
 *   <li>{@code textures/gui/collapsar_health_outline.png}（182×32）—— 框架，
 *       最后画，压在内容之上；</li>
 *   <li>{@code textures/gui/collapsar_health_fullmask.png}（182×32）—— 血量内容的
 *       <b>mask</b>：<b>红通道</b>决定哪里是血条内部（与 {@code void_sword_mask}
 *       完全同一套约定：白=显示、黑=不显示）。所以条形的形状/粗细全在贴图里，
 *       代码里没有任何硬编码像素几何 —— 美术改 mask，条就跟着变。</li>
 * </ul>
 *
 * <h3>三层结构</h3>
 * <pre>
 *   ① 内容   black 底 + 彩色星尘（hall:collapsar_bar 着色器）
 *            · 背景恒为纯黑；星尘是程序化生成的多色粒子，随时间闪烁流动
 *            · uFill = 血量比例：星尘只出现在填充区，黑底仍铺满整条
 *            · 条外 alpha = 0，把 frame 与天空让出来
 *   ② 框架   collapsar_health_outline 原样贴上（普通 GUI 贴图，不走着色器）
 * </pre>
 *
 * <h3>为什么挂在 CustomizeGuiOverlayEvent.BossEventProgress</h3>
 * 实体的血条走的是原版 {@code ServerBossEvent}（见 {@code CollapsarEntity}），
 * 客户端由原版 {@code BossHealthOverlay} 逐条绘制。这个事件正好把它算好的
 * {@code x/y}（{@code screenWidth/2 - 91}，宽 182，与贴图宽度一模一样）交给我们，
 * 我们取消原版绘制后接管即可，无需自己维护血条状态或额外网络包。
 *
 * <p>认出"这条是我的"靠名字：事件里只有 {@code LerpingBossEvent}，它的 id 是
 * 原版随机生成的 UUID，跟实体对不上；名字取的就是实体显示名，稳定且够用。
 * 如果以后有同名 boss，再改成服务端发 UUID 标记即可。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CollapsarBossBar {

    /** 贴图基准尺寸：182 正好等于原版 boss 条宽度（横向对齐用）。 */
    private static final int TEX_W = 182;
    private static final int TEX_H = 32;

    /**
     * 贴图比例离谱时的绘制高度夹取范围。
     * <p>宽度锁死在 {@link #TEX_W}（与原版条横向对齐），高度按贴图真实比例推导；
     * 万一美术给了张方图，也不至于让一条血条把整个 HUD 顶掉。</p>
     */
    private static final int MIN_BAR_H = 8;
    private static final int MAX_BAR_H = 64;

    /**
     * 实际绘制尺寸（GUI 像素），由贴图真实尺寸在 {@link #inspectMaskOnce()} 里推导；
     * 多条血条的垂直间距按它算（{@code barH + 4}）。
     *
     * <p>以前这几处是写死的 182×32，只认"182×32 或它的整数倍"这一种图：换成别的比例
     * （例如 256×64、300×48）时图会被硬塞进 182×32，屏幕上就是"血条被拉伸/压扁"。
     * 现在比例由贴图自己决定。</p>
     */
    private static int barW = TEX_W;
    private static int barH = TEX_H;
    /** 贴图真实宽高比，喂给着色器把星点画成圆而不是椭圆。 */
    private static float barAspect = TEX_W / (float) TEX_H;

    /**
     * 星尘密度倍数：1.0 = 着色器里的基准密度（本轮已从 0.34/0.30 提到 0.80/0.70，
     * 大约 2.3 倍的星点数，条带上约 68 颗小星 + 15 颗大星）。
     * <p>想再浓就往 1.1~1.2 加；<b>不建议超过 1.25</b>：那时两层都变成"每格必有星"，
     * 随机位置被网格锁住，观感会从星云变成规则的网点阵。</p>
     */
    private static final float STAR_DENSITY = 1.0F;
    /** 星尘亮度倍数。密度已经调高了，亮度保持 1.0 免得整条糊成一片白。 */
    private static final float STAR_STRENGTH = 1.0F;

    private static final ResourceLocation MASK = ResourceLocation.fromNamespaceAndPath(
            HallMod.MODID, "textures/gui/collapsar_health_fullmask.png");
    private static final ResourceLocation OUTLINE = ResourceLocation.fromNamespaceAndPath(
            HallMod.MODID, "textures/gui/collapsar_health_outline.png");

    private CollapsarBossBar() {}

    // ══════════════════════════════════════════════════════════════
    // 事件入口
    // ══════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onBossBarProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        ShaderInstance shader = SplendidingShaders.collapsarBarShader;
        String expected = EntityTypeRegistry.COLLAPSAR.get().getDescription().getString();
        String actual = event.getBossEvent().getName().getString();
        boolean mine = expected.equals(actual);

        // 一次性诊断：血条不显示时，这一行能直接区分三种情况 ——
        //   ① 事件根本没来（日志里没有这行）→ 服务端 ServerBossEvent 没送到客户端；
        //   ② 事件来了但名字对不上（mine=false）→ 名字匹配逻辑要改；
        //   ③ 事件来了、名字也对（mine=true）→ 问题在绘制/着色器。
        if (!loggedFirstEvent) {
            loggedFirstEvent = true;
            HallMod.LOGGER.info("[CollapsarBossBar] 首个 boss 血条事件: name='{}' expected='{}' 接管={} shader={}",
                    actual, expected, mine, shader != null ? "ok" : "NULL");
        }

        // 着色器没起来（资源/驱动问题）就不接管 —— 让原版条照常显示，
        // 总比"取消了原版绘制但自己也画不出来"、屏幕上什么都没有要好。
        if (shader == null) return;
        if (!mine) return;

        event.setCanceled(true);          // 接管原版绘制

        // mask 体检：读一遍图，决定是否启用几何兜底带，并按贴图真实比例推导绘制尺寸。
        // 必须放在取 barH 之前 —— 首帧就要用对尺寸，否则多血条布局会先跳一下。
        inspectMaskOnce();

        event.setIncrement(barH + 4);     // 条高 + 4 像素缝，后续血条按这个间距往下排

        GuiGraphics gfx = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY();
        float fill = Mth.clamp(event.getBossEvent().getProgress(), 0.0F, 1.0F);
        // 与 GuiShaderRenderer 用同一个时间源：暂停时星尘也继续流动
        float time = (float) (System.currentTimeMillis() % 1_000_000L) / 1000.0F;

        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            HallMod.LOGGER.info("[CollapsarBossBar] 首次绘制: 位置=({}, {}) 尺寸={}x{}（比例 {}） 填充={}",
                    x, y, barW, barH, barAspect, fill);
        }

        // 血量越低抖得越厉害：整条（槽 + 内容 + 框架）一起位移，才像"整条在抖"，
        // 而不是只有内部内容在飘。
        float shake = shakeAmount(fill);
        int dx = 0;
        int dy = 0;
        if (shake > 0.0F) {
            dx = Math.round(jitter(time, 0.0F, 1.00F) * SHAKE_MAX_X * shake);
            dy = Math.round(jitter(time, 5.0F, 1.31F) * SHAKE_MAX_Y * shake);
        }
        int bx = x + dx;
        int by = y + dy;

        // ① 黑槽：用原版 fill 先垫一条不透明黑。
        //    它走 GuiGraphics 的批处理路径，与"框架"那条路完全同源（而框架已经证明可见），
        //    所以就算着色器整条失效，血条也还有"一条黑槽"这个形态，而不是一片空白。
        //    范围取 mask 亮行范围 —— 图改了它就跟着改。
        gfx.fill(bx, by + Math.round(bandMin * barH),
                 bx + barW, by + Math.round(bandMax * barH), 0xFF000000);

        // 关键：下面两次是 BufferUploader 立即绘制，而 GuiGraphics 的 BufferSource 里可能还压着
        // 之前 HUD 元素的顶点 —— 那些会在我们之后才 flush，从而盖在血条上。先把它们刷出去。
        gfx.flush();

        // ② 彩色星尘（自定义着色器，立即绘制）
        drawContent(shader, bx, by, fill, time, shake);
        // ③ 框架（最后画，压在内容之上）
        drawOutline(bx, by);
        gfx.flush();
    }

    // ══════════════════════════════════════════════════════════════
    // 低血颤抖
    // ══════════════════════════════════════════════════════════════

    /** 从多少血量比例开始抖（含以上不抖）。 */
    private static final float SHAKE_START = 0.55F;
    /** 抖到最大幅度的血量比例。 */
    private static final float SHAKE_FULL = 0.12F;
    /** 最大水平位移（GUI 像素） */
    private static final float SHAKE_MAX_X = 2.6F;
    /** 最大垂直位移（GUI 像素） */
    private static final float SHAKE_MAX_Y = 1.5F;

    /**
     * 颤抖强度：0 = 不抖，1 = 抖到最大。
     * <p>用两段线性把 {@code [SHAKE_FULL, SHAKE_START]} 映射到 {@code [1, 0]}：
     * 满血不抖、掉到 SHAKE_START 开始轻微抖、掉到 SHAKE_FULL 抖到最大，
     * 再低也不会更夸张（否则最后 1% 血会抖到看不出条在哪）。</p>
     */
    private static float shakeAmount(float fill) {
        if (fill >= SHAKE_START) return 0.0F;
        return Mth.clamp((SHAKE_START - fill) / (SHAKE_START - SHAKE_FULL), 0.0F, 1.0F);
    }

    /**
     * 抖动函数：三个不可通约频率的正弦叠加。
     * <p>为什么不用 {@code Random}：每帧独立取随机会变成"闪"（高频噪点，看着像掉帧），
     * 而三个不同速度的正弦叠起来是连续但无规律可循的位移，观感才是"抖"。
     * 三条频率比接近无理数，所以肉眼看不到周期。</p>
     *
     * @return 约 [-1, 1]
     */
    private static float jitter(float time, float seed, float speed) {
        float p = time * speed + seed;
        return (float) (Math.sin(p * 12.9) * 0.55
                      + Math.sin(p * 27.7 + 1.7) * 0.30
                      + Math.sin(p * 51.3 + 3.1) * 0.15);
    }

    /** 一次性诊断开关（见上面那行日志）。 */
    private static boolean loggedFirstEvent = false;
    private static boolean loggedFirstDraw = false;

    /** mask 是否可用（由 {@link #inspectMaskOnce()} 读图后确定）。 */
    private static boolean maskOk = false;
    private static boolean maskInspected = false;
    /** mask 里亮像素所占的行范围（UV，0..1）—— 兼作着色器的几何兜底带与黑槽的像素范围。 */
    private static float bandMin = 0.5625F;
    private static float bandMax = 0.75F;

    /**
     * 直接读一遍 mask PNG，算出亮像素所在的行范围，并记进日志。
     *
     * <p>为什么要读图：mask 是"血条内部"的唯一来源，一旦采样回来是 0，
     * {@code band} 整条为 0，屏幕上就是"框架在、里面全空"——这是最贵的静默失败。
     * 读一次图就能同时做到三件事：把行范围交给着色器当兜底带、给黑槽定位置、
     * 并且在日志里留下 maxR / 亮点数，下次出问题不用猜。</p>
     */
    private static void inspectMaskOnce() {
        if (maskInspected) return;
        maskInspected = true;
        try {
            Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(MASK);
            if (found.isEmpty()) {
                maskOk = false;
                HallMod.LOGGER.warn("[CollapsarBossBar] 找不到 mask 资源 {}，改用几何兜底带 [{}, {}]",
                        MASK, bandMin, bandMax);
                return;
            }
            try (InputStream in = found.get().open();
                 NativeImage image = NativeImage.read(in)) {
                int w = image.getWidth();
                int h = image.getHeight();
                int maxR = 0;
                int bright = 0;
                int minRow = h;
                int maxRow = -1;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // getPixelRGBA 返回 ABGR 打包，红通道就是最低字节
                        int r = image.getPixelRGBA(x, y) & 0xFF;
                        if (r > maxR) maxR = r;
                        if (r > 128) {
                            bright++;
                            if (y < minRow) minRow = y;
                            if (y > maxRow) maxRow = y;
                        }
                    }
                }
                if (bright > 0) {
                    maskOk = true;
                    bandMin = minRow / (float) h;
                    bandMax = (maxRow + 1) / (float) h;
                }
                HallMod.LOGGER.info("[CollapsarBossBar] mask 体检: {}x{} maxR={} 亮像素={} 行范围=[{}, {}] → 兜底带=[{}, {}] 可用={}",
                        w, h, maxR, bright, minRow, maxRow, bandMin, bandMax, maskOk);

                // 按贴图真实比例推导绘制尺寸（见 barW/barH 的注释）
                applyTextureSize(w, h);
            }
            inspectOutlineSizeOnce();
        } catch (Throwable t) {
            maskOk = false;
            HallMod.LOGGER.warn("[CollapsarBossBar] mask 读取失败，改用几何兜底带 [{}, {}]: {}",
                    bandMin, bandMax, t.toString());
        }
    }

    /**
     * 按贴图真实尺寸推导绘制尺寸。
     * <p>宽度锁在原版 {@link #TEX_W}（与其它 boss 条横向对齐），高度按贴图比例换算 ——
     * 这才是"换了一张别的比例的图"不变形的关键。比例离谱的图会被夹到
     * [{@link #MIN_BAR_H}, {@link #MAX_BAR_H}]，并把这件事故意写进日志。</p>
     */
    private static void applyTextureSize(int texW, int texH) {
        if (texW <= 0 || texH <= 0) return;
        barAspect = texW / (float) texH;
        int raw = Math.round(TEX_W * texH / (float) texW);
        barH = Mth.clamp(raw, MIN_BAR_H, MAX_BAR_H);
        HallMod.LOGGER.info("[CollapsarBossBar] 贴图 {}x{} ⇒ 比例 {}，条按 {}x{} 绘制{}",
                texW, texH, barAspect, barW, barH,
                barH != raw ? "（原本算得 " + raw + "，已夹到合理范围）" : "");
    }

    /** 框架图比例与 mask 不一致时黑槽会和框架错位 —— 直接在日志里点出来，别让人靠眼睛猜。 */
    private static void inspectOutlineSizeOnce() {
        try {
            Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(OUTLINE);
            if (found.isEmpty()) return;
            try (InputStream in = found.get().open();
                 NativeImage image = NativeImage.read(in)) {
                float outlineAspect = image.getWidth() / (float) image.getHeight();
                if (Math.abs(outlineAspect - barAspect) > 0.01F) {
                    HallMod.LOGGER.warn("[CollapsarBossBar] 框架图比例 {} 与 mask 比例 {} 不一致："
                                    + "框架会被拉伸或与黑槽错位。两张图的宽高比必须相同（例：182x32 与 364x64）。",
                            outlineAspect, barAspect);
                }
            }
        } catch (Throwable t) {
            HallMod.LOGGER.debug("[CollapsarBossBar] 框架图尺寸体检跳过: {}", t.toString());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ① 内容：纯黑底 + 彩色星尘（着色器）
    // ══════════════════════════════════════════════════════════════

    private static void drawContent(ShaderInstance shader, int x, int y, float fill, float time, float shake) {
        setUniform(shader, "uTime", time);
        setUniform(shader, "uFill", fill);
        setUniform(shader, "uAlpha", 1.0F);
        setUniform(shader, "uShake", shake);
        setUniform(shader, "uStarStrength", STAR_STRENGTH);
        setUniform(shader, "uStarDensity", STAR_DENSITY);
        setUniform(shader, "uBarAspect", barAspect);
        // 几何兜底带：mask 读不到时按这张图真实亮行范围画，至少不会一片空白
        setUniform(shader, "uBandMin", bandMin);
        setUniform(shader, "uBandMax", bandMax);
        setUniform(shader, "uUseGeomBand", maskOk ? 0.0F : 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();      // SRC_ALPHA / ONE_MINUS_SRC_ALPHA：条外透明、条内不透明黑
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 显式把 sampler 绑到纹理对象本身。
        //
        // 为什么不只靠 setShaderTexture(0, MASK)：ShaderInstance.apply() 里对 sampler 的处理是
        //   if (samplerMap.get(name) != null) { uploadInteger(loc, j); activeTexture(GL_TEXTURE0+j); bindTexture(id); }
        // 也就是说——**只有 setSampler 过的 sampler 才会在每次绘制时被显式绑定**；
        // 没设过的只能指望 GL 里 sampler uniform 的默认值恰好是 0、且单元 0 上恰好还留着我们要的纹理，
        // 中间任何一次别的绑定都会让它读到空白（症状正是"框架在、里面全空"）。
        // 传 AbstractTexture 时 apply() 会自己取它的 GL id 并在对应的单元上绑定，链路才闭合。
        AbstractTexture maskTexture = Minecraft.getInstance().getTextureManager().getTexture(MASK);
        shader.setSampler("MaskTexture", maskTexture);
        RenderSystem.setShaderTexture(0, MASK);
        RenderSystem.setShader(() -> shader);

        // 顶点直接用 GUI 缩放坐标：ProjMat / ModelViewMat 由 RenderSystem 提供，
        // 所以这里传单位矩阵即可（与 GuiShaderRenderer 的做法一致）
        drawQuad(x, y, barW, barH);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════════
    // ② 框架：直接贴 outline
    // ══════════════════════════════════════════════════════════════

    private static void drawOutline(int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        RenderSystem.setShaderTexture(0, OUTLINE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        drawQuad(x, y, barW, barH);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════

    /** 发一个 UV 0..1 的矩形（左上角在 x,y），两张贴图都是整张贴满。 */
    private static void drawQuad(int x, int y, int w, int h) {
        Matrix4f pose = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x,         y,         0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(pose, x,         y + h,     0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w,     y + h,     0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w,     y,         0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.safeGetUniform(name).set(value);
    }
}
