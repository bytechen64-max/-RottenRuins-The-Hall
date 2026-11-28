package org.bytechen.rottenruins.overworld.registry.blocks.impl;

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
import org.bytechen.rottenruins.overworld.registry.blocks.base.HallBaseBlock;

import java.util.function.Supplier;

/**
 * 王庭木质方块的「可燃烧」变体集合。原版把引燃/可燃数值写死在 {@code FireBlock#bootStrap()} 的私有映射表里，
 * 模组方块只能自行重写 {@code IForgeBlock#getFlammability} / {@code getFireSpreadSpeed}（Block 上已有默认实现，子类重写即可）。
 * 数值对齐原版：木板族 引燃 5 / 可燃 20；原木 5 / 5（{@link SpreadPillarBlock}）；树叶 30 / 60；藤蔓 15 / 100（{@link HallVineBlock}）。
 * 原版木门/活板门/按钮/压力板本就不在那张表里（不会被火烧掉），这里也刻意不给数值 —— 目标是「与原版一致」而非「全都可烧」。
 */
public final class HallWoodBlocks {

    private HallWoodBlocks() {
    }

    /** 原版木板族（planks / slab / stairs / fence / fence_gate）的引燃难度 */
    public static final int PLANK_ENCOURAGEMENT = 5;

    public static final int PLANK_FLAMMABILITY = 20;

    public static final int LOG_ENCOURAGEMENT = 5;

    public static final int LOG_FLAMMABILITY = 5;

    public static final int LEAVES_ENCOURAGEMENT = 30;

    public static final int LEAVES_FLAMMABILITY = 60;

    public static final int VINE_ENCOURAGEMENT = 15;

    public static final int VINE_FLAMMABILITY = 100;

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
