package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class DeathHunterLootGameTests {
    @GameTest(template = "potential_empty", batch = "death_hunter_loot")
    public static void playerKillsUseGeneratedLoot(GameTestHelper helper) {
        checkDrops(helper, false);
    }

    @GameTest(template = "potential_empty", batch = "death_hunter_loot")
    public static void burstKillsUseGeneratedLoot(GameTestHelper helper) {
        checkDrops(helper, true);
    }

    private static void checkDrops(GameTestHelper helper, boolean burst) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        var position = helper.absolutePos(new BlockPos(3, 3, 3));
        var area = new AABB(position).inflate(2);
        int before = level.getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(e -> e.getItem().is(RegisterItem.DEATH_DAGGER.get()))
                .mapToInt(e -> e.getItem().getCount()).sum();
        int expected = 0;
        for (int index = 1; index <= 128; index++) {
            long seed = index * 0x9E3779B97F4A7C15L;
            var hunter = EntityTypeRegistry.DEATH_HUNTER.get().create(level);
            hunter.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            CompoundTag saved = new CompoundTag();
            hunter.saveWithoutId(saved);
            saved.putString("DeathLootTable", EntityTypeRegistry.DEATH_HUNTER.get().getDefaultLootTable().toString());
            saved.putLong("DeathLootTableSeed", seed);
            hunter.load(saved);
            hunter.setNoAi(true);
            hunter.setFakeDeathEnabled(burst);
            hunter.setFakeDeathChance(100);
            hunter.setFakeDeathDuration(1);
            var damage = level.damageSources().playerAttack(player);
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, hunter)
                    .withParameter(LootContextParams.ORIGIN, hunter.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, damage)
                    .withParameter(LootContextParams.KILLER_ENTITY, player)
                    .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);
            var table = level.getServer().getLootData().getLootTable(hunter.getLootTable());
            expected += table.getRandomItems(params, seed).stream()
                    .filter(stack -> stack.is(RegisterItem.DEATH_DAGGER.get()))
                    .mapToInt(stack -> stack.getCount()).sum();
            hunter.hurt(damage, 10000);
            if (burst) hunter.tick();
        }
        helper.assertTrue(expected > 0 && expected < 128,
                "Generated table must produce occasional daggers with fixed seeds");
        int after = level.getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(e -> e.getItem().is(RegisterItem.DEATH_DAGGER.get()))
                .mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(after - before == expected,
                "Actual " + (burst ? "burst" : "normal") + " drops " + (after - before)
                        + " must match generated loot table: " + expected);
        helper.succeed();
    }
}
