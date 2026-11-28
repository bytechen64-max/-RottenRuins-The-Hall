package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.api.ICustomOutline;
import org.bytechen.rottenruins.api.IFlowingName;
import org.bytechen.rottenruins.api.ITooltipStyle;
import org.bytechen.rottenruins.api.TooltipShaderSpec;
import org.bytechen.rottenruins.client.rend.text.FlowingNameColors;
import org.bytechen.rottenruins.client.rend.text.TooltipLines;
import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.bytechen.rottenruins.utils.TranslateUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * SilentDaylight（静默白昼）—— 水面湍流剑刃 + 深蓝/浅蓝流动描边 + 低版本（1.8 式）右键格挡。
 * 与 {@link CrimsonVow} 同一套构件（减伤结算在 {@link WeaponBlock}，举剑姿态用同一条按接口判定的
 * client 管线 {@code CrimsonVowBlockRig} + {@code ItemInHandRendererMixin}），只是换了片元路径与配色。
 * 举剑期间同时维持寒气领域（回血 / 冰冻 / 吹散飞行物），状态就是原版 {@code isUsingItem}，不需要自定义同步。
 * getUseAnimation 必须 {@code UseAnim.NONE}：{@code BLOCK} 会让第一人称走盾牌分支，剑会整个消失。
 * tooltip 与 {@link CrimsonVow} 共用管线，机制文案用共用键，只有"湍流""白日"属于本物品。
 */
