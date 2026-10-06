package org.bytechen.hall.overworld.registry.items.magic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerProvider;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 法力板 —— 六个流派的通用板材。可选地带一层<b>泛光（glow）</b>。
 *
 * <h3>走的是 mask 层的「物品接口」那条路，不是模型 JSON</h3>
 * <p>{@link MaskLayerProvider} 是自描述的：物品自己声明"我身上挂哪些效果层"，
 * 于是模型仍然可以是 datagen 生成的 {@code minecraft:item/generated}，
 * <b>不需要</b>手写带 {@code "loader": "hall:mask_layers"} 的模型 JSON。
 * 这一点在这里很重要 —— {@code ItemGenData} 是按注册表遍历自动生成模型的，
 * 手写的 json 会被它覆盖（除非塞进 {@code MANUAL_WHITELIST}）。</p>
 *
 * <p>代价是这条路只在 {@code ItemRenderer} 的注入点生效，与 loader 无关；
 * 两者的取舍见 {@code docs/mask-layers.md} 第 2 节。</p>
 *
 * <h3>泛光的两张图怎么分工</h3>
 * <table border="1">
 *   <tr><th>作用</th><th>来自哪张图</th></tr>
 *   <tr><td>光源（什么颜色、多亮）</td><td>物品本体贴图 {@code light_mana_plate.png}</td></tr>
 *   <tr><td>范围（哪里发光）</td>
 *       <td>遮罩 {@code light_mana_plate_mask_glow.png}，<b>白 = 发光</b></td></tr>
 * </table>
 * <p>所以泛光发的是板材自己的颜色（奶白底 + 琥珀金边），不是凭空指定的一种发光色。
 * 遮罩与本体贴图必须<b>同一张画布</b>（都是 16×16、同对齐），这是美术侧的硬约束。</p>
 *
 * <h3>自发光</h3>
 * <p>{@code mask_glow.vsh} 里 {@code vertexColor = Color}，<b>不乘光照贴图</b> ——
 * 这一层的语义就是"自己会亮"，所以在夜里和洞穴里它不会跟着本体一起变黑。</p>
 *
 * <h3>怎么让另一块板也发光</h3>
 * <p>放一张同画布的 {@code <流派>_mana_plate_mask_glow.png}，然后在 {@code RegisterItem}
 * 里把它从 {@code registerSimpleItem} 换成
 * {@code new ManaPlateItem(props, <那条 MaskLayerSpec>)} 即可。</p>
 */
public class ManaPlateItem extends Item implements MaskLayerProvider {

    // ══════════════════════════════════════════════════════════════
    // 光明法力板的泛光层
    // ══════════════════════════════════════════════════════════════

    /** 泛光遮罩（方块图集里的 sprite 名）。图放在 {@code textures/item/} 下即自动进图集。 */
    private static final ResourceLocation LIGHT_GLOW_MASK =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "item/light_mana_plate_mask_glow");

    /**
     * 光明法力板的泛光参数。
     *
     * <ul>
     *   <li>{@code color} 白 —— 完全用板材贴图自己的颜色，也就是"给泛光上色"的反面。
     *       想把它调成别的色（例如偏黄的光）就改这一个 int。</li>
     *   <li>{@code speed} <b>0 = 静止在满亮度</b>（{@code pulse} 恒为 1.0），不呼吸。
     *       填正数才会脉动，周期约 {@code 126 / speed} tick；脉动时亮度在
     *       0.80~1.00 之间摆动，所以"始终最亮"必须用 0。</li>
     *   <li>{@code intensity} / {@code opacity} —— "多亮"的旋钮，乘积直接等于暗处看到的亮度
     *       （这一层不吃光照，见类注释）。板材贴图本身接近纯白，1.0/1.0 就已经顶到饱和。</li>
     *   <li>{@code width} 2.0 —— 光晕外扩半径（遮罩纹理像素）。<b>对这件物品是空转的</b>：
     *       板材贴图整张不透明，而 shader 的 {@code spill} 要乘 {@code coverage}
     *       （由本体贴图 alpha 得来），所以轮廓外那一圈算出来恒为 0，
     *       不会出现外溢的光晕，泛光表现为"整块板自己发亮"。留默认值不影响。</li>
     * </ul>
     */
    public static final MaskLayerSpec LIGHT_GLOW = MaskLayerSpec.glow(LIGHT_GLOW_MASK)
            .color(0xFFFFFFFF)
            .intensity(1.0f)
            .width(2.0f)
            // 0 = 常亮满值。填正数会变成"一会亮一会不亮"的呼吸（见上面 speed 那一条）。
            .speed(0.0f)
            .opacity(1.0f)
            .build();

    /**
     * 本物品的层列表。<b>存成常量</b>而不是每次 {@code List.of(...)}：
     * {@link MaskLayerProvider#maskLayers} 在渲染热路径上每帧每物品都会被调用，
     * 那里不该有分配。
     */
    private final List<MaskLayerSpec> layers;

    /** 不发光的一块板（纯材料）。 */
    public ManaPlateItem(@NotNull Properties properties) {
        this(properties, null);
    }

    /**
     * @param properties 物品属性
     * @param glowLayer  泛光层；{@code null} = 这块板不发光
     */
    public ManaPlateItem(@NotNull Properties properties, @Nullable MaskLayerSpec glowLayer) {
        super(properties);
        this.layers = glowLayer == null ? List.of() : List.of(glowLayer);
    }

    @Override
    public List<MaskLayerSpec> maskLayers(ItemStack stack) {
        return layers;
    }
}
