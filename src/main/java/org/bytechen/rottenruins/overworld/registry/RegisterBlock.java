package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blocks.base.HallBaseBlock;
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
import org.bytechen.rottenruins.overworld.registry.blocks.impl.DeathCoreBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.GroundingStakeBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceWireBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SourceMachineBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.PotentialCraftingTableBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.FleshRiftBlock;
import org.bytechen.rottenruins.overworld.registry.items.DeathCoreBlockItem;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallAshCactusBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallDeadBushBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallFlowerBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallGrassBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallVineBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallWoodBlocks;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SpreadBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.SpreadPillarBlock;

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
     * 注册方块 + 自定义方块物品（如 {@code DeathCoreBlockItem}）：物品工厂在注册事件触发时才
     * 求值，所以 {@code block} 可以安全地引用方块注册对象。
     */
    public static <T extends Block> RegistryObject<T> registerBlockWithItem(
            String name, Supplier<T> block, java.util.function.Function<T, Item> itemFactory) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        RegisterItem.ITEMS.register(name, () -> itemFactory.apply(toReturn.get()));
        return toReturn;
    }

    /** 王庭石质柱状方块：属性直接复制原版石头（1.5 硬度 / 6.0 抗爆，需镐才掉落）。 */
    public static RegistryObject<Block> registerPillarBlock(String name) {
        return registerBlock(name,
                () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STONE)),
                new Item.Properties());
    }

    public static final RegistryObject<SourceMachineBlock> SOURCE_FILTER=registerBlockWithItem("source_filter",()->
            new SourceMachineBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(2.5F).noOcclusion(),false));
    public static final RegistryObject<SourceMachineBlock> SOURCE_COLLECTOR=registerBlockWithItem("source_collector",()->
            new SourceMachineBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(2.5F).noOcclusion(),true));

    /**
     * <b>势能合成台</b>：16 格环形材料位 + 圆心产物位。继承 {@link SourceMachineBlock}，
     * 因此自动成为势能电路里的一个设备（{@code SourceWireNetwork} 往它的 {@code POWER} 写强度）。
     */
    public static final RegistryObject<PotentialCraftingTableBlock> POTENTIAL_CRAFTING_TABLE =
            registerBlockWithItem("potential_crafting_table",
                    () -> new PotentialCraftingTableBlock(
                            BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                                    .strength(3.5F, 6.0F)
                                    .noOcclusion()));

    public static final RegistryObject<GroundingStakeBlock> GROUNDING_STAKE =
            registerBlockWithItem("grounding_stake", () -> new GroundingStakeBlock(
                    BlockBehaviour.Properties.copy(Blocks.LIGHTNING_ROD).noOcclusion().strength(1.5F)));

    public static final RegistryObject<SourceWireBlock> SOURCE_WIRE =
            registerBlockWithItem("source_wire", () -> new SourceWireBlock(
                    BlockBehaviour.Properties.copy(Blocks.REDSTONE_WIRE).noCollission().noOcclusion()
                            .lightLevel(state -> state.getValue(SourceWireBlock.POWER))));

    /** 王庭石柱：可旋转柱状石块（{@code axis}），合成与挖掘属性对齐原版石质方块。 */
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

    // HALL_PLANKS 建筑方块系列：用 HallWoodBlocks 的可燃变体（燃烧值对齐原版橡木 5/20）；
    // 门 / 活板门 / 按钮 / 压力板原版就不可燃，仍用原版类。

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

    /**
     * 血肉裂隙：往返血肉庭园维度（hall:heall）的通道 —— 该维度地形完全复用王庭方块族，
     * 只新增这一个方块。右键传送：主世界使用记录落点，血肉庭园使用送回该落点。
     */
    public static RegistryObject<Block> FLESH_RIFT = registerBlockWithItem("flesh_rift",
            () -> new FleshRiftBlock(BlockBehaviour.Properties.copy(Blocks.NETHERRACK)
                    .strength(2.0F, 1200.0F)
                    .lightLevel(s -> 11)));

    /**
     * 死亡核心：本体是深色实心方块，只在 mask 白层窗口内被替换成体积效果
     * （{@code death_core}：ShaderToy "Ghosts" 改造的 raymarch）。物品走
     * {@link DeathCoreBlockItem}，手持/物品栏里也显示同一层（物品模型 quad）。
     */
    public static RegistryObject<Block> DEATH_CORE = registerBlockWithItem("death_core",
            DeathCoreBlock::new,
            block -> new DeathCoreBlockItem(block, new Item.Properties()));

}
