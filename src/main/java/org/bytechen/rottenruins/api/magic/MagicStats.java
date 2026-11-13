package org.bytechen.rottenruins.api.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.StaffBase;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.SpellKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 魔法数值的<b>唯一求值入口</b>：求值链 = 法术物品给出基值 → 按 priority 依次折叠所有
 * {@link MagicStatProvider} → 下限钳制。基值只是折叠的起点，直接读法术物品的基值会绕过属性修饰器与附魔。
 */
public final class MagicStats {

    public static final int BASE_MAX_MANA = 1000;

    public static final double BASE_REGEN_PER_SECOND = 1.0d;

    public static final int BASE_SLOT_COUNT = 5;

    /** 槽位数硬上限：底层数组按这个长度分配，属性修饰器不能超过它。 */
    public static final int MAX_SLOT_COUNT = 64;

    /** 击杀回蓝基值：每 1 点「受害者最大生命值」回多少法力（1.0 → 20 血僵尸回 20 点）。 */
    public static final float BASE_KILL_MANA_PER_HP = 1.0f;

    /** {@code KEEP} 触发速度上限（tick，约 10 秒）：纯防呆，填离谱的大数会让持续施法看起来没反应。 */
    public static final int MAX_KEEP_TRIGGER_INTERVAL = 200;

    /** 锁定目标距离硬上限（格）：防呆用，射程几百格等于隔着地形打看不见的东西且射线很贵。 */
    public static final double MAX_TARGET_RANGE = 128.0d;

    /** <b>冷却缩减</b>硬上限 {@code 0.5}（50%）：属性范围也设成它，这里再钳一次是防绕过属性直接塞值。 */
    public static final double MAX_COOLDOWN_REDUCTION = 0.5d;

    /** 通用法术强度的基准值（{@code spell_power} 的默认值）。 */
    public static final double BASE_SPELL_POWER = 1.0d;

    private MagicStats() {
    }

    public static int maxMana(@Nullable Player player) {
        if (player == null) {
            return BASE_MAX_MANA;
        }
        int value = MagicStatRegistry.foldInt(BASE_MAX_MANA,
                (provider, current) -> provider.modifyMaxMana(player, current));
        return Math.max(1, value);
    }

    public static double manaRegenPerSecond(@Nullable Player player) {
        if (player == null) {
            return BASE_REGEN_PER_SECOND;
        }
        double value = MagicStatRegistry.foldDouble(BASE_REGEN_PER_SECOND,
                (provider, current) -> provider.modifyManaRegenPerSecond(player, current));
        return Math.max(0.0d, value);
    }

    public static int slotCount(@Nullable Player player) {
        if (player == null) {
            return BASE_SLOT_COUNT;
        }
        int value = MagicStatRegistry.foldInt(BASE_SLOT_COUNT,
                (provider, current) -> provider.modifySlotCount(player, current));
        return Math.max(1, Math.min(MAX_SLOT_COUNT, value));
    }

    /**
     * <b>有效法术强度</b> = {@code spell_power}（通用倍率，默认 1.0）+ {@code spell_power_&lt;该法术流派&gt;}
     * （加成，默认 0.0）；<b>两者相加而非相乘</b>。它是"一个法术物品"的量，{@code spell} 为空时退化成通用法强。
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

    /** <b>特殊使用</b>入口：法术流派 == 法杖流派，或装备了同流派饰品；{@code caster} 为 null 时只判法杖那条。 */
    public static boolean isSpecialUse(@Nullable ServerPlayer caster,
                                       @Nullable ItemStack staffStack,
                                       @Nullable ItemStack spellStack) {
        IMagicBaseItem spell = spellOf(spellStack);
        if (spell == null) {
            return false;
        }
        return spell.isSpecialUse(caster, staffStack == null ? ItemStack.EMPTY : staffStack, spellStack);
    }

