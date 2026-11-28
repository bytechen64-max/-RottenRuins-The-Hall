package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.gameplay.infection.biome.BiomeSpreadHelper;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.InfSkeletonEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated.ScoutEntity;

/** 王庭死亡转换：非王庭生物死在王庭群系里要翻地，人形家族还要留下畸骸尸体。 */
@GameTestHolder("rotten_ruins")
@PrefixGameTestTemplate(false)
public final class HallDeathConversionGameTests {

    private static final ResourceKey<Biome> HALL_WASTELAND = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("rotten_ruins", "hall_wasteland"));

    @GameTest(template = "potential_empty", batch = "hall_death_terrain", timeoutTicks = 80)
    public static void nonHallDeathConvertsGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareHallPlot(helper, level);

        Cow cow = spawnedAt(helper, level, EntityType.COW.create(level), ground);
        cow.kill();

        helper.runAfterDelay(2, () -> {
            int converted = 0;
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (level.getBlockState(ground.offset(x, 0, z)).is(RegisterBlock.HALL_GRASS_BLOCK.get())) converted++;
                }
            }
            helper.assertTrue(converted > 0,
                    "非王庭生物死在王庭群系里应把脚下方块翻成王庭草方块，实际转换 " + converted + " 块");
            helper.succeed();
        });
    }

    @GameTest(template = "potential_empty", batch = "hall_death_corpse", timeoutTicks = 80)
    public static void skeletonDeathBecomesInfectedSkeleton(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareHallPlot(helper, level);

        Skeleton skeleton = spawnedAt(helper, level, EntityType.SKELETON.create(level), ground);
        skeleton.kill();

        helper.runAfterDelay(2, () -> {
            AABB area = new AABB(ground).inflate(8.0D);
            helper.assertTrue(!level.getEntitiesOfClass(InfSkeletonEntity.class, area).isEmpty(),
                    "骷髅死在王庭群系里应转换成畸骸骷髅（进化表专属映射）");
            helper.succeed();
        });
    }

    @GameTest(template = "potential_empty", batch = "hall_death_fallback", timeoutTicks = 80)
    public static void unmappedDeathFallsBackToScoutByVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = prepareHallPlot(helper, level);

        // 牛：宽 0.9 × 高 1.4 = 1.26 < 2 → 碰撞体积兜底第一档（溃烂纠察）
        Cow cow = spawnedAt(helper, level, EntityType.COW.create(level), ground);
        cow.kill();

        helper.runAfterDelay(2, () -> {
            AABB area = new AABB(ground).inflate(8.0D);
            helper.assertTrue(!level.getEntitiesOfClass(ScoutEntity.class, area).isEmpty(),
                    "没有专属映射的生物应按碰撞体积兜底转换成溃烂纠察");
            helper.succeed();
        });
    }

    /** 铺一层原版草方块的空地，并把该柱改成 hall_wasteland 群系。 */
    private static BlockPos prepareHallPlot(GameTestHelper helper, ServerLevel level) {
        BlockPos ground = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlockAndUpdate(ground.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(ground.offset(x, 1, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(ground.offset(x, 2, z), Blocks.AIR.defaultBlockState());
            }
        }
        Holder<Biome> hall = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(HALL_WASTELAND);
        BiomeSpreadHelper.setColumnQuartBiome(level, ground.above(), hall);
        helper.assertTrue(level.getBiome(ground.above()).is(HALL_WASTELAND),
                "前置条件：测试柱必须已经是 hall_wasteland 群系");
        return ground;
    }

    private static <T extends net.minecraft.world.entity.Mob> T spawnedAt(
            GameTestHelper helper, ServerLevel level, T mob, BlockPos ground) {
        helper.assertTrue(mob != null, "测试实体生成失败");
        mob.moveTo(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D);
        helper.assertTrue(level.addFreshEntity(mob), "测试实体加入世界失败");
        helper.assertTrue(level.getBiome(mob.blockPosition()).is(HALL_WASTELAND),
                "前置条件：尸体所在位置必须是 hall_wasteland 群系");
        return mob;
    }
}
