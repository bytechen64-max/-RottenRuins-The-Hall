package org.bytechen.rottenruins.overworld.registry;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.loot.SourceChargeSandLootModifier;

public final class RegisterLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HallMod.MODID);

    public static final RegistryObject<Codec<SourceChargeSandLootModifier>> SOURCE_CHARGE_SAND =
            SERIALIZERS.register("source_charge_sand", () -> SourceChargeSandLootModifier.CODEC);

    private RegisterLootModifiers() {}
}
