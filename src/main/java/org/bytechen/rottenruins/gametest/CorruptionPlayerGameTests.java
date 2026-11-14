package org.bytechen.rottenruins.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

@GameTestHolder("rotten_ruins_player_skin_tests")
@PrefixGameTestTemplate(false)
public final class CorruptionPlayerGameTests {
    @GameTest(template = "empty", batch = "player_skin")
    public static void identityAndDefaultAppearanceSurviveSave(GameTestHelper helper) {
        var type = CorruptionEntityTypes.CORRUPTION_PLAYER.get();
        var original = type.create(helper.getLevel());
        original.setCustomName(Component.literal("Notch"));
        helper.assertTrue("Notch".equals(original.getSkinPlayerId()), "Named mobs must resolve player names");
        original.setSkinPlayerId("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        var restored = type.create(helper.getLevel());
        restored.load(original.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(original.getSkinPlayerId().equals(restored.getSkinPlayerId()), "Save skin identity");
        helper.assertTrue(original.model().equals(restored.model()) && original.texture().equals(restored.texture()),
                "Default appearance must stay stable after loading");
        restored.setSkinPlayerId("");
        restored.setCustomName(Component.literal("Dinnerbone"));
        helper.assertTrue("Dinnerbone".equals(restored.getSkinPlayerId()), "Clearing identity must restore name lookup");
        restored.setCustomName(null);
        helper.assertTrue(restored.getSkinPlayerId().isEmpty(), "Unnamed mobs must use bundled skins");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "player_skin")
    public static void playerEvolutionPreservesProfileWithoutCustomName(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var result = CorruptionEntityTypes.CORRUPTION_PLAYER.get().create(helper.getLevel());
        helper.assertTrue(!player.hasCustomName(), "Exercise the ordinary player name path");
        MinecraftForge.EVENT_BUS.post(new EntityEvolveEvent(helper.getLevel(), player, result));
        helper.assertTrue(result.getSkinPlayerId().equals(player.getUUID().toString()), "Evolution must retain player UUID");
        helper.assertTrue(result.hasCustomName() && result.getCustomName().equals(player.getName()),
                "Evolution must retain ordinary player name");
        helper.succeed();
    }
}
