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

/**
 * 王庭植被丛 —— 把指定的植物方块铺成一片。
 * <p>
 * 用于在血肉庭园里生长王庭草 / 王庭花等本模组植物，而不是借用原版的
 * {@code minecraft:patch_grass_plain} / {@code flower_plains}（那些会长出
 * 原版的草与花，与王庭的视觉语言不一致）。
 *
 * <h2>为什么不用原版的 RandomPatchFeature</h2>
 * 原版 {@code minecraft:random_patch} 需要一个 {@code BlockStateProvider}
 * 配置（例如 {@code minecraft:simple_state_provider}），虽然可行，
 * 但要在 datagen 里额外构造 provider 树，且只能放单一方块。
 * 这里直接写一个极简的实现：随机撒若干格，逐格做「下方可支撑 + 上方为空」
 * 的判定后放置，行为完全可控，也便于后续加入多方块混播。
 */
public class HallPatchFeature extends Feature<NoneFeatureConfiguration> {

    /** 每次尝试撒出的格数 */
    private static final int PLACEMENT_ATTEMPTS = 48;

    /** 撒播的水平半径 */
    private static final int SPREAD_RADIUS = 5;

    /** 生成时使用的 setBlock 标志 */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** 要铺的植物方块 */
    private final Block plant;

    public HallPatchFeature(Codec<NoneFeatureConfiguration> codec, Block plant) {
        super(codec);
        this.plant = plant;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState state = plant.defaultBlockState();
        boolean placedAny = false;

        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            int dx = random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS;
            int dz = random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS;
            // 垂直方向 ±2，让植被能顺着坡度分布，而不是只贴一个平面
            int dy = random.nextInt(5) - 2;

            BlockPos target = origin.offset(dx, dy, dz);

            // 目标格必须是空气（不覆盖已有方块）
            if (!level.isEmptyBlock(target)) continue;

            // 下方必须有方块支撑
            //
            // 注意：这里<b>不要</b>再追加 isSolidRender 之类的「更严格」判定。
            // 早期版本加了 `!level.getBlockState(support).isSolidRender(level, support)`
            // 之后草木一格都长不出来 —— 世界生成期跨区块查询并不保证可靠。
            // 支撑合法性交给方块自身的 canSurvive（王庭草/花会据此判定土壤白名单）。
            BlockPos support = target.below();
            if (level.isEmptyBlock(support)) continue;

            // 交给方块自身判定能否存活（含王庭草方块 / 王庭泥土的土壤白名单）
            if (!state.canSurvive(level, target)) continue;

            level.setBlock(target, state, SET_FLAGS);
            placedAny = true;
        }

        return placedAny;
    }
}
