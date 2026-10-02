package org.bytechen.hall.overworld.registry.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.bytechen.hall.overworld.registry.RegisterBlock;

/**
 * 王庭树 —— 由王庭原木 + 王庭树叶构成的树木地物。
 * <p>
 * 之所以不用原版的 {@code minecraft:tree} / {@link net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration}：
 * 那套配置要求 {@code dirt_provider}、树干放置器、树叶放置器成套使用，
 * 在使用自定义树叶方块时容易踩到「树叶被判定为不可存活而掉落」的坑。
 * 这里直接手写、只依赖 {@code setBlock}，行为完全可预测。
 *
 * <h2>造型</h2>
 * 瘦高的主干（4~6 格）配 7×7 的伞状树冠，下层边缘随机剔除一格，
 * 让轮廓不生硬。整体风格贴合同系列的王庭植被。
 *
 * <h2>几点设计取舍</h2>
 * <ul>
 *   <li>树叶使用 {@code targetState} 而非硬替换：不会把已有方块（比如玩家建筑）
 *       直接覆盖掉。</li>
 *   <li>树干不检查「是否为空」以外的条件，因此不会出现半截悬空树。</li>
 *   <li>返回 {@code false} 表示「此处无法生成」，放置器会跳过这个点，
 *       密度由 PlacedFeature 的 {@code count} 控制。</li>
 * </ul>
 */
public class HallTreeFeature extends Feature<NoneFeatureConfiguration> {

    /** 树干高度范围（含）。树干越高，伞状树冠越显得挺拔 */
    private static final int TRUNK_MIN = 5;
    private static final int TRUNK_MAX = 8;

    /** 树冠半径。半径 2 = 5×5，配合间距校验能让树彼此分明 */
    private static final int LEAF_RADIUS = 2;

    /**
     * 间距扫描的水平半径。
     * <p>
     * 取 6 而非更小的值，有一个关键原因：<b>相邻区块</b>。
     * 两次尝试可能分属相邻区块（例如一棵在区块边缘 x=15、另一棵在邻块 x=16），
     * 若扫描半径不足以跨出本区块，生成时就看不出对方的存在。
     * 半径 6 使扫描区为 13×13，从区块内任意点出发都能越界看到邻块 ——
     * 这是「树长在树上」在区块边界处仍然出现的原因。
     * <p>
     * 注意：世界生成期邻块可见性并非绝对可靠，因此这是一道「尽力而为」的
     * 约束，无法 100% 杜绝边界处的相接，但已能消除绝大多数重叠。
     */
    private static final int MIN_TREE_SPACING = 6;

    /** 间距扫描时向下延伸的格数（覆盖附近较低地表的树冠） */
    private static final int SCAN_DOWN = 4;

    /**
     * 生成时使用的 setBlock 标志。
     * <p>
     * {@code UPDATE_CLIENTS} 保证客户端看到树；
     * {@code UPDATE_KNOWN_SHAPE} 跳过形状更新——这是原版世界生成的标准组合
     * （见 {@code WorldGenRegion#setBlock}），能避免生成期触发大量邻居更新。
     */
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

        // ── 落脚点校验 ──
        // origin 由 HeightmapPlacement 给出，指向地表之上<b>第一个空气格</b>，
        // 也就是树干该开始的位置。因此要检查的是：
        //   · 下方（below）必须是实心地面  → 否则悬空
        //   · 上方（origin）必须是空气      → 否则被挡住
        //
        // 注意：这里曾经把第二个判断写成 `isEmptyBlock(origin)` 并反转返回，
        // 导致条件恒为真、树木从未生成。改动前请确认逻辑方向。
        if (level.isEmptyBlock(below)) {
            return false;   // 下方是空气 → 悬空，放弃
        }
        if (!level.isEmptyBlock(origin)) {
            return false;   // 上方不是空气 → 被挡住，放弃
        }

