package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.overworld.registry.capability.base.AbstractCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteNumberAbility;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("removal")
public class CapabilityRegistry {

    public static final Capability<AnomalyCapability> ANOMALY_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});
    public static final Capability<ByteNumberAbility> TWIST =
            CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ByteItemHandle> ITEM_HANDLE =
            CapabilityManager.get(new CapabilityToken<>(){});

    private static final Map<ResourceLocation, Capability<?>> BY_KEY = new HashMap<>();
    private static final Map<Capability<?>, ResourceLocation> BY_CAP = new HashMap<>();

    static {
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "anomaly"), ANOMALY_CAP);
    }

    public static <T extends AbstractCapability<T>> void register(ResourceLocation key, Capability<T> cap) {
        BY_KEY.put(key, cap);
        BY_CAP.put(cap, key);
    }

    public static ResourceLocation getId(Capability<?> cap) {
        return BY_CAP.get(cap);
    }

    public static Capability<?> getById(ResourceLocation id) {
        return BY_KEY.get(id);
    }
}
