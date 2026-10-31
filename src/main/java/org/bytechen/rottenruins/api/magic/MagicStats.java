package org.bytechen.rottenruins.api.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.SpellKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 魔法数值的<b>唯一求值入口</b>。
 *
 * <p>任何地方要读"这个玩家有多少法力上限 / 这个法术耗多少蓝 / 冷却多久"，
 * 都必须走这里，不要直接读法术物品的基值 —— 基值只是 {@link MagicStatProvider}
 * 折叠的<b>起点</b>，直接读会绕过属性修饰器与附魔。</p>
 *
 * <h3>求值链</h3>
 * <pre>
 *   法术物品给出基值  →  按 priority 依次折叠所有 MagicStatProvider  →  下限钳制
 * </pre>
 *
 * <h3>常量</h3>
 * 全是 {@code public static final}，方便文档与命令引用；业务代码里不要写死同样的数字。
 */
public final class MagicStats {

    /** 法力上限基值。 */
    public static final int BASE_MAX_MANA = 1000;

    /** 每秒回蓝基值。 */
    public static final double BASE_REGEN_PER_SECOND = 1.0d;

    /** 魔法槽位基值 —— 玩家初始 5 格（可用属性 {@code magic_slots} 往上加）。 */
    public static final int BASE_SLOT_COUNT = 5;

    /** 槽位数硬上限：底层数组按这个长度分配，属性修饰器不能超过它。 */
    public static final int MAX_SLOT_COUNT = 64;

    /**
     * 击杀回蓝基值：每 1 点「受害者最大生命值」回多少法力。
     *
     * <p>默认 {@code 1.0}，即 20 血的僵尸回 20 点。想改成"回半血量"就调这里，
     * 或者注册一个 provider 用 {@code modifyKillMana} 按生物类型分别处理。</p>
     */
    public static final float BASE_KILL_MANA_PER_HP = 1.0f;

    /**
     * {@code KEEP} 触发速度的上限（tick）。
     *
     * <p>取 200（约 10 秒）纯粹是防呆：填一个离谱的大数会让"持续施法"看起来完全没反应，
     * 排查起来很费劲。要更慢的效果请换设计，而不是把触发间隔拉到几分钟。</p>
     */
    public static final int MAX_KEEP_TRIGGER_INTERVAL = 200;

    /**
     * 锁定目标的距离硬上限（格）。
     *
     * <p>和触发间隔那个上限一样是防呆用的：法术射程填到几百格意味着
     * 玩家能隔着地形点到看不见的东西，而且射线本身会变得很贵。
     * 真要远程打击请做投射物，而不是把锁定距离拉长。</p>
     */
    public static final double MAX_TARGET_RANGE = 128.0d;

    /**
     * <b>冷却缩减</b>的硬上限：{@code 0.5}（50%）。
     *
     * <p>需求就是"最多叠加 50%"。{@code cooldown_reduction} 属性的范围上限
     * 也设成它，这里再钳一次是为了防"绕过属性直接塞值"
     * （别的模组手动改 {@code AttributeInstance}，或者以后加了不走属性的来源）。</p>
     */
    public static final double MAX_COOLDOWN_REDUCTION = 0.5d;

    /** 通用法术强度的基准值（{@code spell_power} 的默认值）。 */
    public static final double BASE_SPELL_POWER = 1.0d;

    private MagicStats() {
    }

    // ══════════════════════════════════════════════════════════════
    // 玩家级
    // ══════════════════════════════════════════════════════════════

    /** 法力上限（至少 1）。 */
    public static int maxMana(@Nullable Player player) {
        if (player == null) {
            return BASE_MAX_MANA;
        }
        int value = MagicStatRegistry.foldInt(BASE_MAX_MANA,
                (provider, current) -> provider.modifyMaxMana(player, current));
        return Math.max(1, value);
    }

    /** 每秒回蓝量（可能带小数，所以是 double）。 */
    public static double manaRegenPerSecond(@Nullable Player player) {
        if (player == null) {
            return BASE_REGEN_PER_SECOND;
        }
        double value = MagicStatRegistry.foldDouble(BASE_REGEN_PER_SECOND,
                (provider, current) -> provider.modifyManaRegenPerSecond(player, current));
        return Math.max(0.0d, value);
    }

