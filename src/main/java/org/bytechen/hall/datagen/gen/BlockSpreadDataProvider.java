package org.bytechen.hall.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.infcore.core.blockspread.BlockSpreadEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Hall 扩散规则数据生成。
 * <p>
 * 只需在对应数组里加 vanilla 方块，循环会自动生成传播条目。
 * 多个 vanilla 方块 → 同一个 hall 方块时，只需把 vanilla 方块加进数组即可。
 */
public class BlockSpreadDataProvider extends org.bytechen.infcore.core.datagen.BlockSpreadDataProvider {
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
