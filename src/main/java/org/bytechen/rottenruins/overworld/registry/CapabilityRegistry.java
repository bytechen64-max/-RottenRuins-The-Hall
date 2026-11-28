package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventory;
import org.bytechen.rottenruins.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.rottenruins.overworld.registry.capability.threat.ThreatCapability;
import org.bytechen.rottenruins.gameplay.magic.state.ManaAccount;

import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectHolder;

import java.util.HashMap;
import java.util.Map;

public final class CapabilityRegistry {
    public static final Capability<CustomEffectHolder> CUSTOM_EFFECT_CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<AnomalyCapability> ANOMALY_CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ThreatCapability> THREAT_CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<SpellInventory> MAGIC_POOL_CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ManaAccount> MANA_CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private static final Map<ResourceLocation, Capability<?>> BY_KEY = new HashMap<>();
    private static final Map<Capability<?>, ResourceLocation> BY_CAP = new HashMap<>();

    static {
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "anomaly"), ANOMALY_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "threat"), THREAT_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "magic_pool"), MAGIC_POOL_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "mana"), MANA_CAP);
    }

    private CapabilityRegistry() {}

    public static void register(ResourceLocation key, Capability<?> cap) {
        BY_KEY.put(key, cap);
        BY_CAP.put(cap, key);
    }
    public static ResourceLocation getId(Capability<?> cap) { return BY_CAP.get(cap); }
    public static Capability<?> getById(ResourceLocation id) { return BY_KEY.get(id); }
}
