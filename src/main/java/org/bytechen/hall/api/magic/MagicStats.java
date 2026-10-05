package org.bytechen.hall.api.magic;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.overworld.registry.items.magic.bases.IMagicBaseItem;
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

    /** 魔法槽位基值 —— 玩家初始 10 格。 */
    public static final int BASE_SLOT_COUNT = 10;

    /** 槽位数硬上限：底层数组按这个长度分配，属性修饰器不能超过它。 */
    public static final int MAX_SLOT_COUNT = 64;

    /**
     * 击杀回蓝基值：每 1 点「受害者最大生命值」回多少法力。
     *
     * <p>默认 {@code 1.0}，即 20 血的僵尸回 20 点。想改成"回半血量"就调这里，
     * 或者注册一个 provider 用 {@code modifyKillMana} 按生物类型分别处理。</p>
     */
    public static final float BASE_KILL_MANA_PER_HP = 1.0f;

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
