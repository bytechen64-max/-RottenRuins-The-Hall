package org.bytechen.rottenruins.client.rend.gui;

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
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.Optional;

/**
 * 坍缩使徒的自定义 boss 血条：纯黑底 + 着色器星尘（{@code hall:collapsar_bar}），框架图最后压在内容之上。
 * <p>mask 图（{@code collapsar_health_fullmask.png}）的红通道决定哪里是血条内部（白=显示、黑=不显示，与
 * {@code void_sword_mask} 同一套约定），所以条形的形状/粗细全在贴图里，代码没有硬编码像素几何。
 * <p>挂在 {@code CustomizeGuiOverlayEvent.BossEventProgress} 上接管原版逐条绘制；靠名字认出"这条是我的"
 * （{@code LerpingBossEvent} 的 id 是原版随机 UUID，跟实体对不上）。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CollapsarBossBar {

    /** 贴图基准尺寸：182 正好等于原版 boss 条宽度（横向对齐用）。 */
    private static final int TEX_W = 182;
    private static final int TEX_H = 32;

    /** 贴图比例离谱时的绘制高度夹取范围：宽度锁死在 {@link #TEX_W}（与原版条横向对齐），高度按贴图比例推导。 */
    private static final int MIN_BAR_H = 8;
    private static final int MAX_BAR_H = 64;

    /** 实际绘制尺寸（GUI 像素），由贴图真实尺寸在 {@link #inspectMaskOnce()} 里推导（写死 182×32 会把别的比例图拉伸）。 */
    private static int barW = TEX_W;
    private static int barH = TEX_H;
    /** 贴图真实宽高比，喂给着色器把星点画成圆而不是椭圆。 */
    private static float barAspect = TEX_W / (float) TEX_H;

    /** 星尘密度倍数：1.0 = 着色器基准密度。<b>不建议超过 1.25</b>，否则两层都"每格必有星"，观感变成规则网点阵。 */
    private static final float STAR_DENSITY = 1.0F;
    /** 星尘亮度倍数。密度已经调高，亮度保持 1.0 免得整条糊成一片白。 */
    private static final float STAR_STRENGTH = 1.0F;

    private static final ResourceLocation MASK = ResourceLocation.fromNamespaceAndPath(
            HallMod.MODID, "textures/gui/collapsar_health_fullmask.png");
    private static final ResourceLocation OUTLINE = ResourceLocation.fromNamespaceAndPath(
            HallMod.MODID, "textures/gui/collapsar_health_outline.png");

    private CollapsarBossBar() {}

    @SubscribeEvent
    public static void onBossBarProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        ShaderInstance shader = SplendidingShaders.collapsarBarShader;
        String expected = EntityTypeRegistry.COLLAPSAR.get().getDescription().getString();
        String actual = event.getBossEvent().getName().getString();
        boolean mine = expected.equals(actual);

        // 一次性诊断：血条不显示时，这行日志能区分"事件没来 / 名字对不上 / 问题在绘制或着色器"三种情况
        if (!loggedFirstEvent) {
            loggedFirstEvent = true;
            HallMod.LOGGER.info("[CollapsarBossBar] 首个 boss 血条事件: name='{}' expected='{}' 接管={} shader={}",
                    actual, expected, mine, shader != null ? "ok" : "NULL");
        }

        // 着色器没起来（资源/驱动问题）就让原版条照常显示：总比"取消了原版绘制但自己也画不出来"好
        if (shader == null) return;
        if (!mine) return;

        event.setCanceled(true);          // 接管原版绘制

        // mask 体检必须放在取 barH 之前 —— 首帧就要用对尺寸，否则多血条布局会先跳一下
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

        // 血量越低抖得越厉害：整条（槽 + 内容 + 框架）一起位移才像"整条在抖"
        float shake = shakeAmount(fill);
        int dx = 0;
        int dy = 0;
        if (shake > 0.0F) {
            dx = Math.round(jitter(time, 0.0F, 1.00F) * SHAKE_MAX_X * shake);
            dy = Math.round(jitter(time, 5.0F, 1.31F) * SHAKE_MAX_Y * shake);
        }
        int bx = x + dx;
        int by = y + dy;

        // 黑槽：即使着色器整条失效，血条也还有"一条黑槽"这个形态而不是一片空白。范围取 mask 亮行范围
        gfx.fill(bx, by + Math.round(bandMin * barH),
                 bx + barW, by + Math.round(bandMax * barH), 0xFF000000);

        // 下面两次是 BufferUploader 立即绘制，先把 GuiGraphics 里压着的 HUD 顶点刷出去，否则它们会盖在血条上
        gfx.flush();

        drawContent(shader, bx, by, fill, time, shake);
        drawOutline(bx, by);
        gfx.flush();
    }

    /** 从多少血量比例开始抖（含以上不抖）。 */
    private static final float SHAKE_START = 0.55F;
    /** 抖到最大幅度的血量比例。 */
    private static final float SHAKE_FULL = 0.12F;
    /** 最大水平位移（GUI 像素） */
    private static final float SHAKE_MAX_X = 2.6F;
    /** 最大垂直位移（GUI 像素） */
    private static final float SHAKE_MAX_Y = 1.5F;

    /** 颤抖强度：0 = 不抖，1 = 抖到最大。把 {@code [SHAKE_FULL, SHAKE_START]} 映射到 {@code [1, 0]}，再低不会更夸张。 */
    private static float shakeAmount(float fill) {
        if (fill >= SHAKE_START) return 0.0F;
        return Mth.clamp((SHAKE_START - fill) / (SHAKE_START - SHAKE_FULL), 0.0F, 1.0F);
    }

    /**
     * 抖动函数：三个不可通约频率的正弦叠加，返回约 [-1, 1]。不用 {@code Random}：每帧独立取随机会变成"闪"
     * （高频噪点，看着像掉帧），连续但无规律的位移观感才是"抖"。
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
     * 读一遍 mask PNG，算出亮像素的行范围并记日志。mask 是"血条内部"的唯一来源，采样回来是 0 就会"框架在、里面全空"
     * ——最贵的静默失败，所以行范围既当着色器兜底带、也给黑槽定位置。
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

                applyTextureSize(w, h);
            }
            inspectOutlineSizeOnce();
        } catch (Throwable t) {
            maskOk = false;
            HallMod.LOGGER.warn("[CollapsarBossBar] mask 读取失败，改用几何兜底带 [{}, {}]: {}",
                    bandMin, bandMax, t.toString());
        }
    }

    /** 按贴图真实尺寸推导绘制尺寸：宽锁 {@link #TEX_W}，高按比例换算（这才是换图不变形的关键），离谱比例夹到 [MIN, MAX] 并记日志。 */
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

        // 只有 setSampler 过的 sampler 才会在每次绘制时被显式绑定；只靠 setShaderTexture(0, MASK) 时，
        // 中间任何一次别的纹理绑定都会让它读到空白（症状正是"框架在、里面全空"）。传 AbstractTexture 链路才闭合。
        AbstractTexture maskTexture = Minecraft.getInstance().getTextureManager().getTexture(MASK);
        shader.setSampler("MaskTexture", maskTexture);
        RenderSystem.setShaderTexture(0, MASK);
        RenderSystem.setShader(() -> shader);

        // 顶点直接用 GUI 缩放坐标：ProjMat / ModelViewMat 由 RenderSystem 提供，所以这里传单位矩阵即可
        drawQuad(x, y, barW, barH);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

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
