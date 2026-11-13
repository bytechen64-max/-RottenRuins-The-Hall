package org.bytechen.rottenruins.client.gui.creative;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.text.TextEffectText;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 创造物品栏的「分区隔断行」：流动色带（{@code hall:gui_tab_divider}）+ 压在上面的流动彩色标题。
 * 挂 Foreground 而非 Background：隔断行的空槽位会被原版画一个 16×16 白色悬停高亮，会盖住 Background 的色带。
 * 立即绘制时先 {@code gui.flush()}、uniform 设完就画、画完还原 RenderSystem；最终混合由 json 的 {@code "blend"} 决定（在 {@code apply()} 里，晚于 RenderSystem）。
 * 着色器为 null 时退回纯色细线 + 同款标题，分区功能不能因着色器缺失而失效。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreativeTabDividerRenderer {

    private CreativeTabDividerRenderer() {}

    // 几何数从原版 tab_items.png 量出：槽位井每格 18×18，井外框 x ∈ [8, 170)、y ∈ [17 + 18i, 35 + 18i)。
    // 必须盖住井外框 —— 只盖井内区会在左、上各留 1px 缝，底下还多压 1px 到下一行。
    private static final int GRID_X = 8;
    /** 9 个井的总宽：9 × 18 = 162。 */
    private static final int GRID_W = 162;
    private static final int GRID_Y = 17;
    private static final int ROW_H = 18;

    // 调色板（陈金 / 病绿青 / 暗绯红）
    private static final float[] PALETTE_A = { 0.79F, 0.63F, 0.15F, 1.00F };
    private static final float[] PALETTE_B = { 0.27F, 0.78F, 0.62F, 0.85F };
    private static final float[] PALETTE_C = { 0.60F, 0.16F, 0.26F, 0.70F };
    private static final float INTENSITY = 1.0F;
    private static final float GLOW = 0.6F;

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

        // 用墙钟而不是 gameTime：单人游戏里开着界面就是暂停的；取模避免 float 在大数值上丢精度。
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;

        if (SplendidingShaders.guiTabDividerShader == null) {
            // 这里是**面板相对坐标**（gui.fill 走 PoseStack），与 drawBars 的绝对屏幕坐标不是一个空间；
            // 降级也铺满整行，只画一条细线会让这一行读起来是空的。
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
            drawFlowingTitle(gui, font, title,
                    GRID_X + GRID_W / 2, y + (ROW_H - font.lineHeight) / 2);
        }
    }

    //  色带

    /**
     * 一次 draw call 把视野里所有隔断行的色带画掉。这里的 {@code left/top} 是绝对的屏幕 GUI 坐标：
     * {@code drawWithShader} 走单位阵、PoseStack 没参与，而 Foreground 派发前已 push 了 {@code (leftPos, topPos)} 平移 ——
     * 两套坐标混用就是「色带位置对、文字整块飘到面板外」，改的时候别只改一边。
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

        // 先把自己人刷出去，否则压着的背景/格子高亮会盖在这层之上
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

        // 还原状态，后面还有 tooltip / 拖拽物品要画
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    //  标题

    // 标题走 § 分节符的 rainbow 特效（与 tooltip / 聊天文字同一套实现）。centerX / y 是「相对面板左上角」坐标：
    // Foreground 在 push (leftPos, topPos) 之后才派发，传绝对坐标会被平移两次、飘到面板外。开头垫 §6 让特效关闭时标题仍是金色。
    private static void drawFlowingTitle(GuiGraphics gui, Font font, Component title,
                                         int centerX, int y) {
        String text = title.getString();
        if (text.isEmpty()) {
            return;
        }
        Component flowing = Component.literal(
                "§6" + TextEffectText.open("rainbow:sat=0.45:speed=2.2") + text + TextEffectText.close());
        int x = centerX - font.width(flowing) / 2;
        // 带投影：色带本身很花，不加一层暗描边会读不清
        gui.drawString(font, flowing, x, y, 0xFFFFFFFF, true);
    }

    //  uniform 写入：两层保护（与 TooltipShaders 同写法）。① 先查 getUniform != null —— json 少声明一个 uniform 时
    //  safeGetUniform 给的是「哑」对象；② try/catch —— 给 int 型 uniform 写 float 会直接 NPE，判空挡不住。

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
