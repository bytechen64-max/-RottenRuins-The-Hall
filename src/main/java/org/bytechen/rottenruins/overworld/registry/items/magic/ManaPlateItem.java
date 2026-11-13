package org.bytechen.rottenruins.overworld.registry.items.magic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.ICustomOutline;
import org.bytechen.rottenruins.api.mask.MaskLayerProvider;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 法力板 —— 六个流派的通用板材，可选地挂两层互不相同的视觉效果。
 *
 * <p>两层机制分家：<b>泛光</b>是 mask 效果层（贴在板面让它自己发亮），<b>泛光描边</b>走 {@link ICustomOutline}
 * 的屏幕空间管线（剪影膨胀 + 光雾，长在轮廓外面）。{@code mask_glow.fsh} 的 {@code spill} 要乘本体贴图
 * 的 {@code coverage}，剪影外恒为 0，所以泛光层溢不出轮廓 —— 想要"轮廓外一圈光"只能用描边管线。
 * 泛光走 {@link MaskLayerProvider}（物品自描述），模型仍可用 datagen 生成的 {@code item/generated}；
 * 手写带 {@code mask_layers} loader 的模型 JSON 会被 {@code ItemGenData} 覆盖。</p>
 */
public class ManaPlateItem extends Item implements MaskLayerProvider, ICustomOutline {

    private static final ResourceLocation LIGHT_GLOW_MASK =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/light_mana_plate_mask_glow");

    /** 光明板的泛光参数：speed 0 = 常亮不呼吸（填正数周期约 126/speed tick）；width 对这件物品是空转的。 */
    public static final MaskLayerSpec LIGHT_GLOW = MaskLayerSpec.glow(LIGHT_GLOW_MASK)
            .color(0xFFFFFFFF)
            .intensity(1.0f)
            .width(2.0f)
            .speed(0.0f)
            .opacity(1.0f)
            .build();

    /** 一块板的描边预设；只有颜色/宽度/颜色模式按物品不同，光雾与混合方式走管线全局配置。{@code null} = 不描边。 */
    public record GlowOutline(int color, float pixelWidth, @Nullable String shaderKey, boolean drawInGui) {

        public GlowOutline(int color, float pixelWidth) {
            this(color, pixelWidth, null, true);
        }

        public GlowOutline(int color, float pixelWidth, @Nullable String shaderKey) {
            this(color, pixelWidth, shaderKey, true);
        }
    }

    /** 光明板描边：色值取自贴图的琥珀金边 #FFCB32；pixelWidth 3.0 保证 16px 图标上也读得出。 */
    public static final GlowOutline LIGHT_OUTLINE = new GlowOutline(0xFFFFCB32, 3.0f);

    /** 火焰板描边：{@code fire_2} 世界空间体积火焰（固定 0.9×1.0×0.9 盒内 ray marching）；GUI 不画。 */
    public static final GlowOutline FIRE_OUTLINE = new GlowOutline(0xFFFF6A18, 3.0f, "fire_2", false);

    /** 层列表存成常量：{@link MaskLayerProvider#maskLayers} 在渲染热路径上每帧每物品都会被调用，不该有分配。 */
    private final List<MaskLayerSpec> layers;

    @Nullable
    private final GlowOutline glowOutline;

    public ManaPlateItem(@NotNull Properties properties) {
        this(properties, null, null);
    }

    public ManaPlateItem(@NotNull Properties properties, @Nullable MaskLayerSpec glowLayer) {
        this(properties, glowLayer, null);
    }

    public ManaPlateItem(@NotNull Properties properties,
                         @Nullable MaskLayerSpec glowLayer,
                         @Nullable GlowOutline glowOutline) {
        super(properties);
        this.layers = glowLayer == null ? List.of() : List.of(glowLayer);
        this.glowOutline = glowOutline;
    }

    @Override
    public List<MaskLayerSpec> maskLayers(ItemStack stack) {
        return layers;
    }

    @Override
    public int outlineColor() {
        return glowOutline == null ? 0 : glowOutline.color();
    }

    @Override
    public float outlinePixelWidth() {
        return glowOutline == null ? 2.5f : glowOutline.pixelWidth();
    }

    @Override
    public String outlineShaderKey() {
        return glowOutline == null ? null : glowOutline.shaderKey();
    }

    /** 只有传了 {@link GlowOutline} 的板才描边；作为头盔（HEAD）一律不描，GUI 由预设的 drawInGui 决定。 */
    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        if (glowOutline == null || ctx == ItemDisplayContext.HEAD) return false;
        return ctx != ItemDisplayContext.GUI || glowOutline.drawInGui();
    }
}
