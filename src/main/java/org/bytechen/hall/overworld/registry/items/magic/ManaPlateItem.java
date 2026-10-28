package org.bytechen.hall.overworld.registry.items.magic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.api.mask.MaskLayerProvider;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 法力板 —— 六个流派的通用板材。可选地挂两层<b>互不相同</b>的视觉效果：
 *
 * <ol>
 *   <li><b>泛光（glow）</b> —— mask 效果层，贴在板材<b>表面</b>让它自己发亮；</li>
 *   <li><b>泛光描边（outline + aura）</b> —— 屏幕空间的剪影描边 + 光雾，长在轮廓<b>外面</b>。</li>
 * </ol>
 *
 * <h3>为什么是两套系统，而不是一个</h3>
 * <p>mask 泛光层即使把 {@code width} 调大也溢不出去：{@code mask_glow.fsh} 里外扩出来的
 * {@code spill} 要乘本体贴图的 {@code coverage}，轮廓外那圈的 {@code coverage} 恒为 0 ——
 * 它只能做「整块板自己发亮」，做不出「轮廓外的一圈光」。那正是
 * {@code docs/mask-layers.md} 第 10 节写明的已知限制。</p>
 *
 * <p>「描边」要的恰好是相反的东西，所以走了另一条管线：
 * {@link ICustomOutline} → {@code ItemOutlinePipeline}（剪影遮罩 + 环形膨胀
 * {@code dilate − self}，只画在物品外侧），详见 {@code docs/item-shader-outline.md}。
 * 外面再叠一层由剪影降采样模糊出来的<b>光雾</b>（{@code outlineAura*} 那组配置），
 * 于是「泛光描边」= 金色硬边 + 贴着轮廓的一团暖光。</p>
 *
 * <p>两层同时存在时观感是：板材本身是亮的（奶白底 + 琥珀金边），
 * 外面再有一圈金色亮边和它的光雾。互不重叠，所以不会互相吃掉。</p>
 *
 * <h3>泛光层：走的是 mask 层的「物品接口」那条路，不是模型 JSON</h3>
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
 * <h3>怎么让另一块板也发光 / 也描边</h3>
 * <p>泛光：放一张同画布的 {@code <流派>_mana_plate_mask_glow.png}，然后在
 * {@code RegisterItem} 里把它从 {@code registerSimpleItem} 换成
 * {@code new ManaPlateItem(props, <那条 MaskLayerSpec>)}。<br>
 * 描边：再传一条 {@link GlowOutline}（{@code new ManaPlateItem(props, glow, outline)}）。
 * 「只有光明板描边」不需要任何按 stack 判断的特判 —— 每个注册项都是<b>独立的物品实例</b>
 * （其它板现在是普通 {@code Item}；将来换成 {@code ManaPlateItem} 也是各自独立的实例），
 * 字段天然互相隔离，{@link #outlineEnabled} 只是看这个字段为不为空。</p>
 */
public class ManaPlateItem extends Item implements MaskLayerProvider, ICustomOutline {

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
     *       板材贴图是"轮廓外透明"的（138 个不透明像素 / 118 个全透明），而 shader 的
     *       {@code spill} 要乘 {@code coverage = smoothstep(0, 0.15, base.a)} —— 剪影外
     *       {@code coverage} 恒为 0，外扩出来的那圈被整个乘没了。所以泛光表现为
     *       "整块板自己发亮"，不会出现外溢的光晕；留默认值不影响。</li>
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

    // ══════════════════════════════════════════════════════════════
    // 法力板的描边预设（光明板 = 纯色，火焰板 = fire_2）
    // ══════════════════════════════════════════════════════════════

    /**
     * 一块板的描边预设：<b>描什么颜色、多宽、什么颜色模式</b>。
     *
     * <p>只有这几项需要按物品不同 —— 光雾（{@code outlineAura*}）、混合方式、
     * 距离自适应等全部走管线与全局配置，不在这里重复。传 {@code null} 表示这块板不描边。</p>
     *
     * @param color      ARGB，<b>alpha 不参与配色</b>（描边用加法混合，强度由
     *                   不透明度旋钮控制），这里只取 rgb
     * @param pixelWidth 屏幕像素宽；与 {@link ICustomOutline#outlinePixelWidth()} 同一个语义，
     *                   同时是距离自适应下的上限（远处按 {@code outlineWidthMaxRatio} 收细）
     * @param shaderKey  颜色模式，见 {@link ICustomOutline#outlineShaderKey()}；
     *                   {@code null} = 纯色（默认）
     * @param drawInGui  是否在物品栏 / 容器界面的图标上也描边。默认 {@code true}。
     *                   可堆叠材料建议关掉：GUI 里的描边是"逐物品就地合成"的，
     *                   每件描边物品都会各建一次光雾模糊链（见
     *                   {@code docs/item-shader-outline.md} 第十一章的代价说明）。
     */
    public record GlowOutline(int color, float pixelWidth, @Nullable String shaderKey, boolean drawInGui) {

        /** 纯色描边（旧的调用方式，等价于 {@code shaderKey = null, drawInGui = true}）。 */
        public GlowOutline(int color, float pixelWidth) {
            this(color, pixelWidth, null, true);
        }

        /** 指定颜色模式，GUI 里也描边。 */
        public GlowOutline(int color, float pixelWidth, @Nullable String shaderKey) {
            this(color, pixelWidth, shaderKey, true);
        }
    }

    /**
     * 光明法力板的泛光描边。
     *
     * <ul>
     *   <li>{@code color 0xFFFFCB32} —— 直接从板材贴图的<b>琥珀金边</b>取色
     *       （贴图里的金边共 34 个像素、颜色都是 {@code #FFCB32}）。描边的语义是"从物品边上
     *       长出来的光"，所以用贴图自己的边色，而不是另指定一种发光色；光雾也是这个色。</li>
     *   <li>{@code pixelWidth 3.0} —— 一块 16×16 的板材在物品栏里只有 16 像素，
     *       默认 2.5 偏细；3.0 手持/掉落时是一条清楚的暖边，GUI 里还会再乘
     *       {@code guiOutlineWidthScale}（默认 0.75）收成 2.25 像素。</li>
     *   <li>没有单独写「光雾」参数：{@code outlineAuraEnabled}（默认 true）、
     *       {@code outlineAuraStrength}、{@code outlineAuraSpread} 是全局配置，
     *       同屏所有描边物品共用同一套模糊图；想要"只有这块板有光雾"得改管线，
     *       目前没这个必要（其它描边物品也都在用同一层光雾）。</li>
     *   <li>颜色模式留默认（纯色）：这块板是基础材料，硬边 + 光雾已经够读；
     *       想要流动的观感再覆写 {@link #outlineShaderKey()} 返回
     *       {@code "gradient"} / {@code "fire"} 并配副色即可，几何完全不变。</li>
     * </ul>
     */
    public static final GlowOutline LIGHT_OUTLINE = new GlowOutline(0xFFFFCB32, 3.0f);

    /**
     * 火焰法力板的<b>燃烧描边</b>（{@code ColorMode 5 / "fire_2"}）。
     *
     * <p><b>世界空间体积火焰</b>：以物品为中心、固定尺寸（0.9 × 1.0 × 0.9 格）的盒子里
     * ray marching 3D 噪声，用场景深度截断、Beer 累积。有视差、范围固定、能被墙挡住；
     * 覆盖掉落物 / 展示框 / 一手三处上下文，GUI 不画（见
     * {@code docs/item-shader-outline.md} 第十二章）。</p>
     *
     * <ul>
     *   <li>{@code color 0xFFFF6A18} —— 火焰法力板的贴图主色（橙红）。在体积火焰里是
     *       主色（{@code ColorHot}），贴物品处会推向白热；描边硬环/光雾也用它。</li>
     *   <li>{@code pixelWidth 3.0} —— 这是屏幕描边硬环的宽度；体积火焰的盒子尺寸是固定的，
     *       不受这个值影响。</li>
     *   <li>纯加法混合（{@link ICustomOutline#outlineBlend()} 的默认值）：火焰是自发光，
     *       加法下"黑 = 不画"。体积火焰的离屏合成固定用加法。</li>
     *   <li>{@code drawInGui = false} —— GUI 不画体积（那是一个世界空间效果），
     *       16px 图标上也读不出来。</li>
     * </ul>
     */
    public static final GlowOutline FIRE_OUTLINE = new GlowOutline(0xFFFF6A18, 3.0f, "fire_2", false);

    /**
     * 本物品的层列表。<b>存成常量</b>而不是每次 {@code List.of(...)}：
     * {@link MaskLayerProvider#maskLayers} 在渲染热路径上每帧每物品都会被调用，
     * 那里不该有分配。
     */
    private final List<MaskLayerSpec> layers;

    /** 描边预设；{@code null} = 这块板不描边。见 {@link #LIGHT_OUTLINE}。 */
    @Nullable
    private final GlowOutline glowOutline;

    /** 不发光、不描边的一块板（纯材料）。 */
    public ManaPlateItem(@NotNull Properties properties) {
        this(properties, null, null);
    }

    /** 只发光、不描边的一块板。 */
    public ManaPlateItem(@NotNull Properties properties, @Nullable MaskLayerSpec glowLayer) {
        this(properties, glowLayer, null);
    }

    /**
     * @param properties  物品属性
     * @param glowLayer   泛光层；{@code null} = 这块板不发光
     * @param glowOutline 泛光描边预设；{@code null} = 这块板不描边
     */
    public ManaPlateItem(@NotNull Properties properties,
                         @Nullable MaskLayerSpec glowLayer,
                         @Nullable GlowOutline glowOutline) {
        super(properties);
        this.layers = glowLayer == null ? List.of() : List.of(glowLayer);
        this.glowOutline = glowOutline;
    }

    // ──────────────────────────────────────────────────────────────
    //  mask 效果层
    // ──────────────────────────────────────────────────────────────

    @Override
    public List<MaskLayerSpec> maskLayers(ItemStack stack) {
        return layers;
    }

    // ──────────────────────────────────────────────────────────────
    //  泛光描边（ICustomOutline）
    //
    //  这几个方法在 {@code ItemOutlinePipeline.resolveStyle} 里被调用；
    //  {@code outlineEnabled} 是第一道闸 —— 返回 false 时整条描边都不会提交，
    //  所以不描边的板不会付任何渲染代价。
    // ──────────────────────────────────────────────────────────────

    @Override
    public int outlineColor() {
        return glowOutline == null ? 0 : glowOutline.color();
    }

    @Override
    public float outlinePixelWidth() {
        return glowOutline == null ? 2.5f : glowOutline.pixelWidth();
    }

    /** 颜色模式：光明板是纯色（{@code null}），火焰板是 {@code "fire_2"}。 */
    @Override
    public String outlineShaderKey() {
        return glowOutline == null ? null : glowOutline.shaderKey();
    }

    /**
     * 只有传了 {@link GlowOutline} 的板才描边；戴在头上（{@code HEAD}）一律不描。
     *
     * <p>{@code ctx != HEAD} 与几把剑的处理一致：描边在物品栏图标、手持、地上都要有，
     * 唯独"作为头盔显示"时不要（物品进入 HEAD 上下文说明它被当成了头部装备，
     * 那时再绕一圈亮边只会干扰角色外观）。</p>
     *
     * <p>GUI 由预设自己的 {@link GlowOutline#drawInGui()} 决定 —— 可堆叠材料（火焰板）
     * 关掉它，避免"一个满格箱子 × 每件一次光雾模糊链"。</p>
     */
    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        if (glowOutline == null || ctx == ItemDisplayContext.HEAD) return false;
        return ctx != ItemDisplayContext.GUI || glowOutline.drawInGui();
    }
}
