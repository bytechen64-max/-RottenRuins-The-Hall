package org.bytechen.hall.client.mask;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;

/**
 * mask 效果层的 uniform 小工具。
 *
 * <h3>为什么不缓存 uniform 句柄</h3>
 * <p>{@code ShaderInstance.getUniform(name)} 只是一次 HashMap 查找，而每层每帧
 * 也就设十来个 uniform —— 缓存省下的那点开销，换来的是「着色器资源重载
 * （F3+T）后句柄失效」这类只在特定操作后才复现的坑。这里每次都查，每次都可空
 * （查不到说明着色器里没声明这个 uniform），查不到就跳过：<b>着色器少写一个 uniform
 * 只该让那个效果退化，不该炸</b>。</p>
 */
public final class MaskUniforms {

    /** 遮罩 sprite 在图集里的左下角 UV（vec2）。 */
    public static final String MASK_MIN = "MaskMin";

    /** 遮罩 sprite 的 UV 尺寸（vec2）。 */
    public static final String MASK_SIZE = "MaskSize";

    /** 遮罩贴图的像素尺寸（vec2），把「像素」单位的参数换算成 UV 用。 */
    public static final String MASK_PIXELS = "MaskPixels";

    /**
     * 本体贴图（光源）在图集里的位置（vec2）与 UV 尺寸（vec2）。
     *
     * <p>泛光层与 cosmic 层最大的不同就在这里：cosmic 只用遮罩自己当光源，
     * 而泛光的光源是<b>本体贴图</b>。于是要把 quad 的图集 UV 先还原成本体贴图的
     * 0..1 局部坐标，再映射到遮罩上 —— 这一对 uniform 就是干这个的。</p>
     */
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

    /**
     * 描进遮罩的三个几何 uniform：{@link #MASK_MIN}、{@link #MASK_SIZE}、
     * {@link #MASK_PIXELS}。
     *
     * <p>遮罩的 UV 换算在每条需要邻域采样的效果里都要写一遍，而且写错的表现是
     * 「泛光出现在图集里另一个贴图上」这种极难看懂的症状，所以统一放这里。</p>
     */
    public static void setSlice(ShaderInstance shader, @Nullable TextureAtlasSprite mask) {
        if (shader == null || mask == null) return;
        set2(shader, MASK_MIN, mask.getU0(), mask.getV0());
        set2(shader, MASK_SIZE, mask.getU1() - mask.getU0(), mask.getV1() - mask.getV0());
        // 像素尺寸走 contents()：TextureAtlasSprite 上的 getX()/getY() 是 sprite 在
        // 图集里的**偏移**，不是宽高，别拿错（这个名字踩过一次）。
        set2(shader, MASK_PIXELS, mask.contents().width(), mask.contents().height());
    }

    /**
     * 描进本体贴图（光源）的 UV 范围。与 {@link #setSlice} 成对使用：
     * 前者定位遮罩（决定哪里发光），后者定位光源（决定发光是什么颜色）。
     */
    public static void setBaseSlice(ShaderInstance shader, @Nullable TextureAtlasSprite base) {
        if (shader == null || base == null) return;
        set2(shader, BASE_MIN, base.getU0(), base.getV0());
        set2(shader, BASE_SIZE, base.getU1() - base.getU0(), base.getV1() - base.getV0());
    }

    /**
     * 取方块图集里的 sprite。图集必须是 {@link TextureAtlas} 且已经 stitch 过，
     * 否则拿到的是 missing sprite（症状是整层变成紫黑格子）。
     */
    public static TextureAtlasSprite sprite(TextureAtlas atlas, net.minecraft.resources.ResourceLocation mask) {
        return atlas.getSprite(mask);
    }

    private MaskUniforms() {}
}
