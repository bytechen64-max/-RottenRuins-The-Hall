package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔法物品（法术）基类 —— 只做数据声明与默认行为，冷却与法力不存这里：
 * 物品是注册表单例，字段挂在实例上会让所有玩家共享同一份冷却。
 * 这里的 getter 返回未修饰的原始值，真正生效的数值一律走 {@code MagicStats} 折叠。
 */
public abstract class CMagicBaseItem extends Item implements IMagicBaseItem {

    /** 蓄力释放的默认最低蓄力比例：低于它作废，达到它就算"弱化版"也放出去。 */
    public static final float DEFAULT_MIN_CHARGE_RATIO = 0.25f;

    /** {@code KEEP} 模式的默认触发速度：每 tick 触发一次。 */
    public static final int DEFAULT_KEEP_TRIGGER_INTERVAL = 1;

    private final MagicType magicType;
    private final UsingType usingType;
    private final int manaCost;
    private final int keepCostPerSecond;
    private final int keepTriggerInterval;
    private final int cooldownTicks;
    private final int chargeTicks;
    private final float damage;
    private final float minChargeRatio;

    /** 完整构造器（最低蓄力比例用默认值 {@value #DEFAULT_MIN_CHARGE_RATIO}）。 */
    protected CMagicBaseItem(@NotNull Properties properties,
                             @NotNull MagicType magicType,
                             @NotNull UsingType usingType,
                             int manaCost,
                             int cooldownTicks,
                             int chargeTicks,
                             int keepCostPerSecond,
                             float damage) {
        this(properties, magicType, usingType, manaCost, cooldownTicks, chargeTicks,
                keepCostPerSecond, damage, DEFAULT_MIN_CHARGE_RATIO);
    }

    /** 完整构造器（可指定最低蓄力比例：0 = 点一下就放，1 = 必须蓄满，中间值 = 低于它作废）。 */
    protected CMagicBaseItem(@NotNull Properties properties,
                             @NotNull MagicType magicType,
                             @NotNull UsingType usingType,
                             int manaCost,
                             int cooldownTicks,
                             int chargeTicks,
                             int keepCostPerSecond,
                             float damage,
                             float minChargeRatio) {
        this(properties, magicType, usingType, manaCost, cooldownTicks, chargeTicks,
                keepCostPerSecond, damage, minChargeRatio, DEFAULT_KEEP_TRIGGER_INTERVAL);
    }

    /**
     * 完整构造器（可指定最低蓄力比例与 KEEP 触发速度）。
     * keepTriggerInterval：效果每多少 tick 触发一次（1 = 每 tick，20 = 每秒），小于 1 钳到 1，非 KEEP 忽略。
     */
    protected CMagicBaseItem(@NotNull Properties properties,
                             @NotNull MagicType magicType,
                             @NotNull UsingType usingType,
                             int manaCost,
                             int cooldownTicks,
                             int chargeTicks,
                             int keepCostPerSecond,
                             float damage,
                             float minChargeRatio,
                             int keepTriggerInterval) {
        super(properties);
        this.magicType = magicType;
        this.usingType = usingType;
        this.manaCost = Math.max(0, manaCost);
        this.cooldownTicks = Math.max(0, cooldownTicks);
        // 只有对应模式才保留这几个数，其它模式一律归零/归一 —— 免得子类填错导致行为诡异
        this.chargeTicks = usingType == UsingType.CHARGE ? Math.max(0, chargeTicks) : 0;
        this.keepCostPerSecond = usingType == UsingType.KEEP ? Math.max(0, keepCostPerSecond) : 0;
        this.keepTriggerInterval = usingType == UsingType.KEEP
                ? Math.max(1, keepTriggerInterval)
                : DEFAULT_KEEP_TRIGGER_INTERVAL;
        this.damage = Math.max(0f, damage);
        this.minChargeRatio = clampRatio(minChargeRatio);
    }

    /** 法术物品的推荐属性：不可堆叠（一个槽位只放一个）。 */
    @NotNull
    public static Properties defaultProperties() {
        return new Properties().stacksTo(1);
    }

    // 基值（可覆写，但请保持"无副作用"：这些 getter 会被 HUD 频繁调用）

    @Override
    @NotNull
    public MagicType getMagicType() {
        return magicType;
    }

    @Override
    @NotNull
    public UsingType getUsingType() {
        return usingType;
    }

    @Override
    public int baseManaCost() {
        return manaCost;
    }

    @Override
    public int baseKeepCostPerSecond() {
        return keepCostPerSecond;
    }

    @Override
    public int baseCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int baseChargeTicks() {
        return chargeTicks;
    }

    @Override
    public float baseDamage() {
        return damage;
    }

    @Override
    public float baseMinChargeRatio() {
        return minChargeRatio;
    }

    @Override
    public int baseKeepTriggerInterval() {
        return keepTriggerInterval;
    }

    /** 把比例钳到 {@code [0,1]}；{@code NaN} 视为 0。 */
    private static float clampRatio(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    // 便利判定

    /** 按下即放。 */
    public final boolean isInstantMode() {
        return usingType == UsingType.IMMEDIATELY;
    }

    /** 需要蓄力到 {@link #baseChargeTicks()} 才放。 */
    public final boolean isChargeMode() {
        return usingType == UsingType.CHARGE;
    }

    /** 按住持续维持。 */
    public final boolean isKeepMode() {
        return usingType == UsingType.KEEP;
    }

    /** 法术的注册名，用作冷却的键（同名物品共享冷却）；未注册时返回 {@code null}。 */
    @Nullable
    public ResourceLocation getMagicId() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    /** 从堆里取法术接口；不是法术返回 {@code null}。 */
    @Nullable
    public static IMagicBaseItem of(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IMagicBaseItem item
                ? item
                : null;
    }

    /**
     * 介绍三行（类型 / 冷却 / 消耗，KEEP 额外一行维持耗蓝）；显示的是基值而非修饰后的实际值
     * —— 介绍是在没有玩家的上下文里画的。文案与颜色集中在 {@link MagicTooltips}。
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(MagicTooltips.typeLine(this.magicType));
        tooltip.add(MagicTooltips.cooldownLine(this.baseCooldownTicks(), this.magicType));
        tooltip.add(MagicTooltips.manaLine(this.baseManaCost(), this.magicType));
        // KEEP 模式才有维持耗蓝，其它模式显示出来只是噪音
        if (this.usingType == UsingType.KEEP && this.baseKeepCostPerSecond() > 0) {
            tooltip.add(MagicTooltips.keepCostLine(this.baseKeepCostPerSecond(), this.magicType));
        }
        appendSpellHoverText(stack, level, tooltip);
    }

    /** 子类追加自己的介绍（例如"命中额外造成 3 点真伤"）。 */
    protected void appendSpellHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                        @NotNull List<Component> tooltip) {
    }

    /** 默认什么都不做：KEEP 型法术可能只靠 {@link #onKeepTick} 干活，{@code cast} 本身确实是空的。 */
    @Override
    public boolean cast(@NotNull net.minecraft.server.level.ServerPlayer caster,
                        @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        return false;
    }
}
