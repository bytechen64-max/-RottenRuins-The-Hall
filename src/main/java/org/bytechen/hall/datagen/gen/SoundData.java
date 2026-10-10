package org.bytechen.hall.datagen.gen;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.SoundEventRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SoundDefinitionsProvider;

public class SoundData extends SoundDefinitionsProvider {
    public SoundData(PackOutput output, ExistingFileHelper helper) { super(output, HallMod.MODID, helper); }

    @Override
    public void registerSounds() {
        // 群系背景音乐（长音乐建议 stream 流式加载）
        add(SoundEventRegistry.BIOME_MUSIC.get(),
                definition().with(
                        sound(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "biome_music")).stream()
                ));
    }
}
