package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundEventRegistry {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HallMod.MODID);

    public static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, name)));
    }

    // Your sounds here
    // public static final RegistryObject<SoundEvent> EXAMPLE_SOUND = registerSound("example_sound");

    /** 群系背景音乐 */
    public static final RegistryObject<SoundEvent> BIOME_MUSIC = registerSound("biome_music");
}
