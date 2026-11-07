package org.bytechen.rottenruins.client.fx.effects;

import org.bytechen.rottenruins.client.fx.ItemFxBlend;
import org.bytechen.rottenruins.client.fx.ItemFxDomain;
import org.bytechen.rottenruins.client.fx.ItemFxEffect;
import org.bytechen.rottenruins.client.fx.ItemFxFieldSpec;
import org.bytechen.rottenruins.client.fx.ItemFxFormat;
import org.bytechen.rottenruins.client.fx.ItemFxRegistry;

import java.util.List;

/**
 * 把现有的「剪影遮罩 + 环形膨胀 + 光雾」描边注册成框架的第一个效果。
 *
 * <p>S1 里它只负责<b>声明</b>：这个效果叫什么、作用在哪个域、需要哪几张场、
 * 缺省怎么合成。场池按这份声明开图，管线按 {@link #FIELD_MASK} /
 * {@link #FIELD_HALO_HALF} / {@link #FIELD_HALO_WIDE} 取用 —— 于是"效果要什么场"
 * 从管线里搬到了效果里，加第二个效果时不会再打架。</p>
 *
 * <h3>为什么场的名字与尺寸是这几个</h3>
 * <ul>
 *   <li>{@link #FIELD_MASK} —— 全分辨率剪影遮罩（颜色烘在 rgb 里）。全分辨率是必须的：
 *       环形合成要按像素距离采样，降分辨率会把描边边缘糊掉。</li>
 *   <li>{@link #FIELD_HALO_HALF} / {@link #FIELD_HALO_WIDE} —— 1/2、1/4 两级模糊图。
 *       雾是低频的，降到 1/4 面积只剩 1/16，而且整屏一次、同屏所有物品共用。</li>
 *   <li>{@code haloHalfWork} —— 1/2 那级的 X/Y 分离模糊需要一张乒乓图（名字带 work 的就是它）。</li>
 * </ul>
 *
 * <p>这些场都是 {@code persistent=false}：描边是"每帧现算"的无状态效果。</p>
 */
public final class OutlineFxEffect implements ItemFxEffect {

    /** 效果 id，同时也是场名的前缀（见 {@code ItemFxFieldPool} 的 key 规则）。 */
    public static final String ID = "outline";

    /** 全分辨率剪影遮罩：颜色模式算出来的颜色也烘在这张图的 rgb 里。 */
    public static final String FIELD_MASK = "mask";
    /** 1/2 分辨率模糊图（两层里较锐的那层，兼作分离模糊的乒乓目标）。 */
    public static final String FIELD_HALO_HALF = "haloHalf";
    public static final String FIELD_HALO_HALF_WORK = "haloHalfWork";
    /** 1/4 分辨率模糊图（大范围那层）。 */
    public static final String FIELD_HALO_WIDE = "haloWide";
    public static final String FIELD_HALO_WIDE_WORK = "haloWideWork";

    /**
     * 火焰光环（fire_2）的「离轮廓多远」密度场：剪影遮罩按物品尺寸放大的模糊。
     *
     * <p>与 halo 那两张的区别是<b>同一套四趟模糊、但 BlurScale 按物品屏幕尺寸放大</b>
     * （一个火条目各建一次），所以火焰能伸出物品本身好几倍，而光雾始终是固定像素。</p>
     */
    public static final String FIELD_FIRE_HALF = "fireHalf";
    public static final String FIELD_FIRE_HALF_WORK = "fireHalfWork";
    public static final String FIELD_FIRE_WIDE = "fireWide";
    public static final String FIELD_FIRE_WIDE_WORK = "fireWideWork";

    /**
     * 体积火焰（fire_2）的离屏目标：全分辨率 RGBA8。
     *
     * <p>为什么不在主 RT 上直接画：体积火焰要在片元里采样<b>主 RT 的深度附件</b>
     * 来做射线截断，而"采样一张正被绑成当前 FBO 附件的纹理"是未定义行为。
     * 画到这张图里、再加法合成回主 RT 就没有这个问题。</p>
     */
    public static final String FIELD_FIRE_VOLUME = "fireVolume";

    private static final OutlineFxEffect INSTANCE = new OutlineFxEffect();

    static {
        // 自带注册：这个效果是管线的一部分，不该依赖别人记得去注册它。
        ItemFxRegistry.register(INSTANCE);
    }

    private static final List<ItemFxFieldSpec> FIELDS = List.of(
            ItemFxFieldSpec.full(FIELD_MASK, ItemFxFormat.RGBA8),
            ItemFxFieldSpec.scaled(FIELD_HALO_HALF, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_HALO_HALF_WORK, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_HALO_WIDE, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_HALO_WIDE_WORK, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_FIRE_HALF, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_FIRE_HALF_WORK, ItemFxFormat.RGBA8, 2, 64),
            ItemFxFieldSpec.scaled(FIELD_FIRE_WIDE, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.scaled(FIELD_FIRE_WIDE_WORK, ItemFxFormat.RGBA8, 4, 32),
            ItemFxFieldSpec.full(FIELD_FIRE_VOLUME, ItemFxFormat.RGBA8)
    );

    private OutlineFxEffect() { }

    /** 静态初始化时就会把自己注册进去。 */
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
        // 缺省加法（ICustomOutline 的默认值）；逐物品可以用 outlineBlend() 选 ALPHA。
        return ItemFxBlend.ADD;
    }

    @Override
    public float order() {
        // 描边压在表面类效果（mask_layers 默认 order = 100）之外的同一档；
        // 将来两边统一 order 语义时这里要重新对表。
        return 200.0f;
    }
}
