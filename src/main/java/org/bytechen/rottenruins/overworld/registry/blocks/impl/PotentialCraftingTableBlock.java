package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 势能合成台。
 *
 * <p>继承 {@link SourceMachineBlock} 是为了接入势能电路：源导线网络只把该类型的方块当设备，
 * 会把解出的强度写进 {@code POWER} 属性。子类只需覆写开界面 / 方块实体 / tick / 掉落。</p>
 */
public final class PotentialCraftingTableBlock extends SourceMachineBlock {

    public PotentialCraftingTableBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PotentialCraftingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                RegisterBlockEntities.POTENTIAL_CRAFTING_TABLE.get(),
                PotentialCraftingTableBlockEntity::tick);
    }

    @Override
    @NotNull
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                 @NotNull Player player, @NotNull InteractionHand hand,
                                 @NotNull BlockHitResult hit) {
        // 潜行空手 = 旋转朝向（父类行为），否则打开界面
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof PotentialCraftingTableBlockEntity station
                && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, station, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 拆机掉内容物；合成中先扣掉已投入的材料（不可返还）。 */
    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof PotentialCraftingTableBlockEntity station) {
            station.dropRemainderAfterInterrupt();
            Containers.dropContents(level, pos, station);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("block.rotten_ruins.potential_crafting_table.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.rotten_ruins.potential_crafting_table.hint_power")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
