package org.bytechen.rottenruins.client.fx.effects;

import org.bytechen.rottenruins.client.fx.ItemFxBlend;
import org.bytechen.rottenruins.client.fx.ItemFxDomain;
import org.bytechen.rottenruins.client.fx.ItemFxEffect;
import org.bytechen.rottenruins.client.fx.ItemFxFieldSpec;
import org.bytechen.rottenruins.client.fx.ItemFxFormat;
import org.bytechen.rottenruins.client.fx.ItemFxRegistry;

import java.util.List;

/**
 * 把「剪影遮罩 + 环形膨胀 + 光雾」描边注册成框架的第一个效果：声明效果名、域、需要的场与缺省合成，场池按此开图。
 * 遮罩必须全分辨率（环形合成按像素距离采样，降分辨率会糊边）；光雾是低频、降到 1/4 面积只剩 1/16、整屏一次同屏共用；
 * 这些场都是 {@code persistent=false}。 */
public final class OutlineFxEffect implements ItemFxEffect {

    /** 效果 id，同时也是场名的前缀（见 {@code ItemFxFieldPool} 的 key 规则）。 */
    public static final String ID = "outline";

    /** 全分辨率剪影遮罩：颜色模式算出来的颜色也烘在这张图的 rgb 里。 */
    public static final String FIELD_MASK = "mask";
    public static final String FIELD_HALO_HALF = "haloHalf";
    public static final String FIELD_HALO_HALF_WORK = "haloHalfWork";
    public static final String FIELD_HALO_WIDE = "haloWide";
    public static final String FIELD_HALO_WIDE_WORK = "haloWideWork";

    /** 火焰光环（fire_2）的密度场：同一套四趟模糊，但 BlurScale 按物品屏幕尺寸放大，火焰能伸出物品好几倍。 */
    public static final String FIELD_FIRE_HALF = "fireHalf";
    public static final String FIELD_FIRE_HALF_WORK = "fireHalfWork";
    public static final String FIELD_FIRE_WIDE = "fireWide";
    public static final String FIELD_FIRE_WIDE_WORK = "fireWideWork";

    /** 体积火焰（fire_2）的离屏目标：全分辨率 RGBA8。不能直接画在主 RT 上 —— 体积火焰要在片元里
     *  采样主 RT 的深度附件做射线截断，而"采样一张正被绑成当前 FBO 附件的纹理"是未定义行为。 */
    public static final String FIELD_FIRE_VOLUME = "fireVolume";

    private static final OutlineFxEffect INSTANCE = new OutlineFxEffect();

    static {
        ItemFxRegistry.register(INSTANCE);
    }

    private static final List<ItemFxFieldSpec> FIELDS = List.of(
            ItemFxFieldSpec.full(FIELD_MASK, ItemFxFormat.RGBA8),
            ItemFxFieldSpec.scaled(FIELD_HALO_HALF, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_HALO_HALF_WORK, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_HALO_WIDE, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_HALO_WIDE_WORK, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_FIRE_HALF, ItemFxFormat.RGBA16F, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_FIRE_HALF_WORK, ItemFxFormat.RGBA16F, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_FIRE_WIDE, ItemFxFormat.RGBA16F, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_FIRE_WIDE_WORK, ItemFxFormat.RGBA16F, 4, 32),
            ItemFxFieldSpec.full(FIELD_FIRE_VOLUME, ItemFxFormat.RGBA8)
    );

    private OutlineFxEffect() { }

    public static OutlineFxEffect instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemFxDomain domain() {
        return ItemFxDomain.SILHOUETTE;
    }

    @Override
    public List<ItemFxFieldSpec> fields() {
        return FIELDS;
    }

    @Override
    public ItemFxBlend blend() {
        return ItemFxBlend.ADD;
    }

    @Override
    public float order() {
        // 压在表面类效果（mask_layers 默认 order=100）之上；将来统一 order 语义时要重新对表。
        return 200.0f;
    }
}
