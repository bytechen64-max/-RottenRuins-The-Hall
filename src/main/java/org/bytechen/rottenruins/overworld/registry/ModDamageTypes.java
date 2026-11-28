package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.bytechen.rottenruins.HallMod;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> APOPTOSIS = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(HallMod.MODID, "apoptosis"));

    private ModDamageTypes() {}
}
