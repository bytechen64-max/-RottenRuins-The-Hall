package org.bytechen.rottenruins.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;

import java.util.Set;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class StructureSpellLootGameTests {
    @GameTest(template = "potential_empty", batch = "structure_spell_loot")
    public static void structureChestsAppendExpectedSpells(GameTestHelper helper) {
        checkTable(helper, "bastion_bridge", RegisterItem.FIRE_BALL.get());
        checkTable(helper, "bastion_hoglin_stable", RegisterItem.FIRE_BALL.get());
        checkTable(helper, "bastion_other", RegisterItem.FIRE_BALL.get());
        checkTable(helper, "bastion_treasure", RegisterItem.FIRE_BALL.get());
        checkTable(helper, "shipwreck_treasure", RegisterItem.TIDAL_PULSE.get());
        checkTable(helper, "jungle_temple", RegisterItem.BRIAR_SNARE.get());
        checkTable(helper, "desert_pyramid", RegisterItem.SPRINT.get());
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "structure_spell_loot")
    public static void unrelatedLootDoesNotGainSpells(GameTestHelper helper) {
        checkTable(helper, "shipwreck_supply", null);
        checkTable(helper, "shipwreck_map", null);
        checkTable(helper, "jungle_temple_dispenser", null);
        checkTable(helper, "simple_dungeon", null);
        helper.succeed();
    }

    private static void checkTable(GameTestHelper helper, String chest, Item expected) {
        Set<Item> spells = Set.of(RegisterItem.FIRE_BALL.get(), RegisterItem.TIDAL_PULSE.get(),
                RegisterItem.BRIAR_SNARE.get(), RegisterItem.SPRINT.get());
        var table = helper.getLevel().getServer().getLootData()
                .getLootTable(new ResourceLocation("minecraft", "chests/" + chest));
        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        int hits = 0;
        int originalItems = 0;
        for (int index = 1; index <= 128; index++) {
            var loot = table.getRandomItems(params, index * 0x9E3779B97F4A7C15L);
            int spellCount = 0;
            for (var stack : loot) {
                if (spells.contains(stack.getItem())) {
                    helper.assertTrue(stack.is(expected), chest + " produced an unexpected spell");
                    spellCount += stack.getCount();
                } else {
                    originalItems += stack.getCount();
                }
            }
            helper.assertTrue(spellCount <= 1, chest + " must append at most one spell");
            hits += spellCount;
        }
        helper.assertTrue(originalItems > 0, chest + " must retain vanilla loot");
        helper.assertTrue(expected == null ? hits == 0 : hits > 0 && hits < 128,
                chest + " must " + (expected == null ? "not gain spells" : "occasionally produce its spell")
                        + "; actual hits: " + hits);
    }
}
