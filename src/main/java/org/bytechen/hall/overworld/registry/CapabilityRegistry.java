package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.capability.anomaly.AnomalyCapability;
import org.bytechen.hall.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteItemHandle;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.bytechen.hall.overworld.registry.capability.threat.ThreatCapability;
import org.bytechen.hall.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.hall.overworld.registry.capability.TwistedPoint;
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
     * 玩家的魔法物品槽（{@code PlayerMagicPool}，默认 10 格，可被属性修饰器扩容）。
     * <p>这是旧的无类型入口，仅用于兼容；新代码请用 {@link #MAGIC_POOL_CAP}。</p>
     */
    public static final Capability<ByteItemHandle> ITEM_HANDLE_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /**
     * 带类型的魔法池入口：直接拿到 {@code PlayerMagicPool}，因此能调
     * {@code getSelectedSlot()} / {@code addSpell()} / 冷却读写这些
     * {@link ByteItemHandle} 之外的便利方法。与 {@link #ITEM_HANDLE_CAP} 指向同一个对象。
     */
    public static final Capability<PlayerMagicPool> MAGIC_POOL_CAP =
            CapabilityManager.get(new CapabilityToken<>(){});

    /**
     * 带类型的法力池入口（{@code TwistedPoint}）。与旧的 {@link #TWIST_CAP} 指向同一个对象，
     * 区别只是这里能直接调 {@code spend()} / {@code addKillRefund()}。
     */
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

    /**
     * 建立「id ↔ capability」双向映射。
     *
     * <p>参数类型从原来的 {@code Capability<T extends AbstractCapability<T>>} 放宽为
     * {@code Capability<?>}：原来的上界把 {@code PlayerMagicPool}、{@code TwistedPoint}
     * 这类没有继承 {@code AbstractCapability} 的实现挡在门外，于是它们无法参与按 id 查找 ——
     * 而按 id 查找正是以后做客户端同步时最顺手的入口。</p>
     */
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
