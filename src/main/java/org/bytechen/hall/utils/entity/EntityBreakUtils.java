package org.bytechen.hall.utils.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class EntityBreakUtils {

    /**
     * 让实体破坏其碰撞箱扩大范围内的方块（不破坏脚下方块）。
     *
     * @param entity       执行破坏的实体
     * @param expandRange  碰撞箱向外扩展的距离（水平及上方）
     * @param maxHardness  允许破坏的最大硬度（含），-1 的方块始终跳过
     * @param dropItems    是否产生掉落物
     */
    public static void breakBlocksInRange(Entity entity, double expandRange, float maxHardness, boolean dropItems) {
        if (entity == null) return;
        Level level = entity.level();
        if (level.isClientSide) return;

        AABB box = entity.getBoundingBox();
        // 向上扩展，但不向下，同时上浮 0.001 以排除脚底方块
        AABB searchArea = new AABB(
                box.minX - expandRange,
                box.minY + 0.001,          // 关键：避免脚底方块
                box.minZ - expandRange,
                box.maxX + expandRange,
                box.maxY + expandRange,
                box.maxZ + expandRange
        );

        BlockPos.betweenClosed(
                (int) Math.floor(searchArea.minX),
                (int) Math.floor(searchArea.minY),
                (int) Math.floor(searchArea.minZ),
                (int) Math.floor(searchArea.maxX),
                (int) Math.floor(searchArea.maxY),
                (int) Math.floor(searchArea.maxZ)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;

            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0) return;               // 不可破坏（如基岩）
            // 可选：对门特殊处理，忽略硬度
            // if (state.getBlock() instanceof DoorBlock) { /* 直接破坏 */ }
            if (hardness <= maxHardness) {
                level.destroyBlock(pos, dropItems, entity);
            }
        });
    }
}