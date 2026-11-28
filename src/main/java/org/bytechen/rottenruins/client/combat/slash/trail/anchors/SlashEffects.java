package org.bytechen.rottenruins.client.combat.slash.trail.anchors;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.HallMod;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="rotten_ruins",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SlashEffects extends SimpleJsonResourceReloadListener {
    private static final Map<ResourceLocation,SlashEffect> EFFECTS=new HashMap<>();
    private SlashEffects(){super(new Gson(),"slash_effects");}
    public static SlashEffect get(ItemStack stack){return EFFECTS.getOrDefault(ForgeRegistries.ITEMS.getKey(stack.getItem()),SlashEffect.DEFAULT);}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener(new SlashEffects());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        EFFECTS.clear();resources.forEach(SlashEffects::load);
        Path folder=FMLPaths.CONFIGDIR.get().resolve("rotten_ruins/slash_effects");
        if(Files.isDirectory(folder))try(var files=Files.walk(folder)){
            files.filter(p->p.toString().endsWith(".json")).sorted().forEach(path->{try{
                String relative=folder.relativize(path).toString().replace('\\','/');int slash=relative.indexOf('/');
                if(slash>0)load(ResourceLocation.fromNamespaceAndPath(relative.substring(0,slash),relative.substring(slash+1,relative.length()-5)),JsonParser.parseString(Files.readString(path)));
            }catch(Exception e){HallMod.LOGGER.warn("Invalid slash effect {}: {}",path,e.getMessage());}});
        }catch(Exception e){HallMod.LOGGER.warn("Cannot load slash effects: {}",e.getMessage());}
        TrackedSlashTrails.clear();HallMod.LOGGER.info("Loaded {} JSON slash effects",EFFECTS.size());
    }
    private static void load(ResourceLocation id,JsonElement json){try{EFFECTS.put(id,SlashEffect.parse(json.getAsJsonObject()));}catch(Exception e){HallMod.LOGGER.warn("Invalid slash effect {}: {}",id,e.getMessage());}}
}
