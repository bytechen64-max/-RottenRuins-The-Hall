package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import java.util.List;
import org.bytechen.rottenruins.advancement.RotAdvancementTriggers;
import org.bytechen.rottenruins.advancement.SourceMilestoneTrigger;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.energy.SourceWireNetwork;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import org.jetbrains.annotations.Nullable;

/** 前面为输出口，背面为输入口，侧面不导通。 */
public final class SourceMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty POWER = SourceWireBlock.POWER;
    private final boolean collector;

    public SourceMachineBlock(Properties properties, boolean collector) {
        super(properties);
        this.collector=collector;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWER,0));
    }

    public static boolean hasPort(BlockState state, Direction side) {
        return state.getBlock() instanceof SourceMachineBlock && side.getAxis()==state.getValue(FACING).getAxis();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,POWER); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new SourceMachineBlockEntity(pos,state,collector); }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide || !collector ? null : createTickerHelper(type,RegisterBlockEntities.SOURCE_COLLECTOR.get(),SourceMachineBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (level instanceof ServerLevel server) {
                level.setBlockAndUpdate(pos,state.setValue(FACING,state.getValue(FACING).getClockWise()));
                SourceWireNetwork.invalidate(server);
                SourceWireNetwork.refresh(server,pos,RegisterBlock.SOURCE_WIRE.get());
                level.scheduleTick(pos,this,1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SourceMachineBlockEntity machine) {
            NetworkHooks.openScreen((ServerPlayer)player,machine,pos);
            if(machine.getBlockState().getValue(POWER)>0) {
                RotAdvancementTriggers.triggerSourceMilestone((ServerPlayer)player,SourceMilestoneTrigger.Milestone.POWERED_CIRCUIT);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack,@Nullable BlockGetter level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.rotten_ruins.source.ports_hint").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos,this,1);
    }

    @Override
    public void neighborChanged(BlockState state,Level level,BlockPos pos,Block other,BlockPos otherPos,boolean moving) {
        if (level instanceof ServerLevel server) {
            level.scheduleTick(pos,this,1);
            SourceWireNetwork.invalidate(server);
            SourceWireNetwork.refresh(server,pos,RegisterBlock.SOURCE_WIRE.get());
        }
    }

    @Override
    public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        SourceWireNetwork.refresh(level,pos,RegisterBlock.SOURCE_WIRE.get());
        level.scheduleTick(pos,this,(int)(20-level.getGameTime()%20));
    }

    @Override
    public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!next.is(this) && level.getBlockEntity(pos) instanceof SourceMachineBlockEntity machine) {
            Containers.dropContents(level,pos,machine);
            level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,next,moving);
    }

    @Override public BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
