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
 * 内置效果 {@code death_core} —— ShaderToy "Ghosts"（by XorDev）的遮罩窗口层：mask 白层内渲染体积光，窗口外 alpha=0 露出本体贴图；
 * 光线方向由片元视空间位置经 {@code IViewRotMat} 转回世界方向，故转视角不滑屏。
 * 与 {@code glow} 的差别：glow 是 ADDITIVE、遮罩决定哪些像素发光；这里是 {@link MaskLayerRenderType#surface}（TRANSLUCENT）、遮罩决定窗口形状。
 */
public final class DeathCoreMaskPass implements MaskEffectPass {

    public static final String ID = MaskLayerSpec.EFFECT_DEATH_CORE;

    // uniform 名必须与 death_core.fsh 逐字一致；MaskUniforms 查不到就跳过，所以写错只会安静地不生效、不会崩。
    private static final String U_CORE      = "CoreColor";
    private static final String U_INTENSITY = "Intensity";
    private static final String U_OPACITY   = "Opacity";
    private static final String U_PHASE     = "Phase";
    private static final String U_TIME      = "time";

    @Override public String id() { return ID; }

    @Override public ShaderInstance shader() { return MaskLayerShaders.deathCore(); }

    @Override
    public RenderType immediateType() {
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

        // 遮罩几何 uniform：当前 shader 直接用 UV0 采样，保留是给以后邻域采样的效果复用，未声明时安静跳过。
        MaskUniforms.setSlice(shader, mask);

        MaskUniforms.set4(shader, U_CORE,
                layer.red(), layer.green(), layer.blue(), layer.alpha());
        MaskUniforms.set1(shader, U_INTENSITY, layer.intensity());
        MaskUniforms.set1(shader, U_OPACITY, layer.opacity());
        MaskUniforms.set1(shader, U_PHASE, layer.phase());
        MaskUniforms.set1(shader, U_TIME, time);

        // 相机矩阵 IViewRotMat 由 ShaderInstance 自动上传，这里不重复设置，只在片元里用它转回世界方向。
    }

    @Override
    public String toString() {
        return "DeathCoreMaskPass";
    }
}
