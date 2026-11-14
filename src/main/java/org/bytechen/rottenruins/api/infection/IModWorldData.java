package org.bytechen.rottenruins.api.infection;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 世界数据标记接口。
 * <p>
 * 与 {@link org.bytechen.rottenruins.platform.forge.infection.capability.IModCapability} 对等，
 * 标记一个类为世界级别（维度/Dimension）的数据对象。
 * 继承 {@link INBTSerializable} 以支持 NBT 持久化。
 * <p>
 * 下游模组不直接实现此接口，而是继承
 * {@link org.bytechen.rottenruins.platform.minecraft.infection.worlddata.AbstractWorldData} 抽象基类，
 * 后者已实现完整的 SavedData 持久化和网络同步机制。
 *
 * @see org.bytechen.rottenruins.platform.minecraft.infection.worlddata.AbstractWorldData
 * @see org.bytechen.rottenruins.platform.minecraft.infection.worlddata.WorldDataRegistry
 */
public interface IModWorldData extends INBTSerializable<CompoundTag> {
}
