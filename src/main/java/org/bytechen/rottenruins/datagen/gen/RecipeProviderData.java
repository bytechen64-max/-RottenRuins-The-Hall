package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.WandRecipes;
import org.bytechen.rottenruins.HallMod;

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
        addWandRecipes(consumer);
        addPotentialEquipmentRecipes(consumer);
        addChipRecipes(consumer);
        addSourceMachineRecipes(consumer);
        addManaPlateRecipes(consumer);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RegisterBlock.SOURCE_WIRE.get())
                .requires(RegisterItem.SOURCE_CHARGE_SAND.get(), 2)
                .unlockedBy("has_source_charge_sand", has(RegisterItem.SOURCE_CHARGE_SAND.get()))
                .save(consumer);
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

        // ── 王庭烬痕砂岩系列合成 ──
        addSandstoneVariantRecipes(consumer);

        // ── 血肉裂隙：通往血肉庭园的门 ──
        addFleshRiftRecipe(consumer);

        // ── 死亡之星：下界之星 + 一圈凋零玫瑰 ──
        addDeathStarRecipe(consumer);
    }

    private void addManaPlateRecipes(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,RegisterItem.SOURCE_MANA_PLATE.get())
                .define('C',Items.COPPER_INGOT).define('I',Items.IRON_INGOT)
                .define('S',RegisterItem.SOURCE_CHARGE_SAND.get())
                .pattern(" C ").pattern("ISI").pattern(" C ")
                .unlockedBy("has_source_charge_sand",has(RegisterItem.SOURCE_CHARGE_SAND.get())).save(consumer);
        addInfusedManaPlateRecipe(consumer,RegisterItem.LIGHT_MANA_PLATE.get(),RegisterItem.LIGHT_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.FIRE_MANA_PLATE.get(),RegisterItem.FIRE_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.WATER_MANA_PLATE.get(),RegisterItem.WATER_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.NATURE_MANA_PLATE.get(),RegisterItem.NATURE_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.DEATH_MANA_PLATE.get(),RegisterItem.DEATH_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.VOID_MANA_PLATE.get(),RegisterItem.VOID_MANA_BOTTLE.get());
        addInfusedManaPlateRecipe(consumer,RegisterItem.MYSTERIOUS_MANA_PLATE.get(),RegisterItem.MYSTERIOUS_MANA_BOTTLE.get());
    }

    /** 八个同系储魔瓶包围一块源荷板材，产出一块对应板材。 */
    private void addInfusedManaPlateRecipe(Consumer<FinishedRecipe> consumer,Item result,Item bottle) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,result)
                .define('P',RegisterItem.SOURCE_MANA_PLATE.get()).define('B',bottle)
                .pattern("BBB").pattern("BPB").pattern("BBB")
                .unlockedBy("has_source_mana_plate",has(RegisterItem.SOURCE_MANA_PLATE.get()))
                .unlockedBy("has_element_bottle",has(bottle)).save(consumer);
    }

    private void addSourceMachineRecipes(Consumer<FinishedRecipe> consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,RegisterItem.MANA_BOTTLE.get())
                .requires(Items.GLASS_BOTTLE).requires(Items.COPPER_INGOT).requires(RegisterItem.SOURCE_CHARGE_SAND.get())
                .unlockedBy("has_source_charge_sand",has(RegisterItem.SOURCE_CHARGE_SAND.get())).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,RegisterBlock.SOURCE_FILTER.get())
                .define('I',Items.IRON_INGOT).define('G',Items.GLASS).define('C',Items.COPPER_INGOT)
                .define('S',RegisterItem.CHIP_SCAN.get()).define('W',RegisterBlock.SOURCE_WIRE.get())
                .pattern("IGI").pattern("CSC").pattern("IWI")
                .unlockedBy("has_chip_scan",has(RegisterItem.CHIP_SCAN.get())).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,RegisterBlock.SOURCE_COLLECTOR.get())
                .define('I',Items.IRON_INGOT).define('G',Items.GLASS).define('C',Items.COPPER_INGOT)
                .define('E',RegisterItem.CHIP_EXTRACT.get()).define('H',Items.HOPPER)
                .pattern("IGI").pattern("CEC").pattern(" H ")
                .unlockedBy("has_chip_extract",has(RegisterItem.CHIP_EXTRACT.get())).save(consumer);
    }

    private void addPotentialEquipmentRecipes(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RegisterBlock.GROUNDING_STAKE.get())
                .define('S', RegisterItem.SOURCE_CHARGE_SAND.get())
                .define('I', Items.IRON_INGOT)
                .define('T', Items.STICK)
                .pattern("S").pattern("I").pattern("T")
                .unlockedBy("has_source_charge_sand", has(RegisterItem.SOURCE_CHARGE_SAND.get()))
                .save(consumer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.SIMPLE_FIELD_METER.get())
                .define('G', Items.GLASS)
                .define('S', RegisterItem.SOURCE_CHARGE_SAND.get())
                .define('C', Items.COPPER_INGOT)
                .define('I', Items.IRON_INGOT)
                .pattern("GGG").pattern("SCS").pattern("III")
                .unlockedBy("has_source_charge_sand", has(RegisterItem.SOURCE_CHARGE_SAND.get()))
                .save(consumer);
    }

    private void addChipRecipes(Consumer<FinishedRecipe> consumer) {
        // 蓝色空白芯片是三种功能芯片的共同基底。
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, RegisterItem.CHIP_NULL.get())
                .requires(RegisterItem.SOURCE_CHARGE_SAND.get())
                .requires(Items.COPPER_INGOT)
                .requires(Items.REDSTONE)
                .unlockedBy("has_source_charge_sand", has(RegisterItem.SOURCE_CHARGE_SAND.get()))
                .save(consumer);
        // 增强：金质导体与红石电路围绕空白芯片布置。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RegisterItem.CHIP_ENHANCED.get())
                .define('C', RegisterItem.CHIP_NULL.get())
                .define('G', Items.GOLD_INGOT)
                .define('R', Items.REDSTONE)
                .pattern(" R ").pattern("GCG").pattern(" R ")
                .unlockedBy("has_chip_null", has(RegisterItem.CHIP_NULL.get()))
                .save(consumer);

        // 扫描：紫水晶与石英作为感应材料，指南针作为定位部件。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RegisterItem.CHIP_SCAN.get())
                .define('C', RegisterItem.CHIP_NULL.get())
                .define('A', Items.AMETHYST_SHARD)
                .define('Q', Items.QUARTZ)
                .define('P', Items.COMPASS)
                .pattern(" A ").pattern("QCQ").pattern(" P ")
                .unlockedBy("has_chip_null", has(RegisterItem.CHIP_NULL.get()))
                .save(consumer);

        // 提取：漏斗与活塞代表收集和搬运部件，铁锭构成外壳。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RegisterItem.CHIP_EXTRACT.get())
                .define('C', RegisterItem.CHIP_NULL.get())
                .define('H', Items.HOPPER)
                .define('I', Items.IRON_INGOT)
                .define('P', Items.PISTON)
                .pattern(" H ").pattern("ICI").pattern(" P ")
                .unlockedBy("has_chip_null", has(RegisterItem.CHIP_NULL.get()))
                .save(consumer);
    }

    private void addWandRecipes(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, RegisterItem.WAND.get())
                .define('I', Items.IRON_INGOT).define('N', Items.NETHER_STAR)
                .pattern(" I ").pattern(" N ").pattern(" I ")
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(consumer);

        for (String id : new String[]{"wand_fire", "wand_death", "wand_light",
                "wand_nature", "wand_void", "wand_water", "wand_mysterious", "wand_common"}) {
            SpecialRecipeBuilder.special(WandRecipes.WAND_CRAFTING.get())
                    .save(consumer, HallMod.MODID + ":" + id);
            SpecialRecipeBuilder.special(WandRecipes.WAND_CRAFTING.get())
                    .save(consumer, HallMod.MODID + ":" + id + "_recharge");
        }
    }

    /**
     * 血肉裂隙配方。
     * <p>
     * 用「穹顶胚晶 + 王庭骨」拼成 —— 两种材料都来自现有的战斗循环
     * （穹顶矿 + 碎骨掉落），因此不需要为开门额外新增资源链。
     * 阵型参考原版末地传送门的「虚空感」：中心放胚晶，四角放骨。
     */
    private void addFleshRiftRecipe(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RegisterBlock.FLESH_RIFT.get().asItem())
                .define('B', RegisterItem.HALL_BONE_FRAGMENTS.get())
                .define('C', RegisterItem.DOMITE_CRYSTAL.get())
                .pattern("B B")
                .pattern(" C ")
                .pattern("B B")
                .unlockedBy("has_domite_crystal", has(RegisterItem.DOMITE_CRYSTAL.get()))
                .unlockedBy("has_bone_fragments", has(RegisterItem.HALL_BONE_FRAGMENTS.get()))
                .save(consumer);
    }

    /**
     * 为「王庭烬痕沙漠系列」的砂岩变体补齐配方。
     * <p>
     * 对照原版砂岩全家桶（{@code data/minecraft/recipes/}）逐条对齐：
     * <ul>
     *   <li>{@code sandstone}        ← 4 × sand（2×2）</li>
     *   <li>{@code cut_sandstone}    ← 4 × sandstone（2×2）</li>
     *   <li>{@code chiseled_sandstone} ← 2 × sandstone_slab（竖向 1×2）</li>
     *   <li>{@code smooth_sandstone} ← 烧炼 {@code sandstone}（0.1 exp / 200 tick）</li>
     *   <li>切石机：{@code sandstone} → {@code cut / chiseled / smooth}（各 1 个，方便批量出料）</li>
     * </ul>
     * 与「王庭烬痕沙子 / 王庭砂岩」互为主副材料，玩家不必先去找原版沙漠。
     * <p>
     * 注意：模组里没有自定义砂岩台阶，{@code chiseled} 因此沿用原版
     * {@code minecraft:sandstone_slab} 作为材料（与原版配方一致）。
     */
    private void addSandstoneVariantRecipes(Consumer<FinishedRecipe> consumer) {
        Block sand = RegisterBlock.HALL_ASH_SAND.get();
        Block sandstone = RegisterBlock.HALL_SANDSTONE.get();
        Block cutSandstone = RegisterBlock.HALL_ASH_CUT_SANDSTONE.get();
        Block smoothSandstone = RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get();
        Block chiseledSandstone = RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get();

        // 王庭烬痕沙子 ×4 → 王庭砂岩
        // 配方 ID 与输出物品同名（hall:hall_sandstone），所以不传自定义 ID
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, sandstone)
                .pattern("##")
                .pattern("##")
                .define('#', sand)
                .unlockedBy("has_hall_ash_sand", has(sand))
                .save(consumer);

        // 王庭砂岩 ×4 → 王庭切制砂岩
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, cutSandstone, 4)
                .pattern("##")
                .pattern("##")
                .define('#', sandstone)
                .unlockedBy("has_hall_sandstone", has(sandstone))
                .save(consumer);

        // 原版砂岩 ×4 → 王庭切制砂岩（废墟里捡到原版砂岩也能用）
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, cutSandstone, 4)
                .pattern("##")
                .pattern("##")
                .define('#', Blocks.SANDSTONE)
                .unlockedBy("has_sandstone", has(Blocks.SANDSTONE))
                .save(consumer, "rotten_ruins:hall_ash_cut_sandstone_from_sandstone");

        // 砂岩台阶 ×2 → 王庭塌陷雕纹砂岩（竖向摆放，与原版雕纹砂岩配方一致）
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, chiseledSandstone)
                .pattern("#")
                .pattern("#")
                .define('#', Blocks.SANDSTONE_SLAB)
                .unlockedBy("has_hall_ash_cut_sandstone", has(cutSandstone))
                .save(consumer);

        // 烧炼王庭砂岩 → 王庭平滑砂岩
        // 与原版 smooth_sandstone.json 同名（默认 ID：hall:hall_ash_smooth_sandstone）
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(sandstone), RecipeCategory.BUILDING_BLOCKS,
                        smoothSandstone, 0.1F, 200)
                .unlockedBy("has_hall_sandstone", has(sandstone))
                .save(consumer);

        // 烧炼原版砂岩 → 王庭平滑砂岩（与原版 smooth_sandstone 同源，走自定义 ID 区分）
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(Blocks.SANDSTONE), RecipeCategory.BUILDING_BLOCKS,
                        smoothSandstone, 0.1F, 200)
                .unlockedBy("has_sandstone", has(Blocks.SANDSTONE))
                .save(consumer, "rotten_ruins:hall_ash_smooth_sandstone_from_sandstone_smelting");

        // 切石机：王庭砂岩 → 切制 / 雕纹 / 平滑（各 1 个）
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(sandstone), RecipeCategory.BUILDING_BLOCKS,
                        cutSandstone, 1)
                .unlockedBy("has_hall_sandstone", has(sandstone))
                .save(consumer, "rotten_ruins:hall_ash_cut_sandstone_from_stonecutting");

        SingleItemRecipeBuilder.stonecutting(Ingredient.of(sandstone), RecipeCategory.BUILDING_BLOCKS,
                        chiseledSandstone, 1)
                .unlockedBy("has_hall_sandstone", has(sandstone))
                .save(consumer, "rotten_ruins:hall_ash_collapsed_chiseled_sandstone_from_stonecutting");

        SingleItemRecipeBuilder.stonecutting(Ingredient.of(sandstone), RecipeCategory.BUILDING_BLOCKS,
                        smoothSandstone, 1)
                .unlockedBy("has_hall_sandstone", has(sandstone))
                .save(consumer, "rotten_ruins:hall_ash_smooth_sandstone_from_stonecutting");
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

        // 石柱：4 石块 → 4 石柱（2×2）
        // 对应原版「4 石块 → 4 石砖」这一步，让王庭石也有「基础石 → 细加工石」的合成层级
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, RegisterBlock.HALL_PILLAR.get().asItem(), 4)
                .pattern("SS")
                .pattern("SS")
                .define('S', stone)
                .unlockedBy(unlock, has(stone))
                .save(consumer);

        // 石柱切石机：1 石块 → 1 石柱
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(stone), RecipeCategory.BUILDING_BLOCKS,
                        RegisterBlock.HALL_PILLAR.get().asItem(), 1)
                .unlockedBy(unlock, has(stone))
                .save(consumer, "rotten_ruins:hall_pillar_from_stonecutting");

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
                .save(consumer, "rotten_ruins:stone_stairs_from_stonecutting");

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
                .save(consumer, "rotten_ruins:stone_slab_from_stonecutting");

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
                .save(consumer, "rotten_ruins:stone_wall_from_stonecutting");

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
        Item stick = RegisterItem.DOMERITE_STICK.get();

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RegisterItem.DOMERITE_STICK.get())
                .pattern("CIC")
                .pattern("CIC")
                .pattern("CIC")
                .define('I', ingot)
                .define('C', crystal)
                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);

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
                .save(consumer, "rotten_ruins:domerite_axe_left");

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
                .save(consumer, "rotten_ruins:domerite_hoe_left");

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, RegisterItem.DOMERITE_LONGSWORD.get())
                .pattern("III")
                .pattern("CSI")
                .pattern("CS ")
                .define('I', ingot)
                .define('C', crystal)
                .define('S', stick)

                .unlockedBy("has_domerite_ingot", has(ingot))
                .save(consumer);
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
                .save(consumer, "rotten_ruins:" + outputName + "_from_smelting");

        // 高炉配方（速度加倍）
        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(input),
                        RecipeCategory.MISC,
                        output,
                        exp,
                        blastTime
                )
                .unlockedBy("has_" + inputName, has(input))
                .save(consumer, "rotten_ruins:" + outputName + "_from_blasting");
    }

    /**
     * 死亡之星配方。
     * <p>
     * 中心一颗下界之星，四周八朵凋零玫瑰（3×3 全包）。
     */
    private void addDeathStarRecipe(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, RegisterItem.DEATH_STAR.get())
                .define('W', Items.WITHER_ROSE)
                .define('N', Items.NETHER_STAR)
                .pattern("WWW")
                .pattern("WNW")
                .pattern("WWW")
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(consumer);
    }
}
