package org.bytechen.rottenruins.overworld.registry;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.rottenruins.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.bytechen.rottenruins.overworld.registry.capability.threat.ThreatCapability;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectHolder;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.capability.TwistedPoint;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteNumberAbility;

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

    /** 威胁点数：所有生物 / 玩家通用 */
    public static final Capability<ThreatCapability> THREAT_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 自定义效果容器：挂在所有 LivingEntity 上、自带 NBT；与原版 MobEffect 独立，/effect clear 与牛奶清不掉。 */
    public static final Capability<CustomEffectHolder> CUSTOM_EFFECT_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 扭曲点（TwistedPoint，int 计数器）：不进 BY_KEY/BY_CAP，因 register 上界是 AbstractCapability<T> 而它不继承。 */
    public static final Capability<ByteNumberAbility> TWIST_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 玩家的魔法物品槽（PlayerMagicPool，默认 5 格）；旧的无类型入口，仅用于兼容。 */
    public static final Capability<ByteItemHandle> ITEM_HANDLE_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 带类型的魔法池入口，与 ITEM_HANDLE_CAP 指向同一对象，但能直调 getSelectedSlot()/addSpell()。 */
    public static final Capability<PlayerMagicPool> MAGIC_POOL_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 带类型的法力池入口（TwistedPoint），与 TWIST_CAP 指向同一对象，可直调 spend()/addKillRefund()。 */
    public static final Capability<TwistedPoint> MANA_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    private static final Map<ResourceLocation, Capability<?>> BY_KEY = new HashMap<>();
    private static final Map<Capability<?>, ResourceLocation> BY_CAP = new HashMap<>();

    static {
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "anomaly"), ANOMALY_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "threat"), THREAT_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "magic_pool"), MAGIC_POOL_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "mana"), MANA_CAP);
    }

    /** 建立「id ↔ capability」双向映射；上界取 Capability<?> 让 TwistedPoint 这类实现也能按 id 查找。 */
    public static void register(ResourceLocation key, Capability<?> cap) {
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
