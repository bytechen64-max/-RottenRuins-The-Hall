package org.bytechen.hall.api;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 实现此接口的武器即可获得<b>低版本（1.8 式）右键格挡</b>能力。
 *
 * <p>与本模组 {@code ICosmicLayer} / {@code ICustomOutline} 同一套设计：接口即能力，
 * 渲染与事件层只做 {@code instanceof} 判断，<b>不需要任何注册</b>。</p>
 *
 * <h3>最小用法</h3>
 * <pre>{@code
 * public class MySword extends SwordItem implements IBlockingWeapon {
 *     // 其余全部走默认值
 * }
 * }</pre>
 *
 * <h3>必须遵守的三条约定（否则格挡表现会不对）</h3>
 * <ol>
 *   <li><b>{@code getUseDuration} 要给超大值</b> —— 格挡是"按住直到松开"，
 *       给有限时长会出现"举到时间自己落地"。可直接返回
 *       {@link #BLOCK_USE_DURATION}。</li>
 *   <li><b>{@code getUseAnimation} 必须返回 {@link UseAnim#NONE}</b> ——
 *       1.20 的 {@code ItemInHandRenderer} 对 {@code UseAnim.BLOCK} 会走<b>盾牌分支</b>
 *       （隐藏手臂 + 渲染盾模型），武器会整个消失。用 {@code NONE} 保留原版手臂渲染，
 *       再由客户端混入套上格挡姿态。可调 {@link #validateUseAnim(Player, ItemStack)}
 *       在开发期帮你把这条漏掉的配置骂出来。</li>
 *   <li><b>{@code useOnRelease} 要返回 true</b> —— 与 {@code DomeriteLongsword} 同理，
 *       否则"按满时长"会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}，
 *       收剑回调不会触发。</li>
 * </ol>
 *
 * <h3>推荐写法</h3>
 * <pre>{@code
 * @Override
 * public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
 *     return IBlockingWeapon.beginBlock(level, player, hand);   // 顺带处理客户端预测
 * }
 *
 * @Override
 * public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
 *     if (entity instanceof Player p) IBlockingWeapon.endBlock(stack, level, p);
 *     return stack;
 * }
 * }</pre>
 */
public interface IBlockingWeapon {

    // ──────────────────────────────────────────────────────────────
    //  常量
    // ──────────────────────────────────────────────────────────────

    /**
     * "永远按不满"的使用时长，取值参考原版盾牌。
     * 实现 {@code getUseDuration} 时直接返回它即可。
     */
    int BLOCK_USE_DURATION = 72000;

    // ──────────────────────────────────────────────────────────────
    //  数值配置（都有默认值，按需覆写）
    // ──────────────────────────────────────────────────────────────

    /**
     * 格挡成功时的伤害倍率。
     *
     * <p>默认 {@code 0.25}（减伤 75%）。1.8 的剑格挡是 50%，这里默认更硬一点 ——
     * 因为格挡要求玩家放弃全部输出。想还原原版手感就覆写成 {@code 0.5f}。</p>
     */
    default float blockDamageMultiplier() {
        return 0.25f;
    }

    /**
     * 格挡是否只挡<b>正面</b>来敌。
     *
     * <p>默认 {@code true}（1.8 语义：面向哪边挡哪边）。返回 false 则背后也能挡住。</p>
     */
    default boolean blockOnlyFrontal() {
        return true;
    }

    /**
     * 格挡成功时的粒子数量，生成在攻击者与受击者之间。
     * 返回 {@code 0} 表示不要这套反馈（默认）。
     */
    default int blockHitParticleCount() {
        return 0;
    }

    /** 格挡成功时的粒子类型。仅当 {@link #blockHitParticleCount()} &gt; 0 时使用。 */
    default ParticleOptions blockHitParticle() {
        return null;
    }

    /** 格挡成功时提示给玩家的消息；返回 {@code null} 表示不提示。 */
    default Component blockFeedbackMessage() {
        return null;
    }

