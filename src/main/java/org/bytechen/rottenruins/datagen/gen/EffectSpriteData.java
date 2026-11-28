package org.bytechen.rottenruins.datagen.gen;

import org.bytechen.rottenruins.HallMod;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SpriteSourceProvider;

public class EffectSpriteData extends SpriteSourceProvider {
    public EffectSpriteData(PackOutput output, ExistingFileHelper fileHelper) { super(output, fileHelper, HallMod.MODID); }

    @Override
    protected void addSources() {
        // Add effect sprite sources here
    }
}