    /** 可用的魔法槽位数，钳制在 {@code [1, }{@link #MAX_SLOT_COUNT}{@code ]}。 */
    public static int slotCount(@Nullable Player player) {
        if (player == null) {
            return BASE_SLOT_COUNT;
        }
        int value = MagicStatRegistry.foldInt(BASE_SLOT_COUNT,
                (provider, current) -> provider.modifySlotCount(player, current));
        return Math.max(1, Math.min(MAX_SLOT_COUNT, value));
    }

    /**
     * <b>有效法术强度</b> —— 需求要的"和通用法强相加"就在这里。
     *
     * <pre>
     *   有效法强 = spell_power（通用倍率，默认 1.0）
     *            + spell_power_&lt;该法术的流派&gt;（加成，默认 0.0）
     * </pre>
     *
     * <p>例：通用 1.0、火系 +0.5 → 火球术的有效法强 = 1.5 倍；
     * 同一时刻放虚空法术则只有 1.0 倍（虚空那段是 0）。</p>
     *
     * <p>它是"一个法术物品"的量，所以要带上下文：不知道是哪个流派就无从相加。
     * {@code spell} 为空或不是 {@link IMagicBaseItem} 时退化成通用法强。</p>
     *
     * @return 有效法强倍率，已钳到 {@code >= 0}
     */
    public static double spellPower(@Nullable Player player, @Nullable ItemStack spell) {
        if (player == null) {
            return BASE_SPELL_POWER;
        }
        double general = RegisterAttributes.valueOf(player, RegisterAttributes.SPELL_POWER,
                BASE_SPELL_POWER);
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return Math.max(0.0d, general);
        }
        // 流派法强：默认 0.0，和通用法强相加
        double school = RegisterAttributes.valueOf(player,
                RegisterAttributes.spellPowerOf(item.getMagicType()), 0.0d);
        return Math.max(0.0d, general + school);
    }

    /**
     * <b>特殊使用</b>的统一入口 —— 现在是两条：<b>法术流派 == 法杖流派</b>，
     * 或者<b>装备了同流派饰品</b>（详见 {@link IMagicBaseItem#isSpecialUse(ServerPlayer, ItemStack, ItemStack)}）。
     *
     * @param caster     施法者；{@code null} 时只判法杖那条（查不了饰品栏）
     * @param staffStack 法杖堆
     * @param spellStack 法术堆
     */
    public static boolean isSpecialUse(@Nullable ServerPlayer caster,
                                       @Nullable ItemStack staffStack,
                                       @Nullable ItemStack spellStack) {
        IMagicBaseItem spell = spellOf(spellStack);
        if (spell == null) {
            return false;
        }
        return spell.isSpecialUse(caster, staffStack == null ? ItemStack.EMPTY : staffStack, spellStack);
    }

    /**
     * 不带施法者的版本：<b>只判"法杖流派 == 法术流派"</b>，查不到饰品栏。
     *
     * <p>只给"手上没有玩家"的场合用（例如命令、数据生成）。业务代码请用三参版本，
     * 否则戴了饰品的玩家会拿不到特殊使用。</p>
     */
    public static boolean isSpecialUse(@Nullable ItemStack staffStack, @Nullable ItemStack spellStack) {
        IMagicBaseItem spell = spellOf(spellStack);
        if (spell == null) {
            return false;
        }
        return spell.isSpecialUse(staffStack == null ? ItemStack.EMPTY : staffStack, spellStack);
    }

    /** 击杀某个生物时该玩家能回多少法力。 */
    public static float killMana(@Nullable Player player, @NotNull LivingEntity victim) {
        float base = Math.max(0f, victim.getMaxHealth() * BASE_KILL_MANA_PER_HP);
        if (player == null) {
            return base;
        }
        return Math.max(0f, MagicStatRegistry.foldFloat(base,
                (provider, current) -> provider.modifyKillMana(player, victim, current)));
    }

    // ══════════════════════════════════════════════════════════════
    // 法术级
    // ══════════════════════════════════════════════════════════════

    /** 该物品是不是一件法术（可以放进魔法槽）。 */
    public static boolean isSpell(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IMagicBaseItem;
    }

    /** 取法术接口；不是法术时返回 {@code null}。 */
    @Nullable
    public static IMagicBaseItem spellOf(@Nullable ItemStack stack) {
        return isSpell(stack) ? (IMagicBaseItem) stack.getItem() : null;
    }

    /**
     * 法术的<b>分类轴</b>：主动 / 被动。
     *
     * <p>不是法术时按 {@link SpellKind#ACTIVE} 处理，调用方不需要先判空。</p>
     */
    @NotNull
    public static SpellKind spellKind(@Nullable ItemStack stack) {
        IMagicBaseItem item = spellOf(stack);
        return item == null ? SpellKind.ACTIVE : item.spellKind();
    }

    /** 这件物品是不是<b>被动</b>法术（放在魔法槽里生效、不能由法杖释放）。 */
    public static boolean isPassive(@Nullable ItemStack stack) {
        return spellKind(stack) == SpellKind.PASSIVE;
    }

    /**
     * 法术的<b>冷却键</b> —— 就是它的物品注册名。
     *
     * <p>冷却按这个名字记，所以「同一个玩家的所有同名物品共享冷却」是天然成立的：
     * 两把不同的火球法术（两个注册名）各自独立，同一件法术放在 1 号槽还是 3 号槽
     * 则是同一个键。</p>
     *
     * @return 物品注册名；不是法术时返回 {@code null}
     */
    @Nullable
    public static net.minecraft.resources.ResourceLocation magicId(@Nullable ItemStack spell) {
        if (!isSpell(spell)) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(spell.getItem());
    }

    /** 单次释放的启动耗蓝（{@code KEEP} 模式下是启动消耗）。 */
    public static int manaCost(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0;
        }
        int base = Math.max(0, item.baseManaCost());
        if (player == null) {
            return base;
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyManaCost(player, spell, current));
        return Math.max(0, value);
    }

    /** {@code KEEP} 模式维持期间每秒的额外耗蓝。 */
    public static int keepCostPerSecond(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0;
        }
        int base = Math.max(0, item.baseKeepCostPerSecond());
        if (player == null) {
            return base;
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyKeepCostPerSecond(player, spell, current));
        return Math.max(0, value);
    }

    /**
     * {@code KEEP} 模式的<b>触发速度</b>：效果每多少 tick 触发一次。
     *
     * <p>钳在 {@code [1, }{@link #MAX_KEEP_TRIGGER_INTERVAL}{@code ]}。
     * 下限刻意是 1 而不是 0 —— 0 在"每 N tick 触发一次"里没有意义，
     * 而且会让计数逻辑退化成每 tick。</p>
     */
    public static int keepTriggerInterval(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 1;
        }
        int base = Math.max(1, item.baseKeepTriggerInterval());
        if (player == null) {
            return Math.min(MAX_KEEP_TRIGGER_INTERVAL, base);
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyKeepTriggerInterval(player, spell, current));
        return Math.max(1, Math.min(MAX_KEEP_TRIGGER_INTERVAL, value));
    }

    /**
     * {@code KEEP} 模式<b>每生效一次</b>的额外耗蓝。
     *
     * <p>与 {@link #keepCostPerSecond} 并存且互不代替：这一项跟着触发节奏走，
     * 触发间隔是 20 tick 就每 20 tick 扣一次，是 1 tick 就每 tick 扣一次 ——
     * 也就是"每次生效都付钱，生效有间隔则扣费也有间隔"。
     * 想要"维持就要付出代价，跟触发快慢无关"的效果，请用 {@code keepCostPerSecond}。</p>
     */
    public static int keepCostPerTrigger(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0;
        }
        int base = Math.max(0, item.baseKeepCostPerTrigger());
        if (player == null) {
            return base;
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyKeepCostPerTrigger(player, spell, current));
        return Math.max(0, value);
    }

    /**
     * 锁定目标的<b>最大距离</b>（格），钳在
     * {@code [0, }{@link #MAX_TARGET_RANGE}{@code ]}。
     *
     * <p>只对声明了 {@code needsTarget()} 的法术有意义。射线会在"撞到方块"或
     * "超过这个距离"里先到的那一处停下，所以它既是射程也是穿透深度。</p>
     */
    public static double targetRange(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0.0d;
        }
        double base = clampRange(item.baseTargetRange());
        if (player == null) {
            return base;
        }
        return clampRange(MagicStatRegistry.foldDouble(base,
                (provider, current) -> provider.modifyTargetRange(player, spell, current)));
    }

    /** 把射程钳到 {@code [0, MAX_TARGET_RANGE]}；{@code NaN} 视为 0。 */
    private static double clampRange(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(MAX_TARGET_RANGE, value));
    }

    /** 释放后的冷却 tick。同名物品共享这个值。 */
    public static int cooldownTicks(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0;
        }
        int base = Math.max(0, item.baseCooldownTicks());
        if (player == null) {
            return base;
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyCooldownTicks(player, spell, current));
        return Math.max(0, value);
    }

    /** {@code CHARGE} 模式需要的蓄力 tick。 */
    public static int chargeTicks(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 0;
        }
        int base = Math.max(0, item.baseChargeTicks());
        if (player == null) {
            return base;
        }
        int value = MagicStatRegistry.foldInt(base,
                (provider, current) -> provider.modifyChargeTicks(player, spell, current));
        return Math.max(0, value);
    }

    /** 法术物品自己声明的伤害基值。 */
    public static float baseSpellDamage(ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        return item == null ? 0f : item.baseDamage();
    }

    /**
     * {@code CHARGE} 模式的<b>最低蓄力比例</b>，钳在 {@code [0,1]}。
     *
     * <p>低于它这次释放作废；达到它放出去，威力按真实完成度缩放。</p>
     */
    public static float minChargeRatio(@Nullable Player player, ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        if (item == null) {
            return 1.0f;
        }
        float base = clampRatio(item.baseMinChargeRatio());
        if (player == null) {
            return base;
        }
        return clampRatio(MagicStatRegistry.foldFloat(base,
                (provider, current) -> provider.modifyMinChargeRatio(player, spell, current)));
    }

    /**
     * 把"已经按住了多少 tick"换算成<b>蓄力完成度</b> {@code 0.0~1.0}。
     *
     * <p>这就是 {@code MagicCastContext#chargeRatio()} 的来源，也是
     * {@code MagicCastEvent#getChargeRatio()} 的值。想做"蓄力越久打越痛"
     * 就直接拿它乘伤害。</p>
     *
     * <p>需要的 tick 为 0（非蓄力型，或被修饰器减到 0）时返回 {@code 1.0}。</p>
     */
    public static float chargeRatio(@Nullable Player player, ItemStack spell, int heldTicks) {
        int needed = chargeTicks(player, spell);
        if (needed <= 0) {
            return 1.0f;
        }
        return clampRatio(heldTicks / (float) needed);
    }

    /** 把比例钳到 {@code [0,1]}；{@code NaN} 视为 0。 */
    private static float clampRatio(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    /** 法术伤害的最终数值：先取物品基值，再交给 provider 折叠。 */
    public static float spellDamage(@Nullable Player player, ItemStack spell,
                                    @Nullable LivingEntity target, @Nullable DamageSource source,
                                    float base) {
        if (player == null) {
            return base;
        }
        return MagicStatRegistry.foldFloat(base,
                (provider, current) -> provider.modifySpellDamage(player, spell, target, source, current));
    }

    /** 便捷重载：伤害基值直接取法术物品声明的那一份。 */
    public static float spellDamage(@Nullable Player player, ItemStack spell,
                                    @Nullable LivingEntity target, @Nullable DamageSource source) {
        return spellDamage(player, spell, target, source, baseSpellDamage(spell));
    }
}
