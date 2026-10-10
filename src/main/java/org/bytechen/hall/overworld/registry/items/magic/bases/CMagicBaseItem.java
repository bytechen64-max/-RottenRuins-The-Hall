package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 魔法物品（法术）基类 —— 数据声明 + 默认行为，<b>不含</b>冷却与法力的存储。
 *
 * <h3>相对早期版本修正了什么</h3>
 * <ol>
 *   <li><b>不再把冷却存在物品上。</b> 原版把 {@code cooldown / coolcount / usingtime}
 *       做成 {@code public int} 字段挂在 {@code Item} 实例上。物品是<b>注册表单例</b>，
 *       于是"所有人的同一件物品共享一个冷却"—— 甲放完法术，乙也不能放了。
 *       现在冷却按「玩家 + 物品注册名」存在 {@code PlayerMagicPool} 里。</li>
 *   <li><b>三种释放模式有了明确语义</b>，并且时间轴由 {@code MagicHandle} 统一驱动，
 *       而不是让每个法术自己数 tick。</li>
 *   <li><b>基值与最终值分离。</b> 这里的 getter 返回的是"未修饰的原始值"；
 *       真正生效的数值一律走 {@code org.bytechen.hall.api.magic.MagicStats}，
 *       属性修饰器与附魔在那里折叠。</li>
 *   <li><b>{@code onPlayHandleTick} 不再是 abstract。</b> 原版把接口方法声明成 abstract、
 *       却在注释里写"默认什么都不做"，任何子类都被强制实现一个空方法。</li>
 * </ol>
 *
 * <h3>写一个法术</h3>
 * <p>继承它、调一个构造器、实现 {@link #cast} 就够了：</p>
 * <pre>{@code
 * public class FrostBoltSpell extends CMagicBaseItem {
 *     public FrostBoltSpell() {
 *         super(defaultProperties(), MagicType.ACE, UsingType.CHARGE, 120, 40, 20, 0, 12.0f);
 *     }
 *     @Override
 *     protected boolean cast(ServerPlayer caster, ItemStack stack, MagicCastContext ctx) { ... }
 * }
 * }</pre>
 *
 * <p>如果基值需要动态计算（例如读 NBT、读时间），把这些 getter 覆写掉即可 ——
 * 它们不是 final。但<b>不要</b>在里面对玩家做副作用的操作，它们会被 HUD 频繁调用。</p>
 */
public abstract class CMagicBaseItem extends Item implements IMagicBaseItem {

    /** 蓄力释放的默认最低蓄力比例：低于它作废，达到它就算"弱化版"也放出去。 */
    public static final float DEFAULT_MIN_CHARGE_RATIO = 0.25f;

    private final MagicType magicType;
    private final UsingType usingType;
    private final int manaCost;
    private final int keepCostPerSecond;
    private final int cooldownTicks;
    private final int chargeTicks;
    private final float damage;
    private final float minChargeRatio;

    /**
     * 完整构造器（最低蓄力比例用默认值 {@value #DEFAULT_MIN_CHARGE_RATIO}）。
     *
     * @param properties        物品属性；法术建议 {@link #defaultProperties()}
     * @param usingType         <b>必须显式给出</b>：每个法术都得声明自己是哪一种使用方式
     * @param manaCost          启动耗蓝
     * @param cooldownTicks     冷却 tick（同名物品共享）
     * @param chargeTicks       {@code CHARGE} 蓄满所需的 tick；非蓄力模式会被强制为 0
     * @param keepCostPerSecond {@code KEEP} 每秒额外耗蓝；非 KEEP 模式会被强制为 0
     * @param damage            伤害基值
     */
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

    /**
     * 完整构造器（可指定最低蓄力比例）。
     *
     * @param minChargeRatio {@code CHARGE} 的最低蓄力比例：
     *                       {@code 0} = 点一下就放（只是威力小）、
     *                       {@code 1} = 必须蓄满、中间值 = 低于它作废
     */
    protected CMagicBaseItem(@NotNull Properties properties,
                             @NotNull MagicType magicType,
                             @NotNull UsingType usingType,
                             int manaCost,
                             int cooldownTicks,
                             int chargeTicks,
                             int keepCostPerSecond,
                             float damage,
                             float minChargeRatio) {
        super(properties);
        this.magicType = magicType;
        this.usingType = usingType;
        this.manaCost = Math.max(0, manaCost);
        this.cooldownTicks = Math.max(0, cooldownTicks);
        // 只有对应模式才保留这两个数，其它模式一律归零 —— 免得子类填错导致行为诡异
        this.chargeTicks = usingType == UsingType.CHARGE ? Math.max(0, chargeTicks) : 0;
        this.keepCostPerSecond = usingType == UsingType.KEEP ? Math.max(0, keepCostPerSecond) : 0;
        this.damage = Math.max(0f, damage);
        this.minChargeRatio = clampRatio(minChargeRatio);
    }

    /** 法术物品的推荐属性：不可堆叠（一个槽位只放一个）。 */
    @NotNull
    public static Properties defaultProperties() {
        return new Properties().stacksTo(1);
    }

    // ══════════════════════════════════════════════════════════════
    // 基值（可覆写，但请保持"无副作用"）
    // ══════════════════════════════════════════════════════════════

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

    /** 把比例钳到 {@code [0,1]}；{@code NaN} 视为 0。 */
    private static float clampRatio(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    // ══════════════════════════════════════════════════════════════
    // 便利判定
    // ══════════════════════════════════════════════════════════════

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

    /**
     * 法术的注册名，用作冷却的键（同名物品共享冷却）。
     *
     * <p>未注册（{@code minecraft:air}）时返回 {@code null}，调用方需要自己处理
     * —— 理论上不会发生，法术物品一定注册过。</p>
     */
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
     * 默认的 {@link #cast} 实现：什么都不做，返回 {@code false}。
     *
     * <p>之所以给出默认实现而不是做成 abstract：{@code KEEP} 型法术可能只靠
     * {@link #onKeepTick} 干活，{@code cast} 本身确实是空的。强迫它写一个空方法没有意义。</p>
     */
    @Override
    public boolean cast(@NotNull net.minecraft.server.level.ServerPlayer caster,
                        @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        return false;
    }
}
