package org.bytechen.rottenruins.content.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.infection.blockspread.BlockSpreadManager;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.MeteoriteEntity;

/**
 * 王庭召唤仪式：半径 {@value RADIUS} 格持续落陨石，并在 {@value DURATION_TICKS} tick（20 秒）内
 * 把同一半径的地表改造成王庭土地、把地表群系换成王庭群系。
 *
 * <h3>为什么地表转换只跑一遍</h3>
 * <p>覆盖面积 {@code π·50² ≈ 7854} 列。用黄金角螺旋按下标取列，保证 400 tick 内每列恰好处理一次、
 * 分布均匀，不需要记录任何进度：{@code 列下标 = elapsedTicks × 每 tick 列数 + i}。</p>
 *
 * <p>群系改造不单独写：表层方块走 {@link BlockSpreadManager#applySpread}，它触发的
 * {@code BlockSpreadEvent.Post} 会由王庭扩散处理器入队 {@code HallBiomeProcessor}，
 * 群系与方块因此永远同一份规则、同一个维度隔离状态。地下两层直接替换（不触发事件），
 * 避免每列给群系队列塞三条任务把硬上限吃满。</p>
 */
public final class HallSummoningRitual {
    /** 仪式影响半径（格）。 */
    public static final int RADIUS = 50;
    /** 仪式总时长：20 秒。 */
    public static final int DURATION_TICKS = 400;
    /** 每 tick 处理的列数：400 × 20 = 8000 ≥ 7854 列。 */
    public static final int COLUMNS_PER_TICK = 20;
    /** 陨石齐射间隔（tick）。 */
    public static final int METEOR_INTERVAL = 10;
    /** 每次齐射的陨石数量。 */
    public static final int METEORS_PER_BURST = 2;
    /** 陨石生成高度（地表以上）。 */
    private static final int METEOR_HEIGHT = 45;

    private static final int TOTAL_COLUMNS = (int) Math.round(Math.PI * RADIUS * RADIUS);
    private static final double GOLDEN_ANGLE = Math.PI * (3.0d - Math.sqrt(5.0d));
    private static final ResourceLocation SPREAD_TYPE = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "spread");

    private HallSummoningRitual() {}

    /**
     * 仪式单个 tick 的工作量：先落陨石，再改造地表。
     *
     * @param elapsedTicks 自仪式开始经过的 tick 数（0 起）
     */
    public static void tick(ServerLevel level, BlockPos center, int elapsedTicks) {
        if (elapsedTicks < 0 || elapsedTicks >= DURATION_TICKS) return;
        if (elapsedTicks % METEOR_INTERVAL == 0) spawnMeteors(level, center);
        convertColumns(level, center, elapsedTicks);
    }

    /** 在仪式半径内随机取点，从高处放一颗以固定速度下坠的陨石。 */
    private static void spawnMeteors(ServerLevel level, BlockPos center) {
        for (int i = 0; i < METEORS_PER_BURST; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0d;
            double distance = RADIUS * Math.sqrt(level.random.nextDouble());
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(probe)) continue;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double startY = ground + METEOR_HEIGHT + level.random.nextInt(15);
            Vec3 spawn = new Vec3(x + 0.5d, startY, z + 0.5d);
            Vec3 velocity = new Vec3(level.random.nextDouble() * 0.2d - 0.1d, -1.1d,
                    level.random.nextDouble() * 0.2d - 0.1d);
            MeteoriteEntity.spawn(level, spawn, velocity);
        }
    }

    /** 按黄金角螺旋取本 tick 的列，逐列把表层方块换成王庭方块。 */
    private static void convertColumns(ServerLevel level, BlockPos center, int elapsedTicks) {
        for (int i = 0; i < COLUMNS_PER_TICK; i++) {
            int index = elapsedTicks * COLUMNS_PER_TICK + i;
            if (index >= TOTAL_COLUMNS) return;
            double distance = RADIUS * Math.sqrt((index + 0.5d) / TOTAL_COLUMNS);
            double angle = index * GOLDEN_ANGLE;
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(probe)) continue;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) continue;
            BlockPos surface = new BlockPos(x, surfaceY, z);
            BlockState state = level.getBlockState(surface);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            // 只有真正被扩散的表层才入队群系，保证一列一条任务。
            if (!spreadSurface(level, surface, state)) continue;
            replaceSubsurface(level, surface.below());
            replaceSubsurface(level, surface.below(2));
        }
    }

    private static boolean spreadSurface(ServerLevel level, BlockPos pos, BlockState state) {
        if (!BlockSpreadManager.hasSpreadRule(state, SPREAD_TYPE)) return false;
        return BlockSpreadManager.applySpread(level, pos, state, SPREAD_TYPE);
    }

    /** 表层以下两层：泥土系 → 王庭泥土，石头系 → 王庭石头；其余方块不动。 */
    private static void replaceSubsurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(pos, RegisterBlock.HALL_DIRT.get().defaultBlockState());
        } else if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)) {
            level.setBlockAndUpdate(pos, RegisterBlock.HALL_STONE.get().defaultBlockState());
        }
    }
}
