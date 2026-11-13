package org.bytechen.rottenruins.api;

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
 * 实现此接口的武器即可获得低版本（1.8 式）右键格挡：接口即能力，事件层只做 {@code instanceof}，无需注册。
 *
 * <p>三条约定：{@code getUseDuration} 返回 {@link #BLOCK_USE_DURATION}；{@code getUseAnimation} 返回 {@link UseAnim#NONE}
 * （1.20 对 BLOCK 走盾牌分支：隐藏手臂 + 渲染盾模型，武器会整个消失）；{@code useOnRelease} 返回 true
 * （否则按满时长会走 {@code completeUsingItem()}、跳过 {@code releaseUsing()}，收剑回调不触发）。</p>
 */
public interface IBlockingWeapon {

    /** "永远按不满"的使用时长，取值参考原版盾牌；{@code getUseDuration} 直接返回它即可。 */
    int BLOCK_USE_DURATION = 72000;

    /** 格挡成功时的伤害倍率，默认 0.25（减伤 75%）；1.8 的剑格挡是 0.5。 */
    default float blockDamageMultiplier() {
        return 0.25f;
    }

    /** 格挡是否只挡正面来敌（1.8 语义）；false 则背后也能挡住。 */
    default boolean blockOnlyFrontal() {
        return true;
    }

    default int blockHitParticleCount() {
        return 0;
    }

    default ParticleOptions blockHitParticle() {
        return null;
    }

    default Component blockFeedbackMessage() {
        return null;
    }

    /** 格挡期间是否禁止挖掘方块（原版 1.8 举剑时挖不动）；实现需在 {@code canAttackBlock} 里调 {@link #blocksMining}。 */
    default boolean blockDisablesMining() {
        return true;
    }

    /**
     * 让第三人称显示原版盾牌的格挡手势：在 {@code Item#canPerformAction} 里直接返回它。
     * <p>声明 {@code SHIELD_BLOCK} 才让 {@code LivingEntity.isBlocking()} 为真，第三人称 {@code ArmPose} 才有举盾姿势
     * （只在服务端声明的话，别的玩家看你时没有）。第一人称不受盾牌分支影响，因为 {@code CrimsonVowBlockMixin}
     * 在 {@code renderArmWithItem} 的 HEAD 就 cancel 了；唯一副作用是 HUD 会显示原版盾牌图标。</p>
     */
    static boolean handlesShieldBlockAction(ItemStack stack,
                                            net.minecraftforge.common.ToolAction action) {
        if (action != net.minecraftforge.common.ToolActions.SHIELD_BLOCK) return false;
        return stack != null && !stack.isEmpty();
    }

    /** 按下右键、格挡开始（服务端）；上 buff、播技能音、起冷却都放这里，此时 {@code isUsingItem()} 已为 true。 */
    default void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
    }

    /** 格挡持续中，每个服务端 tick 调用一次；{@code heldTicks} 从 1 开始。 */
    default void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
    }

    /** 松开右键、格挡结束（服务端）；{@code heldTicks} 为本次格挡总时长。 */
    default void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
    }

    /** 格挡成功挡下一次伤害（服务端）：可做反击、消耗耐久、播弹刀音效。 */
    default void onBlockedHit(ItemStack stack, Level level, Player player,
                              net.minecraft.world.damagesource.DamageSource source,
                              float original, float reduced) {
    }

    /** 开发期自检：{@code getUseAnimation} 漏改会让武器整个消失，症状难从代码看出来，不如在日志里直说。 */
    default void validateUseAnim(Player player, ItemStack stack) {
        Item item = stack.getItem();
        UseAnim anim = item.getUseAnimation(stack);
        if (anim != UseAnim.NONE) {
            org.bytechen.rottenruins.utils.ModUtils.LOGGER.warn(
                    "[IBlockingWeapon] {} 的 getUseAnimation() 返回 {}，不是 NONE。"
                            + "1.20 对 BLOCK 会走盾牌分支（隐藏手臂 + 画盾模型），武器会整个消失；"
                            + "请改成 UseAnim.NONE 再由客户端混入套格挡姿态。",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item), anim);
        }
        if (item.getUseDuration(stack) < 3600) {
            org.bytechen.rottenruins.utils.ModUtils.LOGGER.warn(
                    "[IBlockingWeapon] {} 的 getUseDuration() 只有 {}，格挡是"
                            + "\"按住直到松开\"，建议返回 IBlockingWeapon.BLOCK_USE_DURATION(72000)。",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item),
                    item.getUseDuration(stack));
        }
    }

    /** 该实体此刻是否正在用某把 {@link IBlockingWeapon} 格挡（只认正在使用的那只手，同原版做法）。 */
    static boolean isBlocking(LivingEntity entity) {
        if (entity == null || !entity.isUsingItem()) return false;
        return resolve(entity.getUseItem()) != null;
    }

    static IBlockingWeapon resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItem() instanceof IBlockingWeapon weapon ? weapon : null;
    }

    static IBlockingWeapon blockingWeapon(LivingEntity entity) {
        if (entity == null || !entity.isUsingItem()) return null;
        return resolve(entity.getUseItem());
    }

    /** 开始格挡的推荐实现体：客户端走原版预测的手臂挥动，{@link #onBlockStart} 只在服务端触发，回调不会重复。 */
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

    /** 结束格挡的推荐实现体：触发 {@link #onBlockStop} 并清掉使用状态；幂等。 */
    static void endBlock(ItemStack stack, Level level, Player player) {
        if (level.isClientSide()) return;
        if (!(stack.getItem() instanceof IBlockingWeapon weapon)) return;
        if (!player.isUsingItem()) return;
        int held = heldTicks(player);
        player.stopUsingItem();
        weapon.onBlockStop(stack, level, player, held);
    }

    static int heldTicks(LivingEntity entity) {
        return Math.max(1, entity.getTicksUsingItem());
    }

    /** 举着武器时挖方块无效 —— 实现 {@code canAttackBlock} 时直接返回它，与 {@link #blockDisablesMining} 配合。 */
    static boolean blocksMining(Player player) {
        IBlockingWeapon weapon = blockingWeapon(player);
        return weapon != null && weapon.blockDisablesMining();
    }

    /** 服务端每 tick 的驱动：由本模组的 {@code ForgeEventHandler.onLivingTick} 统一调用一次即可。 */
    static void tickBlocking(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        Level level = entity.level();
        if (level.isClientSide()) return;
        IBlockingWeapon weapon = blockingWeapon(entity);
        if (weapon == null) return;
        weapon.onBlockTick(player.getUseItem(), level, player, heldTicks(entity));
    }

    /** 格挡反馈：实现可在 {@link #onBlockedHit} 里做正事；返回是否挡下了。 */
    static boolean notifyBlockedHit(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
                                    float original, float reduced) {
        if (!(entity instanceof Player player)) return false;
        IBlockingWeapon weapon = blockingWeapon(entity);
        if (weapon == null) return false;

        Level level = entity.level();
        if (level.isClientSide()) return false;

        weapon.onBlockedHit(player.getUseItem(), level, player, source, original, reduced);

        if (player instanceof ServerPlayer serverPlayer) {
            Component msg = weapon.blockFeedbackMessage();
            if (msg != null) {
                serverPlayer.displayClientMessage(msg, true);
            }
        }

        int count = weapon.blockHitParticleCount();
        ParticleOptions particle = weapon.blockHitParticle();
        if (count > 0 && particle != null && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, 0.25, 0.25, 0.25, 0.05);
        }

        if (weapon instanceof net.minecraft.world.item.Item) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.6f, 1.2f + (float) entity.getRandom().nextGaussian() * 0.05f);
        }
        return true;
    }

    static float clampAngle(float degrees) {
        return Mth.clamp(degrees, -90.0f, 90.0f);
    }
}
