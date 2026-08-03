package org.bytechen.hall.event;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.ClientPacketHandlers;
import org.bytechen.hall.client.entity.render.impl.BlackHoleRenderer;
import org.bytechen.hall.client.entity.render.impl.CollapseRenderer;
import org.bytechen.hall.client.entity.render.GeoBaseRender;
import org.bytechen.hall.client.entity.render.GeoEntityRender;
import org.bytechen.hall.client.entity.render.GeoProjectileRenderer;
import org.bytechen.hall.client.entity.render.impl.MeteoriteRenderer;
import org.bytechen.hall.client.entity.render.impl.ShockwaveRenderer;
import org.bytechen.hall.client.entity.render.impl.SwordAuraRenderer;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.client.rend.glint.GlintEffectProfile;
import org.bytechen.hall.client.rend.glint.GlintRenderManager;
import org.bytechen.hall.client.rend.glint.HeldItemGlintHelper;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineSettings;
import org.bytechen.hall.overworld.registry.RegisterItem;
import org.bytechen.hall.overworld.registry.entities.base.EntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallProjectileManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEventHandler {

    private static final ResourceLocation DUMMY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    @SubscribeEvent
    public static void onEntityRenderersRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (EntityType<?> type : HallEntityManager.consumeRenderTypes())
            registerGeoBaseRenderer(event, type);
        for (EntityType<?> type : HallProjectileManager.consumeProjectileTypes())
            registerGeoProjectileRenderer(event, type);
        for (EntityType<?> type : EntityManager.consumeSkillEntityTypes())
            registerGeoEntityRenderer(event, type);

        // 自定义渲染器 —— 陨石（无 GeckoLib 模型，程序化水滴网格）
        event.registerEntityRenderer(EntityTypeRegistry.METEORITE.get(), MeteoriteRenderer::new);

        // 自定义渲染器 —— 冲击波（warp 着色器，Möbius 条带 + 屏幕空间光线追踪）
        event.registerEntityRenderer(EntityTypeRegistry.SHOCKWAVE.get(), ShockwaveRenderer::new);

        // 自定义渲染器 —— 剑气（双锥体白色发光，逐渐缩小淡出）
        event.registerEntityRenderer(EntityTypeRegistry.SWORD_AURA.get(), SwordAuraRenderer::new);

        // 自定义渲染器 —— 坍缩（多类型坍缩渲染：末影龙式/超立方体/二十面体/变换多棱柱）
        event.registerEntityRenderer(EntityTypeRegistry.COLLAPSE.get(), CollapseRenderer::new);

        // 自定义渲染器 —— 黑洞（光线追踪引力透镜，视空间 billboard，光影兼容）
        event.registerEntityRenderer(EntityTypeRegistry.BLACK_HOLE.get(), BlackHoleRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeoBaseRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type) {
        event.registerEntityRenderer((EntityType) type, GeoBaseRender::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeoProjectileRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type) {
        event.registerEntityRenderer((EntityType) type, GeoProjectileRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeoEntityRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type) {
        event.registerEntityRenderer((EntityType) type, GeoEntityRender::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Entity> void registerNoopRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type) {
        event.registerEntityRenderer(type, ctx -> new EntityRenderer<T>(ctx) {
            @Override public ResourceLocation getTextureLocation(T entity) { return DUMMY_TEXTURE; }
        });
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        // Register particle providers here
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientEventHelpers.setupClientRenderLayers();
        ClientPacketHandlers.init();

        // Use VERTEX_SHADER outline for all contexts (first/third/ground)
        HeldItemOutlineSettings.setOutlineMode(HeldItemOutlineSettings.OutlineMode.VERTEX_SHADER);
        HeldItemOutlineSettings.setColorMode(HeldItemOutlineSettings.ColorMode.AUTO_SAMPLE_SCROLL);
        HeldItemOutlineSettings.setBloomEnabled(false);
        HeldItemGlintHelper.applyDefaultPreset();
        HeldItemGlintHelper.enableForItems(
                RegisterItem.EXAMPLE_ITEM.get());

        registerGlintProfiles();

        HallMod.LOGGER.info("Splendiding item shader system initialized");
    }

    private static void registerGlintProfiles() {
        // example_item — glint + tinted warp_fbm outline
        GlintRenderManager.registerForItem(RegisterItem.EXAMPLE_ITEM.get(),
                GlintEffectProfile.builder()
                        .colorMode(GlintEffectProfile.ColorMode.AUTO_SAMPLE_SCROLL)
                        .bloomStrength(0.6f).bloomRadius(1.2f)
                        .speed(0.9f).intensity(0.85f)
                        .outlineShaderKey("warp_fbm")
                        .build());

        // void_sword — same warp_fbm outline as example_item, purple-gold tint
//        GlintRenderManager.registerForItem(RegisterItem.VOID_SWORD.get(),
//                GlintEffectProfile.builder()
//                        .worldOutlineWidth(0.1f)
//                        .color(0.63f, 0.13f, 1.0f)                      // purple
//                        .secondaryColor(1.0f, 0.84f, 0.0f)              // gold
//                        .colorMode(GlintEffectProfile.ColorMode.AUTO_SAMPLE_SCROLL)
//                        .bloomStrength(0.6f).bloomRadius(1.2f)
//                        .speed(0.9f).intensity(0.85f)
//                        .outlineShaderKey("warp_fbm")
//                        .build());
    }

}
