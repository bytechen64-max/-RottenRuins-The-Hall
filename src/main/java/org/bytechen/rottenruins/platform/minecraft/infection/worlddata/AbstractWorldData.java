package org.bytechen.rottenruins.platform.minecraft.infection.worlddata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.bytechen.rottenruins.api.infection.IModWorldData;
import org.bytechen.rottenruins.platform.forge.infection.network.NetworkHelper;
import org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncWorldData;
import org.jetbrains.annotations.ApiStatus;

/**
 * 世界数据抽象基类。
 * <p>
 * 提供与 {@link org.bytechen.rottenruins.platform.forge.infection.capability.AbstractCapability} 风格一致的
 * 世界级别数据管理，内置 SavedData 磁盘持久化和网络同步。
 * <p>
 * <b>下游模组使用步骤：</b>
 * <ol>
 *   <li>继承此类，实现 {@link #writeSyncData(CompoundTag)}、
 *       {@link #readSyncData(CompoundTag)}、{@link #serializeNBT()}、
 *       {@link #deserializeNBT(CompoundTag)}</li>
 *   <li>在模组构造时通过 {@link WorldDataRegistry#register} 注册</li>
 *   <li>服务端通过 {@link WorldDataRegistry#get(ServerLevel, Class)} 获取实例</li>
 *   <li>修改数据后调用 {@link #setDirty()} + {@link #markSyncDataDirty()}</li>
 *   <li>客户端通过 {@link WorldDataRegistry#getClientData} 读取同步数据</li>
 * </ol>
 *
 * <p><b>同步数据流：</b>
 * <pre>
 * 服务端: markSyncDataDirty()
 *   → writeSyncData(syncData)
 *   → new PacketSyncWorldData(dimension, worldDataId, syncData)
 *   → NetworkHelper.sendToAllClients(packet)
 *   → 客户端: WorldDataRegistry 缓存 syncData
 * </pre>
 */
public abstract class AbstractWorldData extends SavedData implements IModWorldData {

    protected final CompoundTag syncData = new CompoundTag();
    private boolean syncDirty;
    ServerLevel level;

    /**
     * 将需要网络同步的数据写入 sync tag。
     * <p>
     * 与 {@link org.bytechen.rottenruins.platform.forge.infection.capability.AbstractCapability#writeSyncData(CompoundTag)}
     * 模式一致 —— 子类决定哪些字段需要同步到客户端。
     * <p>
     * 例如：只同步少量展示数据，不同步完整的内部状态。
     */
    public abstract void writeSyncData(CompoundTag tag);

    /**
     * 从 sync tag 中读取网络同步数据。
     * <p>
     * 与 {@link org.bytechen.rottenruins.platform.forge.infection.capability.AbstractCapability#readSyncData(CompoundTag)}
     * 模式一致 —— 在客户端接收同步包时调用。
     */
    public abstract void readSyncData(CompoundTag tag);

    // ==================== SavedData 持久化 ====================

    /**
     * 将完整数据持久化到磁盘。
     * <p>
     * 来自 {@link SavedData}，委托给 {@link #serializeNBT()}。
     * 子类应覆写 {@link #serializeNBT()} 而非此方法。
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag result = serializeNBT();
        // 将序列化结果合并到传入的 tag 中
        for (String key : result.getAllKeys()) {
            tag.put(key, result.get(key));
        }
        return tag;
    }

    // ==================== 网络同步 ====================

    /**
     * 标记同步数据为脏，触发向所有客户端的网络同步。
     * <p>
     * 调用此方法后，框架自动：
     * <ol>
     *   <li>调用 {@link #writeSyncData(CompoundTag)} 写入当前同步数据</li>
     *   <li>构造 {@link PacketSyncWorldData} 并通过 {@link NetworkHelper#sendToAllClients} 发送</li>
     * </ol>
     * <p>
     * <b>注意：</b>此方法只处理网络同步。如需磁盘持久化，请同时调用 {@link #setDirty()}。
     */
    public void markSyncDataDirty() {
        this.syncDirty = true;
        this.syncData.getAllKeys().clear();
        writeSyncData(this.syncData);
        dispatchSync();
    }

    /**
     * 检查同步数据是否有未发送的更改。
     */
    public boolean isSyncDirty() {
        return syncDirty;
    }

    private void dispatchSync() {
        if (level == null || level.isClientSide()) return;

        var id = WorldDataRegistry.getIdByInstance(this);
        if (id != null) {
            NetworkHelper.sendToAllClients(
                    new PacketSyncWorldData(level.dimension().location(), id, syncData.copy()));
            syncDirty = false;
        }
    }

    // ==================== 内部 API ====================

    /**
     * 设置所属 ServerLevel 引用。由 {@link WorldDataRegistry#get} 在首次获取时调用。
     */
    @ApiStatus.Internal
    void setLevel(ServerLevel level) {
        this.level = level;
    }
}
