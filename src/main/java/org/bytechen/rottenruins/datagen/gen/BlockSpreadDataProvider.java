package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Hall 扩散规则数据生成。
 * <p>
 * 只需在对应数组里加 vanilla 方块，循环会自动生成传播条目。
 * 多个 vanilla 方块 → 同一个 hall 方块时，只需把 vanilla 方块加进数组即可。
 */
public class BlockSpreadDataProvider extends org.bytechen.rottenruins.datagen.infection.BlockSpreadDataProvider {
    public static final String SPREAD_KEY_DEFAULT = "spread";

    /** 传播到 hall_grass 的 vanilla 方块 */
    private static final Block[] GRASS_SOURCES = {
            Blocks.GRASS_BLOCK,
    };
    private static final Block[] STONE_SOURCES = {
            Blocks.STONE,
    };


    /** 传播到 hall_dirt 的 vanilla 方块 */
    private static final Block[] DIRT_SOURCES = {
            Blocks.DIRT,
    };

    /** 传播到 hall_log 的 vanilla 方块（所有原木变种） */
    private static final Block[] LOG_SOURCES = {
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
            Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
    };

    /** 传播到 hall_leaves 的 vanilla 方块（所有树叶变种） */
    private static final Block[] LEAVES_SOURCES = {
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES,
    };

    /** 传播到 hall_grass（王庭草植物）的 vanilla 方块 */
    private static final Block[] GRASS_PLANT_SOURCES = {
            Blocks.GRASS, Blocks.TALL_GRASS,
    };

    /** 传播到 hall_flower 的 vanilla 方块（花类） */
    private static final Block[] FLOWER_SOURCES = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID,
            Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
            Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
    };

    private static final Block[] PLANKS_SOURCES = {
            Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
            Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS,
    };

    /**
     * 王庭可以反向覆盖的腐化方块：腐化地被王庭扩散时改回对应的王庭方块。
     * <p>腐化 → 王庭这一步只用 {@code spread} 类型登记，腐化自己那套 {@code corruption}
     * 规则不受影响；两边各自调 {@code applySpread} 时可以互相翻转，谁扩散到谁就归谁。</p>
     */
    private static final Block[] CORRUPTION_GRASS_SOURCES = {CorruptionBlocks.CORRUPTION_GRASS.get()};
    private static final Block[] CORRUPTION_DIRT_SOURCES = {CorruptionBlocks.SPREAD_DIRT.get()};
    private static final Block[] CORRUPTION_STONE_SOURCES = {
            CorruptionBlocks.SPREAD_STONE.get(), CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get()};
    private static final Block[] CORRUPTION_LOG_SOURCES = {CorruptionBlocks.SPREAD_INFECTION_WOOD.get()};
    private static final Block[] CORRUPTION_LEAVES_SOURCES = {CorruptionBlocks.SPREAD_INFECTION_LEAVES.get()};
    private static final Block[] CORRUPTION_PLANT_SOURCES = {CorruptionBlocks.VARIABLE_HEIGHT_CROP.get()};

    public BlockSpreadDataProvider(PackOutput packOutput) {
        super(packOutput, HallMod.MODID, "default");
    }

    @Override
    public List<BlockSpreadEntry> buildEntries() {
        List<BlockSpreadEntry> entries = new ArrayList<>();

        addMapping(entries, GRASS_SOURCES,  RegisterBlock.HALL_GRASS_BLOCK.get());
        addMapping(entries, DIRT_SOURCES,   RegisterBlock.HALL_DIRT.get());
        addMapping(entries, LOG_SOURCES,    RegisterBlock.HALL_LOG.get());
        addMapping(entries, LEAVES_SOURCES, RegisterBlock.HALL_LEAVES.get());
        addMapping(entries, FLOWER_SOURCES, RegisterBlock.HALL_FLOWER.get());
        addMapping(entries, GRASS_PLANT_SOURCES, RegisterBlock.HALL_GRASS.get());
        addMapping(entries, PLANKS_SOURCES, RegisterBlock.HALL_PLANKS.get());
        addMapping(entries, STONE_SOURCES, RegisterBlock.HALL_STONE.get());

        // 王庭感染腐化：腐化方块被 spread 类型扩散时改回王庭方块。
        addMapping(entries, CORRUPTION_GRASS_SOURCES, RegisterBlock.HALL_GRASS_BLOCK.get());
        addMapping(entries, CORRUPTION_DIRT_SOURCES, RegisterBlock.HALL_DIRT.get());
        addMapping(entries, CORRUPTION_STONE_SOURCES, RegisterBlock.HALL_STONE.get());
        addMapping(entries, CORRUPTION_LOG_SOURCES, RegisterBlock.HALL_LOG.get());
        addMapping(entries, CORRUPTION_LEAVES_SOURCES, RegisterBlock.HALL_LEAVES.get());
        addMapping(entries, CORRUPTION_PLANT_SOURCES, RegisterBlock.HALL_GRASS.get());

        return entries;
    }

    /** 将一组 vanilla 方块全部映射到同一个 hall 目标方块 */
    private void addMapping(List<BlockSpreadEntry> entries, Block[] sources, Block target) {
        for (Block source : sources) {
            entries.add(entry(SPREAD_KEY_DEFAULT, source, false,
                    List.of(target(target, 100))));
        }
    }
}
