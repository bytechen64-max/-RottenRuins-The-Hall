package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.infection.blockspread.BlockSpreadManager;
import org.bytechen.rottenruins.platform.forge.infection.capability.CapabilityRegistry;
import org.bytechen.rottenruins.gameplay.infection.difficulty.DifficultyHelper;
import org.bytechen.rottenruins.platform.forge.infection.difficulty.GlobalDifficultyData;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class InfectionCoreGameTests {
    @GameTest(template = "potential_empty", batch = "infection_core")
    public static void deathEventCountsOnceAndInfects(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("infcore"), "Core must work without a standalone mod");
        var killer = helper.spawn(EntityTypeRegistry.SCOUT.get(), new BlockPos(1, 2, 1));
        killer.setNoAi(true);
        var victim = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 1));
        helper.assertTrue(killer.getCapability(CapabilityRegistry.KILL_COUNT).isPresent(),
                "Rotten Ruins must attach the infection capability");
        killer.setKillCount(0);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim, victim.damageSources().mobAttack(killer)));
        helper.assertTrue(killer.getKillCount() == 1, "One death event must count exactly once");
        helper.assertTrue(victim.isRemoved(), "The infection rule must replace the skeleton");
        helper.assertTrue(!helper.getLevel().getEntities(EntityTypeRegistry.INF_SKELETON.get(),
                victim.getBoundingBox().inflate(1), entity -> true).isEmpty(),
                "The infected skeleton must be spawned from the bundled rule");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "infection_core")
    public static void legacyKillCountLoadsFromEntityNbt(GameTestHelper helper) {
        var entity = helper.spawn(EntityTypeRegistry.SCOUT.get(), new BlockPos(1, 2, 1));
        entity.setNoAi(true);
        CompoundTag saved = entity.serializeNBT();
        CompoundTag caps = saved.getCompound("ForgeCaps");
        helper.assertTrue(caps.contains("infcore:kill_count"), "Keep the old capability storage key");
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("killCount", 27);
        legacy.putBoolean("enabled", true);
        caps.put("infcore:kill_count", legacy);
        entity.deserializeNBT(saved);
        helper.assertTrue(entity.getKillCount() == 27, "Read the old save's kill count");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "infection_core")
    public static void bundledSpreadRuleConvertsDirt(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        boolean spread = BlockSpreadManager.applySpread(helper.getLevel(), pos,
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "spread"));
        helper.assertTrue(spread, "Find the bundled infcore_blockspread rule");
        helper.assertTrue(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_dirt").equals(
                ForgeRegistries.BLOCKS.getKey(helper.getLevel().getBlockState(pos).getBlock())),
                "Dirt must convert to hall dirt");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "infection_core")
    public static void legacyDifficultyStorageAndCommandRemainAvailable(GameTestHelper helper) {
        var overworld = helper.getLevel().getServer().overworld();
        var storage = overworld.getDataStorage();
        var previous = storage.computeIfAbsent(GlobalDifficultyData::load,
                GlobalDifficultyData::new, "infcore_global_difficulty");
        CompoundTag legacy = new CompoundTag();
        legacy.putString("difficulty", "infcore:hard");
        try {
            storage.set("infcore_global_difficulty", GlobalDifficultyData.load(legacy));
            helper.assertTrue("infcore:hard".equals(DifficultyHelper.getDifficulty(helper.getLevel()).toString()),
                    "Difficulty helper must read the legacy save name and ID");
            helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher()
                    .getRoot().getChild("infcore") != null, "Register the legacy difficulty command");
        } finally {
            storage.set("infcore_global_difficulty", previous);
        }
        helper.succeed();
    }
}
