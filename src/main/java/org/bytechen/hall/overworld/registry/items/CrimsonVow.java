package org.bytechen.hall.overworld.registry.items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.api.IBlockingWeapon;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import org.bytechen.hall.utils.ModUtils;

/**
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
        org.bytechen.hall.api.IFlowingName {

    public CrimsonVow() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
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
        return 0xFF2A1B3D; // 深紫黑，粉紫阴影
    }

    /** （描边亮部）。 */
    @Override
    public int outlineSecondaryColor() {
        return 0xFFE8A6FF; // 雾粉紫高光
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
