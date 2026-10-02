package org.bytechen.hall.datagen;

import org.bytechen.hall.datagen.gen.*;
import org.bytechen.hall.datagen.gen.lang.LangDataCN;
import org.bytechen.hall.datagen.gen.lang.LangDataEN;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenEvent {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var efh = event.getExistingFileHelper();
        PackOutput out = event.getGenerator().getPackOutput();
        var lp = event.getLookupProvider();

        event.getGenerator().addProvider(event.includeClient(), new BlockStateData(out, efh));
        event.getGenerator().addProvider(event.includeClient(), new ItemGenData(out, efh));
        event.getGenerator().addProvider(event.includeClient(), new LangDataCN(out, "zh_cn"));
        event.getGenerator().addProvider(event.includeClient(), new LangDataEN(out, "en_us"));
        event.getGenerator().addProvider(event.includeClient(), new SoundData(out, efh));
        event.getGenerator().addProvider(event.includeClient(), new ParticleData(out,efh));
        event.getGenerator().addProvider(event.includeClient(), new EffectSpriteData(out, efh));
        event.getGenerator().addProvider(event.includeClient(), new EvolutionDataProvider(out));
        event.getGenerator().addProvider(event.includeClient(), new BlockSpreadDataProvider(out));

        event.getGenerator().addProvider(event.includeServer(), new HallWorldGenData(out, lp));

        // 方块标签与物品标签是两张独立的表 —— 物品标签必须由 BlockTagsProvider
        // 的 contentsGetter() 喂给 ItemTagsProvider，否则王庭木/石在原版配方里认不出来。
        BlockTagData blockTags = new BlockTagData(out, lp, efh);
        event.getGenerator().addProvider(event.includeServer(), blockTags);
        event.getGenerator().addProvider(event.includeServer(),
                new ItemTagData(out, blockTags.contentsGetter(), lp, efh));

        event.getGenerator().addProvider(event.includeServer(), new RecipeProviderData(out));
        event.getGenerator().addProvider(event.includeServer(), new LootTableData(out));
    }
}
