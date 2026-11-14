package org.bytechen.rottenruins.overworld.registry;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.loot.SourceChargeSandLootModifier;
import org.bytechen.rottenruins.overworld.loot.StructureSpellLootModifier;

public final class RegisterLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HallMod.MODID);

    public static final RegistryObject<Codec<SourceChargeSandLootModifier>> SOURCE_CHARGE_SAND =
            SERIALIZERS.register("source_charge_sand", () -> SourceChargeSandLootModifier.CODEC);
    public static final RegistryObject<Codec<StructureSpellLootModifier>> STRUCTURE_SPELL =
            SERIALIZERS.register("structure_spell", () -> StructureSpellLootModifier.CODEC);
    public static final RegistryObject<Codec<org.bytechen.rottenruins.overworld.loot.VoidShardLootModifier>> VOID_SHARD =
            SERIALIZERS.register("void_shard", () -> org.bytechen.rottenruins.overworld.loot.VoidShardLootModifier.CODEC);

    private RegisterLootModifiers() {}
}