public class SilentDaylight extends SwordItem implements ICustomOutline, IBlockingWeapon,
        IFlowingName, ITooltipStyle {

    /**
     * 攻击力 37：SwordItem 的修饰器是 {@code attackDamageIn + 材质加成}，下界合金加成 4，
     * 所以这里传 32 得修饰器 36，加上玩家基础 1 点正好 37（原版 tooltip 显示的就是这个总数）。
     */
    public SilentDaylight() {
        super(Tiers.NETHERITE, 32, -2.4f, new Item.Properties().fireResistant());
    }

    /**
     * 深蓝 —— 描边暗部。刻意不取接近黑的海军蓝：描边用 {@code TRANSLUCENT}，暗部会真的压暗背景，
     * 太暗会读成"剑外一圈黑边"而不是"深蓝色的边"。
     */
    private static final int ACCENT_DEEP = 0xFF0B2E7A;

    private static final int ACCENT_LIGHT = 0xFF8FE6FF;

    /** 名字渐变的暗端不能直接用 ACCENT_DEEP（近黑蓝压在深色底板上等于后半截字消失）。 */
    private static final int ACCENT_MID = 0xFF4F8CFF;

    private static final int ACCENT_ICE = 0xFFE4FAFF;

    private static final int TOOLTIP_BG_TOP = 0xF0081830;

    private static final int TOOLTIP_BG_BOTTOM = 0xF0030A18;

    private static final int TOOLTIP_BORDER_TOP = accent(ACCENT_LIGHT, 0x80);

    private static final int TOOLTIP_BORDER_BOTTOM = accent(ACCENT_MID, 0x80);

    private static final int LORE_FROM = 0xFFD9F4FF;
    private static final int LORE_TO = 0xFF6FC8FF;

    private static final int BLOCK_TEXT_FROM = 0xFF6E9FD8;
    private static final int BLOCK_TEXT_TO = 0xFFA8DCFF;

    private static final int TIDE_TEXT_FROM = 0xFF7FE3D0;
    private static final int TIDE_TEXT_TO = 0xFF8FE6FF;

    private static final int TRAIT_TEXT_FROM = 0xFFB6E8FF;
    private static final int TRAIT_TEXT_TO = 0xFF7FA8FF;

    private static final int SLASH_TEXT_FROM = 0xFFD6F1FF;
    private static final int SLASH_TEXT_TO = 0xFF7FB4FF;

    private static final int WARD_TEXT_FROM = 0xFFCFEEFF;
    private static final int WARD_TEXT_TO = 0xFF63A6FF;

    /** 整数不带小数点、非整数保留一位 —— 直接 {@code %s} 会打出 {@code 8.0}，与原版 tooltip 写法不一致。 */
    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 1.0E-4F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int accent(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 走 {@link IBlockingWeapon#beginBlock}，它顺带处理客户端预测与三条自检。
     * <b>这里不能有任何"提前 return"的闸门</b>：原版 {@code MultiPlayerGameMode.useItem}
     * 在本物品处于冷却中时直接本地 PASS，连 {@code ItemStack.use} 都不调 ——
     * 那样右键既没有举剑姿势也没有领域，所以本剑刻意不给冷却。
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

    /** 收剑的汇合点（松手 / 挥刀 / 换栈都经过它）；endBlock 自身幂等。 */
    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                            int timeLeft) {
        if (entity instanceof Player player) {
            IBlockingWeapon.endBlock(stack, level, player);
        }
    }

    /**
     * 第三人称的盾牌格挡手势由这一个覆写换来（{@code ArmPose} 读 {@code isBlocking()}，它要求
     * 物品声明 {@code SHIELD_BLOCK} tool action）；第一人称不会被带进盾牌分支，由客户端 mixin 接管。
     */
    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
        return IBlockingWeapon.handlesShieldBlockAction(stack, action)
                || super.canPerformAction(stack, action);
    }

    @Override
    public void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡开始 player={} hand={}",
                player.getName().getString(), hand);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    24, 0.9D, 1.0D, 0.9D, 0.12D);
        }
    }

    /** 举剑期间每 tick 一次，是寒气领域的<b>唯一</b>驱动点 —— 它跟着原版"正在使用本剑"的状态走。 */
    @Override
    public void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        tickWard(serverLevel, player, heldTicks);
        if (heldTicks % 20 == 0) {
            ModUtils.LOGGER.debug("[SilentDaylight] 格挡持续 {} tick", heldTicks);
        }
    }

    /** 领域半径（格）。 */
    public static final double WARD_RADIUS = 6.0D;

    /** 每 tick 回复的生命值（4 tick 回 1 点 ≈ 5 点/秒）。 */
    public static final float WARD_HEAL_PER_TICK = 0.25F;

    /** 冰冻判定：每隔这么多 tick 给范围内生物顶一次冻结计时。 */
    public static final int WARD_FREEZE_INTERVAL = 5;

    /** 每次冰冻结算额外造成的一次冰伤（正常伤害，吃护甲与事件，可以致死）。 */
    public static final float WARD_FREEZE_DAMAGE = 2.0F;

    /** 玩家此刻是否在维持寒气领域（客户端也用这个判断画三棱锥；{@code isUsingItem} 原版就同步）。 */
    public static boolean isWarding(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return player.isUsingItem() && player.getUseItem().getItem() instanceof SilentDaylight;
    }

    /** 手上（主手或副手）是否拿着寂寒白日：三棱锥的<b>常驻</b>显示条件，举剑时变成三颗。 */
    public static boolean isHolding(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().getItem() instanceof SilentDaylight
                || player.getOffhandItem().getItem() instanceof SilentDaylight;
    }

    /**
     * 寒气领域的一次结算（服务端，每 tick）：回血、每 {@value #WARD_FREEZE_INTERVAL} tick 顶满冻结计时
     * 并附带缓慢（用原版 powder-snow 冻结，抗冻生物自然免疫）、以及销毁范围内所有投射物。
     */
    private static void tickWard(net.minecraft.server.level.ServerLevel level, Player player,
                                 int heldTicks) {
        float heal = (float) (player.getHealth()+player.getMaxHealth()*0.02);
        if (player.isAlive() && player.getHealth() < player.getMaxHealth()) {
            player.getEntityData().set(DATA_HEALTH_ID,heal);
        }

        net.minecraft.world.phys.AABB box =
                player.getBoundingBox().inflate(WARD_RADIUS);
        double radiusSqr = WARD_RADIUS * WARD_RADIUS;
        net.minecraft.world.phys.Vec3 center = player.getBoundingBox().getCenter();

        if (heldTicks % WARD_FREEZE_INTERVAL == 0) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive() && e.canFreeze()
                            && e.getBoundingBox().distanceToSqr(center) <= radiusSqr)) {
                living.setTicksFrozen(living.getTicksRequiredToFreeze());
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ(),
                        6, 0.3D, 0.4D, 0.3D, 0.01D);

                // 冰冻也要能杀人，而且伤害类型要是"冰"：
                // damageSources().freeze() 没有攻击者（掉落缺 killer），playerAttack 类型又不对，
                // 所以用 iceSourceOf 把「FREEZE 类型 + 玩家攻击者」拼起来。
                if (player instanceof net.minecraft.server.level.ServerPlayer serverCaster) {
                    DamageSource freezeSource = SpellDamageUtil.iceSourceOf(living, serverCaster);
                    SpellDamageUtil.hurt(living, freezeSource, player.getUseItem(),
                            serverCaster, WARD_FREEZE_DAMAGE);
                }
            }
        }

        for (net.minecraft.world.entity.projectile.Projectile projectile :
                level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, box,
                        p -> p.isAlive()
                                && p.getBoundingBox().distanceToSqr(center) <= radiusSqr)) {
            if (projectile.getOwner() == player) {
                continue;   // 自己的投射物不动
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    projectile.getX(), projectile.getY(), projectile.getZ(),
                    8, 0.2D, 0.2D, 0.2D, 0.08D);
            projectile.levelCallback.onRemove(Entity.RemovalReason.DISCARDED);
        }
    }

    /** 松开右键、格挡结束：领域随之停止，<b>不上冷却</b>（原因见 {@link #use}）。 */
    @Override
    public void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡结束，共 {} tick", heldTicks);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    16, 0.8D, 1.0D, 0.8D, 0.03D);
        }
    }

    @Override
    public void onBlockedHit(ItemStack stack, Level level, Player player,
                             DamageSource source, float original, float reduced) {
        ModUtils.LOGGER.debug("[SilentDaylight] 格挡生效 {} -> {}", original, reduced);
    }

    /** 格挡成功溅起冷色火花：用 {@code ELECTRIC_SPARK} 而不是绯红誓约那串粉色 {@code CRIT}。 */
    @Override
    public int blockHitParticleCount() {
        return 6;
    }

    @Override
    public ParticleOptions blockHitParticle() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    /**
     * 挖掘速度固定 0.2：{@code getDestroySpeed(stack, state)} 的签名里<b>没有玩家</b>，拿不到
     * "此刻是否在举剑"；「举剑时完全不能挖」由 {@link #canAttackBlock} 精确处理。
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.2f;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !IBlockingWeapon.blocksMining(player);
    }

    @Override
    public int outlineColor() {
        return ACCENT_DEEP;
    }

    @Override
    public int outlineSecondaryColor() {
        return ACCENT_LIGHT;
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";
    }

    /** 必须 {@code TRANSLUCENT}：加法混合会把深蓝那半截吃掉（暗 ≈ 加 0），只剩浅蓝在闪。 */
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

    //  手持显示走 getName（静态渐变）；tooltip 首行由 FlowingNameTooltipHook 每帧重算（会流动）。

    @Override
    public int flowingNameColorFrom() {
        return ACCENT_LIGHT;
    }

    @Override
    public int flowingNameColorTo() {
        return ACCENT_MID;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        tooltip.add(FlowingNameColors.line(
                Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LORE)
                        .withStyle(net.minecraft.ChatFormatting.ITALIC),
                LORE_FROM, LORE_TO, level));

        tooltip.add(TooltipLines.feature(TranslateUtils.TOOLTIP_LABEL_BLOCK,
                TranslateUtils.TOOLTIP_BLOCK, level,
                ACCENT_LIGHT, ACCENT_MID, BLOCK_TEXT_FROM, BLOCK_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE,
                TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_TIDE, level,
                ACCENT_ICE, ACCENT_LIGHT, TIDE_TEXT_FROM, TIDE_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_SLASH,
                Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_SLASH,
                        SilentDaylightAttack.SLASH_COUNT,
                        formatNumber(SilentDaylightAttack.ICE_DAMAGE),
                        formatNumber(SilentDaylightAttack.PERCENT_HEALTH),
                        formatNumber((float) SilentDaylightAttack.SLASH_RANGE)),
                level,
                ACCENT_ICE, ACCENT_LIGHT, SLASH_TEXT_FROM, SLASH_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_WARD,
                Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_WARD,
                        formatNumber((float) WARD_RADIUS)),
                level,
                ACCENT_ICE, ACCENT_LIGHT, WARD_TEXT_FROM, WARD_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT,
                TranslateUtils.TOOLTIP_TRAIT, level,
                ACCENT_ICE, ACCENT_LIGHT, TRAIT_TEXT_FROM, TRAIT_TEXT_TO));

        if (flag.isAdvanced()) {
            int percent = Math.round(blockDamageMultiplier() * 100.0F);
            tooltip.add(Component.translatable(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                            percent, blockOnlyFrontal(), BLOCK_USE_DURATION)
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    /** 轨道色用 {@link #ACCENT_DEEP}：它比底板亮，所以"空着的那部分"看得见。 */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new BlockBarTooltip(
                blockDamageMultiplier(),
                TranslateUtils.TOOLTIP_BAR_LABEL, TranslateUtils.TOOLTIP_BAR_VALUE,
                ACCENT_LIGHT, ACCENT_MID, ACCENT_ICE, ACCENT_DEEP));
    }

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
     * 冷色版 {@code thermal} 图案（图案与三色是分开的，换色就能得到"寒流 / 水光"的观感）。
     * 真要水面湍流的干涉纹再单开一个 key + 新 fsh，物品侧只改这一个方法。
     */
    @Override
    public TooltipShaderSpec tooltipShader() {
        return TooltipShaderSpec.thermal(TOOLTIP_BG_TOP, ACCENT_LIGHT, ACCENT_ICE);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }
}
