package org.bytechen.hall.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.TooltipShaderSpec;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

/**
 * tooltip 底板的着色器效果：解析 {@link TooltipShaderSpec#key()} 并把它画出来。
 *
 * <h3>为什么是"立即绘制"而不是 RenderType</h3>
 * <p>和坍缩使徒血条（{@code client/rend/gui/CollapsarBossBar}）走同一条路：
 * GUI 空间里画一小块自绘内容，用 {@code BufferUploader.drawWithShader} 直接提交，
 * 不建 RenderType —— 少一套 StateShard，也就不用给 RenderType 的
 * 剔除/深度/写掩码各配一遍（那套配置的默认值很容易把方块整个剔没）。</p>
 *
 * <h3>三条必须守住的规矩（都是踩过的）</h3>
 * <ol>
 *   <li><b>先 {@code gui.flush()} 再立即绘制。</b> {@code GuiGraphics} 的批处理里
 *       可能还压着先前 GUI 元素的顶点，它们要等下一次 flush 才提交 —— 不先刷出去，
 *       它们会盖在我们这层之上。（血条那次的现象是"框架在、里面全空"。）</li>
 *   <li><b>uniform 设完再画，中间不要插别的绘制。</b> {@code Uniform.set()} 只是记值，
 *       真正上传发生在 {@code ShaderInstance.apply()}（也就是这次 draw）——
 *       攒着写会把上一份值用到下一次绘制里（见 {@code docs/mask-layers.md} 第 8 节）。</li>
 *   <li><b>画完把 RenderSystem 状态还回去。</b> tooltip 后面还要画原版底板、边框和文字，
 *       留着一个 {@code depthMask(false)} + 关着混合的状态，后面那些都会受影响。</li>
 * </ol>
 *
 * <h3>着色器 json 里的 blend 才是生效的那份</h3>
 * <p>{@code ShaderInstance.apply()} 会按 json 里的 {@code "blend"} 调 {@code BlendMode.apply()}，
 * 而它发生在 RenderType/手动状态之后，所以 <b>json 赢</b>。本效果 json 写的是
 * {@code srcalpha / 1-srcalpha}（标准 alpha 混合），与这里的
 * {@code defaultBlendFunc()} 一致；json 里漏写 {@code "blend"} 会退回"关闭混合"，
 * 症状是把自己原样写进帧缓冲（一块实心方块）。</p>
 *
 * <h3>json 里的 {@code depthtest} / {@code depthwrite} 是不生效的</h3>
 * <p>1.20.1 的 {@code ShaderInstance} 只解析 {@code vertex} / {@code fragment} /
 * {@code samplers} / {@code attributes} / {@code uniforms} / {@code blend} 六个键
 * （见 {@code ShaderInstance.java} 的构造段），深度相关的那两个键是更早版本格式的遗留。
 * 所以<b>深度只能靠这里的 {@code RenderSystem} 调用</b>（或 RenderType 的
 * WriteMask / DepthTest state）—— 别以为在 json 里写了 {@code "depthtest": "always"}
 * 就万事大吉（项目里好几个 json 都留着这两个键，属于历史写法）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TooltipShaders {

    private TooltipShaders() {}

    /**
     * 热羽的特征尺寸（GUI 像素）。调大 = 大团的热浪，调小 = 细密的丝。
     * 它是"像素"而不是 uv，所以面板大小变了热羽粗细不变。
     */
    private static final float THERMAL_SCALE = 96.0F;

    /** 对流速度（噪声空间/秒）：约 15 GUI 像素/秒的上升。 */
    private static final float THERMAL_SPEED = 0.16F;

    /**
     * 这个 key 现在能不能画。
     *
     * <p>{@code false} 时调用方要退回纯色底板 —— 着色器加载失败（驱动老旧、资源包
     * 覆盖了 shader）不该表现成"tooltip 变成一块透明玻璃"。</p>
     */
    public static boolean isReady(String key) {
        return TooltipShaderSpec.THERMAL.equals(key) && SplendidingShaders.tooltipThermalShader != null;
    }

    /**
     * 在 {@code (x, y, w, h)} 这块矩形上画效果。
     *
     * <p>调用方负责算好矩形 —— 对 tooltip 来说就是
     * {@code (contentX - 4, contentY - 4, contentW + 8, contentH + 8)}，
     * 与 {@code TooltipRenderUtil} 涂到的范围一致。</p>
     *
     * @param time 秒。用游戏时间 + 帧插值（不是 {@code System.nanoTime}），
     *             这样暂停/单步时热流也会停住。
     */
    public static void draw(String key, GuiGraphics gui, int x, int y, int w, int h,
                            TooltipShaderSpec spec, float time) {
        if (!isReady(key)) return;
        if (TooltipShaderSpec.THERMAL.equals(key)) {
            drawThermal(gui, x, y, w, h, spec, time);
        }
        // 以后加第二个效果就在这里开分支；key 与图案的对应关系保持"一个 key 一个方法"，
        // 别做成表 —— 表会让"这个 key 到底画什么"更难查（与 ICustomOutline.outlineShaderKey 同思路）。
    }

    // ──────────────────────────────────────────────────────────────
    //  thermal：下方加热的板，热羽上升
    // ──────────────────────────────────────────────────────────────

    private static void drawThermal(GuiGraphics gui, int x, int y, int w, int h,
                                    TooltipShaderSpec spec, float time) {
        ShaderInstance shader = SplendidingShaders.tooltipThermalShader;
        if (shader == null) return;

        set1(shader, "uTime", time);
        set1(shader, "uSpeed", THERMAL_SPEED);
        set1(shader, "uScale", THERMAL_SCALE);
        set1(shader, "uIntensity", spec.intensity());
        set2(shader, "uPanelSize", w, h);
        set4(shader, "uBaseColor", spec.baseColor());
        set4(shader, "uFlowColor", spec.flowColor());
        set4(shader, "uHotColor", spec.hotColor());

        // ① 先把自己人刷出去（见类注释第 1 条）
        gui.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();      // SRC_ALPHA / ONE_MINUS_SRC_ALPHA
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        // 着色器自己决定颜色，这里保证 shader color 不带着上一处的 tint
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        drawQuad(x, y, w, h);

        // ③ 还原状态，后面还有原版底板/边框/文字要画
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 发一个 UV 0..1 的矩形（左上角在 {@code x, y}）。
     *
     * <p>顶点直接用 GUI 缩放坐标，<b>矩阵用单位阵</b> —— {@code ProjMat} /
     * {@code ModelViewMat} 由 RenderSystem 提供（与 {@code CollapsarBossBar.drawQuad}
     * 和 {@code GuiShaderRenderer} 一致）。uv.y = 0 在上沿，着色器里
     * "热源在下沿"就是靠这个约定。</p>
     */
    private static void drawQuad(int x, int y, int w, int h) {
        Matrix4f pose = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x,     y,     0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(pose, x,     y + h, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w, y + h, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w, y,     0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    // ──────────────────────────────────────────────────────────────
    //  uniform 写入（uniform 名与 json 一一对应，缺一个就静默不生效）
    // ──────────────────────────────────────────────────────────────
    //
    // 两层保护，都不是多余的：
    //   ① 先查 getUniform(name) != null —— json 少声明一个 uniform 时
    //      safeGetUniform 会给一个"哑"对象，写进去毫无反应（与 CollapsarBossBar 同写法）；
    //   ② 外面再包一层 catch —— Uniform 的构造器按类型分配缓冲：int 类型只分配
    //      intValues、floatValues 是 null，而 set(float...) 无条件访问 floatValues，
    //      于是"给 int 型 uniform 写 float"会直接 NPE 把客户端带崩，判空完全挡不住
    //      （DifficultySelectScreen 踩过这一次）。本效果 json 里全是 float，
    //      但以后有人改 json 的 type 时不该表现成崩溃 —— 静默跳过 + 只报一次日志。

    private static void set1(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void set2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(x, y);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    /** ARGB → 归一化 RGBA。alpha 参与：底色的透明度就是靠它。 */
    private static void set4(ShaderInstance shader, String name, int argb) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(
                    ((argb >> 16) & 0xFF) / 255.0F,
                    ((argb >> 8) & 0xFF) / 255.0F,
                    (argb & 0xFF) / 255.0F,
                    ((argb >>> 24) & 0xFF) / 255.0F);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    /** 每个出问题的 uniform 只报一次，避免每帧刷日志。 */
    private static final java.util.Set<String> WARNED_UNIFORMS = new java.util.HashSet<>();

    private static void warnUniformOnce(String name, Throwable t) {
        if (WARNED_UNIFORMS.add(name)) {
            HallMod.LOGGER.warn("[TooltipThermal] uniform '{}' 上传失败"
                    + "（多半是 json 里的 type 与 shader 声明不一致）：{}", name, t.toString());
        }
    }
}
