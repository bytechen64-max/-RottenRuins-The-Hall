package org.bytechen.rottenruins.platform.forge.infection.capability;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.HashMap;
import java.util.Map;

/**
 * 能力注册中心。
 * <p>
 * 集中管理所有模组能力，提供 ResourceLocation 与 Forge Capability 之间的双向查找。
 */
@SuppressWarnings("removal")
public final class CapabilityRegistry {

    public static final Capability<KillCountCapability> KILL_COUNT =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final Map<ResourceLocation, Capability<?>> BY_KEY = new HashMap<>();
    private static final Map<Capability<?>, ResourceLocation> BY_CAP = new HashMap<>();

    static {
        register(ResourceLocation.fromNamespaceAndPath(InfectionIds.NAMESPACE, "kill_count"), KILL_COUNT);
    }

    /**
     * 注册一个能力及其标识符。
     */
    public static <T extends AbstractCapability<T>> void register(ResourceLocation key, Capability<T> cap) {
        BY_KEY.put(key, cap);
        BY_CAP.put(cap, key);
    }

    /**
     * 根据能力获取其 ResourceLocation 标识符。
     */
    public static ResourceLocation getId(Capability<?> cap) {
        return BY_CAP.get(cap);
    }

    /**
     * 根据 ResourceLocation 获取对应的能力。
     */
    public static Capability<?> getById(ResourceLocation id) {
        return BY_KEY.get(id);
    }
}
