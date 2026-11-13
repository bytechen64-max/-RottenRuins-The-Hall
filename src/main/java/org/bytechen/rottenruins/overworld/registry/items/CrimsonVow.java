package org.bytechen.rottenruins.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.api.ICustomOutline;
import org.bytechen.rottenruins.api.ItemFxParams;
import org.bytechen.rottenruins.api.ItemFxProvider;
import org.bytechen.rottenruins.client.fx.effects.FlameFxEffect;
import org.bytechen.rottenruins.api.IFlowingName;
import org.bytechen.rottenruins.api.ITooltipStyle;
import org.bytechen.rottenruins.api.TooltipShaderSpec;
import org.bytechen.rottenruins.client.rend.text.FlowingNameColors;
import org.bytechen.rottenruins.client.rend.text.TooltipLines;
import org.bytechen.rottenruins.client.rend.twitch.ITwitchItem;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowChargeEntity;
import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.TranslateUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrimsonVow —— 深粉方块图案 + 雾粉紫流动描边 + 低版本（1.8 式）右键格挡（{@link IBlockingWeapon}）。
 * useOnRelease 必须 true，否则按满时长会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}。
 * getUseAnimation 必须 {@code UseAnim.NONE}：BLOCK 走盾牌分支（隐藏手臂 + 画盾），剑整个消失。
 */
