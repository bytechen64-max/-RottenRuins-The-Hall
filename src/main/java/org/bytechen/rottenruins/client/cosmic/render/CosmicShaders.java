package org.bytechen.rottenruins.client.cosmic.render;

import org.bytechen.rottenruins.HallMod;
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
 * Cosmic shader registration and uniform management (externalScale is 100.0 in GUI, 1.0 in world).
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

    /** 遮罩 sprite 的图集 UV 矩形（{@link #setMaskSlice} 写入）：着色器取不到图集位置。 */
    public static Uniform maskUvMinUniform;
    public static Uniform maskUvSizeUniform;

    /** Corruption shader — RGB split + colour shift + scanlines + glitch. */
    public static ShaderInstance corruptionShader;
    public static Uniform corruptionTimeUniform;
    public static Uniform corruptionIntensityUniform;

    /** 当前是否 GUI 渲染（星星不跟随相机）。 */
    public static boolean cosmicInventoryRender = false;

    /** 12 个 cosmic sprite 的图集 UV：每个 4 个 float，依次 u0,v0,u1,v1。 */
    public static float[] COSMIC_UVS = new float[48];
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

    /** 写入遮罩 sprite 的图集 UV 矩形；即时路径与 baked 回放路径都必须调用。 */
    public static void setMaskSlice(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) return;
        if (maskUvMinUniform != null)  maskUvMinUniform.set(sprite.getU0(), sprite.getV0());
        if (maskUvSizeUniform != null) maskUvSizeUniform.set(
                sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0());
    }

    // Embeddium 每 tick 重置 sprite 的 active 标记；cosmic sprite 只经 uniform 采样，
    // 走不到标准顶点路径，所以每帧用反射补标 active。
    private static volatile Method markSpriteActiveMethod;
    private static volatile boolean markSpriteActiveResolved;

    private static void resolveMarkSpriteActive() {
        if (!markSpriteActiveResolved) {
            try {
                markSpriteActiveMethod = Class.forName(
                        "me.jellysquid.mods.sodium.client.render.texture.SpriteUtil")
                        .getMethod("markSpriteActive", TextureAtlasSprite.class);
            } catch (Throwable ignored) {
            }
            markSpriteActiveResolved = true;
        }
    }

    /** 每帧调用：标记 12 个 cosmic sprite 活跃；无 Embeddium/Sodium 时空操作。 */
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
