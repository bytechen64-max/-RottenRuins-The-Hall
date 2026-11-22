package org.bytechen.rottenruins.core.magic.rules;

/** Numeric magic rules; inputs are already resolved from items, attributes and providers. */
public final class MagicMath {
    public static final int BASE_MAX_MANA = 1000;
    public static final double BASE_REGEN_PER_SECOND = 1.0d;
    public static final int BASE_SLOT_COUNT = 5;
    public static final int MAX_SLOT_COUNT = 64;
    public static final float BASE_KILL_MANA_PER_HP = 1.0f;
    public static final int MAX_KEEP_TRIGGER_INTERVAL = 200;
    public static final double MAX_TARGET_RANGE = 128.0d;
    /** 冷却缩减上限，单位是百分比点数：50 表示最多减 50%。 */
    public static final double MAX_COOLDOWN_REDUCTION = 50.0d;
    public static final double BASE_SPELL_POWER = 0.0d;
    /** 流派法强在增伤公式里的权重。 */
    public static final double SCHOOL_POWER_WEIGHT = 0.75d;

    private MagicMath() {}

    public static int maxMana(int value) { return Math.max(1, value); }
    public static int slotCount(int value) { return Math.max(1, Math.min(MAX_SLOT_COUNT, value)); }
    public static int keepInterval(int value) { return Math.max(1, Math.min(MAX_KEEP_TRIGGER_INTERVAL, value)); }

    public static double targetRange(double value) {
        return Double.isNaN(value) ? 0.0d : Math.max(0.0d, Math.min(MAX_TARGET_RANGE, value));
    }

    public static float ratio(float value) {
        return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }

    public static float chargeRatio(int heldTicks, int neededTicks) {
        return neededTicks <= 0 ? 1.0f : ratio(heldTicks / (float) neededTicks);
    }

    public static float baseKillMana(float maxHealth) {
        return Math.max(0f, maxHealth * BASE_KILL_MANA_PER_HP);
    }

    /** General power plus the equal-weight average of distinct spell-school bonuses. */
    public static double spellPower(double general, double... schoolBonuses) {
        double average = 0;
        // Sum the scaled terms to avoid overflowing a sum of finite large inputs.
        for (double bonus : schoolBonuses) average += bonus / schoolBonuses.length;
        return Math.max(0.0d, general + average);
    }

    /** 0 法强时正好 1.0 倍，用于半径、治疗、升力这类"原本就按 1.0 起步"的数值。 */
    public static double powerScale(double power) {
        return 1.0d + Math.max(0.0d, power);
    }

    /** 法术增伤倍率：{@code (通用法强 + 流派法强 × 0.75)² / 2 + 1}，0 法强时正好 1.0。 */
    public static double powerMultiplier(double general, double schoolAverage) {
        double power = general + schoolAverage * SCHOOL_POWER_WEIGHT;
        return power * power / 2.0d + 1.0d;
    }

    /** {@code reduction} 是百分比点数：20 表示冷却减 20%，钳在 {@code [0, 50]}。 */
    public static int reducedCooldown(int base, double reduction) {
        reduction = Math.max(0.0d, Math.min(MAX_COOLDOWN_REDUCTION, reduction));
        if (reduction <= 0.0d) return base;
        return Math.max(0, (int) Math.round(base * (1.0d - reduction / 100.0d)));
    }

    /** Existing mana-saving enchantment: five percent per level, capped at five levels. */
    public static int discountedCost(int base, int enchantmentLevel) {
        return Math.max(0, (int) Math.ceil(base * (1.0d - 0.05d * Math.min(5, enchantmentLevel))));
    }
}
