package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.mask.block.BlockMaskOverlayRenderer;
import org.bytechen.rottenruins.client.mask.render.MaskLayerRenderUtils;
import org.bytechen.rottenruins.client.mask.render.MaskLayerShaders;
import org.bytechen.rottenruins.client.mask.render.pass.DeathCoreMaskPass;
import org.bytechen.rottenruins.client.mask.render.pass.GlowMaskPass;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * mask 效果层系统的客户端初始化。
 *
 * <p>与 {@code CosmicClient} 平级：那边管星空，这边管可叠加的效果层。
 * 两者共用同一个物品渲染注入点（{@code MixinItemRendererCosmic}）和同一个光影
 * 延迟回放时机（{@code CosmicAfterLevelMixin}），所以这里不注册任何 mixin、
 * 也不需要在 {@code rotten_ruins.mixins.json} 里加东西。</p>
 */
public final class MaskLayerClient {

    /**
     * 在 mod 构造期（客户端侧）调用一次。
     *
     * @param modEventBus mod 事件总线
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MaskLayerClient::onRegisterGeometryLoaders);
        modEventBus.addListener(MaskLayerClient::onRegisterShaders);
        modEventBus.addListener(MaskLayerClient::onTextureStitch);

        // 内置效果在这里注册。效果表就是个普通 map，不依赖任何事件时机；
        // 之所以放在 init 而不是 FMLClientSetupEvent，是因为物品模型烘焙
        // （可能早于 client setup）解析层时就会去问「这个 effect id 存在吗」。
        MaskEffectRegistry.register(new GlowMaskPass());
        // 方块侧效果：ShaderToy "Ghosts" 改造的 death_core（死亡核心用）。
        MaskEffectRegistry.register(new DeathCoreMaskPass());

        HallMod.LOGGER.info("[MaskLayer] mask 效果层系统已就绪：内置效果 [{}]",
                GlowMaskPass.ID + ", " + DeathCoreMaskPass.ID);
    }

    private static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(GeometryLoaderMaskLayers.LOADER_ID, new GeometryLoaderMaskLayers());
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        MaskLayerShaders.onRegisterShaders(event);
    }

    private static void onTextureStitch(TextureStitchEvent event) {
        // quad 缓存以 sprite 实例为键；重新 stitch 会造出全新实例，旧条目必须丢弃，
        // 否则重启资源包后会一直画着旧贴图烘出来的几何。
        MaskLayerRenderUtils.clear();
        // 允许重新警告一次：着色器改了之后如果还是失败，日志里要能看到新的那条。
        MaskLayerResolver.resetWarnings();
        // 方块侧渲染器有自己的「只警告一次」表，同样在重载后复位。
        BlockMaskOverlayRenderer.resetWarnings();
    }

    private MaskLayerClient() {}
}
