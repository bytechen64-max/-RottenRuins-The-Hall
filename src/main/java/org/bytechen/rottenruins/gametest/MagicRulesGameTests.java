package org.bytechen.rottenruins.gametest;

import org.bytechen.rottenruins.core.magic.model.UsingType;

import org.bytechen.rottenruins.core.magic.model.MagicType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.core.magic.model.MagicTypes;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.content.magic.WandItem;
import org.bytechen.rottenruins.content.magic.base.*;
import org.bytechen.rottenruins.gameplay.magic.spell.*;

import static org.bytechen.rottenruins.core.magic.model.MagicType.*;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class MagicRulesGameTests {
    @GameTest(template = "potential_empty", batch = "magic_rules")
    public static void numericBoundariesPreserveRoundingAndLimits(GameTestHelper helper) {
        helper.assertTrue(MagicMath.maxMana(-1) == 1 && MagicMath.slotCount(100) == 64,
                "Keep mana and slot lower/upper bounds");
        helper.assertTrue(MagicMath.keepInterval(0) == 1 && MagicMath.keepInterval(201) == 200,
                "Keep interval must remain within 1..200");
        helper.assertTrue(MagicMath.targetRange(Double.NaN) == 0 && MagicMath.targetRange(999) == 128,
                "Invalid and excessive range must clamp");
        helper.assertTrue(MagicMath.ratio(Float.NaN) == 0 && MagicMath.chargeRatio(-1, 20) == 0
                        && MagicMath.chargeRatio(10, 20) == 0.5f && MagicMath.chargeRatio(21, 20) == 1
                        && MagicMath.chargeRatio(0, 0) == 1, "Preserve charge completion semantics");
        helper.assertTrue(MagicMath.reducedCooldown(21, 50) == 11
                        && MagicMath.reducedCooldown(21, 90) == 11
                        && MagicMath.reducedCooldown(21, 20) == 17
                        && MagicMath.reducedCooldown(21, -1) == 21,
                "Cooldown reduction is a percentage capped at 50 and rounds rather than truncates");
        helper.assertTrue(MagicMath.discountedCost(21, 1) == 20
                        && MagicMath.discountedCost(100, 99) == 75
                        && MagicMath.baseKillMana(20) == 20, "Preserve mana discount and refund formulas");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "magic_rules")
    public static void combinationsAreDistinctImmutableAndAverageBonuses(GameTestHelper helper) {
        MagicType[] input = {FIRE, WATER, FIRE};
        MagicTypes types = MagicTypes.of(input);
        input[0] = VOID;
        helper.assertTrue(types.primary() == FIRE && types.values().size() == 2
                        && types.contains(WATER) && !types.contains(VOID),
                "Copy, deduplicate, and retain the first type for legacy display");
        boolean immutable = false;
        try { types.values().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable, "Caller must not change a spell's declared types");
        boolean emptyRejected = false;
        try { MagicTypes.of(); } catch (IllegalArgumentException expected) { emptyRejected = true; }
        helper.assertTrue(emptyRejected, "A spell must declare at least one type");
        close(helper, MagicMath.spellPower(1, 0.6, 0.2), 1.4);
        close(helper, MagicMath.spellPower(1, 0.6, 0), 1.3);
        close(helper, MagicMath.spellPower(1, 0.6), 1.6);
        close(helper, MagicMath.spellPower(1), 1.0);
        close(helper, MagicMath.spellPower(-1, 0), 0);
        // 增伤曲线：0～10 线性 0.3x，10～16 二次段在 16 处斜率 0.3，之后继续 0.3
        close(helper, MagicMath.powerCurve(0), 0);
        close(helper, MagicMath.powerCurve(5), 1.5);
        close(helper, MagicMath.powerCurve(10), 3);
        close(helper, MagicMath.powerCurve(16), 6);
        close(helper, MagicMath.powerCurve(20), 7.2);
        close(helper, MagicMath.powerMultiplier(0, 0), 1.0);
        close(helper, MagicMath.powerMultiplier(10, 0), 4.0);
        close(helper, MagicMath.powerMultiplier(12, 4), 1.0 + MagicMath.powerCurve(12 + 4 * MagicMath.SCHOOL_POWER_WEIGHT));
        String tooltip = MagicTooltips.typeLine(types).getString();
        helper.assertTrue(tooltip.contains(Component.translatable(FIRE.translationKey(HallMod.MODID)).getString())
                        && tooltip.contains(Component.translatable(WATER.translationKey(HallMod.MODID)).getString()),
                "Show all schools in the spell tooltip");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "magic_rules")
    public static void mixedSchoolsResolveAttributesAndMatchBothWands(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAttribute(RegisterAttributes.SPELL_POWER.get()).setBaseValue(1);
        player.getAttribute(RegisterAttributes.SPELL_POWER_FIRE.get()).setBaseValue(0.6);
        player.getAttribute(RegisterAttributes.SPELL_POWER_WATER.get()).setBaseValue(0.2);
        MagicTypes types = MagicTypes.of(FIRE, WATER, FIRE);
        close(helper, MagicStats.spellPowerForTypes(player, types), 1.4);
        ItemStack legacyFireBall = new ItemStack(RegisterItem.FIRE_BALL.get());
        close(helper, MagicStats.spellPower(player, legacyFireBall), 1.6);
        helper.assertTrue(MagicStats.spellOf(legacyFireBall).getMagicTypes().values().size() == 1,
                "Existing spells must remain single-school without source changes");
        var mixed = new TestSpell(types);
        ItemStack fire = new ItemStack(RegisterItem.WAND_FIRE.get());
        ItemStack water = new ItemStack(RegisterItem.WAND_WATER.get());
        ItemStack unrelated = new ItemStack(RegisterItem.WAND_VOID.get());
        helper.assertTrue(mixed.isSpecialUse(fire, ItemStack.EMPTY)
                        && mixed.isSpecialUse(water, ItemStack.EMPTY)
                        && !mixed.isSpecialUse(unrelated, ItemStack.EMPTY),
                "Either declared school must qualify for special use");
        helper.assertTrue(((WandItem) fire.getItem()).usesReserve(mixed)
                        && ((WandItem) water.getItem()).usesReserve(mixed)
                        && !((WandItem) unrelated.getItem()).usesReserve(mixed),
                "Reserve spending must use the same multi-school matching rule");
        helper.succeed();
    }

    private static void close(GameTestHelper helper, double actual, double expected) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-9, "Expected " + expected + ", got " + actual);
    }

    /** Does not register test-only content or alter the existing spells. */
    private record TestSpell(MagicTypes getMagicTypes) implements IMagicBaseItem {
        @Override public MagicType getMagicType() { return getMagicTypes.primary(); }
        @Override public UsingType getUsingType() { return UsingType.IMMEDIATELY; }
        @Override public int baseManaCost() { return 10; }
        @Override public int baseKeepCostPerSecond() { return 0; }
        @Override public int baseCooldownTicks() { return 20; }
        @Override public int baseChargeTicks() { return 0; }
        @Override public float baseDamage() { return 1; }
        @Override public boolean cast(ServerPlayer player, ItemStack stack, MagicCastContext context) { return false; }
    }
}