    /**
     * 格挡期间是否禁止挖掘方块（原版 1.8 举剑时挖不动）。
     * 默认 {@code true}；返回 true 的实现请在 {@code canAttackBlock} 里调用
     * {@link #blocksMining(Player)}。
     */
    default boolean blockDisablesMining() {
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  第三人称：借用原版的盾牌格挡手势
    // ──────────────────────────────────────────────────────────────

    /**
     * <b>让第三人称显示出原版盾牌的格挡手势</b>——在物品类里覆写
     * {@code Item#canPerformAction} 时直接返回它：
     *
     * <pre>{@code
     * @Override
     * public boolean canPerformAction(ItemStack stack, ToolAction action) {
     *     return IBlockingWeapon.handlesShieldBlockAction(stack, action)
     *             || super.canPerformAction(stack, action);
     * }
     * }</pre>
     *
     * <h3>原理</h3>
     * <p>1.20.1 的 {@code LivingEntity.isBlocking()} 实际是这样判的（字节码核对）：</p>
     * <pre>
     *   if (isUsingItem() && !useItem.isEmpty()) {
     *       Item item = useItem.getItem();
     *       if (!useItem.canPerformAction(ToolActions.SHIELD_BLOCK)) return false;
     *       ...
     *       return item.getUseAnimation(useItem) == UseAnim.BLOCK;
     *   }
     * </pre>
     * <p>而第三人称的手臂姿势由 {@code HumanoidModel.ArmPose} 决定，
     * 它读的正是 {@code isBlocking()} —— 所以只要声明 {@code SHIELD_BLOCK}，
     * 就能白拿原版那套举盾姿势（手臂横挡在身前），不必自己摆骨骼。</p>
     *
     * <h3>为什么可以无条件声明（不会被第一人称的盾牌分支坑到）</h3>
     * <p>客户端一旦返回 true，第一人称的 {@code ItemInHandRenderer.renderArmWithItem}
     * 确实会走到"使用中"分支，并在 {@code UseAnim} 的 {@code tableswitch} 里进 BLOCK
     * 那个 case（隐藏手臂 + 画盾模型 + HUD 盾牌图标）。</p>
     *
     * <p>但是：本模组的 {@code CrimsonVowBlockMixin} 是在同一个
     * {@code renderArmWithItem} 的 <b>HEAD 就 cancel</b> 的 —— 也就是说整个原版分支
     * （包括盾牌 case）根本不会执行，第一人称始终是"我们自己的举剑姿态"。
     * 所以这里无条件返回 true 是安全的，而且好处是<b>所有客户端的第三人称视角
     * 都能看到原版盾牌格挡姿势</b>（只在服务端声明的话，别人看你时是没有的）。</p>
     *
     * <p>唯一副作用：举武器时会显示原版那个盾牌图标（HUD）。它是"正在格挡"的
     * 直观提示，先保留；若要换成别的图标，需要单独处理 HUD 渲染。</p>
     */
    static boolean handlesShieldBlockAction(ItemStack stack,
                                            net.minecraftforge.common.ToolAction action) {
        if (action != net.minecraftforge.common.ToolActions.SHIELD_BLOCK) return false;
        return stack != null && !stack.isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    //  生命周期回调 —— 全部在【服务端】调用
    // ──────────────────────────────────────────────────────────────

    /**
     * <b>按下右键、格挡开始</b>时调用（服务端）。
     *
     * <p>这是本接口最主要的钩子：上 buff、播技能音、打日志、起冷却都放这里。
     * 默认空实现。</p>
     *
     * <p>触发时机与 {@code Player.startUsingItem} 同步，因此
     * {@code entity.isUsingItem()} 此时已经为 true。</p>
     *
     * @param stack  正在被举起的武器
     * @param level  服务端世界
     * @param player 举剑的玩家
     * @param hand   举剑的手
     */
    default void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
    }

    /**
     * <b>格挡持续中</b>，每个服务端 tick 调用一次。
     *
     * <p>适合做持续消耗、进度累积、按住时长判定等。默认空实现。</p>
     *
     * @param heldTicks 从开始格挡起已经持续了多少 tick（第一次回调为 1）
     */
    default void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
    }

