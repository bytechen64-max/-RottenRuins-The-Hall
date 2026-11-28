package org.bytechen.rottenruins.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.ModDamageTypes;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class ApoptosisGameTests {
    @GameTest(template = "potential_empty", batch = "apoptosis")
    public static void damageTimingArmorAndResistance(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var effect = RegisterEffect.APOPTOSIS.get();
        player.getAttribute(Attributes.ARMOR).setBaseValue(30);
        player.addEffect(new MobEffectInstance(effect, 160, 1));
        for (int i = 0; i < 39; i++) effect.applyEffectTick(player, 1);
        close(helper, player.getHealth(), 20);
        effect.applyEffectTick(player, 1);
        close(helper, player.getHealth(), 18);
        player.invulnerableTime = 0;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        for (int i = 0; i < 40; i++) effect.applyEffectTick(player, 1);
        close(helper, player.getHealth(), 16.4F);
        var source = new DamageSource(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.APOPTOSIS));
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR), "Apoptosis bypasses armor");
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_RESISTANCE), "Resistance must apply");
        player.removeAllEffects();
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "apoptosis")
    public static void healingAndFoodRegeneration(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.setHealth(10);
        player.addEffect(new MobEffectInstance(RegisterEffect.APOPTOSIS.get(), 200, 0));
        player.heal(4);
        close(helper, player.getHealth(), 12);
        var food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(5);
        food.setExhaustion(0);
        for (int i = 0; i < 100; i++) food.tick(player);
        close(helper, player.getHealth(), 12);
        close(helper, food.getExhaustionLevel(), 0);
        food.setSaturation(0);
        food.setFoodLevel(18);
        for (int i = 0; i < 100; i++) food.tick(player);
        close(helper, player.getHealth(), 12);
        player.removeAllEffects();
        player.heal(4);
        close(helper, player.getHealth(), 16);
        for (int i = 0; i < 80; i++) food.tick(player);
        close(helper, player.getHealth(), 17);
        helper.succeed();
    }

    private static void close(GameTestHelper helper, float actual, float expected) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001F,
                "Expected " + expected + " but got " + actual);
    }
}
