package org.bytechen.hall.datagen.gen;

import org.bytechen.hall.HallMod;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SoundDefinitionsProvider;

public class SoundData extends SoundDefinitionsProvider {
    public SoundData(PackOutput output, ExistingFileHelper helper) { super(output, HallMod.MODID, helper); }

    @Override
    public void registerSounds() {
        // Add sounds here
    }
}
