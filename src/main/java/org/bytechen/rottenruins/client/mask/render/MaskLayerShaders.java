package org.bytechen.rottenruins.client.mask.render;

import org.bytechen.rottenruins.HallMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * mask 效果层的着色器注册中心。
 *
 * <h3>注册回调是异步的</h3>
 * <p>{@code event.registerShader(shader, onLoaded)} 只是把「着色器 + 回调」记进
 * 队列，回调要等所有着色器都装载完才被调用 —— 所以赋值必须写在回调里，
 * 不能指望 {@code registerShader} 返回时字段已经有值。这也是项目里
 * {@code CosmicShaders} / {@code SplendidingShaders} 的既有写法。</p>
 *
 * <h3>为什么不缓存 uniform 句柄</h3>
 * <p>cosmic 那边在回调里就抓好 {@code timeUniform} 之类的句柄存进静态字段。
 * 这里不抓：效果实现每帧用 {@link org.bytechen.rottenruins.client.mask.MaskUniforms}
 * 按名字查（一次 HashMap 查找）。换来的是资源重载（F3+T）后句柄不会失效 ——
 * 抓句柄的写法在重载后会指向已经销毁的 Uniform，症状是「重载后效果就不动了」，
 * 很难第一时间联想到资源重载。</p>
 *
 * <p>错误一律走 LOGGER：{@code printStackTrace()} 只写 stderr、不进
 * {@code run/logs/latest.log}，于是「着色器没加载成功」会在日志里彻底消失 ——
 * 项目里已经因为这个坑查过半天（见 SplendidingShaders 的注释）。</p>
 */
public final class MaskLayerShaders {

    /** 泛光效果 {@code glow} 的着色器；未加载或加载失败时为 null。 */
    public static ShaderInstance glowShader;

    /** 方块效果 {@code death_core}（ShaderToy "Ghosts" 改）的着色器。 */
    public static ShaderInstance deathCoreShader;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, "mask_glow", shader -> glowShader = shader);
        register(event, "death_core", shader -> deathCoreShader = shader);
    }

    /**
     * 注册一个 mask 效果着色器。失败不抛：一个效果挂掉不该让整个客户端起不来。
     * 引用它的层会被 {@code MaskLayerResolver} / 方块侧渲染跳过并在日志里点名。
     */
    private static void register(RegisterShadersEvent event, String name,
                                 Consumer<ShaderInstance> target) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, name),
                            DefaultVertexFormat.BLOCK),
                    target);
        } catch (IOException e) {
            HallMod.LOGGER.error("[MaskLayer] 着色器 {} 加载失败，引用它的层将不可见"
                            + "（检查 assets/{}/shaders/core/{}.{{vsh,fsh,json}}）",
                    name, HallMod.MODID, name, e);
        }
    }

    /** 泛光着色器的当前实例。资源重载后这里会自动是新实例。 */
    @Nullable
    public static ShaderInstance glow() {
        return glowShader;
    }

    /** {@code death_core} 着色器的当前实例。 */
    @Nullable
    public static ShaderInstance deathCore() {
        return deathCoreShader;
    }

    private MaskLayerShaders() {}
}
