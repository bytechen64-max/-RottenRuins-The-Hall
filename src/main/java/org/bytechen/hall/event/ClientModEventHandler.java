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
import org.bytechen.hall.client.entity.render.impl.VerdictBeamRenderer;
import org.bytechen.hall.client.entity.render.impl.VerdictFieldRenderer;
import org.bytechen.hall.client.entity.render.impl.VerdictSwordDropRenderer;
import org.bytechen.hall.client.particle.HeartLoseParticle;
import org.bytechen.hall.client.particle.UlceratedMeatParticle;
import org.bytechen.hall.client.rend.backplate.CrimsonVowBackplateLayer;
import org.bytechen.hall.client.tooltip.ClientBlockBarTooltip;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.client.rend.glint.GlintEffectProfile;
import org.bytechen.hall.client.rend.glint.GlintRenderManager;
import org.bytechen.hall.client.rend.glint.HeldItemGlintHelper;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineSettings;
import org.bytechen.hall.overworld.registry.RegisterItem;
import org.bytechen.hall.overworld.registry.RegisterParticles;
import org.bytechen.hall.overworld.registry.entities.base.EntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallEntityManager;
import org.bytechen.hall.overworld.registry.entities.base.HallProjectileManager;
import org.bytechen.hall.overworld.registry.items.BlockBarTooltip;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
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

        // 自定义渲染器 —— 天穹裁决的光柱（自主发光体积光束，加法混合）
        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_BEAM.get(), VerdictBeamRenderer::new);

        // 自定义渲染器 —— 裁决领域（地面光纹圆盘 + 悬浮二十面体棱片）
        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_FIELD.get(), VerdictFieldRenderer::new);

        // 自定义渲染器 —— 裁决落剑（从天而降、落地插入地面的剑气）
        event.registerEntityRenderer(EntityTypeRegistry.VERDICT_SWORD_DROP.get(),
                VerdictSwordDropRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeoBaseRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<?> type) {
        event.registerEntityRenderer((EntityType) type, GeoBaseRender::new);
    }

    /**
     * 玩家渲染层：绯红誓约的背板。
     *
     * <h3>为什么是 {@code AddLayers} 而不是 {@code RegisterRenderers}</h3>
     * <p>玩家的渲染器不是我们注册的（原版自己注册），能插手的只有"往里加一层" ——
     * 这正是 {@code AddLayers} 的用途。它同时给了皮肤表，而玩家渲染器在
     * {@code "default"}（粗手臂）与 {@code "slim"}（细手臂）两个皮肤下是
     * <b>两个不同的实例</b>，所以两个都得挂 —— 只挂 default 的症状是
     * "细手臂皮肤背板不显示"，很容易被当成"没生效"。</p>
     *
     * <p>用 {@code getSkin} 而不是 {@code getPlayerSkin}：后者返回的是
     * 泛化的 {@code EntityRenderer}，加层需要 {@code LivingEntityRenderer}。</p>
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
        // 溃烂肉屑
        event.registerSpriteSet(RegisterParticles.ULCERATED_MEAT.get(), UlceratedMeatParticle.Provider::new);
        // 失心粒子 —— 序列帧，5 张（heart_lose0..4），按顺序播放
        event.registerSpriteSet(RegisterParticles.HEART_LOSE.get(), HeartLoseParticle.Provider::new);
    }

    /**
     * 格挡武器 tooltip 里的自绘减伤条（绯红誓约 / 寂寒白日共用）。
     *
     * <h3>为什么必须按类型注册，而不是让物品自己去画</h3>
     * <p>Forge 的工厂表是按 {@code TooltipComponent.getClass()} <b>精确匹配</b>的
     * （见 {@code ClientTooltipComponentManager}），而物品侧只能给出数据
     * （{@code Item#getTooltipImage} 是公共代码，不能碰 {@code ClientTooltipComponent}）。
     * 两边的对接点就是这里：数据类 {@link BlockBarTooltip}
     * → 绘制类 {@link ClientBlockBarTooltip}。</p>
     *
     * <p>本事件挂在 <b>mod 总线</b>上（不是 Forge 总线），所以它和旁边的渲染器注册
     * 一样写在本类里；漏注册的症状是打开背包就崩，异常信息里那句
     * "Unknown TooltipComponent" 就是它。</p>
     */
    @SubscribeEvent
    public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BlockBarTooltip.class, ClientBlockBarTooltip::new);
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

        // void_sword 不在这里注册。它走 ICustomOutline 接口（见 VoidSword 类），
        // 因为 GlintEffectProfile 是"描边 + GUI 辉光"打包的 —— 注册一个 profile
        // 顺带会把辉光画到物品表面，把那层宇宙星空糊掉。
        // 接口路径下 glintSettings() 返回 null，就只描边、不动表面。
    }

}
