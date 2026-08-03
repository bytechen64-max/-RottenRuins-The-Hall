package org.bytechen.hall.overworld.registry.capability;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;

public class CapabilityAttacher {
    public static <T> T getCapability(Entity entity, Capability<T> cap, T defaultValue) {
        return entity.getCapability(cap).orElse(defaultValue);
    }
    public static <T> boolean hasCapability(Entity entity, Capability<T> cap) {
        return entity.getCapability(cap).isPresent();
    }
}
