package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class RegisterEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HallMod.MODID);

    public static RegistryObject<MobEffect> registerEffect(String name, Supplier<MobEffect> effectSupplier) {
        return EFFECTS.register(name, effectSupplier);
    }

    // —— 异常适应 Buff（叠满触发后自动获得 15s，阻止异常进度积累） ——
    public static final RegistryObject<MobEffect> HEAT_ANOMALY_ADAPTATION =
            registerEffect("heat_anomaly_adaptation", () -> org.bytechen.hall.overworld.registry.effect.HeatAnomalyAdaptationEffect.INSTANCE);
    public static final RegistryObject<MobEffect> COLD_ANOMALY_ADAPTATION =
            registerEffect("cold_anomaly_adaptation", () -> org.bytechen.hall.overworld.registry.effect.ColdAnomalyAdaptationEffect.INSTANCE);
    public static final RegistryObject<MobEffect> ACID_ANOMALY_ADAPTATION =
            registerEffect("acid_anomaly_adaptation", () -> org.bytechen.hall.overworld.registry.effect.AcidAnomalyAdaptationEffect.INSTANCE);

    // —— 裁决（天穹裁决的覆盖增益，见 VerdictEffect）——
    //  只加光柱半径与领域出剑速度，不加伤害：伤害已由 VerdictDamage 定死成 25 + 5。
    public static final RegistryObject<MobEffect> VERDICT =
            registerEffect("verdict", () -> org.bytechen.hall.overworld.registry.effect.VerdictEffect.INSTANCE);
}
