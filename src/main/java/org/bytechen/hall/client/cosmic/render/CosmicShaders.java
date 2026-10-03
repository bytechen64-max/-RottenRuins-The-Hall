package org.bytechen.hall.client.cosmic.render;

import org.bytechen.hall.HallMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import com.mojang.blaze3d.shaders.Uniform;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Cosmic shader registration and uniform management, matching
 * {@code mystery_buding.live.render.CosmicShaders} exactly.
 *
 * <h3>Shader uniforms</h3>
 * <table>
 *   <tr><th>Uniform</th><th>Type</th><th>Purpose</th></tr>
 *   <tr><td>{@code time}</td><td>float</td><td>Game time for animation</td></tr>
 *   <tr><td>{@code yaw}</td><td>float</td><td>Player yaw (camera rotation)</td></tr>
 *   <tr><td>{@code pitch}</td><td>float</td><td>Player pitch (camera rotation)</td></tr>
 *   <tr><td>{@code externalScale}</td><td>float</td><td>100.0 in GUI, 1.0 in world</td></tr>
 *   <tr><td>{@code opacity}</td><td>float</td><td>Always 1.0</td></tr>
 *   <tr><td>{@code cosmicuvs}</td><td>mat2x2[12]</td><td>UV rects of 12 cosmic sprites</td></tr>
 *   <tr><td>{@code maskUvMin} / {@code maskUvSize}</td><td>vec2</td>
 *       <td>遮罩 sprite 在图集里的 UV 矩形（{@link #setMaskSlice}），
 *           供"按物品自身坐标排图案"的 style（水面湍流）把图集 UV 折回 0..1</td></tr>
 * </table>
 *
 * <h3>Embeddium/Sodium compatibility</h3>
 * Embeddium's animation tracking system (animateOnlyVisibleTextures) only
 * updates sprite frames that are referenced by standard rendering paths.
 * Cosmic textures are sampled via shader uniforms (not via standard
 * vertex consumer), so the tracking system never sees them — and their
 * animation frames never advance.  We call
 * {@code SpriteUtil.markSpriteActive()} every frame for each of the 12
 * cosmic sprites to work around this.
 */
public class CosmicShaders {
    public static ShaderInstance cosmicShader;
    public static Uniform timeUniform;
    public static Uniform yawUniform;
    public static Uniform pitchUniform;
    public static Uniform externalScaleUniform;
    public static Uniform opacityUniform;
    public static Uniform useTypeUniform;
    public static Uniform cosmicuvsUniform;

    /**
     * 遮罩 sprite 在图集里的 UV 矩形（vec2 × 2），由 {@link #setMaskSlice} 写入。
     *
     * <p>cosmic 的 quad 用的是图集 UV，所以"按物品自身坐标排图案"的 style
     * （目前是 {@code SILENT_DAYLIGHT} 的水面湍流）必须先折回 sprite 内的 0..1。
     * 之所以在 Java 侧取，是因为着色器里拿不到 sprite 在图集里的位置。</p>
     */
    public static Uniform maskUvMinUniform;
    public static Uniform maskUvSizeUniform;

    /** Corruption shader — RGB split + colour shift + scanlines + glitch. */
    public static ShaderInstance corruptionShader;
    /** Corruption intensity uniform (0.0–1.0). */
    public static Uniform corruptionTimeUniform;
    public static Uniform corruptionIntensityUniform;

    /** Whether the current render is in a GUI (stars don't follow camera). */
    public static boolean cosmicInventoryRender = false;

    /** UV coordinates of 12 cosmic sprites in the block atlas (4 floats each: u0,v0,u1,v1). */
    public static float[] COSMIC_UVS = new float[48];
    /** The 12 cosmic sprites, resolved from the block atlas at stitch time. */
    public static TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[12];

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "cosmic"),
                            DefaultVertexFormat.BLOCK),
                    shader -> {
                        cosmicShader = shader;
                        timeUniform = shader.getUniform("time");
                        yawUniform = shader.getUniform("yaw");
                        pitchUniform = shader.getUniform("pitch");
                        externalScaleUniform = shader.getUniform("externalScale");
                        opacityUniform = shader.getUniform("opacity");
                        useTypeUniform = shader.getUniform("useType");
                        cosmicuvsUniform = shader.getUniform("cosmicuvs");
                        maskUvMinUniform = shader.getUniform("maskUvMin");
                        maskUvSizeUniform = shader.getUniform("maskUvSize");
                    }
            );
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "corruption"),
                            DefaultVertexFormat.BLOCK),
                    shader -> {
                        corruptionShader = shader;
                        corruptionTimeUniform = shader.getUniform("time");
                        corruptionIntensityUniform = shader.getUniform("intensity");
                    }
            );
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load Cosmic/Corruption shaders! Check assets/hall/shaders/core/", e);
        }
    }

    public static void onTextureAtlasStitched(TextureStitchEvent event) {
        if (!event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) return;
        TextureAtlas atlas = event.getAtlas();

        for (int i = 0; i < COSMIC_SPRITES.length; i++) {
            COSMIC_SPRITES[i] = atlas.getSprite(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "shader/cosmic_" + i));
            COSMIC_UVS[i * 4 + 0] = COSMIC_SPRITES[i].getU0();
            COSMIC_UVS[i * 4 + 1] = COSMIC_SPRITES[i].getV0();
            COSMIC_UVS[i * 4 + 2] = COSMIC_SPRITES[i].getU1();
            COSMIC_UVS[i * 4 + 3] = COSMIC_SPRITES[i].getV1();
        }
    }

    /**
     * 把遮罩 sprite 在图集里的 UV 矩形写进 {@code maskUvMin} / {@code maskUvSize}。
     *
     * <p>两个调用点：{@code MixinItemRendererCosmic} 的即时路径与
     * {@link org.bytechen.hall.client.cosmic.BakedModelCosmic#renderShaderLayer}
     * 的延迟回放路径 —— 光影包下走的是后者，漏掉任何一处都会让那条路径上的
     * 湍流图案退回"图集坐标直接当 uv"的错误换算。</p>
     *
     * <p>单位说明：{@code getU0/V0} 是 sprite 左上角、{@code getU1/V1} 是右下角，
     * 所以 size 是二者之差 —— 与 {@code MaskUniforms.setSlice} 同一个取法。</p>
     */
    public static void setMaskSlice(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) return;
        if (maskUvMinUniform != null)  maskUvMinUniform.set(sprite.getU0(), sprite.getV0());
        if (maskUvSizeUniform != null) maskUvSizeUniform.set(
                sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0());
    }

    // ── Embeddium / Sodium animation compatibility ──────────────────
    // Embeddium's SpriteContentsAnimatorImplMixin.postTick resets the
    // "active" flag on every sprite at the end of each tick.  Cosmic
    // sprites are never seen by the standard vertex consumer path, so
    // we must mark them active every frame via reflection.
    private static volatile Method markSpriteActiveMethod;
    private static volatile boolean markSpriteActiveResolved;

    private static void resolveMarkSpriteActive() {
        if (!markSpriteActiveResolved) {
            try {
                markSpriteActiveMethod = Class.forName(
                        "me.jellysquid.mods.sodium.client.render.texture.SpriteUtil")
                        .getMethod("markSpriteActive", TextureAtlasSprite.class);
            } catch (Throwable ignored) {
                // Embeddium/Sodium not present — vanilla animation system works fine
            }
            markSpriteActiveResolved = true;
        }
    }

    /**
     * Called every frame when rendering cosmic items.  Marks all 12 cosmic
     * sprites as active so Embeddium's animation ticker advances their
     * animation frames.  No-op when Embeddium/Sodium is not installed.
     */
    public static void markCosmicSpritesActive() {
        resolveMarkSpriteActive();
        if (markSpriteActiveMethod == null) return;
        try {
            for (TextureAtlasSprite sprite : COSMIC_SPRITES) {
                if (sprite != null) {
                    markSpriteActiveMethod.invoke(null, sprite);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