    /** 不带施法者的版本（命令、数据生成用）：只判"法杖流派 == 法术流派"，查不到饰品栏。 */
    public static boolean isSpecialUse(@Nullable ItemStack staffStack, @Nullable ItemStack spellStack) {
        IMagicBaseItem spell = spellOf(spellStack);
        if (spell == null) {
            return false;
        }
        return spell.isSpecialUse(staffStack == null ? ItemStack.EMPTY : staffStack, spellStack);
    }

    public static float killMana(@Nullable Player player, @NotNull LivingEntity victim) {
        float base = Math.max(0f, victim.getMaxHealth() * BASE_KILL_MANA_PER_HP);
        if (player == null) {
            return base;
        }
        return Math.max(0f, MagicStatRegistry.foldFloat(base,
                (provider, current) -> provider.modifyKillMana(player, victim, current)));
    }

    public static boolean isSpell(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IMagicBaseItem;
    }

    @Nullable
    public static IMagicBaseItem spellOf(@Nullable ItemStack stack) {
        return isSpell(stack) ? (IMagicBaseItem) stack.getItem() : null;
    }

    /** 法术的分类轴：主动 / 被动；不是法术时按 {@link SpellKind#ACTIVE} 处理，调用方不必先判空。 */
    @NotNull
    public static SpellKind spellKind(@Nullable ItemStack stack) {
        IMagicBaseItem item = spellOf(stack);
        return item == null ? SpellKind.ACTIVE : item.spellKind();
    }

    public static boolean isPassive(@Nullable ItemStack stack) {
        return spellKind(stack) == SpellKind.PASSIVE;
    }

    /** 法术的<b>冷却键</b> = 物品注册名：同名物品共享冷却，不同注册名各自独立。 */
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

    /** {@code KEEP} 维持期间每秒的额外耗蓝（与触发快慢无关）。 */
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

    /** {@code KEEP} 触发速度（每多少 tick 触发一次），钳在 {@code [1, MAX_KEEP_TRIGGER_INTERVAL]}；下限是 1 不是 0。 */
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
     * {@code KEEP} 每生效一次的额外耗蓝：跟着触发节奏走，触发间隔 20 tick 就每 20 tick 扣一次。
     * 与 {@link #keepCostPerSecond} 并存互不代替（那一项与触发快慢无关）。
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
     * 锁定目标的最大距离（格），钳在 {@code [0, MAX_TARGET_RANGE]}；只对 {needsTarget()} 的法术有意义。
     * 射线在"撞到方块"或"超过该距离"里先到的一处停下，所以它既是射程也是穿透深度。
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

    /** 释放后的冷却 tick（同名物品共享）。 */
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

    public static float baseSpellDamage(ItemStack spell) {
        IMagicBaseItem item = spellOf(spell);
        return item == null ? 0f : item.baseDamage();
    }

    /**
     * 手上法杖等级对<b>同流派法术</b>的固定伤害加成（点）：只看主手/副手，等级每高一级 +2（{@code StaffGrade#spellBonus()}）。
     */
    public static float staffGradeBonus(@Nullable Player player, @Nullable ItemStack spell) {
        if (player == null) {
            return 0f;
        }
        IMagicBaseItem spellItem = spellOf(spell);
        if (spellItem == null) {
            return 0f;
        }
        for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof StaffBase staff
                    && staff.getMagicType() == spellItem.getMagicType()) {
                return staff.getGrade().spellBonus();
            }
        }
        return 0f;
    }

    /** {@code CHARGE} 的最低蓄力比例（钳在 [0,1]）：低于它这次释放作废，达到它放出去按真实完成度缩放。 */
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
     * 把"已按住多少 tick"换算成蓄力完成度 {@code 0.0~1.0}（{@code MagicCastContext#chargeRatio()} 的来源）；
     * 需要的 tick 为 0（非蓄力型，或被修饰器减到 0）时返回 {@code 1.0}。
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
