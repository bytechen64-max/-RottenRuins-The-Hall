package org.bytechen.rottenruins.client.combat.slash.trail;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = "rotten_ruins", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SlashTrailShaders {
    public static ShaderInstance shader;
    public static ShaderInstance air;
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("rotten_ruins", "slash_trail"), DefaultVertexFormat.POSITION_COLOR_TEX),
                value -> shader = value);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("rotten_ruins", "slash_air"), DefaultVertexFormat.POSITION_COLOR_TEX),
                value -> air = value);
    }
}
