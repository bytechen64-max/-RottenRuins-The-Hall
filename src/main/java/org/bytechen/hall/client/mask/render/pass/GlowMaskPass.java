package org.bytechen.hall.client.mask.render.pass;

import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.mask.MaskEffectPass;
import org.bytechen.hall.client.mask.MaskUniforms;
import org.bytechen.hall.client.mask.render.MaskLayerRenderType;
import org.bytechen.hall.client.mask.render.MaskLayerShaders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * 内置效果 {@code glow} —— 贴图泛光。
 *
 * <h3>两张图分工</h3>
 * <table>
 *   <tr><th>作用</th><th>来自哪张图</th></tr>
 *   <tr><td><b>光源</b>（什么颜色、多亮）</td><td>物品<b>本体贴图</b>（layer0）</td></tr>
 *   <tr><td><b>范围</b>（哪里发光）</td><td>本层的 {@code mask}（白 = 发光，黑 = 不发）</td></tr>
 * </table>
 * <p>于是「剑刃归星空、剑柄发光」这种分工就变成了两张图的事：cosmic 的遮罩盖住剑刃，
 * 本层的 mask 盖住剑柄，而发出来的光是剑柄自己的颜色 —— 不是凭空指定的一种发光色。<p>
 *
 * <h3>几何为什么取自本体贴图</h3>
 * <p>quad 由 {@code MaskLayerRenderer} 用<b>本体贴图</b>的 sprite 烘出来。
 * {@code ItemModelGenerator} 按 alpha 裁几何，而本体贴图轮廓外是透明的，所以 quad
 * 精确等于武器轮廓；遮罩图通常为了不漏外圈像素而画成「整张不透明」，拿它烘几何
 * 会得到一整块矩形。（这个坑实际踩过：虚空之剑的遮罩是 32×32 全不透明，
 * 于是闪烁的是一块方形的平面。）</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code color} —— <b>着色</b>（默认白 = 完全用本体贴图自己的颜色；
 *       填别的颜色等于给泛光上色）</li>
 *   <li>{@code width} —— 光晕外扩半径，单位是<b>遮罩纹理像素</b></li>
 *   <li>{@code intensity} —— 亮度倍率</li>
 *   <li>{@code speed} —— 呼吸速度，0 让它静止</li>
 *   <li>{@code opacity} / {@code phase} —— 整体强度与相位偏移</li>
 * </ul>
 */
public final class GlowMaskPass implements MaskEffectPass {

    public static final String ID = MaskLayerSpec.EFFECT_GLOW;

    // uniform 名与 mask_glow.fsh 必须逐字对应。写错的表现是「参数拧了没反应」，
    // 而 MaskUniforms 的设计是「查不到就跳过」，所以不会崩 —— 只会安静地不生效。
    private static final String U_COLOR     = "GlowColor";
    private static final String U_INTENSITY = "Intensity";
    private static final String U_WIDTH     = "GlowWidth";
    private static final String U_SPEED     = "GlowSpeed";
    private static final String U_OPACITY   = "Opacity";
    private static final String U_PHASE     = "Phase";
    private static final String U_TIME      = "time";

    @Override public String id() { return ID; }

    @Override public ShaderInstance shader() { return MaskLayerShaders.glow(); }

    @Override public RenderType immediateType() {
        return MaskLayerRenderType.immediate(ID, shader());
    }

    @Override public RenderType afterLevelType() {
        return MaskLayerRenderType.afterLevel(ID, shader());
    }

    @Override public RenderType handAfterLevelType() {
        return MaskLayerRenderType.handAfterLevel(ID, shader());
    }

    @Override
    public void apply(MaskLayerSpec layer, @Nullable TextureAtlasSprite mask,
                      @Nullable TextureAtlasSprite base, float time) {
        ShaderInstance shader = shader();
        if (shader == null) return;

        // 遮罩决定「哪里发光」，本体贴图决定「发光是什么颜色」。两个都要给：
        // 少了 base，UV 换算会退回 (0,0)-(1,1)，泛光会变成整张图集的平均值。
        MaskUniforms.setSlice(shader, mask);
        MaskUniforms.setBaseSlice(shader, base);

        MaskUniforms.set4(shader, U_COLOR,
                layer.red(), layer.green(), layer.blue(), layer.alpha());
        MaskUniforms.set1(shader, U_INTENSITY, layer.intensity());
        MaskUniforms.set1(shader, U_WIDTH, layer.width());
        MaskUniforms.set1(shader, U_SPEED, layer.speed());
        MaskUniforms.set1(shader, U_OPACITY, layer.opacity());
        MaskUniforms.set1(shader, U_PHASE, layer.phase());
        MaskUniforms.set1(shader, U_TIME, time);
    }

    @Override
    public String toString() {
        return "GlowMaskPass";
    }
}
