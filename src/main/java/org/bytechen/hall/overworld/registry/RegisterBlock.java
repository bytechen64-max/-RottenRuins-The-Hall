package org.bytechen.hall.overworld.registry;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.blocks.base.HallBaseBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.hall.overworld.registry.blocks.impl.FleshRiftBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallAshCactusBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallDeadBushBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallFlowerBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallGrassBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallVineBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallWoodBlocks;
import org.bytechen.hall.overworld.registry.blocks.impl.SpreadBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.SpreadPillarBlock;

import java.util.function.Supplier;

public class RegisterBlock {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HallMod.MODID);

    public static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block, Item.Properties itemProperties) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        RegisterItem.ITEMS.register(name, () -> new BlockItem(toReturn.get(), itemProperties));
        return toReturn;
    }

    public static <T extends Block> RegistryObject<T> registerBlockNoItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static RegistryObject<Block> registerSimpleBlock(String name) {
        return registerBlock(name, HallBaseBlock::new, new Item.Properties());
    }

    public static RegistryObject<Block> registerSimpleSpreadBlock(String name) {
        return registerBlock(name, HallBaseBlock::new, new Item.Properties());
    }

    public static RegistryObject<Block> registerSimpleBlockNoItem(String name) {
        return registerBlockNoItem(name, HallBaseBlock::new);
    }

    public static <T extends Block> RegistryObject<T> registerBlockWithItem(String name, Supplier<T> block) {
        return registerBlock(name, block, new Item.Properties());
    }

    /**
     * 注册一个「王庭石质」柱状方块。
     * <p>
     * 属性直接复制原版 {@code minecraft:stone}：1.5 硬度 / 6.0 抗爆、需要正确工具（镐）才掉落，
     * 与 {@link #HALL_STONE} 及其楼梯 / 台阶 / 石墙保持同一套石质手感。
     */
    public static RegistryObject<Block> registerPillarBlock(String name) {
        return registerBlock(name,
                () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STONE)),
                new Item.Properties());
    }

    // ==================== impl ====================


    /**
     * 王庭石柱：可旋转的柱状石块（{@code axis} 属性）。
     * <p>
     * 合成链对齐原版「4 石块 → 4 石砖」：4 王庭石块 → 4 王庭石柱（另有切石机配方）。
     * 挖掘属性也一并对齐原版石质方块 —— 1.5 硬度 / 6.0 抗爆、需要镐才掉落。
     */
    public static RegistryObject<Block> HALL_PILLAR = registerPillarBlock("hall_pillar");


    public static RegistryObject<Block> HALL_GRASS_BLOCK = registerBlockWithItem("hall_grass_block",
            () -> new SpreadBlock(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)
                    .randomTicks()));

    public static RegistryObject<Block> HALL_DIRT = registerBlockWithItem("hall_dirt",
            () -> new SpreadBlock(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .randomTicks()));



    public static RegistryObject<Block> HALL_STONE = registerBlockWithItem("hall_stone",
            () -> new SpreadBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .randomTicks()));

    // 可扩散原木：RotatedPillarBlock（可旋转）+ ISpreadBlock（可扩散）
    public static RegistryObject<Block> HALL_LOG = registerBlockWithItem("hall_log",
            () -> new SpreadPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)
                    .randomTicks()));

    // 树叶：终端方块不扩散；燃烧特性对齐原版树叶
    public static RegistryObject<Block> HALL_LEAVES = registerBlockWithItem("hall_leaves",
            () -> new HallWoodBlocks.Leaves(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)
                    .noOcclusion()
                    .isSuffocating((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false)));



    public static RegistryObject<Block> DOMITE_MINERAL = registerBlockWithItem("domite_mineral",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));
    public static RegistryObject<Block> DOMERITE_MINERAL = registerBlockWithItem("domerite_mineral",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));

    public static RegistryObject<Block> HALL_PLANKS = registerBlockWithItem("hall_planks",
            () -> new HallWoodBlocks.Planks(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));

    // ---- HALL_PLANKS 建筑方块系列 ----
    //
    // 全部使用 HallWoodBlocks 里的「可燃变体」：燃烧数值与原版橡木系列一一对应
    // （木板族 5 / 20）。原版的木门 / 活板门 / 按钮 / 压力板本身不可燃，因此这几个
    // 仍用原版类，行为与原版完全一致。

    public static RegistryObject<Block> HALL_STAIRS = registerBlockWithItem("hall_stairs",
            () -> new HallWoodBlocks.Stairs(() -> HALL_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(Blocks.OAK_STAIRS)));

    public static RegistryObject<Block> HALL_SLAB = registerBlockWithItem("hall_slab",
            () -> new HallWoodBlocks.Slab(BlockBehaviour.Properties.copy(Blocks.OAK_SLAB)));

    public static RegistryObject<Block> HALL_FENCE = registerBlockWithItem("hall_fence",
            () -> new HallWoodBlocks.Fence(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE)));

    public static RegistryObject<Block> HALL_FENCE_GATE = registerBlockWithItem("hall_fence_gate",
            () -> new HallWoodBlocks.FenceGate(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE_GATE),
                    HallBlockSetTypes.HALL_WOOD));

    public static RegistryObject<Block> HALL_DOOR = registerBlockWithItem("hall_door",
            () -> new DoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_DOOR)
                    .noOcclusion(), HallBlockSetTypes.HALL));

    public static RegistryObject<Block> HALL_TRAPDOOR = registerBlockWithItem("hall_trapdoor",
            () -> new TrapDoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_TRAPDOOR)
                    .noOcclusion(), HallBlockSetTypes.HALL));

    public static RegistryObject<Block> HALL_BUTTON = registerBlockWithItem("hall_button",
            () -> new ButtonBlock(BlockBehaviour.Properties.copy(Blocks.OAK_BUTTON)
                    .noCollission(),
                    HallBlockSetTypes.HALL, 30, true));

    public static RegistryObject<Block> HALL_PRESSURE_PLATE = registerBlockWithItem("hall_pressure_plate",
            () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING,
                    BlockBehaviour.Properties.copy(Blocks.OAK_PRESSURE_PLATE),
                    HallBlockSetTypes.HALL));

    // ---- HALL_STONE 建筑方块系列（楼梯/台阶/石墙/按钮/压力板）----

    public static RegistryObject<Block> HALL_STONE_STAIRS = registerBlockWithItem("hall_stone_stairs",
            () -> new StairBlock(() -> HALL_STONE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(Blocks.STONE_STAIRS)));

    public static RegistryObject<Block> HALL_STONE_SLAB = registerBlockWithItem("hall_stone_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE_SLAB)));

    public static RegistryObject<Block> HALL_STONE_WALL = registerBlockWithItem("hall_stone_wall",
            () -> new WallBlock(BlockBehaviour.Properties.copy(Blocks.COBBLESTONE_WALL)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)));

    public static RegistryObject<Block> HALL_STONE_BUTTON = registerBlockWithItem("hall_stone_button",
            () -> new ButtonBlock(BlockBehaviour.Properties.copy(Blocks.STONE_BUTTON)
                    .noCollission(),
                    HallBlockSetTypes.HALL_STONE, 20, false));

    public static RegistryObject<Block> HALL_STONE_PRESSURE_PLATE = registerBlockWithItem("hall_stone_pressure_plate",
            () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.MOBS,
                    BlockBehaviour.Properties.copy(Blocks.STONE_PRESSURE_PLATE),
                    HallBlockSetTypes.HALL_STONE));


    public static RegistryObject<Block> HALL_VINE = registerBlockWithItem("hall_vine",
            HallVineBlock::new);

    public static RegistryObject<Block> HALL_GRASS = registerBlockWithItem("hall_grass",
            HallGrassBlock::new);

    public static RegistryObject<Block> HALL_FLOWER = registerBlockWithItem("hall_flower",
            HallFlowerBlock::new);

    // ---- 王庭烬痕沙漠系列 ----

    public static RegistryObject<Block> HALL_SANDSTONE = registerBlockWithItem("hall_sandstone",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.SANDSTONE)));

    public static RegistryObject<Block> HALL_ASH_SAND = registerBlockWithItem("hall_ash_sand",
            () -> new SandBlock(14406560, BlockBehaviour.Properties.copy(Blocks.SAND)));

    public static RegistryObject<Block> HALL_ASH_CUT_SANDSTONE = registerBlockWithItem("hall_ash_cut_sandstone",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.CUT_SANDSTONE)));

    public static RegistryObject<Block> HALL_ASH_SMOOTH_SANDSTONE = registerBlockWithItem("hall_ash_smooth_sandstone",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.SMOOTH_SANDSTONE)));

    public static RegistryObject<Block> HALL_ASH_COLLAPSED_CHISELED_SANDSTONE = registerBlockWithItem("hall_ash_collapsed_chiseled_sandstone",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.CHISELED_SANDSTONE)));

    public static RegistryObject<Block> HALL_ASH_CACTUS = registerBlockWithItem("hall_ash_cactus",
            HallAshCactusBlock::new);

    public static RegistryObject<Block> HALL_ASH_DEAD_BUSH = registerBlockWithItem("hall_ash_dead_bush",
            HallDeadBushBlock::new);

    // ---- 血肉庭园维度（hall:heall）系列 ----
    //
    // 该维度的地形完全复用现有的王庭方块族（王庭草方块 / 王庭泥土 / 王庭石块 /
    // 王庭原木 / 王庭树叶 / 王庭草 / 王庭花 / 王庭石柱 / 王庭烬痕砂岩），
    // 不新增任何方块，唯一新增的是往返两个维度的通道 —— 血肉裂隙。

    /**
     * 血肉裂隙：往返血肉庭园维度的通道。
     * <p>
     * 右键即传送；在主世界使用会记录落点，在血肉庭园使用则送回该落点。
     * 自身发光，便于在昏暗处找到归路。
     */
    public static RegistryObject<Block> FLESH_RIFT = registerBlockWithItem("flesh_rift",
            () -> new FleshRiftBlock(BlockBehaviour.Properties.copy(Blocks.NETHERRACK)
                    .strength(2.0F, 1200.0F)
                    .lightLevel(s -> 11)));

}
