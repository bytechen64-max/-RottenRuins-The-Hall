package org.bytechen.rottenruins.datagen.gen.corruption;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bytechen.infcore.core.blockspread.BlockSpreadEntry;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 腐化方块扩散规则（{@code rotten_ruins:corruption} 类型）。
 *
 * <p>供一阶召唤柱/腐化群系使用：把脚下方块逐步感染成腐化方块。
 * 输出 {@code data/rotten_ruins/infcore_blockspread/corruption.json}。</p>
 */
public class CorruptionBlockSpreadDataProvider extends org.bytechen.infcore.core.datagen.BlockSpreadDataProvider {

    public static final String SPREAD_TYPE = "corruption";

    private static final Block[] GRASS_SOURCES = {Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM};
    private static final Block[] DIRT_SOURCES = {Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY};
    private static final Block[] STONE_SOURCES = {Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE,
            Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.CALCITE};
    private static final Block[] COBBLE_SOURCES = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS, Blocks.BRICKS};
    private static final Block[] LEAVES_SOURCES = {
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES,
            Blocks.CHERRY_LEAVES, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES};
    private static final Block[] LOG_SOURCES = {
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG};
    private static final Block[] PLANT_SOURCES = {
            Blocks.GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY};

    public CorruptionBlockSpreadDataProvider(PackOutput packOutput) {
        super(packOutput, HallMod.MODID, "corruption");
    }

    @Override
    public List<BlockSpreadEntry> buildEntries() {
        List<BlockSpreadEntry> entries = new ArrayList<>();
        addMapping(entries, GRASS_SOURCES, CorruptionBlocks.SPREAD_DIRT.get());
        addMapping(entries, DIRT_SOURCES, CorruptionBlocks.SPREAD_DIRT.get());
        addMapping(entries, STONE_SOURCES, CorruptionBlocks.SPREAD_STONE.get());
        addMapping(entries, COBBLE_SOURCES, CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get());
        addMapping(entries, LEAVES_SOURCES, CorruptionBlocks.SPREAD_INFECTION_LEAVES.get());
        addMapping(entries, LOG_SOURCES, CorruptionBlocks.SPREAD_INFECTION_WOOD.get());
        addMapping(entries, PLANT_SOURCES, CorruptionBlocks.VARIABLE_HEIGHT_CROP.get());
        return entries;
    }

    private void addMapping(List<BlockSpreadEntry> entries, Block[] sources, Block target) {
        for (Block source : sources) {
            entries.add(entry(SPREAD_TYPE, source, false,
                    List.of(target(target, 100))));
        }
    }
}
