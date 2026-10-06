package org.bytechen.hall.client.gui.creative;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 画创造物品栏里的「分区隔断行」：一条色彩流动的色带（{@code hall:gui_tab_divider}）
 * + 压在上面的流动彩色标题。
 *
 * <h3>为什么挂在 Foreground 而不是 Background</h3>
 * <p>Forge 的 {@code ContainerScreenEvent.Render.Foreground} 在
 * 「槽位与悬停高亮都画完、tooltip 与手上拖着的物品还没画」的那个缝里触发
 * （见 {@code AbstractContainerScreen#render}）。隔断行的格子是空的，
 * 原版会给悬停的空槽画一个 16×16 的白色高亮方块 —— 挂在 Background
 * 会被那个方块盖在我们的色带上。挂在 Foreground 刚好反过来：色带压住白框，
 * 而 tooltip / 拖拽中的物品仍然在最上层。</p>
 *
 * <h3>为什么是"立即绘制"而不是 RenderType</h3>
 * <p>和 tooltip 底板（{@code client/tooltip/TooltipShaders}）、坍缩使徒血条走同一条路：
 * GUI 里画几块自绘内容，用 {@code BufferUploader.drawWithShader} 直接提交。
 * 少一套 StateShard，也就不用给剔除/深度/写掩码各配一遍默认值。三条规矩照抄那边：</p>
 * <ol>
 *   <li><b>先 {@code gui.flush()}。</b>GUI 批处理里可能还压着背景、标签页、格子高亮的顶点，
 *       不先刷出去它们会盖在我们这层之上。</li>
 *   <li><b>uniform 设完就画，中间不插别的绘制。</b>{@code Uniform.set()} 只是记值，
 *       真正上传发生在这次 draw 的 {@code ShaderInstance.apply()} 里。</li>
 *   <li><b>画完把 RenderSystem 状态还回去。</b>后面还要画 tooltip 和拖着的东西。</li>
 * </ol>
 * <p>还有一条从 tooltip 那边学来的：json 里的 {@code "blend"} 才是最终生效的混合，
 * 它发生在 {@code ShaderInstance.apply()} 里、比这里的 {@code RenderSystem} 调用更晚。</p>
 *
 * <h3>着色器加载失败时不许"消失"</h3>
 * <p>驱动老旧或资源包覆盖了 shader 时 {@link SplendidingShaders#guiTabDividerShader} 会是 null。
 * 那种情况退回一条纯色细线 + 同样的标题 —— 分区功能本身（找东西）不能因为着色器没了就失效。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreativeTabDividerRenderer {

    private CreativeTabDividerRenderer() {}

    // ── 几何：与创造网格对齐 ──
    //
    // 这一组数不是"估"出来的，是从原版 tab_items.png 里量出来的：
    //   槽位井每格 18×18，井内区正好 16×16 落在 (9 + 18j, 18 + 18i) 上，
    //   于是井的左/上边框在 texture (8, 17)、右/下边框在 (25, 34)。
    // 隔断行要"铺满一整行"就必须盖住这 9 个井的**外框**，也就是
    //   x ∈ [8, 8 + 9*18) = [8, 170)、y ∈ [17 + 18i, 35 + 18i)。
    // 只盖井内区（[9,169) × [18,36)）会在左、上各留 1px 缝，底下还多压 1px 到下一行。
    /** 9 个槽位井的整体左边界（槽位 x = 9 + 列 * 18，井比槽位向左多 1px）。 */
    private static final int GRID_X = 8;
    /** 9 个井的总宽：9 × 18 = 162。 */
    private static final int GRID_W = 162;
    /** 网格上沿的井边框（槽位 y = 18 + 行 * 18，井比槽位向上多 1px）。 */
    private static final int GRID_Y = 17;
    private static final int ROW_H = 18;

    // ── 调色板（陈金 / 病绿青 / 暗绯红）──
    private static final float[] PALETTE_A = { 0.79F, 0.63F, 0.15F, 1.00F };
    private static final float[] PALETTE_B = { 0.27F, 0.78F, 0.62F, 0.85F };
    private static final float[] PALETTE_C = { 0.60F, 0.16F, 0.26F, 0.70F };
    private static final float INTENSITY = 1.0F;
    private static final float GLOW = 0.6F;

    /** 标题的流动速度（圈/秒）与沿条的色相梯度（圈/像素）。 */
    private static final float TEXT_FLOW_SPEED = 0.16F;
    private static final float TEXT_FLOW_GRADIENT = 0.0055F;

    /** 降级填充色（着色器没加载时那一整行的暗金底）。 */
    private static final int FALLBACK_FILL = 0x70C9A227;

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof SectionedCreativeScreen screen)) {
            return;
        }
        Map<Integer, Component> dividers = screen.splendiding$dividerRows();
        if (dividers.isEmpty()) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        int left = event.getContainerScreen().getGuiLeft();
        int top = event.getContainerScreen().getGuiTop();
        int scrollRow = screen.splendiding$scrollRow();

        // 屏幕上只有 5 行，把落在视野里的隔断行挑出来（通常是 0~2 行）
        List<Integer> visible = new ArrayList<>(dividers.size());
        for (int local = 0; local < 5; local++) {
            if (dividers.containsKey(scrollRow + local)) {
                visible.add(local);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        // 时间用墙钟而不是游戏时间，理由和 GuiShaderRenderer 一样：
        // 这里在单人游戏里开着界面就是暂停的，用 gameTime 会让色带整个冻住。
        // 取模是为了别让 float 在大数值上丢精度（一小时后回绕一次，没人会盯着看）。
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;

        if (SplendidingShaders.guiTabDividerShader == null) {
            // ↓↓ 注意这里是**面板相对坐标**，和上面 drawBars 的绝对坐标不是一个空间，见 drawFlowingText 的注释。
            // 降级也铺满整行，和着色器那条缎带的占位保持一致（只画一条细线会让这一行读起来是空的）。
            for (int local : visible) {
                int y = GRID_Y + local * ROW_H;
                gui.fill(GRID_X, y, GRID_X + GRID_W, y + ROW_H, FALLBACK_FILL);
            }
        } else {
            drawBars(gui, SplendidingShaders.guiTabDividerShader, left, top, visible, time);
        }

        Font font = Minecraft.getInstance().font;
        for (int local : visible) {
            Component title = dividers.get(scrollRow + local);
            if (title == null) {
                continue;
            }
            int y = GRID_Y + local * ROW_H;
            drawFlowingText(gui, font, title.getString(),
                    GRID_X + GRID_W / 2, y + (ROW_H - font.lineHeight) / 2, time);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  色带
    // ──────────────────────────────────────────────────────────────

    /**
     * 一次 draw call 把视野里所有隔断行的色带画掉。
     *
     * <p><b>这里的 {@code left/top} 是绝对的屏幕 GUI 坐标</b>，因为它走
     * {@code BufferUploader.drawWithShader}：顶点矩阵是单位阵，{@code ProjMat} /
     * {@code ModelViewMat} 由 RenderSystem 给，{@code GuiGraphics} 的 PoseStack
     * <b>完全没参与</b>。同一帧里 {@code gui.fill} / {@code gui.drawString} 却会把
     * PoseStack 烘进顶点 —— 而 {@code AbstractContainerScreen#render} 在派发
     * Foreground 之前已经 push 了一个 {@code (leftPos, topPos)} 平移。
     * 两套坐标混用就是"色带位置对、文字整块飘到面板外"的原因，改的时候别只改一边。</p>
     */
    private static void drawBars(GuiGraphics gui, ShaderInstance shader, int left, int top,
                                 List<Integer> visibleRows, float time) {
        set1(shader, "uTime", time);
        set2(shader, "uSize", GRID_W, ROW_H);
        set4(shader, "uColorA", PALETTE_A);
        set4(shader, "uColorB", PALETTE_B);
        set4(shader, "uColorC", PALETTE_C);
        set1(shader, "uIntensity", INTENSITY);
        set1(shader, "uGlow", GLOW);

        // ① 先把自己人刷出去（见类注释第 1 条）
        gui.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        // 着色器自己算颜色，这里保证不带上一处的 tint
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        // 所有色带几何一样、uniform 一样，合成一次 draw call
        Matrix4f pose = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x1 = left + GRID_X;
        float x2 = x1 + GRID_W;
        for (int local : visibleRows) {
            float y1 = top + GRID_Y + local * ROW_H;
            float y2 = y1 + ROW_H;
            buffer.vertex(pose, x1, y1, 0.0F).uv(0.0F, 0.0F).endVertex();
            buffer.vertex(pose, x1, y2, 0.0F).uv(0.0F, 1.0F).endVertex();
            buffer.vertex(pose, x2, y2, 0.0F).uv(1.0F, 1.0F).endVertex();
            buffer.vertex(pose, x2, y1, 0.0F).uv(1.0F, 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // ③ 还原状态，后面还有 tooltip / 拖拽物品要画
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ──────────────────────────────────────────────────────────────
    //  标题
    // ──────────────────────────────────────────────────────────────

    /**
     * 逐字画标题，每个字一个随时间流动的色相 —— 于是标题本身也在"流淌"，
     * 和底下那条色带是同一套节奏。
     *
     * <p><b>{@code centerX / y} 是"相对面板左上角"的坐标，不是屏幕绝对坐标。</b>
     * {@code GuiGraphics.drawString} 会把当前 PoseStack 烘进顶点，而 Foreground 事件
     * 是在 {@code AbstractContainerScreen} push 了 {@code (leftPos, topPos)} 平移之后派发的
     * —— 这里再传一次绝对坐标，文字就会被平移两次、整块飘到面板外面去
     * （第一次做这个功能时就是这么翻的，截图里"畸骸与王庭生物"跑到了世界渲染区）。
     * 同一屏里的 {@code renderLabels}（标签标题画在 8, 6）也是这个相对口径。</p>
     *
     * <p>用 {@code font.width(String)} 量总宽来居中，逐字用 {@code font.width(单字)}
     * 推进 x。原版字体对普通字符的宽度是可加的，累计误差不会超过一像素。</p>
     */
    private static void drawFlowingText(GuiGraphics gui, Font font, String text,
                                        int centerX, int y, float time) {
        if (text.isEmpty()) {
            return;
        }
        int x = centerX - font.width(text) / 2;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            int advance = font.width(glyph);
            // 沿条的横向位置给一点相位差，让色相是"扫过去"而不是"整条一起闪"
            float hue = (time * TEXT_FLOW_SPEED + (x - centerX) * TEXT_FLOW_GRADIENT) % 1.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
            int rgb = Mth.hsvToRgb(hue, 0.45F, 1.0F);
            // 带投影：色带本身很花，不加一层暗描边会读不清
            gui.drawString(font, glyph, x, y, 0xFF000000 | rgb, true);
            x += advance;
            i += Character.charCount(cp);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  uniform 写入
    // ──────────────────────────────────────────────────────────────
    //  两层保护，与 TooltipShaders 同写法：
    //   ① 先查 getUniform(name) != null —— json 少声明一个 uniform 时
    //      safeGetUniform 给的是"哑"对象，写进去毫无反应；
    //   ② try/catch —— Uniform 按类型分配缓冲，给一个 int 型 uniform 写 float
    //      会直接 NPE 把客户端带崩，判空挡不住（DifficultySelectScreen 踩过）。

    private static void set1(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static void set2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(x, y);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static void set4(ShaderInstance shader, String name, float[] rgba) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(rgba[0], rgba[1], rgba[2], rgba[3]);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    private static void warnOnce(String name, Throwable t) {
        if (WARNED.add(name)) {
            HallMod.LOGGER.warn("[CreativeTabDivider] uniform '{}' 上传失败"
                    + "（多半是 json 里的 type 与 shader 声明不一致）：{}", name, t.toString());
        }
    }
}
