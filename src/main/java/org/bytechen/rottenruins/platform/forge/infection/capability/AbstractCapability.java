package org.bytechen.rottenruins.platform.forge.infection.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.LazyOptional;

/**
 * 能力抽象基类。
 * <p>
 * 提供同步脏标记、LazyOptional 持有者管理、同步回调等通用功能。
 * 子类需实现 {@link #writeSyncData(CompoundTag)} 和 {@link #readSyncData(CompoundTag)}
 * 来定义哪些数据需要同步到客户端。
 *
 * @param <T> 能力实现类型
 */
public abstract class AbstractCapability<T> implements IModCapability {

    protected final CompoundTag syncData = new CompoundTag();
    private boolean syncDirty;
    private LazyOptional<T> holder;
    private Runnable syncCallback;

    /**
     * 将需要同步的数据写入 sync tag。
     */
    public abstract void writeSyncData(CompoundTag tag);

    /**
     * 从 sync tag 中读取同步数据。
     */
    public abstract void readSyncData(CompoundTag tag);

    /**
     * 标记同步数据为脏，触发网络同步。
     */
    public void markSyncDataDirty() {
        this.syncDirty = true;
        this.syncData.getAllKeys().clear();
        writeSyncData(this.syncData);
        if (this.syncCallback != null) {
            this.syncCallback.run();
        }
    }

    public LazyOptional<T> getHolder() {
        return this.holder;
    }

    public void invalidate() {
        if (this.holder != null) {
            this.holder.invalidate();
        }
    }

    public boolean isSyncDirty() {
        return this.syncDirty;
    }

    public void clearSyncDirty() {
        this.syncDirty = false;
    }

    void setHolder(LazyOptional<?> holder) {
        @SuppressWarnings("unchecked")
        LazyOptional<T> casted = (LazyOptional<T>) holder;
        this.holder = casted;
    }

    void setSyncCallback(Runnable syncCallback) {
        this.syncCallback = syncCallback;
    }
}
