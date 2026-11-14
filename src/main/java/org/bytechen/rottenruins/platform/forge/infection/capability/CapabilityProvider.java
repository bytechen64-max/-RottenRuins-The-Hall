package org.bytechen.rottenruins.platform.forge.infection.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 能力提供者，桥接 Forge 的能力系统。
 * <p>
 * 当数据标记为脏时，自动构造 {@link org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncCapability}
 * 并通过 {@link org.bytechen.rottenruins.platform.forge.infection.network.NetworkHelper#sendToClient(Entity, Object)} 发送到追踪的客户端。
 *
 * @param <T> 能力类型
 * @param <C> 能力实现类型
 */
public class CapabilityProvider<T, C extends AbstractCapability<T>> implements ICapabilitySerializable<CompoundTag> {

    private final Capability<T> capToken;
    private final LazyOptional<T> lazyOptional;
    private final C instance;
    private Entity entity;

    @SuppressWarnings("unchecked")
    public CapabilityProvider(Capability<T> capToken, C instance) {
        this.capToken = capToken;
        this.instance = instance;
        this.lazyOptional = (LazyOptional<T>) LazyOptional.of(() -> instance);
        instance.setHolder(this.lazyOptional);
        instance.setSyncCallback(this::sync);
    }

    /**
     * 初始化同步目标实体。必须在附加能力后调用。
     */
    public void initSync(Entity entity) {
        this.entity = entity;
    }

    @Override
    public @NotNull <R> LazyOptional<R> getCapability(@NotNull Capability<R> cap, @Nullable Direction side) {
        return cap == capToken ? lazyOptional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        instance.deserializeNBT(tag);
    }

    private void sync() {
        if (!instance.isSyncDirty() || entity == null) return;
        if (entity.level().isClientSide()) return;

        ResourceLocation capId = CapabilityRegistry.getId(capToken);
        if (capId == null) return;

        var packet = new org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncCapability(
                entity.getId(), capId, instance.syncData);
        org.bytechen.rottenruins.platform.forge.infection.network.NetworkHelper.sendToClient(entity, packet);
        instance.clearSyncDirty();
    }
}
