package org.bytechen.rottenruins.client.mask;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;

/** mask 效果层的 uniform 小工具；不缓存句柄（F3+T 重载后失效），查不到就跳过：着色器少写一个 uniform 只该退化、不该炸。 */
public final class MaskUniforms {

    /** 遮罩 sprite 在图集里的左下角 UV（vec2）。 */
    public static final String MASK_MIN = "MaskMin";

    public static final String MASK_SIZE = "MaskSize";

    /** 遮罩贴图的像素尺寸（vec2），把「像素」单位的参数换算成 UV 用。 */
    public static final String MASK_PIXELS = "MaskPixels";

    /** 本体贴图（光源）在图集里的位置与 UV 尺寸；泛光的光源是本体贴图，需先还原成 0..1 局部坐标再映射到遮罩。 */
    public static final String BASE_MIN = "BaseMin";
    public static final String BASE_SIZE = "BaseSize";

    public static void set1(ShaderInstance shader, String name, float v) {
        if (shader == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    public static void set2(ShaderInstance shader, String name, float x, float y) {
        if (shader == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(x, y);
    }

    public static void set3(ShaderInstance shader, String name, float x, float y, float z) {
        if (shader == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    public static void set4(ShaderInstance shader, String name, float x, float y, float z, float w) {
        if (shader == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(x, y, z, w);
    }

    /** 描进遮罩的三个几何 uniform（MASK_MIN / MASK_SIZE / MASK_PIXELS）：UV 换算写错的表现极难懂，故统一放这里。 */
    public static void setSlice(ShaderInstance shader, @Nullable TextureAtlasSprite mask) {
        if (shader == null || mask == null) return;
        set2(shader, MASK_MIN, mask.getU0(), mask.getV0());
        set2(shader, MASK_SIZE, mask.getU1() - mask.getU0(), mask.getV1() - mask.getV0());
        // 像素尺寸走 contents()：getX()/getY() 是 sprite 在图集里的偏移，不是宽高。
        set2(shader, MASK_PIXELS, mask.contents().width(), mask.contents().height());
    }

    /** 描进本体贴图（光源）的 UV 范围；与 setSlice 成对：前者定位遮罩，后者定位光源。 */
    public static void setBaseSlice(ShaderInstance shader, @Nullable TextureAtlasSprite base) {
        if (shader == null || base == null) return;
        set2(shader, BASE_MIN, base.getU0(), base.getV0());
        set2(shader, BASE_SIZE, base.getU1() - base.getU0(), base.getV1() - base.getV0());
    }

    /** 取方块图集里的 sprite；图集必须已 stitch，否则拿到 missing sprite（整层变紫黑格子）。 */
    public static TextureAtlasSprite sprite(TextureAtlas atlas, net.minecraft.resources.ResourceLocation mask) {
        return atlas.getSprite(mask);
    }

    private MaskUniforms() {}
}