    /**
     * <b>松开右键、格挡结束</b>时调用（服务端）。
     *
     * <p>默认空实现。</p>
     *
     * @param heldTicks 本次格挡总共持续了多少 tick
     */
    default void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
    }

    /**
     * <b>格挡成功挡下一次伤害</b>时调用（服务端）。
     *
     * <p>默认空实现。可以用来做反击、消耗耐久、播弹刀音效。</p>
     *
     * @param source   被挡下的伤害来源
     * @param original 减免前的伤害
     * @param reduced  减免后的伤害
     */
    default void onBlockedHit(ItemStack stack, Level level, Player player,
                              net.minecraft.world.damagesource.DamageSource source,
                              float original, float reduced) {
    }

    // ──────────────────────────────────────────────────────────────
    //  开发期自检
    // ──────────────────────────────────────────────────────────────

    /**
     * 检查三条必须约定，违反时打印一次告警。
     *
     * <p>只在开始格挡时调用一次，代价可忽略。存在的意义：{@code getUseAnimation}
     * 漏改会让武器"整个消失"，那种症状很难从代码上看出来，不如在日志里直说。</p>
     */
    default void validateUseAnim(Player player, ItemStack stack) {
        Item item = stack.getItem();
        UseAnim anim = item.getUseAnimation(stack);
        if (anim != UseAnim.NONE) {
            org.bytechen.hall.utils.ModUtils.LOGGER.warn(
                    "[IBlockingWeapon] {} 的 getUseAnimation() 返回 {}，不是 NONE。"
                            + "1.20 对 BLOCK 会走盾牌分支（隐藏手臂 + 画盾模型），武器会整个消失；"
                            + "请改成 UseAnim.NONE 再由客户端混入套格挡姿态。",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item), anim);
        }
        if (item.getUseDuration(stack) < 3600) {
            org.bytechen.hall.utils.ModUtils.LOGGER.warn(
                    "[IBlockingWeapon] {} 的 getUseDuration() 只有 {}，格挡是"
                            + "\"按住直到松开\"，建议返回 IBlockingWeapon.BLOCK_USE_DURATION(72000)。",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item),
                    item.getUseDuration(stack));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  静态工具（实现里直接调用，避免每个武器重复写同一套）
    // ──────────────────────────────────────────────────────────────

    /**
     * 该实体此刻是否正在用某把 {@link IBlockingWeapon} 格挡。
     *
     * <p>用 {@code getUseItem()} 而不是遍历双手，这是原版自己的做法 ——
     * 它天然只认"正在被使用的那只手"。</p>
     */
    static boolean isBlocking(LivingEntity entity) {
        if (entity == null || !entity.isUsingItem()) return false;
        return resolve(entity.getUseItem()) != null;
    }

    /** 正在使用的物品若实现了本接口则返回它，否则返回 null。 */
    static IBlockingWeapon resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItem() instanceof IBlockingWeapon weapon ? weapon : null;
    }

    /** 正在格挡时返回手里那把武器的接口实例，否则 null。 */
    static IBlockingWeapon blockingWeapon(LivingEntity entity) {
        if (entity == null || !entity.isUsingItem()) return null;
        return resolve(entity.getUseItem());
    }

    /**
     * 开始格挡的推荐实现体，直接 {@code return} 它即可。
     *
     * <p>客户端与服务端都会走到（客户端的调用是原版预测的手臂挥动），
     * 而 {@link #onBlockStart} 只在服务端触发 —— 这样回调天然不会重复。</p>
     */
    static net.minecraft.world.InteractionResultHolder<ItemStack> beginBlock(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (!level.isClientSide() && stack.getItem() instanceof IBlockingWeapon weapon) {
            weapon.validateUseAnim(player, stack);
            weapon.onBlockStart(stack, level, player, hand);
        }
        return net.minecraft.world.InteractionResultHolder.consume(stack);
    }

    /**
     * 结束格挡的推荐实现体：触发 {@link #onBlockStop} 并清掉使用状态。
     *
     * <p>幂等 —— 没在格挡时调用不会重复触发回调。</p>
     */
    static void endBlock(ItemStack stack, Level level, Player player) {
        if (level.isClientSide()) return;
        if (!(stack.getItem() instanceof IBlockingWeapon weapon)) return;
        if (!player.isUsingItem()) return;   // 已经收剑过，别重复回调
        int held = heldTicks(player);
        player.stopUsingItem();
        weapon.onBlockStop(stack, level, player, held);
    }

    /**
     * 本次格挡已经持续了多少 tick。
     *
     * <p>{@code getTicksUsingItem()} 从 1 开始计数，所以刚举起时是 1。</p>
     */
    static int heldTicks(LivingEntity entity) {
        return Math.max(1, entity.getTicksUsingItem());
    }

    /**
     * 举着武器时挖方块无效 —— 实现 {@code canAttackBlock} 时直接返回它。
     * 与 {@link #blockDisablesMining()} 配合。
     */
    static boolean blocksMining(Player player) {
        IBlockingWeapon weapon = blockingWeapon(player);
        return weapon != null && weapon.blockDisablesMining();
    }

    /**
     * 服务端每 tick 的驱动：给正在格挡的玩家发 {@link #onBlockTick}。
     *
     * <p>由本模组的 {@code ForgeEventHandler.onLivingTick} 统一调用一次即可，
     * 不需要每个武器自己挂 tick 事件。</p>
     */
    static void tickBlocking(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        Level level = entity.level();
        if (level.isClientSide()) return;
        IBlockingWeapon weapon = blockingWeapon(entity);
        if (weapon == null) return;
        weapon.onBlockTick(player.getUseItem(), level, player, heldTicks(entity));
    }

    /**
     * 格挡反馈：默认什么都没做，实现可在 {@link #onBlockedHit} 里做正事。
     *
     * @return 是否挡下了（供调用方决定要不要播通用反馈）
     */
    static boolean notifyBlockedHit(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
                                    float original, float reduced) {
        if (!(entity instanceof Player player)) return false;
        IBlockingWeapon weapon = blockingWeapon(entity);
        if (weapon == null) return false;

        Level level = entity.level();
        if (level.isClientSide()) return false;

        weapon.onBlockedHit(player.getUseItem(), level, player, source, original, reduced);

        // 可选：玩家自己看得见的一行提示
        if (player instanceof ServerPlayer serverPlayer) {
            Component msg = weapon.blockFeedbackMessage();
            if (msg != null) {
                serverPlayer.displayClientMessage(msg, true);
            }
        }

        // 可选：在受击点炸一簇粒子
        int count = weapon.blockHitParticleCount();
        ParticleOptions particle = weapon.blockHitParticle();
        if (count > 0 && particle != null && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, 0.25, 0.25, 0.25, 0.05);
        }

        // 兜底音效：实现没自己播的话，给一个通用弹刀声，避免"挡了但毫无反馈"
        if (weapon instanceof net.minecraft.world.item.Item) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.6f, 1.2f + (float) entity.getRandom().nextGaussian() * 0.05f);
        }
        return true;
    }

    /** 小工具：把角度限制在合法区间（给做姿态的实现用）。 */
    static float clampAngle(float degrees) {
        return Mth.clamp(degrees, -90.0f, 90.0f);
    }
}
