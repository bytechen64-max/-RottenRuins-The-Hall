package org.bytechen.hall.datagen.gen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.RegisterItem;

import java.util.function.Consumer;

public class RecipeProviderData extends RecipeProvider {
    public RecipeProviderData(PackOutput output) {
        super(output);
    }


    private void addPlanksRecipe(Consumer<FinishedRecipe> consumer, Block log, Item planks) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, 4)
                .requires(log)
                .unlockedBy("has_" + BuiltInRegistries.BLOCK.getKey(log).getPath(), has(log))
                .save(consumer);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        // 原木 -> 4 个木板
        addPlanksRecipe(consumer, RegisterBlock.HALL_LOG.get(), Item.byBlock(RegisterBlock.HALL_PLANKS.get()));

        // ---- HALL_PLANKS 建筑方块合成配方 ----
        addHallPlanksBuildingRecipes(consumer);

        // ---- HALL_STONE 建筑方块合成配方 ----
        addHallStoneBuildingRecipes(consumer);

        // 为每种矿石生成烧炼 + 高炉配方
        addCookingRecipes(consumer,
                RegisterItem.DOMITE_ORE.get(),
                RegisterItem.DOMITE_CRYSTAL.get(),
                0.7f, 200, 100);

        addCookingRecipes(consumer,
                RegisterItem.DOMERITE_ORE.get(),
                RegisterItem.DOMERITE_INGOT.get(),
                0.7f, 200, 100);

        // ── domerite 工具合成（domerite_ingot 为刃材，domite_crystal 为柄材）──
        addDomeriteToolRecipes(consumer);
    }

    /**
     * 为 HALL_PLANKS 生成所有建筑方块合成配方。
     */
    private void addHallPlanksBuildingRecipes(Consumer<FinishedRecipe> consumer) {
        Block planks = RegisterBlock.HALL_PLANKS.get();
        String unlock = "has_hall_planks";

        // 楼梯：4 木板 → 4 楼梯
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, RegisterBlock.HALL_STAIRS.get().asItem(), 4)
                .pattern("P  ")
                .pattern("PP ")
                .pattern("PPP")
                .define('P', planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 台阶：3 木板 → 6 台阶
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, RegisterBlock.HALL_SLAB.get().asItem(), 6)
                .pattern("PPP")
                .define('P', planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 栅栏：4 木板 + 2 木棍 → 3 栅栏
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RegisterBlock.HALL_FENCE.get().asItem(), 3)
                .pattern("PSP")
                .pattern("PSP")
                .define('P', planks)
                .define('S', Items.STICK)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 栅栏门：2 木板 + 4 木棍 → 1 栅栏门
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RegisterBlock.HALL_FENCE_GATE.get().asItem(), 1)
                .pattern("SPS")
                .pattern("SPS")
                .define('P', planks)
                .define('S', Items.STICK)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 门：6 木板 → 3 门
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RegisterBlock.HALL_DOOR.get().asItem(), 3)
                .pattern("PP")
                .pattern("PP")
                .pattern("PP")
                .define('P', planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 活板门：6 木板 → 2 活板门
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RegisterBlock.HALL_TRAPDOOR.get().asItem(), 2)
                .pattern("PPP")
                .pattern("PPP")
                .define('P', planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 按钮：1 木板 → 1 按钮（无定形合成）
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, RegisterBlock.HALL_BUTTON.get().asItem(), 1)
                .requires(planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);

        // 压力板：2 木板 → 1 压力板
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RegisterBlock.HALL_PRESSURE_PLATE.get().asItem(), 1)
                .pattern("PP")
                .define('P', planks)
                .unlockedBy(unlock, has(planks))
                .save(consumer);
    }

    /**
     * 为 HALL_STONE 生成石制建筑方块合成配方（含切石机）。
     */
    private void addHallStoneBuildingRecipes(Consumer<FinishedRecipe> consumer) {
        Block stone = RegisterBlock.HALL_STONE.get();
        String unlock = "has_hall_stone";

        // 楼梯：6 石块 → 4 楼梯
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, RegisterBlock.HALL_STONE_STAIRS.get().asItem(), 4)
                .pattern("S  ")
                .pattern("SS ")
                .pattern("SSS")
                .define('S', stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);
        // 楼梯切石机：1 石块 → 1 楼梯
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(stone), RecipeCategory.BUILDING_BLOCKS,
                        RegisterBlock.HALL_STONE_STAIRS.get().asItem(), 1)
                .unlockedBy(unlock, has(stone))
                .save(consumer, "hall:stone_stairs_from_stonecutting");

        // 台阶：3 石块 → 6 台阶
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, RegisterBlock.HALL_STONE_SLAB.get().asItem(), 6)
                .pattern("SSS")
                .define('S', stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);
        // 台阶切石机：1 石块 → 2 台阶
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(stone), RecipeCategory.BUILDING_BLOCKS,
                        RegisterBlock.HALL_STONE_SLAB.get().asItem(), 2)
                .unlockedBy(unlock, has(stone))
                .save(consumer, "hall:stone_slab_from_stonecutting");

        // 石墙：6 石块 → 6 墙
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RegisterBlock.HALL_STONE_WALL.get().asItem(), 6)
                .pattern("SSS")
                .pattern("SSS")
                .define('S', stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);
        // 石墙切石机：1 石块 → 1 墙
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(stone), RecipeCategory.DECORATIONS,
                        RegisterBlock.HALL_STONE_WALL.get().asItem(), 1)
                .unlockedBy(unlock, has(stone))
                .save(consumer, "hall:stone_wall_from_stonecutting");

        // 按钮：1 石块 → 1 按钮（无定形）
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, RegisterBlock.HALL_STONE_BUTTON.get().asItem(), 1)
                .requires(stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);

        // 压力板：2 石块 → 1 压力板
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, RegisterBlock.HALL_STONE_PRESSURE_PLATE.get().asItem(), 1)
                .pattern("SS")
                .define('S', stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);
    }

    private void addDomeriteToolRecipes(Consumer<FinishedRecipe> consumer) {
        Item ingot = RegisterItem.DOMERITE_INGOT.get();
        Item crystal = RegisterItem.DOMITE_CRYSTAL.get();

        // 剑
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, RegisterItem.DOMERITE_SWORD.get())
                .pattern(" I ")
                .pattern(" I ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

        // 镐
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_PICKAXE.get())
                .pattern("III")
                .pattern(" C ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

        // 斧（右手版）
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_AXE.get())
                .pattern("II ")
                .pattern("IC ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

        // 斧（左手版）
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_AXE.get())
                .pattern(" II")
                .pattern(" CI")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer, "hall:domerite_axe_left");

        // 锹
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_SHOVEL.get())
                .pattern(" I ")
                .pattern(" C ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

        // 锄
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_HOE.get())
                .pattern("II ")
                .pattern(" C ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

        // 锄（左右反转版）
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.DOMERITE_HOE.get())
                .pattern(" II")
                .pattern(" C ")
                .pattern(" C ")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer, "hall:domerite_hoe_left");
    }

    /**
     * 为一个输入物品生成烧炼（smelting）和高炉（blasting）配方。
     * 配方 ID 自动根据输出物品的注册名生成，例如：
     *   - hall:domite_crystal_from_smelting
     *   - hall:domite_crystal_from_blasting
     * 解锁条件使用输入物品的名称。
     *
     * @param consumer   配方消费者
     * @param input      输入物品（矿石）
     * @param output     输出物品（晶体/锭）
     * @param exp        经验值
     * @param smeltTime  烧炼时间（tick）
     * @param blastTime  高炉时间（tick）
     */
    private void addCookingRecipes(Consumer<FinishedRecipe> consumer,
                                   Item input,
                                   Item output,
                                   float exp,
                                   int smeltTime,
                                   int blastTime) {
        String inputName = BuiltInRegistries.ITEM.getKey(input).getPath();
        String outputName = BuiltInRegistries.ITEM.getKey(output).getPath();

        // 烧炼配方
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(input),
                        RecipeCategory.MISC,
                        output,
                        exp,
                        smeltTime
                )
                .unlockedBy("has_" + inputName, has(input))
                .save(consumer, "hall:" + outputName + "_from_smelting");

        // 高炉配方（速度加倍）
        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(input),
                        RecipeCategory.MISC,
                        output,
                        exp,
                        blastTime
                )
                .unlockedBy("has_" + inputName, has(input))
                .save(consumer, "hall:" + outputName + "_from_blasting");
    }
}