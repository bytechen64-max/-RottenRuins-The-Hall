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
 * <p>已带 {@code FleshbedBlockEntity}（培养物/生物量、tick 与交互）。
 * 仍未移植的是原工程的配方配置 {@code FleshbedRecipeConfig}，以及与 {@code InfectionItem}
 * 的转化交互。</p>
 */
public class FleshbedBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity(pos, state);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
    getTicker(net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities.FLESHBED.get()) return null;
        return (world, pos, block, entity) -> org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity.tick(
                world, pos, block, (org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity) entity);
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity bed) {
            bed.interact(serverPlayer, hand);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof
                org.bytechen.rottenruins.overworld.registry.blockentity.FleshbedBlockEntity bed) bed.cancel();
        super.onRemove(state, level, pos, next, moving);
    }

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
