package org.bytechen.hall.utils;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

/**
 * Calculates dynamic domerite tool stats based on the player's Y-level,
 * stored in the item's NBT via {@code DomeriteToolEvents}.
 *
 * <h3>Formula</h3>
 * y = -64 → iron level  (0.50× netherite)
 * y =  62 → netherite   (1.00×)
 * y = 320 → 2× netherite (2.00×)
 */
public final class DomeriteStatsHelper {

    public static final String Y_TAG = "DomeriteY";

    private static final double MIN_Y       = -64.0;
    private static final double BASE_Y      =  62.0;
    private static final double MAX_Y       = 320.0;

    /** Multiplier at {@code MIN_Y} — iron is roughly half of netherite. */
    private static final double MIN_MULT    = 0.50;
    /** Multiplier at {@code BASE_Y} — netherite baseline. */
    private static final double BASE_MULT   = 1.00;
    /** Multiplier at {@code MAX_Y}. */
    private static final double MAX_MULT    = 2.00;

    // ── netherite reference values ──────────────────────────────────
    private static final int    NETH_DURABILITY  = Tiers.NETHERITE.getUses();       // 2031
    private static final float  NETH_SPEED       = Tiers.NETHERITE.getSpeed();      // 9.0F
    private static final float  NETH_DAMAGE      = Tiers.NETHERITE.getAttackDamageBonus(); // 4.0F

    private DomeriteStatsHelper() {}

    // ── public API ──────────────────────────────────────────────────

    /** Read stored Y from NBT, falling back to {@code BASE_Y} (62). */
    public static double getY(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(Y_TAG)) {
            return stack.getTag().getDouble(Y_TAG);
        }
        return BASE_Y;
    }

    public static void setY(ItemStack stack, double y) {
        stack.getOrCreateTag().putDouble(Y_TAG, y);
    }

    /** The unified multiplier, clamped to [{@value MIN_MULT}, {@value MAX_MULT}]. */
    public static double getMultiplier(ItemStack stack) {
        return multiplierForY(getY(stack));
    }

    /** Durability scaled by Y. */
    public static int getScaledDurability(ItemStack stack) {
        return Math.max(1, (int) (NETH_DURABILITY * getMultiplier(stack)));
    }

    /** Mining speed scaled by Y. */
    public static float getScaledSpeed(ItemStack stack) {
        return (float) (NETH_SPEED * getMultiplier(stack));
    }

    /** Attack damage bonus scaled by Y (the tier's additional damage). */
    public static float getScaledAttackBonus(ItemStack stack) {
        return (float) (NETH_DAMAGE * getMultiplier(stack));
    }

    // ── internal ────────────────────────────────────────────────────

    public static double multiplierForY(double y) {
        double mult;
        if (y <= BASE_Y) {
            // interpolate MIN_Y → BASE_Y  (0.50 → 1.00)
            double t = (y - MIN_Y) / (BASE_Y - MIN_Y); // 0 at min, 1 at base
            mult = MIN_MULT + t * (BASE_MULT - MIN_MULT);
        } else {
            // interpolate BASE_Y → MAX_Y  (1.00 → 2.00)
            double t = (y - BASE_Y) / (MAX_Y - BASE_Y); // 0 at base, 1 at max
            mult = BASE_MULT + t * (MAX_MULT - BASE_MULT);
        }
        return Math.max(MIN_MULT, Math.min(MAX_MULT, mult));
    }
}
