package org.bytechen.rottenruins.api;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Items implementing this interface gain automatic outline + GUI glint
 * rendering without any external registration.
 * <p>
 * The mixin system checks each rendered item — if the item implements
 * {@code ICustomOutline}, outline/glint parameters are read directly
 * from the item instance.  No calls to {@code GlintRenderManager} or
 * {@code HeldItemGlintHelper} are needed.
 * <p>
 * Minimal example:
 * <pre>{@code
 * public class MyItem extends Item implements ICustomOutline {
 *     @Override public int outlineColor() { return 0xFFFF6600; }
 * }
 * }</pre>
 * <p>
 * 渐变描边示例（主色↔副色 沿物品表面流动）：
 * <pre>{@code
 * public class GradientSword extends Item implements ICustomOutline {
 *     @Override public int outlineColor() { return 0xFFFF6600; }
 *     @Override public int outlineSecondaryColor() { return 0xFF23F0FF; }
 *     @Override public String outlineShaderKey() { return "gradient"; }
 *     @Override public float outlinePixelWidth() { return 2.0f; }
 * }
 * }</pre>
 *
 * <p>渲染实现是屏幕空间的「剪影遮罩 + 环形膨胀」：描边只出现在物品剪影<b>外侧</b>，
 * 因此它和贴在物品表面的宇宙星空层永不重叠。见
 * {@code org.bytechen.rottenruins.client.rend.glint.ItemOutlinePipeline}。</p>
 */
public interface ICustomOutline {

    // ─── outline ─────────────────────────────────────────────

    /** Outline colour as ARGB int (e.g. {@code 0xFFFF6600}). Required. */
    int outlineColor();

    /**
     * 实例级覆写：默认退回 {@link #outlineColor()}。
     *
     * <p>为什么需要这一层：{@code outlineColor()} 这类方法<b>拿不到 ItemStack</b>，
     * 于是"同一件物品因为改名 / 附魔 / NBT 而表现不同"根本表达不出来。
     * 需要按实例变化的物品覆写带 {@code stack} 的这几个方法即可，管线优先用它们。</p>
     */
    default int outlineColorFor(ItemStack stack) {
        return outlineColor();
    }

    /** 实例级覆写：默认退回 {@link #outlineSecondaryColor()}。 */
    default int outlineSecondaryColorFor(ItemStack stack) {
        return outlineSecondaryColor();
    }

    /** 实例级覆写：默认退回 {@link #outlineShaderKey()}。 */
    default String outlineShaderKeyFor(ItemStack stack) {
        return outlineShaderKey();
    }

    /** 实例级覆写：默认退回 {@link #outlineBlend()}。 */
    default BlendMode outlineBlendFor(ItemStack stack) {
        return outlineBlend();
    }

