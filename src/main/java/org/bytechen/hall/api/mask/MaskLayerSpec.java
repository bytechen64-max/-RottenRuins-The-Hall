package org.bytechen.hall.api.mask;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 一个 mask 效果层的完整配置（不可变值对象）。
 *
 * <h3>「层」是什么</h3>
 * <p>一张遮罩贴图 + 一个效果着色器 + 一组参数。渲染时每个层都用遮罩烘一个
 * 物品 quad，交给该效果自己的着色器画一遍；层与层之间只共用物品本体写下的
 * 深度，彼此不覆盖 —— 于是 cosmic 星空、泛光、以及以后新增的任何效果可以
 * <b>同时挂在一件物品上</b>，而不是互相顶掉。</p>
 *
 * <h3>遮罩约定</h3>
 * <p>与 cosmic 层完全一致：遮罩贴图的 <b>红通道</b>决定该层在哪里生效
 * （白 = 生效，黑 = 不生效），alpha 通道不参与运算。同一张遮罩可以同时喂给
 * 多个层（比如星空和泛光共用一把剑的剪影）。</p>
 *
 * <h3>order 与绘制顺序</h3>
 * <p>同一物品上的多个层按 {@code order} <b>升序</b>绘制，默认
 * {@value #DEFAULT_ORDER}。整个物品的层序是固定的：
 * 本体纹理 → cosmic 星空 → 本系统的各层（按 order）→ 崩坏层。
 * 想让某个层压在别的层下面，把 order 调小即可。</p>
 *
 * @see org.bytechen.hall.api.mask.MaskLayerProvider
 * @see org.bytechen.hall.client.mask.MaskLayerRegistry
 */
public record MaskLayerSpec(
        /** 效果 id，对应一个已注册的效果实现（见 {@code MaskEffectPass}）。 */
        String effectId,
        /** 遮罩贴图（方块图集里的 sprite 名，例如 {@code hall:item/void_sword_mask}）。 */
        ResourceLocation mask,
        /** 主色，ARGB。 */
        int color,
        /** 强度倍率，1.0 = 效果自己的基准亮度。 */
        float intensity,
        /** 效果的作用宽度（像素，效果自己解释；泛光里是光晕外扩半径）。 */
        float width,
        /** 动画速度倍率。0 表示"静止"，具体静止在什么值由效果自己决定（glow 是满亮度）。 */
        float speed,
        /** 整体不透明度（0–1）。 */
        float opacity,
        /** 同物品内多个层的绘制顺序，升序。 */
        float order,
        /** 相位偏移（弧度），用来错开同一物品上多个同id层的动画。 */
        float phase,
        boolean showInGui,
        boolean showWhenHeld,
        boolean showInWorld
) {

    /** 内置泛光效果的 id。 */
    public static final String EFFECT_GLOW = "glow";
    public static final float DEFAULT_ORDER = 100.0f;
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    public MaskLayerSpec {
        if (effectId == null || effectId.isBlank()) {
            throw new IllegalArgumentException("mask layer 必须指定 effect id");
        }
        if (mask == null) {
            throw new IllegalArgumentException("mask layer 必须指定遮罩贴图");
        }
        // 负数只会让着色器算出反效果或 NaN，这里直接夹到合法区间，把问题挡在构造期。
        intensity = Math.max(0.0f, intensity);
        width     = Math.max(0.0f, width);
        speed     = Math.max(0.0f, speed);
        opacity   = Math.max(0.0f, opacity);
    }

    // ── 颜色分量 ──────────────────────────────────────────────────
    // 着色器侧要的是 0..1 的 vec4，而物品侧习惯按 ARGB int 配（和
    // ICustomOutline.outlineColor() 一致）。转换只写在这里，省得每个 pass 各写一遍。

    public float red()   { return ((color >> 16) & 0xFF) / 255.0f; }
    public float green() { return ((color >> 8) & 0xFF) / 255.0f; }
    public float blue()  { return (color & 0xFF) / 255.0f; }
    public float alpha() { return ((color >>> 24) & 0xFF) / 255.0f; }

    /**
     * 该层在当前渲染上下文下是否显示。默认三个开关全 true，
     * 与 {@code ICosmicLayer.cosmicShouldRender} 的分组保持一致。
     */
    public boolean shouldRender(ItemDisplayContext ctx) {
        return switch (ctx) {
            case GUI, FIXED -> showInGui;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> showWhenHeld;
            case GROUND, NONE, HEAD -> showInWorld;
        };
    }

    public Builder toBuilder() {
        return new Builder(effectId)
                .mask(mask).color(color).intensity(intensity).width(width)
                .speed(speed).opacity(opacity).order(order).phase(phase)
                .showInGui(showInGui).showWhenHeld(showWhenHeld).showInWorld(showInWorld);
    }

    public static Builder builder(String effectId) { return new Builder(effectId); }

    /** 快捷入口：一个默认参数的泛光层。 */
    public static Builder glow(ResourceLocation mask) {
        return new Builder(EFFECT_GLOW).mask(mask);
    }

    /** {@link MaskLayerSpec} 的构建器。参数含义见 record 组件注释。 */
    public static final class Builder {
        private final String effectId;
        private ResourceLocation mask;
        private int color = DEFAULT_COLOR;
        private float intensity = 1.0f;
        private float width = 2.0f;
        private float speed = 1.0f;
        private float opacity = 1.0f;
        private float order = DEFAULT_ORDER;
        private float phase = 0.0f;
        private boolean showInGui = true;
        private boolean showWhenHeld = true;
        private boolean showInWorld = true;

        private Builder(String effectId) { this.effectId = effectId; }

        public Builder mask(ResourceLocation v)      { this.mask = v; return this; }
        public Builder color(int v)                  { this.color = v; return this; }
        public Builder intensity(float v)            { this.intensity = v; return this; }
        public Builder width(float v)                { this.width = v; return this; }
        public Builder speed(float v)                { this.speed = v; return this; }
        public Builder opacity(float v)              { this.opacity = v; return this; }
        public Builder order(float v)                { this.order = v; return this; }
        public Builder phase(float v)                { this.phase = v; return this; }
        public Builder showInGui(boolean v)          { this.showInGui = v; return this; }
        public Builder showWhenHeld(boolean v)       { this.showWhenHeld = v; return this; }
        public Builder showInWorld(boolean v)        { this.showInWorld = v; return this; }

        public MaskLayerSpec build() {
            return new MaskLayerSpec(effectId, mask, color, intensity, width,
                    speed, opacity, order, phase, showInGui, showWhenHeld, showInWorld);
        }
    }
}
