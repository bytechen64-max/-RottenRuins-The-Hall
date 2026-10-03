package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.bytechen.hall.overworld.registry.capability.threat.ThreatCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("removal")
public class CapabilityRegistry {

    public static final Capability<AnomalyCapability> ANOMALY_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /** 威胁点数：所有生物 / 玩家通用 */
    public static final Capability<ThreatCapability> THREAT_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /**
     * 扭曲点（{@code TwistedPoint}）：一个 int 计数器，自带 capability provider 与 NBT 序列化。
     *
     * <p><b>注意</b>：它<b>没有</b>进下面的 {@code BY_KEY}/{@code BY_CAP} 映射表 ——
     * {@link #register} 的类型上界是 {@code AbstractCapability<T>}，而
     * {@link ByteNumberAbility} 不继承它。以后若要让它也参与"按 id 查 capability"，
     * 要么让 {@code ByteNumberAbility} 继承 {@code AbstractCapability}，
     * 要么把 {@link #register} 的上界放宽。</p>
     */
    public static final Capability<ByteNumberAbility> TWIST_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /**
     * 玩家的魔法物品槽（{@code PlayerMagicPool}，10 格 + 可扩容）。
     * <p>同上，也不进映射表。</p>
     */
    public static final Capability<ByteItemHandle> ITEM_HANDLE_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    private static final Map<ResourceLocation, Capability<?>> BY_KEY = new HashMap<>();
    private static final Map<Capability<?>, ResourceLocation> BY_CAP = new HashMap<>();

    static {
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "anomaly"), ANOMALY_CAP);
        register(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "threat"), THREAT_CAP);
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
