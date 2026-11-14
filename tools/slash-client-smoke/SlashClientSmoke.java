package org.bytechen.rottenruins.smoke;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.AnchorProfiles;
import org.bytechen.rottenruins.client.combat.slash.trail.SlashTrailShaders;
@Mod.EventBusSubscriber(modid="rotten_ruins",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SlashClientSmoke {
 private static boolean done;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  var mc=Minecraft.getInstance();
  if(done||event.phase!=TickEvent.Phase.END||mc.screen==null||mc.getOverlay()!=null)return;
  done=true;
  for(String name:new String[]{"true_copper","domerite_sword","domerite_longsword"}){
   var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("rotten_ruins",name));
   if(item==null||AnchorProfiles.get(new ItemStack(item))==null)throw new AssertionError("Missing live anchor profile: "+name);
   var effect=org.bytechen.rottenruins.client.combat.slash.trail.anchors.SlashEffects.get(new ItemStack(item));
   if(effect.width()<1.5F||effect.color().equals("inherit"))throw new AssertionError("JSON effect preset not loaded: "+name);
  }
  if(SlashTrailShaders.shader==null)throw new AssertionError("Missing live slash shader");
  SlashBlendProbe.run();
  SlashRefractionProbe.run();
  System.out.println("SLASH_CLIENT_SMOKE_PASS: client mixins loaded, three anchor profiles reloaded, GLSL linked");
  mc.stop();
 }
}