public class CrimsonVow extends SwordItem implements ICustomOutline, ItemFxProvider, ITwitchItem,
        IBlockingWeapon, IFlowingName, ITooltipStyle {

    public CrimsonVow() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    //  用 *For(ItemStack) 覆写的原因：不带 stack 的老方法拿不到物品实例，表达不了"改名才变样子"。

    /** 触发火焰模式的物品名（不区分大小写）。 */
    public static final String FIRE_MODE_NAME = "BoBao";

    /**
     * 名字里包含 {@code bobao}（不区分大小写）就算，不做 equals 精确匹配：
     * 铁砧输入框预填当前显示名，直接打字会得到"绯红誓约BoBao"这类拼接结果。
     */
    public static boolean isFireMode(ItemStack stack) {
        if (stack == null) return false;
        String name = stack.getHoverName().getString();
        return name != null && name.toLowerCase(java.util.Locale.ROOT)
                .contains(FIRE_MODE_NAME.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public int outlineColorFor(ItemStack stack) {
        return isFireMode(stack) ? ACCENT_FROM : ACCENT_SHADOW;
    }

    @Override
    public int outlineSecondaryColorFor(ItemStack stack) {
        return isFireMode(stack) ? ACCENT_SHADOW : ACCENT_HIGHLIGHT;
    }

    @Override
    public String outlineShaderKeyFor(ItemStack stack) {
        return isFireMode(stack) ? "fire" : "gradient";
    }

    /** gradient 靠 {@code TRANSLUCENT} 才留得住暗部；火焰尾部是深紫黑，加法"加 0"等于不画。 */
    @Override
    public ICustomOutline.BlendMode outlineBlendFor(ItemStack stack) {
        return isFireMode(stack) ? ICustomOutline.BlendMode.ADDITIVE : ICustomOutline.BlendMode.TRANSLUCENT;
    }

    /** 火焰效果层：只在 BoBao 上挂，HEAD / GUI 不挂（图标仅 16 像素，火焰按屏幕高算会冒出一屏）。 */
    @Override
    public List<ItemFxProvider.ItemFxBinding> itemFxLayers(ItemStack stack, ItemDisplayContext ctx) {
        if (ctx == ItemDisplayContext.HEAD || ctx == ItemDisplayContext.GUI) return List.of();

        List<ItemFxProvider.ItemFxBinding> layers = new java.util.ArrayList<>(2);

        if (!isFireMode(stack)) return layers;

        int c = ACCENT_FROM;
        ItemFxParams tint = ItemFxParams.builder()
                .set("tintR", ((c >> 16) & 0xFF) / 255.0F)
                .set("tintG", ((c >> 8) & 0xFF) / 255.0F)
                .set("tintB", (c & 0xFF) / 255.0F)
                .build();
        layers.add(ItemFxProvider.ItemFxBinding.of(FlameFxEffect.ID, tint));
        return layers;
    }

    private static final int ACCENT_FROM = 0xFFFF4FB8;

    private static final int ACCENT_TO = 0xFF9B4DFF;

    private static final int ACCENT_HIGHLIGHT = 0xFFE8A6FF;

    private static final int ACCENT_SHADOW = 0xFF2A1B3D;

    private static final int LORE_FROM = 0xFFC9A7FF;
    private static final int LORE_TO = 0xFFFF4FB8;

    private static final int BLOCK_TEXT_FROM = 0xFF9A86C8;
    private static final int BLOCK_TEXT_TO = 0xFFE8A6FF;

    private static final int VOW_TEXT_FROM = 0xFF8FD6FF;
    private static final int VOW_TEXT_TO = 0xFFB388FF;

    private static final int SIGIL_TEXT_FROM = 0xFFC2527A;
    private static final int SIGIL_TEXT_TO = 0xFFFF9AD4;

    private static final int TOOLTIP_BG_TOP = 0xF01E0D28;

    private static final int TOOLTIP_BG_BOTTOM = 0xF00E0716;

    /** 边框用 {@code 0x80} 透明度（原版 {@code 0x50} 太若有若无）；从 ACCENT_FROM 派生，换色板不会留旧色。 */
    private static final int TOOLTIP_BORDER_TOP = accent(ACCENT_FROM, 0x80);

    private static final int TOOLTIP_BORDER_BOTTOM = accent(ACCENT_TO, 0x80);

    private static int accent(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** 攻击距离加成（格）：{@code forge:entity_reach} 默认 3.0、上限 1024，不需要越界兜底。 */
    public static final double ATTACK_REACH_BONUS = 7;

    /** 固定字面量而非随机：同一修饰符在不同存档 / 不同端必须同 id，否则会被当成两个叠加。 */
    private static final UUID REACH_MODIFIER_UUID =
            UUID.nameUUIDFromBytes(
                    (CrimsonVow.class.getName() + ":reach")
                            .getBytes(StandardCharsets.UTF_8));

    /** 只改主手：这把剑是主手武器，放副手不该给玩家额外的攻击距离。 */
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

    //  刻意不用原版 ItemCooldowns：它是"整件物品不可用"，而格挡必须一直可用；这里只锁起誓。
    //  表按 UUID 存、服务端读写，读取时顺手清掉，不会随对局数增长。

    /** 誓约冷却（tick）= 30 秒（法阵持续 20 秒 + 10 秒余韵）。 */
    public static final int SKILL_COOLDOWN_TICKS = 20 * 30;

    private static final Map<UUID, Long> SKILL_READY_AT = new ConcurrentHashMap<>();

    public static boolean isSkillReady(Player player) {
        if (player == null) return false;
        Long until = SKILL_READY_AT.get(player.getUUID());
        if (until == null) return true;
        if (player.level().getGameTime() >= until) {
            SKILL_READY_AT.remove(player.getUUID());
            return true;
        }
        return false;
    }

    public static int skillCooldownLeft(Player player) {
        if (player == null) return 0;
        Long until = SKILL_READY_AT.get(player.getUUID());
        if (until == null) return 0;
        return (int) Math.max(0L, until - player.level().getGameTime());
    }

    public static void beginSkillCooldown(Player player) {
        if (player == null) return;
        SKILL_READY_AT.put(player.getUUID(),
                player.level().getGameTime() + SKILL_COOLDOWN_TICKS);
    }

    public static void clearSkillCooldown(Player player) {
        if (player != null) SKILL_READY_AT.remove(player.getUUID());
    }

    /** 走 {@link IBlockingWeapon#beginBlock} —— 它除了 {@code startUsingItem}，还会触发服务端的 {@code onBlockStart} 与自检。 */
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

    //  tooltip 首行由 client 侧 FlowingNameTooltipHook 每帧重算（会流动）；手持显示走 getName（静态渐变）——
    //  1.20.1 里 Component 是接口、MutableComponent 构造函数包私有，外部包继承不了，
    //  做不出"每次取文本才算"的组件，为这点差异引渲染注入不划算。

    /** 名字前缀符号（原版风格的"加个符号"）：颜色放在符号上，名字本体保持纯文本。 */
    public static final String NAME_PREFIX = "★ ";

    /**
     * 显示名 = 彩色前缀符号 + 纯文本名字 —— 整名渐变会让显示名不再等于真实名字，
     * 铁砧里看不出改的名字有没有生效，按名字比较的逻辑（含火焰模式判定、命令选择器）也得绕开装饰。
     */
    @Override
    public Component getName(ItemStack stack) {
        Component plain = super.getName(stack);
        if (!flowingNameEnabled()) return plain;
        return Component.literal(NAME_PREFIX)
                .withStyle(Style.EMPTY.withColor(flowingNameColorFrom()))
                .append(plain.copy().withStyle(Style.EMPTY.withColor(flowingNameColorTo())));
    }

    @Override
    public int flowingNameColorFrom() {
        return ACCENT_FROM;
    }

    @Override
    public int flowingNameColorTo() {
        return ACCENT_TO;
    }

    //  appendHoverText 只出 Component；誓约条由 getTooltipImage 声明数据，
    //  渲染在 client.tooltip.ClientBlockBarTooltip。名字行不在这里（被 Hook 替换 index 0）。
    //  数值一律从 IBlockingWeapon 现取，不写字面量，否则调格挡强度后 tooltip 会报旧数字。

    /**
     * 每行一对自己的渐变，三行色相刻意不同，否则一列文字糊成一片。
     * 流动判断收在 {@link FlowingNameColors#line}：本类服务端也会加载，取 Minecraft 实例只能在那儿做。
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        tooltip.add(FlowingNameColors.line(
                Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_LORE)
                        .withStyle(ChatFormatting.ITALIC),
                LORE_FROM, LORE_TO, level));

        tooltip.add(TooltipLines.feature(TranslateUtils.TOOLTIP_LABEL_BLOCK,
                TranslateUtils.TOOLTIP_BLOCK, level,
                ACCENT_FROM, ACCENT_TO, BLOCK_TEXT_FROM, BLOCK_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_SIGIL,
                TranslateUtils.CRIMSON_VOW_TOOLTIP_SIGIL, level,
                ACCENT_FROM, ACCENT_TO, SIGIL_TEXT_FROM, SIGIL_TEXT_TO));

        tooltip.add(TooltipLines.feature(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_TRAIT,
                TranslateUtils.TOOLTIP_TRAIT, level,
                ACCENT_FROM, ACCENT_TO, VOW_TEXT_FROM, VOW_TEXT_TO));

        if (flag.isAdvanced()) {
            int percent = Math.round(blockDamageMultiplier() * 100.0F);
            tooltip.add(Component.translatable(TranslateUtils.CRIMSON_VOW_TOOLTIP_DEBUG,
                            percent, blockOnlyFrontal(), BLOCK_USE_DURATION,
                            (int) ATTACK_REACH_BONUS)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 只传数据不传渲染类型；返回后 Forge 会把它插到 tooltip 的 index 1（紧跟名字行）。 */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new BlockBarTooltip(
                blockDamageMultiplier(),
                TranslateUtils.TOOLTIP_BAR_LABEL, TranslateUtils.TOOLTIP_BAR_VALUE,
                ACCENT_FROM, ACCENT_TO, ACCENT_HIGHLIGHT, ACCENT_SHADOW));
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
     * thermal 图案由 {@code rendertype_tooltip_thermal.fsh} 现场算，这里只声明图案 + 三色：
     * 冷底取 TOOLTIP_BG_TOP（着色器没加载时退回纯色底板仍是同一色系）、热流 ACCENT_FROM、
     * 热核 ACCENT_HIGHLIGHT —— 与名字渐变、描边同一套色板。
     */
    @Override
    public TooltipShaderSpec tooltipShader() {
        return TooltipShaderSpec.thermal(TOOLTIP_BG_TOP, ACCENT_FROM, ACCENT_HIGHLIGHT);
    }

    /** 只在服务端返回 true：客户端 true 会让第一人称走盾牌渲染分支，与自写的举剑姿态冲突。 */
    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
        return IBlockingWeapon.handlesShieldBlockAction(stack, action)
                || super.canPerformAction(stack, action);
    }

    /** 松手 / 攻击 / 物品栈被替换三条收剑路径都汇到 releaseUsing；endBlock 内部幂等。 */
    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity,
                            int timeLeft) {
        if (entity instanceof Player player) {
            IBlockingWeapon.endBlock(stack, level, player);
        }
    }

    /**
     * 按下右键、格挡开始（服务端）。蹲下 + 右键 = 起誓：判断放这里而不是 use()，
     * 因为 use() 两端都跑；冷却期间仍然可以正常格挡，只是不起誓。
     */
    @Override
    public void onBlockStart(ItemStack stack, Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && isSkillReady(player)) {
            CrimsonVowChargeEntity.spawn(level, player);
            ModUtils.LOGGER.debug("[CrimsonVow] 起誓蓄力 player={}", player.getName().getString());
        }
    }

    /** 蓄力的推进在 {@link CrimsonVowChargeEntity} 的 tick 里，物品类只负责"开启"，便于复用。 */
    @Override
    public void onBlockTick(ItemStack stack, Level level, Player player, int heldTicks) {
        if (heldTicks % 40 == 0) {
            ModUtils.LOGGER.debug("[CrimsonVow] 格挡持续 {} tick", heldTicks);
        }
    }

    @Override
    public void onBlockStop(ItemStack stack, Level level, Player player, int heldTicks) {
        ModUtils.LOGGER.debug("[CrimsonVow] 格挡结束，共 {} tick", heldTicks);
    }

    @Override
    public void onBlockedHit(ItemStack stack, Level level, Player player,
                             DamageSource source, float original, float reduced) {
        ModUtils.LOGGER.debug("[CrimsonVow] 格挡生效 {} -> {}", original, reduced);
    }

    @Override
    public int blockHitParticleCount() {
        return 6;
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions blockHitParticle() {
        return net.minecraft.core.particles.ParticleTypes.CRIT;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.2f;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !IBlockingWeapon.blocksMining(player);
    }

    @Override
    public boolean twitchDisabled() {
        return true;
    }

    @Override
    public boolean twitchShouldRender(ItemDisplayContext ctx) {
        return false;
    }

    /** 不带 stack 的旧方法 = 常规模式取值（也是 {@code *For} 的兜底），管线实际走 {@link #outlineColorFor} 那一组。 */
    @Override
    public int outlineColor() {
        return ACCENT_SHADOW;
    }

    @Override
    public int outlineSecondaryColor() {
        return ACCENT_HIGHLIGHT;
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";
    }

    /** gradient 必须 {@code TRANSLUCENT}：加法混合会把暗部整个吃掉；火焰模式反过来要 {@code ADDITIVE}。 */
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

    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }
}
