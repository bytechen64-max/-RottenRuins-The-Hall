package org.bytechen.rottenruins.datagen.gen;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.SoundEventRegistry;
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

        // 爆体音效（移植自 corruption 的 rip_body）：
        // 声音文件 assets/hall/sounds/rip_body.ogg，字幕键 subtitles.rotten_ruins.rip_body。
        // 带字幕而不是裸声音：同一声"躯体爆裂"对玩家是有信息量的（说明有东西在附近炸了），
        // 字幕能让听障玩家 / 静音玩家也能察觉；LangDataCN/EN 里各有一条对应翻译。
        add(SoundEventRegistry.RIP_BODY.get(),
                definition()
                        .subtitle("subtitles." + HallMod.MODID + ".rip_body")
                        .with(sound(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "rip_body"))));
    }
}
