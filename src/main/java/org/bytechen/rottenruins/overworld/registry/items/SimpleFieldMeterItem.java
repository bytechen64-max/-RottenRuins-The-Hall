package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.worldgen.HighPotentialChunks;

/** 简易场强计。服务端读数写入物品 NBT，由原版背包同步发送给客户端。 */
public final class SimpleFieldMeterItem extends Item {
    public static final String READING_TAG = "FieldPotential";
    public static final int LOW = 0;
    public static final int MEDIUM = 1;
    public static final int HIGH = 2;
    public static final int DETECTION_RADIUS = 5;

    public SimpleFieldMeterItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // 背包与副手也工作；每半秒刷新，只有档位改变时才改 NBT，避免持续同步。
        if (stack.hasTag() && stack.getTag().contains(READING_TAG) && entity.tickCount % 10 != 0) return;
        int reading = measure(serverLevel, entity.chunkPosition().x, entity.chunkPosition().z);
        if (!stack.hasTag() || !stack.getTag().contains(READING_TAG) || getReading(stack) != reading) {
            stack.getOrCreateTag().putByte(READING_TAG, (byte) reading);
        }
    }

    /** 水平区块距离；当前区块优先为高档，半径 5（含边界）内存在高势区则为中档。 */
    public static int measure(ServerLevel level, int chunkX, int chunkZ) {
        if (HighPotentialChunks.isHighPotentialChunk(level, chunkX, chunkZ)) return HIGH;
        for (int dx = -DETECTION_RADIUS; dx <= DETECTION_RADIUS; dx++) {
            for (int dz = -DETECTION_RADIUS; dz <= DETECTION_RADIUS; dz++) {
                if ((dx == 0 && dz == 0) || dx * dx + dz * dz > DETECTION_RADIUS * DETECTION_RADIUS) continue;
                if (HighPotentialChunks.isHighPotentialChunk(level, chunkX + dx, chunkZ + dz)) return MEDIUM;
            }
        }
        return LOW;
    }

    public static int getReading(ItemStack stack) {
        return stack.hasTag() ? Math.max(LOW, Math.min(HIGH, stack.getTag().getByte(READING_TAG))) : LOW;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
