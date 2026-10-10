package org.bytechen.hall.overworld.registry.feature;

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
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallVineBlock;
import org.bytechen.infcore.api.block.ISpreadBlock;

/**
 * 王庭藤蔓地物 —— 让藤蔓从王庭方块上自然附着生长，支持向上与向下两种形态。
 * <p>
 * {@link HallVineBlock} 的 {@code canSurvive} 要求支撑方块是王庭藤蔓自身、
 * 或是 {@code getSpreadType()} 命名空间为 {@code hall} 的 {@link ISpreadBlock}
 * （王庭石块 / 泥土 / 草方块 / 原木）。
 *
 * <h2>两种形态的状态差异（很容易设错）</h2>
 * 由 {@code HallVineBlock.getStateForPlacement} 可推出初始状态：
 * <ul>
 *   <li><b>向下生长</b>：附着面在<b>上方</b>，初始 {@code SECTION = BOTTOM}</li>
 *   <li><b>向上生长</b>：附着面在<b>下方</b>，初始 {@code SECTION = TOP}</li>
 * </ul>
 * 两者的 {@code AGE} 都必须为 0 —— {@code isRandomlyTicking} 要求
 * {@code AGE < 25}，设成 25 就变成「已成熟」，永远不再生长。
 *
 * <h2>为什么地物只放一段</h2>
 * 藤蔓是 {@code randomTick} 自生长的：向上最长 4 格、向下最长 10 格。
 * 地物只负责「播下种子」，其余交给游戏内的生长逻辑 —— 既省生成期开销，
 * 玩家也能看到藤蔓慢慢长开的过程。
 */
public class HallVineFeature extends Feature<HallVineFeature.Config> {

    /** 每棵尝试次数 */
    private static final int ATTEMPTS = 16;

    /** 水平散布半径 */
    private static final int SPREAD_RADIUS = 5;

    /** 搜索的最大距离（向下或向上） */
    private static final int MAX_SEARCH = 10;

    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * 地物配置。
     *
     * @param upward {@code true} = 从地面向上竖着长；{@code false} = 从崖壁/洞顶向下垂挂
     */
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

            // 从基点出发，朝生长方向的「附着面」一侧寻找第一个空气格
            //   向下长：附着面在上方 → 从基部向下找空气
            //   向上长：附着面在下方 → 从基点向上找空气
            BlockPos target = findTarget(level, origin.offset(dx, 0, dz), upward);
            if (target == null) continue;

            // 附着方块位于生长方向的相反一侧
            BlockPos supportPos = upward ? target.below() : target.above();
            if (!isHallSupport(level.getBlockState(supportPos))) continue;

            // 初始自然段：SECTION 不硬编码，也无需在此处算 ——
            // 放置后由 HallVineBlock.updateShape 依据上下邻居自行判定。
            // 早期版本硬编码了初始段（向下 BOTTOM / 向上 TOP），
            // 结果整条藤蔓只保留初始段的状态、贴图不随连接关系变化。
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

    /**
     * 从基点出发，沿生长方向寻找第一个可放置的空气格。
     * <p>
     * 向下长时从基点上方向下扫；向上长时从基点向上扫。
     */
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

    /** 支撑方块是否为「王庭化」方块（藤蔓只能附着在这类方块上） */
    private static boolean isHallSupport(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof HallVineBlock) {
            return true;
        }
        if (block instanceof ISpreadBlock spreadBlock) {
            return "hall".equals(spreadBlock.getSpreadType().getNamespace());
        }
        return false;
    }
}
