package org.bytechen.rottenruins.platform.forge.infection.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.infection.IKillCounter;
import org.bytechen.rottenruins.api.infection.event.KillCountAddEvent;
import org.bytechen.rottenruins.api.infection.event.KillCountChangeEvent;

/**
 * 击杀计数能力实现。
 * <p>
 * 存储每实体的击杀计数，支持服务端数据持久化和客户端同步。
 * 通过 Forge 能力系统附加到实现 {@link IKillCounter} 的实体上。
 * <p>
 * 写入 NBT 时持久化：{@code killCount}
 * <br>
 * 同步到客户端时仅发送：{@code killCount}
 */
public class KillCountCapability extends AbstractCapability<KillCountCapability> implements IKillCounter {

    private int killCount;
    private boolean enabled = true;

    public KillCountCapability() {}

    public KillCountCapability(boolean enabled) {
        this.enabled = enabled;
    }

    // ==================== IKillCounter ====================

    @Override
    public boolean killCounterEnabled() {
        return enabled;
    }

    @Override
    public int getKillCount() {
        return killCount;
    }

    @Override
    public void setKillCount(int count) {
        int old = this.killCount;
        this.killCount = Math.max(0, count);
        markSyncDataDirty();
        MinecraftForge.EVENT_BUS.post(new KillCountChangeEvent(null, old, this.killCount));
    }

    @Override
    public void addKillCount(int amount) {
        if (amount <= 0) return;

        KillCountAddEvent event = new KillCountAddEvent(null, amount);
        if (MinecraftForge.EVENT_BUS.post(event)) return;

        int old = this.killCount;
        this.killCount += event.getAmount();
        markSyncDataDirty();
        MinecraftForge.EVENT_BUS.post(new KillCountChangeEvent(null, old, this.killCount));
    }

    /**
     * 带实体引用的增加计数方法（用于事件中携带实体信息）。
     */
    public void addKillCount(LivingEntity entity, int amount) {
        if (amount <= 0 || !enabled) return;

        KillCountAddEvent event = new KillCountAddEvent(entity, amount);
        if (MinecraftForge.EVENT_BUS.post(event)) return;

        int old = this.killCount;
        this.killCount += event.getAmount();
        markSyncDataDirty();
        MinecraftForge.EVENT_BUS.post(new KillCountChangeEvent(entity, old, this.killCount));
    }

    /**
     * 带实体引用的设置计数方法。
     */
    public void setKillCount(LivingEntity entity, int count) {
        int old = this.killCount;
        this.killCount = Math.max(0, count);
        markSyncDataDirty();
        MinecraftForge.EVENT_BUS.post(new KillCountChangeEvent(entity, old, this.killCount));
    }

    // ==================== Sync ====================

    @Override
    public void writeSyncData(CompoundTag tag) {
        tag.putInt("killCount", killCount);
    }

    @Override
    public void readSyncData(CompoundTag tag) {
        killCount = tag.getInt("killCount");
    }

    // ==================== Full NBT ====================

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("killCount", killCount);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        killCount = tag.getInt("killCount");
        if (tag.contains("enabled")) {
            enabled = tag.getBoolean("enabled");
        }
    }
}