    /** @return true to enable outline for the given context. Default: all world contexts. */
    default boolean outlineEnabled(ItemDisplayContext ctx) {
        return switch (ctx) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND,
                 GROUND, NONE, FIXED -> true;
            default -> false;
        };
    }

    /**
     * 描边向外扩展的宽度，单位是 <b>屏幕像素</b>。默认 2.5。
     *
     * <p>它同时是宽度的<b>上限</b>：默认的 {@link OutlineWidthMode#DISTANCE} 下，实际宽度取
     * {@code min(本值, 物品表观尺寸 × SplendidingConfig.outlineWidthMaxRatio)}，
     * 再夹在 {@code SplendidingConfig.outlineMinPixelWidth} 之上（表观尺寸 = 物品包围盒对角线
     * 按当前距离换成的像素数）。于是物品在屏幕上够大时就是本值（近处恒定像素宽，与旧行为逐位一致），
     * 只有物品小到环会占去它一成多时环才按比例收细 ——
     * 否则远处的环相对物品过粗，观感是糊成一圈外扩的厚边。</p>
     *
     * <p>想要旧的「任何距离都一样粗」，把 {@link #outlineWidthMode()} 返回
     * {@link OutlineWidthMode#SCREEN}；想整体加粗/变细用 {@code SplendidingConfig.outlineWidthScale}。</p>
     *
     * <p>描边只向外长，不会侵占物品表面 —— 贴在表面的宇宙星空层因此不受影响。</p>
     */
    default float outlinePixelWidth() { return 2.5f; }

    /**
     * 描边宽度怎么算。
     *
     * <ul>
     *   <li>{@link OutlineWidthMode#SCREEN} —— 恒定屏幕像素宽（旧行为）。</li>
     *   <li>{@link OutlineWidthMode#DISTANCE} —— 屏幕像素宽，但按物品的屏幕占比收缩（默认）。</li>
     * </ul>
     */
    enum OutlineWidthMode {
        /** 恒定屏幕像素宽：远处的物品上，环会相对过粗。 */
        SCREEN,
        /** 近处与 {@link #SCREEN} 逐位一致，远处按「物品表观尺寸 × 比例」收细。 */
        DISTANCE
    }

    /** @return 宽度计算方式。默认 {@link OutlineWidthMode#DISTANCE}。 */
    default OutlineWidthMode outlineWidthMode() { return OutlineWidthMode.DISTANCE; }

    /** 描边整体不透明度。默认 1.0（完全不透明，对齐原版发光描边）。 */
    default float outlineOpacity() { return 1.0f; }

    /**
     * 剪影判定的纹理 alpha 阈值。物品纹理中 alpha 低于它的像素不算物品的一部分。
     * 默认 0.1。调大可以让半透明细节（如飘带、光边）也纳入描边外形。
     */
    default float outlineAlphaCutoff() { return 0.1f; }

    /**
     * 副色，只在 {@link #outlineShaderKey()} 为 {@code "gradient"} 时参与双色流动。
     * 返回 alpha 为 0 表示不启用（退回主色）。默认 0。
     */
    default int outlineSecondaryColor() { return 0; }

    /**
     * @deprecated 旧接口：模型空间缩放系数（默认 0.06 ≈ 放大 1.06×）。
     *     在旧的"放大壳"实现里它既是宽度也是内部染色的元凶。新管线按像素计算宽度，
     *     这个值不再生效，请改用 {@link #outlinePixelWidth()}。
     */
    @Deprecated
    default float outlineWidth() { return 0.06f; }

    /** Blend mode. Default {@link BlendMode#ADDITIVE}. */
    default BlendMode outlineBlend() { return BlendMode.ADDITIVE; }

    /**
     * 颜色模式选择器。它<b>不再</b>对应独立的着色器与 RenderType ——
     * 几条旧的"描边着色器"已经收敛成同一个几何算法，这个 key 只决定颜色怎么算：
     * <ul>
     *   <li>{@code null} / {@code "default"} — 纯色</li>
     *   <li>{@code "gradient"} — 主色↔副色 沿物品表面流动</li>
     *   <li>{@code "warp_fbm"} — 彩虹流动（旧噪声风格的动态观感）</li>
     *   <li>{@code "fire"} — 火焰：噪声湍流 + 白热核（移植自 shadertoy MdjfRK）。
     *       用 {@link #outlineColor()} 当白热核、{@link #outlineSecondaryColor()} 当冷尾；
     *       加法混合下尾部几乎看不见，只剩几缕顺着描边爬的火丝</li>
     *   <li>{@code "fire_2"} — 燃烧描边：<b>世界空间体积火焰</b>。以物品为中心、固定尺寸
     *       （0.9 × 1.0 × 0.9 格）的盒子里 ray marching 3D 噪声，场景深度截断 + Beer 累积。
     *       有视差、范围固定、能被墙遮挡；不是把火画在物品贴图上。
     *       覆盖掉落物 / 展示框 / 一手，GUI 不画。见 {@code docs/item-shader-outline.md} 第十二章</li>
     * </ul>
     */
    default String outlineShaderKey() { return null; }

    /**
     * @deprecated 新管线在遮罩 pass 里直接算颜色，不再把 ShaderInstance 交出去配置。
     *     这个方法仍然会被调用（传的是剪影遮罩着色器），且所有 uniform 名都变了，
     *     所以旧实现里的 {@code ColorCount} / {@code FlowSpeed} 之类设置不会生效。
     *     请改用 {@link #outlineShaderKey()} 与 {@link #outlineSecondaryColor()}。
     */
    @Deprecated
    default void configureOutlineShader(ShaderInstance shader) {}

    /** Convenience: set a float uniform (no‑op if uniform missing). */
    default void setFloatUniform(ShaderInstance s, String name, float value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    /** Convenience: set an int uniform (no‑op if uniform missing). */
    default void setIntUniform(ShaderInstance s, String name, int value) {
        if (s != null && s.getUniform(name) != null) s.safeGetUniform(name).set(value);
    }

    enum BlendMode {
        TRANSLUCENT,  // srcAlpha, 1-srcAlpha
        ADDITIVE,     // srcAlpha, one
    }

    // ─── glint ───────────────────────────────────────────────

    /** Return non‑null to enable automatic GUI glint. Default null (no glint). */
    default GlintSettings glintSettings() { return null; }

    record GlintSettings(
        int color,
        int secondaryColor,
        ColorMode colorMode,
        float speed,
        float intensity,
        float bloomStrength,
        float bloomRadius,
        boolean showInGui,
        boolean showWhenHeld,
        boolean showInWorld
    ) {
        public static final int MAX_PALETTE_COLORS = 8;

        public enum ColorMode {
            SINGLE(0.0f), DUAL_SCROLL(1.0f), RAINBOW(2.0f), AUTO_SAMPLE_SCROLL(3.0f);
            public final float shaderValue;
            ColorMode(float v) { this.shaderValue = v; }
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int color = 0xCC23F0FF, secondary = 0xCCFF40C7;
            private ColorMode mode = ColorMode.SINGLE;
            private float speed = 1f, intensity = 0.8f, bloomStr, bloomR = 1f;
            private boolean inGui = true, held = true, inWorld = true;

            public Builder color(int c) { this.color = c; return this; }
            public Builder secondaryColor(int c) { this.secondary = c; return this; }
            public Builder colorMode(ColorMode m) { this.mode = m; return this; }
            public Builder speed(float s) { this.speed = s; return this; }
            public Builder intensity(float i) { this.intensity = i; return this; }
            public Builder bloomStrength(float b) { this.bloomStr = b; return this; }
            public Builder bloomRadius(float r) { this.bloomR = r; return this; }
            public Builder showInGui(boolean v) { this.inGui = v; return this; }
            public Builder showWhenHeld(boolean v) { this.held = v; return this; }
            public Builder showInWorld(boolean v) { this.inWorld = v; return this; }

            public GlintSettings build() {
                return new GlintSettings(color, secondary, mode, speed, intensity,
                        bloomStr, bloomR, inGui, held, inWorld);
            }
        }
    }
}
