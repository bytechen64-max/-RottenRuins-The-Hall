package org.bytechen.rottenruins.overworld.registry.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;

/**
 * 王庭树 —— 由王庭原木 + 王庭树叶构成的树木地物：4~8 格主干配 7×7 伞状树冠，下层边缘随机剔除一格。
 * 不用原版 minecraft:tree：那套配置要求 dirt_provider、树干/树叶放置器成套，用自定义树叶容易踩「树叶被判不可存活而掉落」的坑。
 */
public class HallTreeFeature extends Feature<NoneFeatureConfiguration> {

    /** 树干高度范围（含）。 */
    private static final int TRUNK_MIN = 5;
    private static final int TRUNK_MAX = 8;

    /** 树冠半径。半径 2 = 5×5。 */
    private static final int LEAF_RADIUS = 2;

    // 取 6 而非更小值：要跨出本区块才能看到邻块的树（扫描区 13×13）；邻块可见性不可靠，只能尽力而为。
    private static final int MIN_TREE_SPACING = 6;

    /** 间距扫描时向下延伸的格数（覆盖附近较低地表的树冠）。 */
    private static final int SCAN_DOWN = 4;

    /** UPDATE_CLIENTS 让客户端看到树；UPDATE_KNOWN_SHAPE 跳过形状更新，避免生成期触发大量邻居更新。 */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public HallTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState log = RegisterBlock.HALL_LOG.get().defaultBlockState();
        BlockState leaves = RegisterBlock.HALL_LEAVES.get().defaultBlockState();

        int trunkHeight = TRUNK_MIN + random.nextInt(TRUNK_MAX - TRUNK_MIN + 1);

        BlockPos below = origin.below();

        // origin 由 HeightmapPlacement 给出，指向地表之上第一个空气格：下方必须实心、origin 必须为空。
        // 这里曾把第二个判断写反导致树从未生成，改动前请确认逻辑方向。
        if (level.isEmptyBlock(below)) {
            return false;
        }
        if (!level.isEmptyBlock(origin)) {
            return false;
        }

        // 间距校验：InSquarePlacement 只保证基点散开，不阻止两棵树贴太近，而树冠半径远大于基点间距。
        if (hasNearbyTree(level, origin)) {
            return false;
        }

        for (int dy = 0; dy < trunkHeight; dy++) {
            BlockPos trunkPos = origin.above(dy);
            if (!level.isEmptyBlock(trunkPos) && !level.getBlockState(trunkPos).canBeReplaced()) {
                // 被挡住就整体放弃，避免生成半截树
                return false;
            }
            level.setBlock(trunkPos, log, SET_FLAGS);
        }

        // 树冠：自下而上两层宽、两层窄，末梢补一格成伞形
        int topY = trunkHeight;
        for (int dy = -2; dy <= -1; dy++) {
            placeLeafLayer(level, random, origin, leaves, topY + dy, LEAF_RADIUS);
        }
        for (int dy = 0; dy <= 1; dy++) {
            placeLeafLayer(level, random, origin, leaves, topY + dy, LEAF_RADIUS - 1);
        }
        BlockPos tip = origin.above(topY + 2);
        if (level.isEmptyBlock(tip)) {
            level.setBlock(tip, leaves, SET_FLAGS);
        }

        return true;
    }

    /** 检查附近是否已有王庭树（树干<b>或树叶</b>）：树冠在树干之上、最高到 origin + TRUNK_MAX + 2，故必须扫到树冠高度且树叶算命中，否则树冠会压上邻树。 */
    private static boolean hasNearbyTree(WorldGenLevel level, BlockPos origin) {
        BlockState logState = RegisterBlock.HALL_LOG.get().defaultBlockState();
        BlockState leafState = RegisterBlock.HALL_LEAVES.get().defaultBlockState();

        for (int dx = -MIN_TREE_SPACING; dx <= MIN_TREE_SPACING; dx++) {
            for (int dz = -MIN_TREE_SPACING; dz <= MIN_TREE_SPACING; dz++) {
                for (int dy = -SCAN_DOWN; dy <= TRUNK_MAX + LEAF_RADIUS + 2; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(logState.getBlock()) || state.is(leafState.getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 铺一层树叶：方形层，四角随机剔除，避免出现生硬的方块团。 */
    private void placeLeafLayer(WorldGenLevel level, RandomSource random, BlockPos origin,
                               BlockState leaves, int y, int radius) {
        BlockState logState = RegisterBlock.HALL_LOG.get().defaultBlockState();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean isCorner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                if (isCorner && random.nextFloat() < 0.6F) continue;

                BlockPos leafPos = origin.offset(dx, y, dz);
                BlockState existing = level.getBlockState(leafPos);

                // 绝不用树叶盖住原木：间距校验因区块边界漏过时，这里挡住「树上长树」最刺眼的情况。
                if (existing.is(logState.getBlock())) continue;

                if (level.isEmptyBlock(leafPos) || existing.canBeReplaced()) {
                    level.setBlock(leafPos, leaves, SET_FLAGS);
                }
            }
        }
    }
}
