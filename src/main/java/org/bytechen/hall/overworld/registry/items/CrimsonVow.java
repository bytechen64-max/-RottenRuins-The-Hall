package org.bytechen.hall.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import org.bytechen.hall.api.IBlockingWeapon;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.api.IFlowingName;
import org.bytechen.hall.api.ITooltipStyle;
import org.bytechen.hall.api.TooltipShaderSpec;
import org.bytechen.hall.client.rend.text.FlowingNameColors;
import org.bytechen.hall.client.rend.text.TooltipLines;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import org.bytechen.hall.utils.ModUtils;
import org.bytechen.hall.utils.TranslateUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BoBao
 * CrimsonVow — 深粉方块图案 + 雾粉紫流动描边 + <b>低版本（1.8 式）右键格挡</b>。
 *
 * <h3>Visual layers</h3>
 * <ol>
 *   <li>物品本体纹理（原版渲染，普通剑的 handheld 模型）</li>
 *   <li>Blocks 图案层（模型 JSON 里的 {@code "loader": "hall:cosmic"}，
 *       {@code "style": 17} → {@link org.bytechen.hall.api.CosmicStyle#CRIMSON_VOW}）。
 *       这一层<b>不</b>走星空采样，而是跑 shadertoy「Fast, Minimal Animated Blocks」
 *       那套 Voronoi 三角形度量（见 {@code cosmic.fsh} 的 blockDist/blockCell/blockShade）。</li>
 *   <li>雾粉紫流动描边（本类实现 {@link ICustomOutline}）</li>
 * </ol>
 *
 * <h3>右键格挡（低版本行为）</h3>
 * <p>按住右键把剑举在身前，受到伤害时按比例减免，且只有<b>来自正面</b>的攻击
 * 才算格挡。本类通过实现 {@link IBlockingWeapon} 获得这套能力，
 * 结算在 {@link WeaponBlock}，第一人称举剑姿态在
 * client 侧的 {@code CrimsonVowBlockRig} + {@code ItemInHandRendererMixin}。</p>
 *
 * <p>这里只负责把原版「使用中」状态打开，三个覆写缺一不可：</p>
 * <ul>
 *   <li>{@link #getUseDuration} 返回超大值 —— 1.8 的格挡是「按住直到松开」，
 *       给有限时长会出现"举到时间自己落地"的怪异行为。</li>
 *   <li>{@link #useOnRelease} 返回 true —— 与 {@code DomeriteLongsword} 同理，
 *       否则按满时长会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}。</li>
 *   <li>{@link #getUseAnimation} 返回 {@code UseAnim.NONE} —— <b>关键</b>。
 *       不能用 {@code UseAnim.BLOCK}：1.20 的 {@code ItemInHandRenderer} 对 BLOCK
 *       走的是<b>盾牌分支</b>（隐藏手臂 + 渲染盾模型），剑会整个消失。
 *       NONE 保留原版手臂渲染，再套自写的低版本举剑姿态。</li>
 * </ul>
 */
public class CrimsonVow extends SwordItem implements ICustomOutline, ITwitchItem, IBlockingWeapon,
        IFlowingName, ITooltipStyle {

    public CrimsonVow() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    // ──────────────────────────────────────────────────────────────
    //  色板：一把剑只有一套颜色
    // ──────────────────────────────────────────────────────────────
    //
    // 这四处消费方全部指向同一组常量：名字的粉紫渐变、描边的暗部/亮部、
    // 以及 tooltip 里的标签与誓约条（见 appendHoverText / getTooltipImage）。
    // 之前描边色和名字色是各自写死的字面量，改一次要翻四个方法，还会漏。

    /** 亮粉 —— 名字渐变起色，也是 tooltip 数值与面板上边框的颜色。 */
    private static final int ACCENT_FROM = 0xFFFF4FB8;

    /** 紫 —— 名字渐变止色，也是面板下/侧边框的颜色。 */
    private static final int ACCENT_TO = 0xFF9B4DFF;

    /** 雾粉紫高光 —— 描边亮部，也是 tooltip 标签与刻度线的颜色。 */
    private static final int ACCENT_HIGHLIGHT = 0xFFE8A6FF;

    /** 深紫黑 —— 描边暗部，也是誓约条未填充那一段的轨道色。 */
    private static final int ACCENT_SHADOW = 0xFF2A1B3D;

    // ── tooltip 各行自己的渐变（三行三色，否则一列文字会糊成一片） ──
    //
    // 取色原则：都在本剑的粉紫色族里，但**色相彼此拉开**、亮度按"信息层级"排 ——
    // 誓词最亮最柔（它是修辞），格挡行偏暖（它是主动技），誓约行偏冷
    // （"不可损坏 / 火焰免疫"读起来是静态属性，也正好和底板的粉色热流对位）。

    /** 誓词（斜体）：浅紫 → 亮粉。 */
    private static final int LORE_FROM = 0xFFC9A7FF;
    private static final int LORE_TO = 0xFFFF4FB8;

    /** 「格挡」正文：灰紫 → 雾粉紫 —— 比标签暗一档，读起来才像正文而不是第二个标题。 */
    private static final int BLOCK_TEXT_FROM = 0xFF9A86C8;
    private static final int BLOCK_TEXT_TO = 0xFFE8A6FF;

    /** 「誓约」正文：冰蓝紫 → 雾紫（有意偏冷）。 */
    private static final int VOW_TEXT_FROM = 0xFF8FD6FF;
    private static final int VOW_TEXT_TO = 0xFFB388FF;

    // ── tooltip 底板（原来那块接近纯黑的 0xF0100010 太出戏，见 ITooltipStyle） ──

    /** 底板渐变上端：深紫红。 */
    private static final int TOOLTIP_BG_TOP = 0xF01E0D28;

    /** 底板渐变下端：更深的紫黑 —— 和上端形成纵深，而不是原版那种一整块死黑。 */
    private static final int TOOLTIP_BG_BOTTOM = 0xF00E0716;

    /**
     * 底板边框（1px 内框）上端：亮粉 {@code 0x80} 透明度。
     *
     * <p>用 {@link #accent} 从 {@link #ACCENT_FROM} 派生而不是另写一个字面量 ——
     * 否则换了色板这里就会悄悄留一个旧颜色。原版边框 alpha 只有 {@code 0x50}，
     * 抬到 {@code 0x80} 是因为我们要的是"这条 tooltip 有主人"，不是一条若有若无的线。</p>
     */
    private static final int TOOLTIP_BORDER_TOP = accent(ACCENT_FROM, 0x80);

    /** 底板边框下端：紫，同样 {@code 0x80} 透明度。 */
    private static final int TOOLTIP_BORDER_BOTTOM = accent(ACCENT_TO, 0x80);

    /** 取色板里的 RGB、换上新的 alpha（底板与边框都要半透明版本）。 */
    private static int accent(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }


    /**
     * 攻击距离加成（格）。
     *
     * <p>{@code forge:entity_reach} 默认 3.0、上限 1024，所以 100 是一个合法的普通值，
     * 不需要任何越界兜底。</p>
     */
    public static final double ATTACK_REACH_BONUS = 7;

    /**
     * 攻击距离修饰符的 UUID。
     *
     * <p>用固定的字面量而不是随机生成：同一个物品的同一个属性修饰符在不同存档、
     * 不同端之间必须同名同 id，否则会被原版当成两个修饰符叠加（来回切换会越加越多）。</p>
     */
    private static final UUID REACH_MODIFIER_UUID =
            UUID.nameUUIDFromBytes(
                    (CrimsonVow.class.getName() + ":reach")
                            .getBytes(StandardCharsets.UTF_8));

    /**
     * 主手拿在手里时，把玩家攻击距离抬到 {@link #ATTACK_REACH_BONUS} 格。
     *
     * <p>只改主手（{@code MAINHAND}）：这把剑是主手武器，放副手不该给玩家 100 格攻击距离。
     * 其它槽位原样返回 {@code super} 的结果，不插手。</p>
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) return original;

        HashMultimap<Attribute, AttributeModifier> dynamic = HashMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            dynamic.put(entry.getKey(), entry.getValue());
        }
        dynamic.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                REACH_MODIFIER_UUID, "reached", ATTACK_REACH_BONUS,
                AttributeModifier.Operation.ADDITION));
        return dynamic;
    }



    // ──────────────────────────────────────────────────────────────
    //  右键：进入格挡（举起）
    // ──────────────────────────────────────────────────────────────

    /**
     * 走 {@link IBlockingWeapon#beginBlock} —— 它除了 {@code startUsingItem} 之外，
     * 还会在服务端触发 {@link IBlockingWeapon#onBlockStart} 与那三条必须约定的自检。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return IBlockingWeapon.beginBlock(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return IBlockingWeapon.BLOCK_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  名字：粉→紫彩字
    // ──────────────────────────────────────────────────────────────
    //
    // 两条显示路径分别处理：
    //   · tooltip   —— FlowingNameTooltipHook 在构建时按当前时间重算逐字颜色（会流动）
    //   · 手持显示  —— Gui.renderSelectedItemName 每帧调 ItemStack.getHoverName()，
    //                  所以这里让 getName 直接返回"逐字带色的组件"（颜色在创建时定死）
    //
    // 为什么手持那条不做"流动"：让颜色随帧变化需要一个"每次取文本才计算"的组件，
    // 而 1.20.1 里 Component 是接口、MutableComponent 的构造函数又是包私有的，
    // 外部包两个都继承不了（实测 javac 直接拒）。为这点差异去引一条高风险的
    // 自定义组件/渲染注入不划算，所以手持给稳定的粉紫渐变。
    //
    // getDisplayName 也要一起覆写：getHoverName() 在"物品被改过名"时走的是它。

    // getDisplayName 在 ItemStack 上、不在 Item 上（Item 只有 getName），
    // 所以这里只覆写 getName 即可 —— ItemStack.getHoverName() 在没有自定义名时
    // 回退到的就是 item.getName(stack)，手持显示走的正是它。

    @Override
    public Component getName(ItemStack stack) {
        Component plain = super.getName(stack);
        if (!flowingNameEnabled()) return plain;
        return org.bytechen.hall.client.rend.text.FlowingNameColors.gradient(
                plain, flowingNameColorFrom(), flowingNameColorTo());
    }

    /** 渐变起色跟着 {@link #ACCENT_FROM} 走，不再依赖接口默认值。 */
    @Override
    public int flowingNameColorFrom() {
        return ACCENT_FROM;
    }

    /** 渐变止色跟着 {@link #ACCENT_TO} 走。 */
    @Override
    public int flowingNameColorTo() {
        return ACCENT_TO;
    }

    // ──────────────────────────────────────────────────────────────
    //  tooltip：文本层（appendHoverText）+ 自绘誓约条（getTooltipImage）
    // ──────────────────────────────────────────────────────────────
    //
    // 两层分工：
    //   · appendHoverText  —— 纯 Component，文案全部来自 datagen 语言文件；
    //   · getTooltipImage  —— 只声明数据，画的事交给
    //                          client.tooltip.ClientBlockBarTooltip（绯红誓约与寂寒白日共用）。
    //
    // 名字那一行不在这里：由 client 侧的 FlowingNameTooltipHook 在
    // ItemTooltipEvent 里替换 index 0，这里只从 index 1 往后追加，两边不打架。
    //
    // 数值一律从 IBlockingWeapon 现取（blockDamageMultiplier），不写字面量 ——
    // 否则以后调格挡强度，tooltip 会继续报旧数字。

    /**
     * tooltip 的文本层 —— <b>每一行都有自己的渐变</b>。
     *
     * <h3>名字行与其余行的分工</h3>
     * <ul>
     *   <li><b>名字行</b>：不在这里。由 client 侧的 {@code FlowingNameTooltipHook}
     *       在 {@code ItemTooltipEvent} 里替换 index 0。</li>
     *   <li><b>自定义说明行</b>：在这里构造，逐行给不同的渐变 ——
     *       誓词一行、格挡一行、誓约一行，三行的色相刻意不同（见下面三个
     *       {@code *_FROM/_TO} 常量），这样它们读起来是三条信息而不是一片糊。</li>
     *   <li>标签（「格挡」「誓约」）保持强调色板，正文比标签暗一档：
     *       一行之内"标签亮、正文暗"，各行之间"色相不同"。</li>
     * </ul>
     *
     * <h3>为什么用 {@code FlowingNameColors.line}</h3>
     * <p>本类在<b>公共代码</b>里（服务端也会加载），而流动版本内部要取
     * {@code Minecraft.getInstance()} —— 纯客户端类。所以"确实在客户端才流动"
     * 这个判断收在了 {@code FlowingNameColors.line} 里（判据是 {@code level.isClientSide}），
     * 物品类不再各写一遍。服务端 / level 为 null 的路径退回静态渐变，
     * 反正那些路径也没人看得见；这与 {@link #getName} 只敢用静态渐变是同一条约束。</p>
     *
     * <p>空行不再需要：誓约条（图像组件）被 Forge 插在 index 1，本身就充当了
     * 名字与说明之间的分隔。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        // 誓词：斜体保留，颜色换成它自己那对（浅紫 → 亮粉）
        tooltip.add(FlowingNameColors.line(
                Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_LORE)
                        .withStyle(ChatFormatting.ITALIC),
                LORE_FROM, LORE_TO, level));

        // 格挡行：标签与正文用的是<共用键>（机制说明几把剑是同一件事），
        // 颜色仍然是本剑自己的 —— 文案共享不会把配色也共享掉。
        tooltip.add(TooltipLines.feature(TranslateUtils.TOOLTIP_LABEL_BLOCK,
                TranslateUtils.TOOLTIP_BLOCK, level,
                ACCENT_FROM, ACCENT_TO, BLOCK_TEXT_FROM, BLOCK_TEXT_TO));

        // 特性行：标签是本剑的身份（「誓约」），正文共用
        tooltip.add(TooltipLines.feature(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_TRAIT,
                TranslateUtils.TOOLTIP_TRAIT, level,
                ACCENT_FROM, ACCENT_TO, VOW_TEXT_FROM, VOW_TEXT_TO));

        if (flag.isAdvanced()) {
            // F3+H：把格挡机制的实现参数摊开，排查"挡住了多少"时不用去翻代码。
            // 这一行<b>故意不上渐变</b>：它是调试信息，越朴素越好读。
            int percent = Math.round(blockDamageMultiplier() * 100.0F);
            tooltip.add(Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_DEBUG,
                            percent, blockOnlyFrontal(), BLOCK_USE_DURATION,
                            (int) ATTACK_REACH_BONUS)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * tooltip 里那条自绘的格挡减伤条。
     *
     * <p>只传数据不传渲染类型 —— 原因见 {@link BlockBarTooltip} 的类注释。
     * 返回 {@code Optional.of} 之后，Forge 会把它插到 tooltip 的 index 1
     * （紧跟名字行），所以它总是压在 lore 之上。</p>
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new BlockBarTooltip(
                blockDamageMultiplier(),
                TranslateUtils.TOOLTIP_BAR_LABEL, TranslateUtils.TOOLTIP_BAR_VALUE,
                ACCENT_FROM, ACCENT_TO, ACCENT_HIGHLIGHT, ACCENT_SHADOW));
    }

    // ──────────────────────────────────────────────────────────────
    //  tooltip 底板（原版那块深灰底 + 淡蓝紫边）
    // ──────────────────────────────────────────────────────────────
    //
    // 底板不是我们画的 —— 原版 TooltipRenderUtil 画，颜色由 Forge 的
    // RenderTooltipEvent.Color 提供。所以这里只声明四个颜色，
    // 由 client.tooltip.TooltipStyleHook 在画之前写进去。
    //
    // 观感上的取法：底板换成"深紫红 → 紫黑"的纵向渐变（原来是一整块死黑），
    // 边框从淡蓝紫改成这把剑自己的粉→紫（alpha 0x80）。
    // 誓约条因此是"同色系里的一块构件"，而不是贴在黑盒子上的异物。

    @Override
    public int tooltipBackgroundTop() {
        return TOOLTIP_BG_TOP;
    }

    @Override
    public int tooltipBackgroundBottom() {
        return TOOLTIP_BG_BOTTOM;
    }

    @Override
    public int tooltipBorderTop() {
        return TOOLTIP_BORDER_TOP;
    }

    @Override
    public int tooltipBorderBottom() {
        return TOOLTIP_BORDER_BOTTOM;
    }

    /**
     * 底板上的<b>粉色热力学流动</b>：一块被从下方加热的板，热羽在粉紫之间翻滚。
     *
     * <p>图案由 {@code rendertype_tooltip_thermal.fsh} 现场算（三层噪声 + 域扭曲，
     * 无贴图），这里只声明"要哪个图案 + 用哪三个颜色"：</p>
     *
     * <ul>
     *   <li><b>冷底</b>取 {@link #TOOLTIP_BG_TOP} —— 也就是纯色底板那个深紫红，
     *       所以着色器没加载成功、退回纯色底板时，观感是同一个色系；</li>
     *   <li><b>热流</b>取 {@link #ACCENT_FROM}（亮粉）、<b>热核</b>取
     *       {@link #ACCENT_HIGHLIGHT}（雾粉紫）—— 与名字渐变、描边同一套色板。</li>
     * </ul>
     *
     * <p>强度 {@code 0.9}：再高文字对比度就开始掉了（面板最烫的地方接近亮粉）。
     * 想要更含蓄就往 0.6~0.7 走，想关掉就返回 {@code null} 或把强度设成 0。</p>
     */
    @Override
    public TooltipShaderSpec tooltipShader() {
        return TooltipShaderSpec.thermal(TOOLTIP_BG_TOP, ACCENT_FROM, ACCENT_HIGHLIGHT);
    }

    /**
     * 第三人称的"原版盾牌格挡手势"就靠这一个覆写换来 —— 原理见
     * {@link IBlockingWeapon#handlesShieldBlockAction}。
     *
     * <p>注意它<b>只在服务端</b>返回 true：客户端返回 true 会让第一人称走盾牌
     * 渲染分支（隐藏手 + 画盾），和我们自己的举剑姿态冲突。</p>
     */
    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
        return IBlockingWeapon.handlesShieldBlockAction(stack, action)
                || super.canPerformAction(stack, action);
    }

    /**
     * 格挡结束的汇合点。
     *
     * <p>{@code releaseUsingItem()} 是原版三条"收剑"路径的共同入口：
     * 松开右键、挥刀攻击（{@code Player.attack} 内部会调 {@code stopUsingItem}）、
     * 以及物品栈被替换。覆写它就能覆盖全部情况，不必逐条挂。</p>
     *
     * <p>{@link IBlockingWeapon#endBlock} 内部幂等（先查 {@code isUsingItem}），
     * 所以和 {@code completeUsingItem} 那条路径重叠调用也不会重复回调。</p>
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                            int timeLeft) {
        if (entity instanceof Player player) {
            IBlockingWeapon.endBlock(stack, level, player);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  格挡回调（全部只在服务端触发）
    // ──────────────────────────────────────────────────────────────

    /**
     * 按下右键、格挡开始。
     *
     * <p>服务端回调 —— 这就是"按下格挡时该做点事"的挂载点：
     * 上 buff / 起冷却 / 播动作音 / 记录开始时间都放这里。
     * 当前这把剑没有额外逻辑，只留一条 debug 日志便于排查。</p>
     */
    @Override
    public void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
        ModUtils.LOGGER.debug("[CrimsonVow] 格挡开始 player={} hand={}",
                player.getName().getString(), hand);
    }

    /**
     * 格挡持续中。
     *
     * <p>每 20 tick 心跳一次，避免刷屏；需要做持续消耗 / 进度累积时在这里扩展。</p>
     */
    @Override
    public void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
        if (heldTicks % 20 == 0) {
            ModUtils.LOGGER.debug("[CrimsonVow] 格挡持续 {} tick", heldTicks);
        }
    }

    /** 松开右键、格挡结束。 */
    @Override
    public void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
        ModUtils.LOGGER.debug("[CrimsonVow] 格挡结束，共 {} tick", heldTicks);
    }

    /** 挡下一次伤害：给一条短暂的提示，让玩家知道"这一下挡住了"。 */
    @Override
    public void onBlockedHit(ItemStack stack, Level level, Player player,
                             DamageSource source, float original, float reduced) {
        ModUtils.LOGGER.debug("[CrimsonVow] 格挡生效 {} -> {}", original, reduced);
    }

    /** 格挡成功时在身前炸一小簇粉色火花（用失心粒子之外的原版粒子，避免和命中反馈混淆）。 */
    @Override
    public int blockHitParticleCount() {
        return 6;
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions blockHitParticle() {
        return net.minecraft.core.particles.ParticleTypes.CRIT;
    }

    // ──────────────────────────────────────────────────────────────
    //  举剑期间的移动/挖掘惩罚（1.8 举剑要降速）
    // ──────────────────────────────────────────────────────────────

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.2f;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // 举剑时挖方块无效，避免"举着剑照样撸方块"
        return !IBlockingWeapon.blocksMining(player);
    }

    @Override
    public boolean twitchDisabled() {
        return true;
    }

    /** 关掉抽动后就不需要任何上下文判断，直接全线不抖。 */
    @Override
    public boolean twitchShouldRender(ItemDisplayContext ctx) {
        return false;
    }

    // ──────────────────────────────────────────────────────────────
    //  描边：绯红 / 深粉流动交替
    // ──────────────────────────────────────────────────────────────

    /** （描边暗部）。 */
    @Override
    public int outlineColor() {
        return ACCENT_SHADOW; // 深紫黑，粉紫阴影
    }

    /** （描边亮部）。 */
    @Override
    public int outlineSecondaryColor() {
        return ACCENT_HIGHLIGHT; // 雾粉紫高光
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";                               // = 双色流动
    }

    /**
     * 必须用 {@code TRANSLUCENT}：加法混合会把暗部整个吃掉，
     * 双色流动的对比就不成立了（和 {@link VoidSword} 同理）。
     */
    @Override
    public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    @Override
    public float outlinePixelWidth() {
        return 4.5f;
    }

    /** 默认实现只覆盖世界上下文；物品栏图标里也要描一圈。 */
    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.HEAD;
    }

    // getName 已在上面覆写成"流动彩字"版本，这里不再重复覆写
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }
}
