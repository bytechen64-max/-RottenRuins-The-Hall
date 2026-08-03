package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side registration for the cosmic starfield shader system,
 * matching {@code mystery_buding.live.client.CosmicClient} exactly.
 *
 * <p>Registers:
 * <ul>
 *   <li>The {@code "splendiding:cosmic"} model loader</li>
 *   <li>The cosmic shader program</li>
 *   <li>Texture atlas stitching (captures cosmic sprite UVs)</li>
 *   <li>Screen render events (toggle cosmicInventoryRender flag)</li>
 * </ul>
 */
public class CosmicClient {

    /**
     * Initialize all cosmic rendering subsystems.  Must be called from
     * the mod constructor on the client dist only.
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CosmicClient::onRegisterGeometryLoaders);
        modEventBus.addListener(CosmicClient::onRegisterShaders);
        modEventBus.addListener(CosmicClient::onTextureAtlasStitched);

        MinecraftForge.EVENT_BUS.addListener(CosmicClient::onScreenRenderPre);
        MinecraftForge.EVENT_BUS.addListener(CosmicClient::onScreenRenderPost);
    }

    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("cosmic", new GeometryLoaderCosmic());
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        CosmicShaders.onRegisterShaders(event);
    }

    public static void onTextureAtlasStitched(TextureStitchEvent event) {
        CosmicShaders.onTextureAtlasStitched(event);
    }

    /** Flag set during GUI rendering — cosmic stars don't follow camera rotation. */
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        CosmicShaders.cosmicInventoryRender = true;
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        CosmicShaders.cosmicInventoryRender = false;
    }
}
