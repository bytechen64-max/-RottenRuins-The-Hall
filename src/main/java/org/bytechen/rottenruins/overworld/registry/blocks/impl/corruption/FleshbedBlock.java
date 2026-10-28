package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * 血肉温床（从 corruption 工程 {@code FleshbedBlock} 移植，简化版）。
 *
 * <p>模型是 corruption 工程手写的 Blockbench 模型
 * （{@code assets/rotten_ruins/models/block/fleshbed.json}），因此方块需要
 * {@code horizontal_facing} 属性；blockstate 与物品模型由 datagen 的
 * {@code horizontalBlockStateWithItem} 生成。</p>
 *
 * <p>TODO（机制层，后续补）：原工程的 BlockEntity（培养物/生物量）、
 * 配方配置 {@code FleshbedRecipeConfig}、与 {@code InfectionItem} 的交互都尚未移植。</p>
 */
public class FleshbedBlock extends HorizontalDirectionalBlock {

    public FleshbedBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(2.0F, 6.0F)
                .sound(net.minecraft.world.level.block.SoundType.STONE)
                .requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
