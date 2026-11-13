package org.bytechen.rottenruins.overworld.registry.feature;
import org.bytechen.rottenruins.HallMod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.HallVineBlock;
import org.bytechen.infcore.api.block.ISpreadBlock;

/**
 * 王庭藤蔓地物：让藤蔓从王庭方块上自然附着生长，支持向上与向下两种形态。
 * 支撑须是王庭藤蔓自身或命名空间为 {@code hall} 的 {@link ISpreadBlock}；形态差异易设错：向下长附着面
 * 在上方、初始 {@code SECTION = BOTTOM}，向上长反之；两者 {@code AGE} 必须为 0（{@code AGE < 25} 才随机生长）。
 */
public class HallVineFeature extends Feature<HallVineFeature.Config> {

    /** 每棵尝试次数 */
    private static final int ATTEMPTS = 16;

    private static final int SPREAD_RADIUS = 5;

    /** 搜索的最大距离（向下或向上） */
    private static final int MAX_SEARCH = 10;

    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** 地物配置：{@code upward} 为 true 表示从地面向上竖着长，false 表示从崖壁 / 洞顶向下垂挂。 */
    public record Config(boolean upward) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("upward").forGetter(Config::upward)
        ).apply(instance, Config::new));

        public static Config up() {
            return new Config(true);
        }

        public static Config down() {
            return new Config(false);
        }
    }

    public HallVineFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean upward = context.config().upward();

        boolean placedAny = false;

        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS;
            int dz = random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS;

            // 从基点朝「附着面」一侧找第一个空气格：向下长时附着面在上方，向上长反之
            BlockPos target = findTarget(level, origin.offset(dx, 0, dz), upward);
            if (target == null) continue;

            // 附着方块位于生长方向的相反一侧
            BlockPos supportPos = upward ? target.below() : target.above();
            if (!isHallSupport(level.getBlockState(supportPos))) continue;

            // SECTION 不硬编码，由 HallVineBlock.updateShape 依据上下邻居判定；
            // 早期硬编码初始段会让整条藤蔓贴图不随连接关系变化。
            BlockState vine = RegisterBlock.HALL_VINE.get().defaultBlockState()
                    .setValue(HallVineBlock.GROWTH_DIR,
                            upward ? HallVineBlock.GrowthDirection.UP
                                   : HallVineBlock.GrowthDirection.DOWN)
                    // AGE 必须为 0，否则会被当作「已成熟」不再生长
                    .setValue(HallVineBlock.AGE, 0);

            if (!vine.canSurvive(level, target)) continue;

            level.setBlock(target, vine, SET_FLAGS);
            placedAny = true;
        }

        return placedAny;
    }

    private static BlockPos findTarget(WorldGenLevel level, BlockPos base, boolean upward) {
        BlockPos cursor = upward ? base : base.above();
        for (int step = 0; step < MAX_SEARCH; step++) {
            if (level.isEmptyBlock(cursor)) {
                return cursor;
            }
            cursor = upward ? cursor.above() : cursor.below();
        }
        return null;
    }

    private static boolean isHallSupport(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof HallVineBlock) {
            return true;
        }
        if (block instanceof ISpreadBlock spreadBlock) {
            return HallMod.MODID.equals(spreadBlock.getSpreadType().getNamespace());
        }
        return false;
    }
}