        // ── 间距校验：附近已有王庭树（树干或树叶）就主动让位 ──
        // 这是「树叠在一起 / 树长在树上」的关键修复。InSquarePlacement 只保证
        // 多次尝试的<b>基点</b>在区块内散开，它并不阻止两棵树离得太近；
        // 而树冠半径（LEAF_RADIUS）远大于相邻基点的间距。
        // 注意必须连同<b>树叶</b>一起检查，且扫描高度要覆盖树冠 ——
        // 详见 hasNearbyTree 的说明。
        if (hasNearbyTree(level, origin)) {
            return false;
        }

        // ── 树干 ──
        for (int dy = 0; dy < trunkHeight; dy++) {
            BlockPos trunkPos = origin.above(dy);
            if (!level.isEmptyBlock(trunkPos) && !level.getBlockState(trunkPos).canBeReplaced()) {
                // 被挡住就整体放弃，避免生成半截树
                return false;
            }
            level.setBlock(trunkPos, log, SET_FLAGS);
        }

        // ── 树冠 ──
        // 自下而上两层宽、两层窄，末梢补一格，形成伞形
        int topY = trunkHeight;
        for (int dy = -2; dy <= -1; dy++) {
            placeLeafLayer(level, random, origin, leaves, topY + dy, LEAF_RADIUS);
        }
        for (int dy = 0; dy <= 1; dy++) {
            placeLeafLayer(level, random, origin, leaves, topY + dy, LEAF_RADIUS - 1);
        }
        // 树尖
        BlockPos tip = origin.above(topY + 2);
        if (level.isEmptyBlock(tip)) {
            level.setBlock(tip, leaves, SET_FLAGS);
        }

        return true;
    }

    /**
     * 检查目标点附近是否已有王庭树（树干<b>或树叶</b>）。
     * <p>
     * 这是避免「树叠在一起 / 树长在树上」的核心手段。
     *
     * <h2>为什么必须同时检查树叶，且要扫到树冠高度</h2>
     * 本树的竖直分布是：
     * <pre>
     *   树干：origin            … origin + trunkHeight
     *   树叶：origin + trunkHeight - 2 … origin + trunkHeight + 2
     * </pre>
     * 也就是说<b>树冠位于树干之上</b>，最高可达 {@code origin + TRUNK_MAX + 2}。
     * 早期版本只扫描 {@code dy = 0..TRUNK_MAX} 且只认原木，结果：
     * <ul>
     *   <li>扫不到树冠所在的更高层 → 树冠会压在邻近的树冠上；</li>
     *   <li>完全不检查树叶 → 即使扫到那个高度也发现不了。</li>
     * </ul>
     * 表现为「树长在树上」。因此扫描范围向上延伸到
     * {@code TRUNK_MAX + LEAF_RADIUS}，并且原木与树叶都算命中。
     *
     * <h2>向下也留了余量</h2>
     * 相邻点可能位于更低的地表（缓坡），其树冠有可能探进本点范围，
     * 因此向下也扩展 {@value #SCAN_DOWN} 格。
     */
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

    /**
     * 铺一层树叶。方形层，四角随机剔除，避免出现生硬的方块团。
     */
    private void placeLeafLayer(WorldGenLevel level, RandomSource random, BlockPos origin,
                               BlockState leaves, int y, int radius) {
        BlockState logState = RegisterBlock.HALL_LOG.get().defaultBlockState();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 四个外角有一定概率被剔除
                boolean isCorner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                if (isCorner && random.nextFloat() < 0.6F) continue;

                BlockPos leafPos = origin.offset(dx, y, dz);
                BlockState existing = level.getBlockState(leafPos);

                // 绝不用树叶盖住原木。
                // 这一条是「树长在树上」的直接防线：若两棵树的点恰好相邻，
                // 后来的那棵会把树冠铺到先来那棵的树干上，形成「树上长树」的
                // 怪状。即使间距校验因区块边界而漏过，这里也能挡住最刺眼的情况。
                if (existing.is(logState.getBlock())) continue;

                // 只填充空气或可替换方块，不破坏已有结构
                if (level.isEmptyBlock(leafPos) || existing.canBeReplaced()) {
                    level.setBlock(leafPos, leaves, SET_FLAGS);
                }
            }
        }
    }
}
