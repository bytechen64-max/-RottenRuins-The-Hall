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

    /**
     * 爆体音效 —— 尸体被"撑爆"的那一瞬间播放（移植自 corruption 的 {@code rip_body}）。
     *
     * <p>声音文件：{@code assets/hall/sounds/rip_body.ogg}（直接从 corruption 复制，未改音频）；
     * sounds.json 条目由 datagen（{@code SoundData}）生成，带字幕键
     * {@code subtitles.hall.rip_body}；播放点在 {@code AbstractHallEntity#onBurstEnd()}，
     * 由服务端 {@code playSound} 广播，附近所有玩家都能听到同一时刻的一声。</p>
     */
    public static final RegistryObject<SoundEvent> RIP_BODY = registerSound("rip_body");
}
