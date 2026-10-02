package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.bytechen.hall.overworld.registry.blocks.base.HallBaseBlock;

import java.util.function.Supplier;

/**
 * 王庭木质方块的「可燃烧」变体集合。
 *
 * <p>原版把「木头到底有多容易被点着」写死在
 * {@code FireBlock#bootStrap()} 的一张<b>私有映射表</b>里（{@code igniteOdds} / {@code burnOdds}），
 * 表里只有 {@code minecraft:oak_planks} 这类原版方块。模组的方块想走同一条火焰逻辑，
 * 只能按 Forge 的 {@code IForgeBlock#getFlammability} / {@code getFireSpreadSpeed} 自行重写 ——
 * 这两个方法在 {@code Block} 上已有默认实现（转调原版那张表），所以子类重写即可。
 *
 * <p>数值与原版一一对应：
 * <ul>
 *   <li><b>木板族</b>（木板 / 台阶 / 楼梯 / 栅栏 / 栅栏门）—— 引燃 5 / 可燃 20</li>
 *   <li><b>原木</b>（{@link SpreadPillarBlock}）—— 引燃 5 / 可燃 5</li>
 *   <li><b>树叶</b> —— 引燃 30 / 可燃 60</li>
 *   <li><b>藤蔓</b>（{@link HallVineBlock}）—— 引燃 15 / 可燃 100</li>
 * </ul>
 *
 * <p><b>注意</b>：原版的木门 / 活板门 / 按钮 / 压力板<b>本身就不在</b>那张表里（不会被火烧掉），
 * 因此这里也刻意<b>不</b>给它们数值 —— 目标是「与原版一致」，而不是「全都可烧」。
 */
public final class HallWoodBlocks {

    private HallWoodBlocks() {
    }

    // ==================== 原版数值常量 ====================

    /** 原版木板族（planks / slab / stairs / fence / fence_gate）的引燃难度 */
    public static final int PLANK_ENCOURAGEMENT = 5;

    /** 原版木板族的可燃度 */
    public static final int PLANK_FLAMMABILITY = 20;

    /** 原版原木的引燃难度 */
    public static final int LOG_ENCOURAGEMENT = 5;

    /** 原版原木的可燃度 */
    public static final int LOG_FLAMMABILITY = 5;

    /** 原版树叶的引燃难度 */
    public static final int LEAVES_ENCOURAGEMENT = 30;

    /** 原版树叶的可燃度 */
    public static final int LEAVES_FLAMMABILITY = 60;

    /** 原版藤蔓的引燃难度 */
    public static final int VINE_ENCOURAGEMENT = 15;

    /** 原版藤蔓的可燃度 */
    public static final int VINE_FLAMMABILITY = 100;

    // ==================== 木板族 ====================

    /** 王庭木板 —— 对齐原版 {@code oak_planks}（5 / 20） */
    public static class Planks extends HallBaseBlock {

        public Planks(Properties properties) {
            super(properties);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_ENCOURAGEMENT;
        }
    }

    /** 王庭楼梯 —— 对齐原版 {@code oak_stairs}（5 / 20） */
    public static class Stairs extends StairBlock {

        public Stairs(Supplier<BlockState> baseState, Properties properties) {
            super(baseState, properties);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_ENCOURAGEMENT;
        }
    }

    /** 王庭台阶 —— 对齐原版 {@code oak_slab}（5 / 20） */
    public static class Slab extends SlabBlock {

        public Slab(Properties properties) {
            super(properties);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_ENCOURAGEMENT;
        }
    }

    /** 王庭栅栏 —— 对齐原版 {@code oak_fence}（5 / 20） */
    public static class Fence extends FenceBlock {

        public Fence(Properties properties) {
            super(properties);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_ENCOURAGEMENT;
        }
    }

    /** 王庭栅栏门 —— 对齐原版 {@code oak_fence_gate}（5 / 20） */
    public static class FenceGate extends FenceGateBlock {

        public FenceGate(Properties properties, WoodType woodType) {
            super(properties, woodType);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return PLANK_ENCOURAGEMENT;
        }
    }

    // ==================== 树叶 ====================

    /** 王庭树叶 —— 对齐原版 {@code oak_leaves}（30 / 60） */
    public static class Leaves extends Block {

        public Leaves(Properties properties) {
            super(properties);
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return LEAVES_FLAMMABILITY;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return LEAVES_ENCOURAGEMENT;
        }
    }
}
