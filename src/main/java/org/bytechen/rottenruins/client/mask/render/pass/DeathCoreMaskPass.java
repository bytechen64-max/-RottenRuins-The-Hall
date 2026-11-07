package org.bytechen.rottenruins.client.mask.render.pass;

import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.client.mask.MaskEffectPass;
import org.bytechen.rottenruins.client.mask.MaskUniforms;
import org.bytechen.rottenruins.client.mask.render.MaskLayerRenderType;
import org.bytechen.rottenruins.client.mask.render.MaskLayerShaders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * 内置效果 {@code death_core} —— ShaderToy "Ghosts"（by XorDev）的「遮罩窗口」层。
 *
 * <h3>语义</h3>
 * <ul>
 *   <li><b>mask 白层 = 窗口</b>：窗口内渲染 Ghosts 体积光（alpha = 1，原贴图不透出来）；</li>
 *   <li>窗口外 alpha = 0，方块本体贴图照常可见；</li>
 *   <li>光线方向来自片元的视空间位置（相机→片元），再乘 {@code IViewRotMat}
 *       转回世界方向 —— 所有 {@code death_core} 方块看到的是同一片世界锚定的
 *       体积效果（转视角时不会跟着屏幕滑动）。</li>
 * </ul>
 *
 * <h3>和 {@code glow} 的语义差别</h3>
 * <table>
 *   <tr><th></th><th>glow</th><th>death_core</th></tr>
 *   <tr><td>输出</td><td>加法叠加在贴图上（黑区不可见）</td>
 *       <td>遮罩窗口内的半透明覆盖（窗口外露出本体贴图）</td></tr>
 *   <tr><td>遮罩作用</td><td>哪些像素发光</td><td><b>窗口形状</b></td></tr>
 *   <tr><td>RenderType</td><td>{@code MaskLayerRenderType.immediate}（ADDITIVE）</td>
 *       <td>{@link MaskLayerRenderType#surface}（TRANSLUCENT）</td></tr>
 *   <tr><td>适合的宿主</td><td>物品（本体贴图就是光源）</td>
 *       <td>方块（BER）与方块物品（ItemRenderer 注入），都用方块模型 quad 绘制</td></tr>
 * </table>
 *
 * <h3>uniform</h3>
 * <p>{@link #apply} 只设置层参数（CoreColor / Intensity / Opacity / Phase / time）
 * 与遮罩 slice；相机矩阵 {@code IViewRotMat} 由 {@code ShaderInstance} 自动上传，
 * 不需要这里每帧设置。着色器假设 quad 的 UV 已被渲染器重映射到遮罩 sprite ——
 * 方块与方块物品都满足。</p>
 */
public final class DeathCoreMaskPass implements MaskEffectPass {

    public static final String ID = MaskLayerSpec.EFFECT_DEATH_CORE;

    // uniform 名必须与 death_core.fsh 逐字一致。写错的表现是「参数拧了没反应」，
    // 而 MaskUniforms 的设计是「查不到就跳过」，所以不会崩 —— 只会安静地不生效。
    private static final String U_CORE      = "CoreColor";
    private static final String U_INTENSITY = "Intensity";
    private static final String U_OPACITY   = "Opacity";
    private static final String U_PHASE     = "Phase";
    private static final String U_TIME      = "time";

    @Override public String id() { return ID; }

    @Override public ShaderInstance shader() { return MaskLayerShaders.deathCore(); }

    @Override
    public RenderType immediateType() {
        // 遮罩窗口：窗口内不透明（Ghosts 内容），窗口外 alpha=0 露出本体贴图。
        return MaskLayerRenderType.surface(ID, shader());
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

        // 遮罩几何 uniform（当前 shader 直接用 UV0 采样，保留这行是给以后的
        // 邻域采样效果复用；shader 没声明时 MaskUniforms 会安静跳过）。
        MaskUniforms.setSlice(shader, mask);

        MaskUniforms.set4(shader, U_CORE,
                layer.red(), layer.green(), layer.blue(), layer.alpha());
        MaskUniforms.set1(shader, U_INTENSITY, layer.intensity());
        MaskUniforms.set1(shader, U_OPACITY, layer.opacity());
        MaskUniforms.set1(shader, U_PHASE, layer.phase());
        MaskUniforms.set1(shader, U_TIME, time);

        // 相机相关矩阵 IViewRotMat 由 ShaderInstance.apply() 自动上传
        // （RenderSystem.setInverseViewRotationMatrix），这里不重复设置。
    }

    @Override
    public String toString() {
        return "DeathCoreMaskPass";
    }
}
