package org.bytechen.rottenruins.platform.forge.infection.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 模组能力的基础接口。
 * 所有自定义能力需实现此接口，提供 NBT 序列化支持。
 */
public interface IModCapability extends INBTSerializable<CompoundTag> {
}
