package org.bytechen.rottenruins.event;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.ClientPacketHandlers;
import org.bytechen.rottenruins.client.MagicClientHandler;
import org.bytechen.rottenruins.client.entity.render.impl.BlackHoleRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.CollapseRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.CrimsonVowChargeRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.CrimsonVowFieldRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.MeteorEventRenderer;
import org.bytechen.rottenruins.client.entity.render.GeoBaseRender;
import org.bytechen.rottenruins.client.entity.render.GeoEntityRender;
import org.bytechen.rottenruins.client.entity.render.GeoProjectileRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.MeteoriteRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.ShockwaveRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.SpearOfJudgmentRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.SwordAuraRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.VerdictBeamRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.VerdictFieldRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.VerdictSwordDropRenderer;
import org.bytechen.rottenruins.client.mask.block.DeathCoreRenderer;
import org.bytechen.rottenruins.client.particle.BloodParticle;
import org.bytechen.rottenruins.client.particle.ExplosionBloodyParticle;
import org.bytechen.rottenruins.client.particle.HeartLoseParticle;
import org.bytechen.rottenruins.client.particle.MeatParticle;
import org.bytechen.rottenruins.client.particle.UlceratedMeatParticle;
import org.bytechen.rottenruins.client.rend.backplate.CrimsonVowBackplateLayer;
import org.bytechen.rottenruins.client.tooltip.ClientBlockBarTooltip;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.client.rend.glint.GlintEffectProfile;
import org.bytechen.rottenruins.client.rend.glint.GlintRenderManager;
import org.bytechen.rottenruins.client.rend.glint.HeldItemGlintHelper;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineSettings;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;
import org.bytechen.rottenruins.overworld.registry.entities.base.EntityManager;
import org.bytechen.rottenruins.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.rottenruins.overworld.registry.entities.base.HallProjectileManager;
import net.minecraft.world.entity.Entity;
import org.bytechen.rottenruins.client.entity.render.SkillProjectileRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.CuboidRenderer;
import org.bytechen.rottenruins.client.entity.render.impl.ShieldRenderer;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.items.BlockBarTooltip;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.items.SimpleFieldMeterItem;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

        event.registerEntityRenderer(CorruptionEntityTypes.CORRUPTION_DART.get(), SkillProjectileRenderer::new);
        event.registerEntityRenderer(CorruptionEntityTypes.CORRUPTION_POOL.get(), SkillProjectileRenderer::new);
        event.registerEntityRenderer(CorruptionEntityTypes.CUBOID.get(), CuboidRenderer::new);
        event.registerEntityRenderer(CorruptionEntityTypes.SHIELD.get(), ShieldRenderer::new);
        for (EntityType<?> type : EntityManager.consumeSkillEntityTypes())
            registerGeoEntityRenderer(event, type);

        event.registerEntityRenderer(EntityTypeRegistry.METEORITE.get(), MeteoriteRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.SHOCKWAVE.get(), ShockwaveRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.SWORD_AURA.get(), SwordAuraRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.COLLAPSE.get(), CollapseRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.BLACK_HOLE.get(), BlackHoleRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_BEAM.get(), VerdictBeamRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_FIELD.get(), VerdictFieldRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_SWORD_DROP.get(),
                VerdictSwordDropRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.CRIMSON_VOW_CHARGE.get(),
                CrimsonVowChargeRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.CRIMSON_VOW_FIELD.get(),
                CrimsonVowFieldRenderer::new);
        event.registerEntityRenderer(EntityTypeRegistry.METEOR_EVENT.get(),
                MeteorEventRenderer::new);

        // 方块版 mask 效果层的唯一接入点：新方块只需加 BlockEntity（实现 BlockMaskLayerProvider）
        // + 一个同样三行的 BER，然后在这里注册，不需要新增 mixin。
        event.registerBlockEntityRenderer(RegisterBlockEntities.DEATH_CORE.get(),
                DeathCoreRenderer::new);
        // 火球用原版 ThrownItemRenderer：把 Fireball#getItem() 的贴图 billboard 出来（缩放 2.0、全亮）。
        event.registerEntityRenderer(EntityTypeRegistry.FIRE_BALL.get(),
                ctx -> new ThrownItemRenderer<>(ctx, 2.0F, true));

        // 审判之矛使用沿飞行方向的立体光矛，不复用物品图标。
        event.registerEntityRenderer(EntityTypeRegistry.SPEAR_OF_JUDGMENT.get(),
                SpearOfJudgmentRenderer::new);

        event.registerEntityRenderer(EntityTypeRegistry.OLYMPIAD_CUBE.get(),
                org.bytechen.rottenruins.client.entity.render.impl.OlympiadCubeRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeoBaseRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type) {
        event.registerEntityRenderer((EntityType) type, GeoBaseRender::new);
    }

    /**
     * 玩家渲染层（绯红誓约背板）。必须用 {@code AddLayers}：玩家渲染器是原版注册的，只能往里加层；
     * 且 {@code "default"}（粗手臂）与 {@code "slim"}（细手臂）是两个不同实例，两个都得挂 ——
     * 只挂 default 的症状是「细手臂皮肤背板不显示」。用 {@code getSkin}（返回 PlayerRenderer），
     * 不用返回泛化 EntityRenderer 的 {@code getPlayerSkin}。
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : new String[]{"default", "slim"}) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            CrimsonVowBackplateLayer.attachTo(renderer);
        }
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
        event.registerSpriteSet(RegisterParticles.ULCERATED_MEAT.get(), UlceratedMeatParticle.Provider::new);
        event.registerSpriteSet(RegisterParticles.HEART_LOSE.get(), HeartLoseParticle.Provider::new);
        event.registerSpriteSet(RegisterParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(RegisterParticles.MEAT.get(), MeatParticle.Provider::new);
        event.registerSpriteSet(RegisterParticles.EXPLOSION_BLOODY.get(), ExplosionBloodyParticle.Provider::new);
    }

    /**
     * 格挡武器减伤条的 tooltip 绘制类注册。Forge 工厂表按 {@code TooltipComponent.getClass()} 精确匹配，
     * 物品侧只能给数据（{@link BlockBarTooltip}），两边对接点在这里，对应绘制类 {@link ClientBlockBarTooltip}；
     * 漏注册的症状是打开背包就崩，异常里那句 "Unknown TooltipComponent" 就是它。
     */
    @SubscribeEvent
    public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BlockBarTooltip.class, ClientBlockBarTooltip::new);
    }

    /** 按键必须挂在 mod 总线的 {@link RegisterKeyMappingsEvent} 上，这是 1.20.1 唯一正确的时机。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MagicClientHandler.OPEN_MAGIC);
        event.register(MagicClientHandler.OPEN_WHEEL);
        event.register(MagicClientHandler.OPEN_LOADOUT);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(
                org.bytechen.rottenruins.overworld.registry.SourceMenus.SOURCE_MACHINE.get(),
                org.bytechen.rottenruins.client.gui.SourceMachineScreen::new));
        // 势能合成台：16 格环形 + 圆心产物，界面无贴图（全几何绘制 + 旋转/向心动画）
        event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(
                org.bytechen.rottenruins.overworld.registry.SourceMenus.POTENTIAL_CRAFTING.get(),
                org.bytechen.rottenruins.client.gui.PotentialCraftingScreen::new));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(RegisterBlock.SOURCE_WIRE.get(), RenderType.cutout()));
        event.enqueueWork(() -> ItemProperties.register(RegisterItem.SIMPLE_FIELD_METER.get(),
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "field_potential"),
                (stack, level, entity, seed) -> SimpleFieldMeterItem.getReading(stack) / 2.0F));
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
        GlintRenderManager.registerForItem(RegisterItem.EXAMPLE_ITEM.get(),
                GlintEffectProfile.builder()
                        .colorMode(GlintEffectProfile.ColorMode.AUTO_SAMPLE_SCROLL)
                        .bloomStrength(0.6f).bloomRadius(1.2f)
                        .speed(0.9f).intensity(0.85f)
                        .outlineShaderKey("warp_fbm")
                        .build());

        // void_sword 不在这里注册：它走 ICustomOutline 接口（glintSettings() 返回 null），
        // 因为注册 profile 会顺带把 GUI 辉光画到物品表面，糊掉那层宇宙星空。
    }

}
