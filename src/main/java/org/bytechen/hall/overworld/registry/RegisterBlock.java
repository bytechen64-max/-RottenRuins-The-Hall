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
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.hall.overworld.registry.blocks.impl.HallVineBlock;
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

    public static RegistryObject<Block> registerPillarBlock(String name) {
        return registerBlock(name,
                () -> new RotatedPillarBlock(HallBaseBlock.createBlockProps()),
                new Item.Properties());
    }

    // ==================== impl ====================


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

    // 树叶：终端方块不扩散
    public static RegistryObject<Block> HALL_LEAVES = registerBlockWithItem("hall_leaves",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)
                    .noOcclusion()
                    .isSuffocating((s, r, p) -> false)
                    .isViewBlocking((s, r, p) -> false)));



    public static RegistryObject<Block> DOMITE_MINERAL = registerBlockWithItem("domite_mineral",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));
    public static RegistryObject<Block> DOMERITE_MINERAL = registerBlockWithItem("domerite_mineral",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));

    public static RegistryObject<Block> HALL_PLANKS = registerBlockWithItem("hall_planks",
            () -> new HallBaseBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));

    // ---- HALL_PLANKS 建筑方块系列 ----

    public static RegistryObject<Block> HALL_STAIRS = registerBlockWithItem("hall_stairs",
            () -> new StairBlock(() -> HALL_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(Blocks.OAK_STAIRS)));

    public static RegistryObject<Block> HALL_SLAB = registerBlockWithItem("hall_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.OAK_SLAB)));

    public static RegistryObject<Block> HALL_FENCE = registerBlockWithItem("hall_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE)));

    public static RegistryObject<Block> HALL_FENCE_GATE = registerBlockWithItem("hall_fence_gate",
            () -> new FenceGateBlock(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE_GATE),
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
            () -> new Block(BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .sound(net.minecraft.world.level.block.SoundType.GRASS)
                    .noOcclusion()));

    public static RegistryObject<Block> HALL_FLOWER = registerBlockWithItem("hall_flower",
            () -> new Block(BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .sound(net.minecraft.world.level.block.SoundType.GRASS)
                    .noOcclusion()));


}
