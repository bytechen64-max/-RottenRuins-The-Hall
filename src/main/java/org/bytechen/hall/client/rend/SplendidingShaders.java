package org.bytechen.hall.client.rend;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderType;
import org.bytechen.hall.mixin.accessor.AccessorRenderStateShard;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SplendidingShaders {

    // ── shader instances ──
    public static ShaderInstance lightningOutlineShader;
    public static ShaderInstance itemGlintShader;
    public static ShaderInstance worldOutlineShader;
    public static ShaderInstance gradientOutlineShader;
    public static ShaderInstance shockwaveShader;

    // ── GUI overlay shaders ──
    public static ShaderInstance guiHorrorVoronoiShader;
    public static ShaderInstance guiGlowShader;
    public static ShaderInstance blackHoleShader;

    // ── render types (lazy) ──
    private static RenderType lightningOutlineRenderType;
    private static RenderType itemGlintRenderType;
    private static RenderType worldOutlineRenderType;
    private static RenderType gradientOutlineRenderType;
    private static RenderType shockwaveRenderType;

    // ── named lookup for ICustomOutline.outlineShaderKey() ──
    private static final Map<String, ShaderInstance> OUTLINE_SHADERS = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> OUTLINE_RENDER_TYPES = new ConcurrentHashMap<>();
    // Late-deferred RenderTypes for shader-pack-compatible two-phase replay
    private static final Map<String, RenderType> LATE_WORLD_RENDER_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> LATE_HAND_RENDER_TYPES = new ConcurrentHashMap<>();
    // World-space outline RenderTypes (LEQUAL depth, COLOR_WRITE) for correct occlusion
    private static final Map<String, RenderType> WORLD_OUTLINE_RENDER_TYPES = new ConcurrentHashMap<>();
    public static ShaderInstance warpFbmOutlineShader;
    private static RenderType warpFbmOutlineRenderType;

    public static final String KEY_DEFAULT = "default";
    public static final String KEY_GRADIENT = "gradient";
    public static final String KEY_WARP_FBM = "warp_fbm";
    public static final String KEY_DEFAULT_SP = "default_sp";
    public static final String KEY_GRADIENT_SP = "gradient_sp";
    public static final String KEY_WARP_FBM_SP = "warp_fbm_sp";

    public static String spKey(String k) {
        return switch (k) { case KEY_DEFAULT -> KEY_DEFAULT_SP; case KEY_GRADIENT -> KEY_GRADIENT_SP; case KEY_WARP_FBM -> KEY_WARP_FBM_SP; default -> KEY_DEFAULT_SP; };
    }

    // ── registration ──

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        // lightning outline
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "lightning_outline"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> lightningOutlineShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

        // GUI glint
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_item_glint"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> itemGlintShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

        // world outline (default)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_item_world_outline"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        worldOutlineShader = shader;
                        OUTLINE_SHADERS.put(KEY_DEFAULT, shader);
                        worldOutlineRenderType = makeNoDepthRenderType("item_world_outline", () -> worldOutlineShader);
                        OUTLINE_RENDER_TYPES.put(KEY_DEFAULT, worldOutlineRenderType);
                        // World-space LEQUAL variant for correct occlusion
                        WORLD_OUTLINE_RENDER_TYPES.put(KEY_DEFAULT,
                                makeLequalOutlineRenderType("item_world_outline_w", () -> shader));
                        LATE_WORLD_RENDER_TYPES.put(KEY_DEFAULT,
                                LateOutlineRenderType.createLateWorldOutline(() -> shader, "world_outline"));
                        LATE_HAND_RENDER_TYPES.put(KEY_DEFAULT,
                                LateOutlineRenderType.createLateHandOutline(() -> shader, "world_outline"));
                    });
        } catch (IOException e) { e.printStackTrace(); }

        // gradient outline
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_item_gradient_outline"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        gradientOutlineShader = shader;
                        OUTLINE_SHADERS.put(KEY_GRADIENT, shader);
                        gradientOutlineRenderType = makeNoDepthRenderType("item_gradient_outline", () -> gradientOutlineShader);
                        OUTLINE_RENDER_TYPES.put(KEY_GRADIENT, gradientOutlineRenderType);
                        WORLD_OUTLINE_RENDER_TYPES.put(KEY_GRADIENT,
                                makeLequalOutlineRenderType("item_gradient_outline_w", () -> shader));
                        LATE_WORLD_RENDER_TYPES.put(KEY_GRADIENT,
                                LateOutlineRenderType.createLateWorldOutline(() -> shader, "gradient_outline"));
                        LATE_HAND_RENDER_TYPES.put(KEY_GRADIENT,
                                LateOutlineRenderType.createLateHandOutline(() -> shader, "gradient_outline"));
                    });
        } catch (IOException e) { e.printStackTrace(); }

        // warp fbm outline
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_item_warp_fbm"),
                            DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        warpFbmOutlineShader = shader;
                        OUTLINE_SHADERS.put(KEY_WARP_FBM, shader);
                        warpFbmOutlineRenderType = makeNoDepthRenderType("item_warp_fbm", () -> warpFbmOutlineShader);
                        OUTLINE_RENDER_TYPES.put(KEY_WARP_FBM, warpFbmOutlineRenderType);
                        WORLD_OUTLINE_RENDER_TYPES.put(KEY_WARP_FBM,
                                makeLequalOutlineRenderType("item_warp_fbm_w", () -> shader));
                        LATE_WORLD_RENDER_TYPES.put(KEY_WARP_FBM,
                                LateOutlineRenderType.createLateWorldOutline(() -> shader, "warp_fbm"));
                        LATE_HAND_RENDER_TYPES.put(KEY_WARP_FBM,
                                LateOutlineRenderType.createLateHandOutline(() -> shader, "warp_fbm"));
                    });
        } catch (IOException e) { e.printStackTrace(); }

        // _sp variants (identity vertex, no ProjMat/ModelViewMat, for shader pack replay)
        regSp(event, "rendertype_item_world_outline_sp", KEY_DEFAULT_SP);
        regSp(event, "rendertype_item_gradient_outline_sp", KEY_GRADIENT_SP);
        regSp(event, "rendertype_item_warp_fbm_sp", KEY_WARP_FBM_SP);

        // shockwave
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_shockwave"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> shockwaveShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

        // GUI horror voronoi overlay (fullscreen post-process)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_gui_horror_voronoi"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiHorrorVoronoiShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

        // GUI icon glow (texture-based edge dilation for difficulty select)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_gui_glow"),
                            DefaultVertexFormat.POSITION_TEX),
                    shader -> guiGlowShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

        // black hole (raymarched gravitational lensing, view-space billboard)
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, "rendertype_blackhole"),
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> blackHoleShader = shader);
        } catch (IOException e) { e.printStackTrace(); }

    }

    private static void regSp(RegisterShadersEvent ev, String path, String key) {
        try {
            ev.registerShader(
                    new ShaderInstance(ev.getResourceProvider(),
                            new ResourceLocation(HallMod.MODID, path),
                            DefaultVertexFormat.NEW_ENTITY),
                    s -> {
                        OUTLINE_SHADERS.put(key, s);
                        OUTLINE_RENDER_TYPES.put(key, makeNoDepthRenderType(path, () -> s));
                    });
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── public lookups for ICustomOutline ──

    @Nullable public static ShaderInstance getOutlineShader(String key) { return OUTLINE_SHADERS.get(key); }
    @Nullable public static RenderType getOutlineRenderType(String key) { return OUTLINE_RENDER_TYPES.get(key); }

    /**
     * Look up the late-deferred world-space RenderType for the given shader key.
     * Uses {@code LEQUAL} depth test + polygon offset + {@code MAIN_TARGET} + {@code COLOR_WRITE}.
     * Returns {@code null} if no late RenderType has been registered for this key.
     */
    @Nullable public static RenderType getLateWorldOutlineRenderType(String key) {
        return LATE_WORLD_RENDER_TYPES.get(key);
    }

    /**
     * Look up the late-deferred first-person-hand RenderType for the given shader key.
     * Uses {@code NO_DEPTH_TEST} + {@code MAIN_TARGET} + {@code COLOR_WRITE}.
     * Returns {@code null} if no late RenderType has been registered for this key.
     */
    @Nullable public static RenderType getLateHandOutlineRenderType(String key) {
        return LATE_HAND_RENDER_TYPES.get(key);
    }

    /**
     * Look up the LEQUAL-depth world-space outline RenderType for the given key.
     * Blocks/entities in front of the item will occlude the outline.
     * Falls back to the regular NO_DEPTH type if no LEQUAL variant is registered.
     */
    @Nullable public static RenderType getWorldOutlineRenderType(String key) {
        // For world-space contexts, prefer LEQUAL; fall back to NO_DEPTH
        RenderType t = WORLD_OUTLINE_RENDER_TYPES.get(key);
        return t != null ? t : OUTLINE_RENDER_TYPES.get(key);
    }

    public static void registerOutlineShader(String key, ShaderInstance shader, RenderType type) {
        OUTLINE_SHADERS.put(key, shader);
        OUTLINE_RENDER_TYPES.put(key, type);
    }

    // ── built-in render types ──

    public static RenderType getLightningOutlineRenderType() {
        if (lightningOutlineShader == null) return null;
        if (lightningOutlineRenderType == null) {
            lightningOutlineRenderType = makeRenderType("lightning_outline",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES,
                    () -> lightningOutlineShader, null, true, true);
        }
        return lightningOutlineRenderType;
    }

    public static RenderType getItemGlintRenderType() {
        if (itemGlintShader == null) return null;
        if (itemGlintRenderType == null) {
            itemGlintRenderType = makeRenderType("item_glint",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    () -> itemGlintShader, null, true, false);
        }
        return itemGlintRenderType;
    }

    public static RenderType getItemWorldOutlineRenderType() {
        if (worldOutlineShader == null) return null;
        if (worldOutlineRenderType == null) {
            worldOutlineRenderType = makeNoDepthRenderType("item_world_outline",
                    () -> worldOutlineShader);
            OUTLINE_RENDER_TYPES.put(KEY_DEFAULT, worldOutlineRenderType);
        }
        return worldOutlineRenderType;
    }

    public static RenderType getItemGradientOutlineRenderType() {
        if (gradientOutlineShader == null) return null;
        if (gradientOutlineRenderType == null) {
            gradientOutlineRenderType = makeNoDepthRenderType("item_gradient_outline",
                    () -> gradientOutlineShader);
            OUTLINE_RENDER_TYPES.put(KEY_GRADIENT, gradientOutlineRenderType);
        }
        return gradientOutlineRenderType;
    }

    public static RenderType getShockwaveRenderType() {
        if (shockwaveShader == null) return null;
        if (shockwaveRenderType == null) {
            // Large buffer: 32×64×6 = 12288 vertices, ~344KB
            shockwaveRenderType = RenderType.create(HallMod.MODID + ":shockwave",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 524288,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shockwaveShader))
                            .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                            .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                            .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                            .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                            .createCompositeState(false));
        }
        return shockwaveRenderType;
    }

    /**
     * Late-deferred shockwave RenderType for shader-pack compatibility.
     * Same as {@link #getShockwaveRenderType()} but with {@code MAIN_TARGET}
     * output so the shockwave writes directly to the main framebuffer,
     * bypassing the shader pack's GBuffer pipeline.
     */
    public static RenderType getLateShockwaveRenderType() {
        if (shockwaveShader == null) return null;
        return RenderType.create(HallMod.MODID + ":late_shockwave",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 524288,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> shockwaveShader))
                        .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                        .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                        .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                        .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                        .setOutputState(AccessorRenderStateShard.splendiding$getMainTarget())
                        .createCompositeState(false));
    }

    // ── helpers ──

    private static RenderType makeRenderType(String name, VertexFormat format,
                                              VertexFormat.Mode mode,
                                              java.util.function.Supplier<ShaderInstance> shader,
                                              @Nullable RenderStateShard.TextureStateShard tex,
                                              boolean depth, boolean depthWrite) {
        return RenderType.create(HallMod.MODID + ":" + name, format, mode, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(tex != null ? tex
                                : new RenderStateShard.EmptyTextureStateShard(() -> { }, () -> { }))
                        .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                        .setDepthTestState(depth
                                ? AccessorRenderStateShard.splendiding$getLequalDepthTest()
                                : AccessorRenderStateShard.splendiding$getNoDepthTest())
                        .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                        .setWriteMaskState(depthWrite
                                ? AccessorRenderStateShard.splendiding$getColorDepthWrite()
                                : AccessorRenderStateShard.splendiding$getColorWrite())
                        .createCompositeState(false));
    }

    private static RenderType makeNoDepthRenderType(String name,
                                                     java.util.function.Supplier<ShaderInstance> shader) {
        return RenderType.create(HallMod.MODID + ":" + name,
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                        .setDepthTestState(AccessorRenderStateShard.splendiding$getNoDepthTest())
                        .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                        .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                        .createCompositeState(false));
    }

    /** Same as makeNoDepthRenderType but with LEQUAL depth for world-space occlusion. */
    private static RenderType makeLequalOutlineRenderType(String name,
                                                           java.util.function.Supplier<ShaderInstance> shader) {
        return RenderType.create(HallMod.MODID + ":" + name,
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(AccessorRenderStateShard.splendiding$getTranslucentTransparency())
                        .setDepthTestState(AccessorRenderStateShard.splendiding$getLequalDepthTest())
                        .setCullState(AccessorRenderStateShard.splendiding$getNoCull())
                        .setWriteMaskState(AccessorRenderStateShard.splendiding$getColorWrite())
                        .createCompositeState(false));
    }
}
