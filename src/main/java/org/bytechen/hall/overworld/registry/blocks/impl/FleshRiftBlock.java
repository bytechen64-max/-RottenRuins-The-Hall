package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.HallDimensions;
import org.bytechen.hall.overworld.registry.RegisterBlock;

/**
 * 血肉裂隙：主世界与血肉庭园（{@code hall:heall}）之间的通道。
 * <p>
 * 为什么不用传送门方块：原版传送门（PortalShape / PortalForcer）与下界维度
 * 强绑定，要复用它需要覆写大量逻辑。这里直接用右键交互加服务端传送，
 * 逻辑集中、可控，也便于后续加入「需要穹顶胚晶激活」之类的门禁。
 *
 * <h2>往返锚点</h2>
 * 去程时把玩家在主世界的落点写入其持久化数据（{@code hall:return_anchor}），
 * 回程再从血肉庭园右键裂隙即可回到原处，避免在异界迷路后无法归家。
 */
public class FleshRiftBlock extends Block {

    /** 玩家持久化数据中记录主世界返回锚点的键 */
    private static final String RETURN_ANCHOR_KEY = "hall:return_anchor";

    /** 抵达血肉庭园时临时清出的站立平台半径 */
    private static final int ARRIVAL_CLEARANCE = 2;

    public FleshRiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            // 客户端只负责播放反馈，实际传送由服务端决定
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ServerLevel target = serverLevel.getServer().getLevel(HallDimensions.HEALL_LEVEL);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("block.hall.flesh_rift.no_dimension"), true);
            return InteractionResult.FAIL;
        }

        boolean returning = serverLevel.dimension().equals(HallDimensions.HEALL_LEVEL);

        if (returning) {
            Vec3 anchor = readReturnAnchor(player);
            ServerLevel overworld = serverLevel.getServer().overworld();
            if (anchor == null) {
                // 没有记录过锚点（例如用指令直接进入），退回主世界出生点
                BlockPos spawn = overworld.getSharedSpawnPos();
                teleportTo(player, overworld,
                        new Vec3(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D));
            } else {
                teleportTo(player, overworld, anchor);
            }
        } else {
            // 记录主世界落点，供回程使用
            player.getPersistentData().putLong(RETURN_ANCHOR_KEY, BlockPos.asLong(
                    (int) Math.floor(player.getX()),
                    (int) Math.floor(player.getY()),
                    (int) Math.floor(player.getZ())));

            Vec3 arrival = prepareArrival(target, player.blockPosition());
            teleportTo(player, target, arrival);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * 在目标维度准备一个安全的抵达点。
     * <p>
     * 维度是开放地形、地表高度随机，因此不能写死 Y。这里用
     * {@link Heightmap.Types#MOTION_BLOCKING} 找到该列真实地表，
     * 清出一个 5x5 的站立空间并铺上王庭石块地板，避免把玩家埋进山体里。
     */
    private static Vec3 prepareArrival(ServerLevel target, BlockPos sourcePos) {
        // 先确保区块已生成，再读高度图
        int x = sourcePos.getX();
        int z = sourcePos.getZ();
        target.getChunkAt(sourcePos);

        int surfaceY = target.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int baseY = Math.max(target.getMinBuildHeight() + 2,
                Math.min(target.getMaxBuildHeight() - 6, surfaceY));

        BlockPos.MutableBlockPos center = new BlockPos.MutableBlockPos(x, baseY, z);
        Block floor = RegisterBlock.HALL_STONE.get();

        for (int dx = -ARRIVAL_CLEARANCE; dx <= ARRIVAL_CLEARANCE; dx++) {
            for (int dz = -ARRIVAL_CLEARANCE; dz <= ARRIVAL_CLEARANCE; dz++) {
                // 清空站立空间
                for (int dy = 0; dy <= ARRIVAL_CLEARANCE; dy++) {
                    BlockPos air = center.offset(dx, dy, dz);
                    if (!target.isEmptyBlock(air)) {
                        target.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
                    }
                }
                // 铺地板，保证不会直接掉进虚空
                BlockPos floorPos = center.offset(dx, -1, dz);
                if (target.isEmptyBlock(floorPos) || target.getBlockState(floorPos).canBeReplaced()) {
                    target.setBlockAndUpdate(floorPos, floor.defaultBlockState());
                }
            }
        }

        return new Vec3(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
    }

    private static void teleportTo(Player player, ServerLevel target, Vec3 destination) {
        // 只做建造高度上下限保护；落点本身已由 prepareArrival /
        // 记录的锚点保证是合法位置
        int safeY = Math.max(target.getMinBuildHeight() + 1,
                Math.min(target.getMaxBuildHeight() - 2, (int) Math.floor(destination.y)));

        BlockPos landing = BlockPos.containing(destination.x, safeY, destination.z);
        if (!target.isEmptyBlock(landing)) {
            // 落点被占：抬到该列的空隙
            landing = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, landing);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(target,
                    destination.x, landing.getY(), destination.z,
                    player.getYRot(), player.getXRot());
        } else {
            Entity moved = player.changeDimension(target);
            if (moved != null) {
                moved.teleportTo(destination.x, landing.getY(), destination.z);
            }
        }

        target.playSound(null, BlockPos.containing(destination), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.resetFallDistance();
        HallMod.LOGGER.debug("[FleshRift] {} 传送至 {} ({})",
                player.getName().getString(), target.dimension().location(), destination);
    }

    private static Vec3 readReturnAnchor(Player player) {
        if (!player.getPersistentData().contains(RETURN_ANCHOR_KEY)) return null;
        BlockPos anchor = BlockPos.of(player.getPersistentData().getLong(RETURN_ANCHOR_KEY));
        return new Vec3(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
    }

    /** 仅供调试与其它系统引用 */
    public static ResourceKey<Level> targetDimension() {
        return HallDimensions.HEALL_LEVEL;
    }
}
