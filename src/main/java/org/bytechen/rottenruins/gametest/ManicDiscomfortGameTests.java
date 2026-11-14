package org.bytechen.rottenruins.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;

import java.util.UUID;

@GameTestHolder("rotten_ruins_effect_tests")
@PrefixGameTestTemplate(false)
public final class ManicDiscomfortGameTests {
    @GameTest(template = "empty")
    public static void damageWaitsThreeSecondsAndResetsOnRemoval(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var other = helper.makeMockPlayer();
        var effect = RegisterEffect.MANIC_DISCOMFORT.get();
        player.getAttribute(Attributes.ARMOR).setBaseValue(30);
        player.addEffect(new MobEffectInstance(effect, 1200));
        other.addEffect(new MobEffectInstance(effect, 1200));
        for (int i = 0; i < 59; i++) effect.applyEffectTick(player, 0);
        close(helper, player.getHealth(), 20);
        effect.applyEffectTick(player, 0);
        close(helper, player.getHealth(), 16);
        for (int i = 0; i < 30; i++) effect.applyEffectTick(other, 0);
        close(helper, other.getHealth(), 20);
        player.invulnerableTime = 0;
        for (int i = 0; i < 60; i++) effect.applyEffectTick(player, 0);
        close(helper, player.getHealth(), 12);
        other.removeEffect(effect);
        other.addEffect(new MobEffectInstance(effect, 1200));
        for (int i = 0; i < 59; i++) effect.applyEffectTick(other, 0);
        close(helper, other.getHealth(), 20);
        effect.applyEffectTick(other, 0);
        close(helper, other.getHealth(), 16);
        player.removeAllEffects();
        other.removeAllEffects();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attackBonusIsMultiplicativeAndRemovedCleanly(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        attack.setBaseValue(8);
        var extra = new AttributeModifier(UUID.fromString("afef7e25-74ba-412e-b566-c83cd6a30f81"),
                "test multiplier", 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);
        attack.addTransientModifier(extra);
        close(helper, attack.getValue(), 8.8);
        var effect = RegisterEffect.MANIC_DISCOMFORT.get();
        player.addEffect(new MobEffectInstance(effect, 200));
        close(helper, attack.getValue(), 11);
        player.removeEffect(effect);
        close(helper, attack.getValue(), 8.8);
        player.addEffect(new MobEffectInstance(effect, 200, 3));
        close(helper, attack.getValue(), 11);
        player.removeAllEffects();
        attack.removeModifier(extra);
        close(helper, attack.getValue(), 8);
        helper.succeed();
    }

    private static void close(GameTestHelper helper, double actual, double expected) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001,
                "Expected " + expected + " but got " + actual);
    }
}
