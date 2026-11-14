package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.ModDamageTypes;

import java.util.concurrent.CompletableFuture;

public final class DamageTypeTagData extends TagsProvider<DamageType> {
    public DamageTypeTagData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                             ExistingFileHelper helper) {
        super(output, Registries.DAMAGE_TYPE, lookup, HallMod.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        tag(DamageTypeTags.BYPASSES_ARMOR).add(ModDamageTypes.APOPTOSIS);
    }
}
