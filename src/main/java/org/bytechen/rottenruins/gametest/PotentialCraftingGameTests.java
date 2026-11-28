package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipe;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;

/**
 * 势能合成台的服务端逻辑验证（GameTest 跑在专用服务端上，所以也顺带证明了服务端可用）。
 *
 * <p>注意：这里全部在<b>同一个游戏 tick 内</b>手动推进方块实体，避免自己的
 * {@code SourceWireNetwork} 在下个 tick 把 {@code POWER} 刷回 0（没有真实回路时它就是 0）。</p>
 */
@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class PotentialCraftingGameTests {

    private static BlockPos stationPos(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(8, 3, 8));
    }

    /** 放一个通电势能（POWER=15）的合成台，并按配方填好材料。 */
    private static PotentialCraftingTableBlockEntity setup(GameTestHelper helper,
                                                           PotentialCraftingRecipe recipe,
                                                           int power) {
        var level = helper.getLevel();
        BlockPos pos = stationPos(helper);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, RegisterBlock.POTENTIAL_CRAFTING_TABLE.get().defaultBlockState()
                .setValue(SourceMachineBlock.POWER, power));
        var station = (PotentialCraftingTableBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(station != null, "合成台必须创建出方块实体");
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            station.setItem(i, recipe.ingredients().get(i).display());
        }
        return station;
    }

    @GameTest(template = "potential_empty", batch = "potential_crafting", timeoutTicks = 100)
    public static void recipeMatchesShapelessly(GameTestHelper helper) {
        var level = helper.getLevel();
        PotentialCraftingRecipe expected = PotentialCraftingRecipes.byId(level,
                new net.minecraft.resources.ResourceLocation(HallMod.MODID,
                        "potential_crafting/mana_bottle"));
        helper.assertTrue(expected != null, "mana_bottle 配方必须能从数据包加载到");

        // 材料换个顺序放进环形槽：无序配方照样匹配
        BlockPos pos = stationPos(helper);
        level.setBlockAndUpdate(pos, RegisterBlock.POTENTIAL_CRAFTING_TABLE.get().defaultBlockState());
        var station = (PotentialCraftingTableBlockEntity) level.getBlockEntity(pos);
        station.setItem(0, new ItemStack(RegisterItem.SOURCE_CHARGE_SAND.get()));
        station.setItem(14, new ItemStack(Items.GLASS_BOTTLE));
        station.setItem(7, new ItemStack(Items.GLASS_BOTTLE));
        station.setItem(3, new ItemStack(Items.GLASS_BOTTLE));
        station.setItem(11, new ItemStack(Items.GLASS_BOTTLE));

        PotentialCraftingRecipe found = PotentialCraftingRecipes.findMatch(level, station);
        helper.assertTrue(found != null, "打乱顺序后仍应匹配到配方");
        helper.assertTrue(found.getId().equals(expected.getId()), "匹配到的应该是 mana_bottle");

        // 多放一个物品就不再匹配（无序配方要求刚好用完）
        station.setItem(5, new ItemStack(Items.DIRT));
        helper.assertTrue(PotentialCraftingRecipes.findMatch(level, station) == null,
                "多出的物品必须让配方失配");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "potential_crafting", timeoutTicks = 100)
    public static void fullPowerHalvesCraftTime(GameTestHelper helper) {
        var level = helper.getLevel();
        PotentialCraftingRecipe recipe = PotentialCraftingRecipes.byId(level,
                new net.minecraft.resources.ResourceLocation(HallMod.MODID,
                        "potential_crafting/mana_bottle"));
        helper.assertTrue(recipe != null, "配方必须存在");
        int time = recipe.timeTicks();

        // 满势能（15）：每 tick 进度 +30，需求 time×15 → 恰好 time/2 tick 完成
        var fast = setup(helper, recipe, 15);
        helper.assertTrue(fast.requestStart(), "点按钮后应能开始合成");
        int fastTicks = 0;
        for (int i = 0; i < time; i++) {
            if (!fast.isCrafting()) {
                break;
            }
            PotentialCraftingTableBlockEntity.tick(level, fast.getBlockPos(),
                    fast.getBlockState(), fast);
            fastTicks++;
            if (!fast.isCrafting()) {
                break;
            }
        }
        helper.assertTrue(!fast.isCrafting(), "满势能下必须完成合成");
        helper.assertTrue(fastTicks <= time / 2 + 2,
                "满势能应把时间减半：用了 " + fastTicks + " tick，基准 " + time);
        helper.assertTrue(fast.getItem(PotentialCraftingTableBlockEntity.OUTPUT_SLOT)
                        .is(RegisterItem.MANA_BOTTLE.get()),
                "产物槽应是储魔瓶");
        helper.assertTrue(fast.getItem(0).isEmpty() && fast.getItem(14).isEmpty(),
                "材料必须被消耗掉");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "potential_crafting", timeoutTicks = 100)
    public static void noPowerMeansNoProgress(GameTestHelper helper) {
        var level = helper.getLevel();
        PotentialCraftingRecipe recipe = PotentialCraftingRecipes.byId(level,
                new net.minecraft.resources.ResourceLocation(HallMod.MODID,
                        "potential_crafting/mana_bottle"));
        helper.assertTrue(recipe != null, "配方必须存在");

        var station = setup(helper, recipe, 0);
        helper.assertTrue(!station.requestStart(), "没有势能时点按钮也不该开始");
        for (int i = 0; i < recipe.timeTicks() * 2; i++) {
            PotentialCraftingTableBlockEntity.tick(level, station.getBlockPos(),
                    station.getBlockState(), station);
        }
        helper.assertTrue(!station.isCrafting(), "没有势能时不该开始合成");
        helper.assertTrue(station.getItem(PotentialCraftingTableBlockEntity.OUTPUT_SLOT).isEmpty(),
                "没有势能时不该有产物");
        helper.assertTrue(station.progressPermille() == 0, "没有势能时进度必须停在 0");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "potential_crafting", timeoutTicks = 100)
    public static void oneItemPerSlot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = stationPos(helper);
        level.setBlockAndUpdate(pos, RegisterBlock.POTENTIAL_CRAFTING_TABLE.get().defaultBlockState());
        var station = (PotentialCraftingTableBlockEntity) level.getBlockEntity(pos);
        station.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 16));
        helper.assertTrue(station.getItem(0).getCount() == 1, "环形槽每格只能放 1 个");
        helper.assertTrue(!station.canPlaceItem(0, new ItemStack(Items.GLASS_BOTTLE)),
                "已占用的格子不能再塞");
        helper.assertTrue(station.canPlaceItem(1, new ItemStack(Items.GLASS_BOTTLE)),
                "空格子可以放");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "potential_crafting", timeoutTicks = 100)
    public static void breakingMidCraftConsumesIngredients(GameTestHelper helper) {
        var level = helper.getLevel();
        PotentialCraftingRecipe recipe = PotentialCraftingRecipes.byId(level,
                new net.minecraft.resources.ResourceLocation(HallMod.MODID,
                        "potential_crafting/mana_bottle"));
        helper.assertTrue(recipe != null, "配方必须存在");

        var station = setup(helper, recipe, 15);
        helper.assertTrue(station.requestStart(), "满势能下点按钮应能开始合成");
        helper.assertTrue(station.isCrafting(), "按钮生效后应进入合成中");

        station.dropRemainderAfterInterrupt();
        for (int slot = 0; slot < PotentialCraftingTableBlockEntity.RING_SLOTS; slot++) {
            helper.assertTrue(station.getItem(slot).isEmpty(),
                    "拆机时已投入的材料不可返还，槽位 " + slot + " 应已清空");
        }
        helper.assertTrue(!station.isCrafting(), "拆机后不应还处于合成中");
        helper.succeed();
    }
}
