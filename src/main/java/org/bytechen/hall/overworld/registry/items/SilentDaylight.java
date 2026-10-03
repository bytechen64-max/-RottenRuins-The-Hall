package org.bytechen.hall.overworld.registry.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.api.IBlockingWeapon;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.api.IFlowingName;
import org.bytechen.hall.api.ITooltipStyle;
import org.bytechen.hall.api.TooltipShaderSpec;
import org.bytechen.hall.client.rend.text.FlowingNameColors;
import org.bytechen.hall.client.rend.text.TooltipLines;
import org.bytechen.hall.utils.ModUtils;
import org.bytechen.hall.utils.TranslateUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * SilentDaylight（静默白昼）— 水面湍流剑刃 + 深蓝/浅蓝流动描边 + <b>低版本（1.8 式）右键格挡</b>。
 *
 * <p>与 {@link CrimsonVow} 同一套构件，只是换了一条片元路径与一套配色：</p>
 *
 * <h3>Visual layers</h3>
 * <ol>
 *   <li>物品本体纹理（原版渲染，普通剑的 handheld 模型）</li>
 *   <li>水面湍流层（模型 JSON 里的 {@code "loader": "hall:cosmic"}，
 *       {@code "style": 18} → {@link org.bytechen.hall.api.CosmicStyle#SILENT_DAYLIGHT}）。
 *       这一层不是星空：它跑 shadertoy 那套<b>迭代三角反馈</b>的湍流，
 *       再用遮罩（{@code silent_daylight_mask}）把水"灌"进剑刃 —— 白 = 水下，
 *       剑柄留在外面。着色器本体与移植说明见 {@code cosmic.fsh} 的
 *       {@code waterTurbulence()}。</li>
 *   <li>深蓝 ↔ 浅蓝流动描边（本类实现 {@link ICustomOutline}）</li>
 * </ol>
 *
 * <h3>右键格挡（低版本行为）</h3>
 * <p>按住右键把剑举在身前，受到伤害时按比例减免，且只有<b>来自正面</b>的攻击
 * 才算格挡。能力来自 {@link IBlockingWeapon}，结算在 {@link WeaponBlock}，
 * 第一人称举剑姿态在 client 侧的 {@code CrimsonVowBlockRig} +
 * {@code ItemInHandRendererMixin} —— 那条姿态管线是按接口判定的，所以任何
 * {@code IBlockingWeapon} 都自动获得同一套举剑动作，不需要为这把剑新增混入。</p>
 *
 * <p>三个覆写缺一不可（原因见 {@link IBlockingWeapon} 的类注释）：</p>
 * <ul>
 *   <li>{@link #getUseDuration} 返回超大值 —— 1.8 的格挡是「按住直到松开」。</li>
 *   <li>{@link #useOnRelease} 返回 true —— 否则按满时长会走 {@code completeUsingItem()}
 *       而跳过 {@code releaseUsing()}，收剑回调不触发。</li>
 *   <li>{@link #getUseAnimation} 返回 {@code UseAnim.NONE} —— <b>关键</b>：
 *       {@code UseAnim.BLOCK} 会让 1.20 的第一人称走盾牌分支（隐藏手臂 + 画盾），
 *       剑会整个消失。</li>
 * </ul>
 *
 * <h3>tooltip（与 {@link CrimsonVow} 共用一套管线）</h3>
 * <p>文本层每一行都有自己的冷色渐变（誓词 / 格挡 / 湍流 / 白日），底板换成深蓝，
 * 上面压一层<b>冷色</b>的对流效果（同一个 {@code thermal} 图案换了三色，
 * 见 {@link #tooltipShader()}），条子由共用的
 * {@code client.tooltip.ClientBlockBarTooltip} 绘制。机制文案是共用键
 * （{@code hall.tooltip.*}），只有"湍流""白日"这种身份词属于本物品。</p>
 */
public class SilentDaylight extends SwordItem implements ICustomOutline, IBlockingWeapon,
        IFlowingName, ITooltipStyle {

    public SilentDaylight() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    // ──────────────────────────────────────────────────────────────
    //  色板：深蓝（暗部） / 浅蓝（亮部）
    // ──────────────────────────────────────────────────────────────
    //
    // 描边的两种颜色就是这两个常量。描边本体是屏幕空间的「剪影遮罩 + 环形膨胀」，
    // 颜色在 outline_mask 里逐像素按 sin 在两者之间插值并随时间滚动，
    // 所以看到的是一圈蓝白交替、持续流动的带子（见 docs/item-shader-outline.md）。
    //
    // 名字渐变、tooltip 底板与条子也从这一组常量派生 —— 一把剑只有一套颜色。

    /**
     * 深蓝 —— 描边暗部，也是减伤条的轨道色。
     *
     * <p>刻意不取"接近黑的海军蓝"：描边用的是 {@code TRANSLUCENT} 混合，
     * 暗部会真的把背景压下去，太暗会读成"剑外面有一圈黑边"，
     * 而不是"深蓝色的边"。这里保留足够的蓝通道，暗部才是蓝的。</p>
     */
    private static final int ACCENT_DEEP = 0xFF0B2E7A;

    /** 浅蓝 —— 描边亮部（冰蓝高光），也是名字渐变亮端与条子填充亮端。 */
    private static final int ACCENT_LIGHT = 0xFF8FE6FF;

    /**
     * 亮靛蓝 —— 名字渐变的暗端。
     *
     * <p>名字<b>不能</b>直接拿 {@link #ACCENT_DEEP} 当暗端：那接近黑蓝，
     * 压在深色 tooltip 底板上等于后半截字消失。所以另取一个更亮的蓝做文字端色，
     * 描边仍然用它自己的深蓝。</p>
     */
    private static final int ACCENT_MID = 0xFF4F8CFF;

    /** 冰白 —— 标签与条子刻度线的高光。 */
    private static final int ACCENT_ICE = 0xFFE4FAFF;

    // ── tooltip 底板（深蓝 → 近黑的蓝） ──

    /** 底板渐变上端：深海蓝。 */
    private static final int TOOLTIP_BG_TOP = 0xF0081830;

    /** 底板渐变下端：更深的蓝黑，给面板纵深。 */
    private static final int TOOLTIP_BG_BOTTOM = 0xF0030A18;

    /** 底板边框上端 = 浅蓝半透明（由 {@link #accent} 派生，避免两处色值漂移）。 */
    private static final int TOOLTIP_BORDER_TOP = accent(ACCENT_LIGHT, 0x80);

    /** 底板边框下端 = 亮靛蓝半透明。 */
    private static final int TOOLTIP_BORDER_BOTTOM = accent(ACCENT_MID, 0x80);

    // ── tooltip 各行自己的渐变（三行三色，否则一列文字会糊成一片） ──

    /** 誓词（斜体）：冰白 → 浅天蓝。 */
    private static final int LORE_FROM = 0xFFD9F4FF;
    private static final int LORE_TO = 0xFF6FC8FF;

    /** 「格挡」正文：灰蓝 → 冰蓝 —— 比标签暗一档，读起来才像正文。 */
    private static final int BLOCK_TEXT_FROM = 0xFF6E9FD8;
    private static final int BLOCK_TEXT_TO = 0xFFA8DCFF;

    /** 「湍流」正文：青绿 → 浅蓝（水色），与格挡行的灰蓝拉开。 */
    private static final int TIDE_TEXT_FROM = 0xFF7FE3D0;
    private static final int TIDE_TEXT_TO = 0xFF8FE6FF;

    /** 「白日」正文：浅蓝 → 亮靛 —— 收束到这把剑的主色上。 */
    private static final int TRAIT_TEXT_FROM = 0xFFB6E8FF;
    private static final int TRAIT_TEXT_TO = 0xFF7FA8FF;

    /** 取色板里的 RGB、换上新的 alpha（底板与边框都要半透明版本）。 */
    private static int accent(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // ──────────────────────────────────────────────────────────────
    //  右键：进入格挡（举起）
    // ──────────────────────────────────────────────────────────────

    /** 走 {@link IBlockingWeapon#beginBlock}，它顺带处理客户端预测与三条自检。 */
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

    /**
     * 收剑的汇合点 —— 松开右键、挥刀、物品栈被替换都会经过 {@code releaseUsing}。
     *
     * <p>{@link IBlockingWeapon#endBlock} 自身幂等（先查 {@code isUsingItem}），
     * 与 {@code completeUsingItem} 那条路径重叠调用也不会重复回调。</p>
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                            int timeLeft) {
        if (entity instanceof Player player) {
            IBlockingWeapon.endBlock(stack, level, player);
        }
    }

    /**
     * 第三人称的"原版盾牌格挡手势"由这一个覆写换来，原理见
     * {@link IBlockingWeapon#handlesShieldBlockAction}：{@code HumanoidModel.ArmPose}
     * 读的是 {@code isBlocking()}，而它要求物品声明 {@code SHIELD_BLOCK} 这个 tool action。
     *
     * <p>第一人称不会被它带进盾牌分支 —— 那条路径由客户端的
     * {@code CrimsonVowBlockMixin} 在 {@code renderArmWithItem} 里接管。</p>
     */
    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
        return IBlockingWeapon.handlesShieldBlockAction(stack, action)
                || super.canPerformAction(stack, action);
    }

    // ──────────────────────────────────────────────────────────────
    //  格挡回调（全部只在服务端触发）
    // ──────────────────────────────────────────────────────────────

    /** 按下右键、格挡开始。挂 buff / 起冷却 / 播动作音都放这里。 */
    @Override
    public void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡开始 player={} hand={}",
                player.getName().getString(), hand);
    }

    /** 每 20 tick 心跳一次，避免刷屏；需要持续消耗 / 进度累积时在这里扩展。 */
    @Override
    public void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
        if (heldTicks % 20 == 0) {
            ModUtils.LOGGER.debug("[SilentDaylight] 格挡持续 {} tick", heldTicks);
        }
    }

    /** 松开右键、格挡结束。 */
    @Override
    public void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡结束，共 {} tick", heldTicks);
    }

    /** 挡下一次伤害。做反击 / 耗耐久 / 弹刀音效时的挂载点。 */
    @Override
    public void onBlockedHit(ItemStack stack, Level level, Player player,
                             DamageSource source, float original, float reduced) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡生效 {} -> {}", original, reduced);
    }

    /**
     * 格挡成功时溅起的冷色火花。
     *
     * <p>用 {@code ELECTRIC_SPARK}（蓝白）而不是绯红誓约那串粉色 {@code CRIT}：
     * 两把剑的格挡反馈在视觉上就不该是一个东西。</p>
     */
    @Override
    public int blockHitParticleCount() {
        return 6;
    }

    @Override
    public ParticleOptions blockHitParticle() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    // ──────────────────────────────────────────────────────────────
    //  举剑期间的挖掘惩罚（1.8 举剑挖不动方块）
    // ──────────────────────────────────────────────────────────────

    /**
     * 挖掘速度压到 {@code 0.2}。
     *
     * <p>与 {@link CrimsonVow} 同样的取舍：{@code getDestroySpeed(stack, state)}
     * 的签名里<b>没有玩家</b>，拿不到"此刻是否在举剑"，所以只能给一个恒定值。
     * 「举剑时完全不能挖」那条由下面的 {@link #canAttackBlock} 精确处理。</p>
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.2f;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // 举剑时挖方块无效，避免"举着剑照样撸方块"
        return !IBlockingWeapon.blocksMining(player);
    }

    // ──────────────────────────────────────────────────────────────
    //  描边：深蓝 ↔ 浅蓝流动交替
    // ──────────────────────────────────────────────────────────────

    /** 描边暗部：深蓝。 */
    @Override
    public int outlineColor() {
        return ACCENT_DEEP;
    }

    /** 描边亮部：浅蓝。 */
    @Override
    public int outlineSecondaryColor() {
        return ACCENT_LIGHT;
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";                               // = 双色流动
    }

    /**
     * 必须用 {@code TRANSLUCENT}：加法混合会把深蓝那半截整个吃掉
     * （黑/暗 ≈ 加 0），双色流动就只剩浅蓝在闪，和 {@link VoidSword} 是同一条理由。
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

    // ──────────────────────────────────────────────────────────────
    //  名字：冰蓝 → 亮靛 彩字
    // ──────────────────────────────────────────────────────────────
    //
    // 与绯红誓约同一条路径：手持显示走 getName（静态渐变，颜色在创建时定死），
    // tooltip 首行由 client 侧的 FlowingNameTooltipHook 每帧重算（会流动）。
    // 原因见 IFlowingName 的类注释（Component 是接口、MutableComponent 的
    // 构造函数包私有，外部继承不了，所以做不出"每次取文本才算"的组件）。

    /** 名字渐变亮端：浅蓝（描边亮部同色）。 */
    @Override
    public int flowingNameColorFrom() {
        return ACCENT_LIGHT;
    }

    /** 名字渐变暗端：亮靛蓝 —— 刻意不用深蓝，那在深色底板上读不出来。 */
    @Override
    public int flowingNameColorTo() {
        return ACCENT_MID;
    }

    // ──────────────────────────────────────────────────────────────
    //  tooltip：文本层 + 共用的格挡减伤条 + 冷色底板
    // ──────────────────────────────────────────────────────────────
    //
    // 与绯红誓约共用同一套管线（见 docs/crimson-vow-tooltip.md）：
    //   · 文本行 —— 每行一对渐变，标签与正文是两个兄弟组件
    //     （TooltipLines.feature，渐变的逐字上色会把整行拍平）；
    //   · 条子   —— BlockBarTooltip + client.tooltip.ClientBlockBarTooltip；
    //   · 底板   —— ITooltipStyle 四色 + thermal 图案的冷色版。
    //
    // 机制文案（格挡 / 特性）用的是共用键 hall.tooltip.*：几把剑的格挡是同一件事，
    // 只有"湍流""白日"这种身份词属于本物品。颜色则完全归本物品 ——
    // 文案共享不会把配色也共享掉。

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        // 誓词：斜体保留，颜色换成它自己那对（冰白 → 浅天蓝）
        tooltip.add(FlowingNameColors.line(
                Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LORE)
                        .withStyle(net.minecraft.ChatFormatting.ITALIC),
                LORE_FROM, LORE_TO, level));

        tooltip.add(TooltipLines.feature(TranslateUtils.TOOLTIP_LABEL_BLOCK,
                TranslateUtils.TOOLTIP_BLOCK, level,
                ACCENT_LIGHT, ACCENT_MID, BLOCK_TEXT_FROM, BLOCK_TEXT_TO));

        // 只有这把剑有的一行：剑刃上的水光（它自己的身份）
        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE,
                TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_TIDE, level,
                ACCENT_ICE, ACCENT_LIGHT, TIDE_TEXT_FROM, TIDE_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT,
                TranslateUtils.TOOLTIP_TRAIT, level,
                ACCENT_ICE, ACCENT_LIGHT, TRAIT_TEXT_FROM, TRAIT_TEXT_TO));

        if (flag.isAdvanced()) {
            // F3+H 的调试行：故意不上渐变，越朴素越好读。
            // 与绯红誓约的区别是少一个参数 —— 本剑没有攻击距离加成。
            int percent = Math.round(blockDamageMultiplier() * 100.0F);
            tooltip.add(Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                            percent, blockOnlyFrontal(), BLOCK_USE_DURATION)
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 格挡减伤条 —— 与绯红誓约同一个组件，只是文案键（共用）与四个颜色不同。
     *
     * <p>轨道色用 {@link #ACCENT_DEEP}：它比底板（{@code #081830} / {@code #030A18}）
     * 亮，所以"空着的那 25%"看得见 —— 轨道与底板的明度关系是这条能不能读出来的关键。</p>
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new BlockBarTooltip(
                blockDamageMultiplier(),
                TranslateUtils.TOOLTIP_BAR_LABEL, TranslateUtils.TOOLTIP_BAR_VALUE,
                ACCENT_LIGHT, ACCENT_MID, ACCENT_ICE, ACCENT_DEEP));
    }

    // ── 底板（ITooltipStyle） ──

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
     * 底板上的对流效果，用的是<b>冷色版</b>：图案仍是 {@code thermal}
     * （{@code rendertype_tooltip_thermal.fsh} —— 下方加热的板、热羽上升），
     * 但三个颜色换成了冷底 → 冰蓝 → 冰白，于是它读起来是"寒流 / 水光"，
     * 而不是绯红誓约那层粉色热浪。
     *
     * <p>为什么先复用而不是另写一条水面焦散的 fsh：图案（对流场）与参数（三色）
     * 是分开的，换色就能拿到一把剑该有的观感；真要做出"水面湍流的干涉纹"
     * 再单开一个 key（{@code TooltipShaders.draw} 里加分支 + 一份新 fsh）即可，
     * 物品侧只改这一个方法的 key。</p>
     */
    @Override
    public TooltipShaderSpec tooltipShader() {
        return TooltipShaderSpec.thermal(TOOLTIP_BG_TOP, ACCENT_LIGHT, ACCENT_ICE);
    }

    // ──────────────────────────────────────────────────────────────
    //  与另外两把传说剑一致的三条：不可损坏 / 不可耗尽 / 合成后归还
    // ──────────────────────────────────────────────────────────────

    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }
}
