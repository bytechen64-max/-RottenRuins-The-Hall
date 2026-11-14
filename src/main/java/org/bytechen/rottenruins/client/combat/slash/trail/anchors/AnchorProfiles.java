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
public final class AnchorProfiles extends SimpleJsonResourceReloadListener {
    private static final Map<ResourceLocation,AnchorProfile> PROFILES=new HashMap<>();
    private AnchorProfiles(){super(new Gson(),"slash_anchors");}
    public static AnchorProfile get(ItemStack stack){return PROFILES.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));}
    public static float lifetime(ItemStack stack){var p=get(stack);return p==null?0:Math.max(p.firstPerson().lifetimeTicks(),p.thirdPerson().lifetimeTicks());}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener(new AnchorProfiles());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        PROFILES.clear();
        resources.forEach((id,json)->load(id,json));
        Path folder=FMLPaths.CONFIGDIR.get().resolve("rotten_ruins/slash_anchors");
        if(Files.isDirectory(folder))try(var files=Files.walk(folder)){
            files.filter(p->p.toString().endsWith(".json")).sorted().forEach(path->{
                try {
                    String relative=folder.relativize(path).toString().replace('\\','/');
                    int slash=relative.indexOf('/');
                    if(slash>0)load(ResourceLocation.fromNamespaceAndPath(relative.substring(0,slash),relative.substring(slash+1,relative.length()-5)),JsonParser.parseString(Files.readString(path)));
                }catch(Exception error){HallMod.LOGGER.warn("Invalid slash anchor override {}: {}",path,error.getMessage());}
            });
        }catch(Exception error){HallMod.LOGGER.warn("Cannot read slash anchors: {}",error.getMessage());}
        TrackedSlashTrails.clear();
        HallMod.LOGGER.info("Loaded {} slash anchor profiles",PROFILES.size());
    }
    private static void load(ResourceLocation id,JsonElement json){
        try{PROFILES.put(id,AnchorProfile.parse(json.getAsJsonObject()));}
        catch(Exception error){HallMod.LOGGER.warn("Invalid slash anchors {}: {}",id,error.getMessage());}
    }
}
