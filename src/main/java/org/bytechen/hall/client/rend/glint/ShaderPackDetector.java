package org.bytechen.hall.client.rend.glint;

import net.minecraftforge.fml.ModList;
import org.bytechen.hall.utils.ModUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Single detection entry point for shader pack compatibility.
 * Replaces the multi-layer detection in {@link HeldItemOutlineCompat}
 * and the conditional mixin loading in {@code ItemGlintMixinPlugin}.
 *
 * <p>Architecture (inspired by ArcaneVortex's {@code Helper.isShaderPackActive}):
 * <ol>
 *   <li>Try Iris API ({@code net.irisshaders.iris.api.v0.IrisApi}) via reflection</li>
 *   <li>Fall back to OptiFine class existence check</li>
 *   <li>Returns {@code Boolean} with three-state semantics:
 *       {@code TRUE} = shader pack active,
 *       {@code FALSE} = loader present but no pack,
 *       {@code null} = no shader loader at all</li>
 * </ol>
 */
public final class ShaderPackDetector {

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String OPTIFINE_SHADERS_CLASS = "net.optifine.shaders.Shaders";

    private static final boolean OCULUS_LOADED;
    private static final boolean EMBEDDIUM_LOADED;
    private static final boolean IRIS_AVAILABLE;
    private static final boolean OPTIFINE_AVAILABLE;

    @Nullable
    private static final Method IRIS_GET_INSTANCE;
    @Nullable
    private static final Method IRIS_SHADER_PACK_IN_USE;
    @Nullable
    private static final Method IRIS_SHADOW_PASS;

    static {
        EMBEDDIUM_LOADED = ModList.get().isLoaded("embeddium");
        OCULUS_LOADED = ModList.get().isLoaded("oculus");

        Method getInstance = null;
        Method shaderPackInUse = null;
        Method shadowPass = null;
        boolean irisFound = false;

        if (OCULUS_LOADED) {
            try {
                Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
                getInstance = irisApiClass.getMethod("getInstance");
                shaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
                shadowPass = irisApiClass.getMethod("isRenderingShadowPass");
                irisFound = true;
            } catch (ReflectiveOperationException e) {
                ModUtils.LOGGER.warn("[ShaderPackDetector] Oculus loaded but Iris API unavailable", e);
            }
        }

        IRIS_GET_INSTANCE = getInstance;
        IRIS_SHADER_PACK_IN_USE = shaderPackInUse;
        IRIS_SHADOW_PASS = shadowPass;
        IRIS_AVAILABLE = irisFound;

        boolean optiFound = false;
        if (!IRIS_AVAILABLE) {
            try {
                Class.forName(OPTIFINE_SHADERS_CLASS);
                optiFound = true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        OPTIFINE_AVAILABLE = optiFound;

        ModUtils.LOGGER.info("[ShaderPackDetector] Detection complete: oculus={}, embeddium={}, iris={}, optifine={}",
                OCULUS_LOADED, EMBEDDIUM_LOADED, IRIS_AVAILABLE, OPTIFINE_AVAILABLE);
    }

    private ShaderPackDetector() {}

    // ── detection queries ──────────────────────────────────────────

    public static boolean isEmbeddiumLoaded() {
        return EMBEDDIUM_LOADED;
    }

    public static boolean isOculusLoaded() {
        return OCULUS_LOADED;
    }

    /**
     * Three-state result:
     * <ul>
     *   <li>{@code Boolean.TRUE} — shader pack is active right now</li>
     *   <li>{@code Boolean.FALSE} — shader loader installed but no pack loaded</li>
     *   <li>{@code null} — no shader loader present at all</li>
     * </ul>
     */
    @Nullable
    public static Boolean isShaderPackActive() {
        if (IRIS_AVAILABLE) {
            return invokeIrisBoolean(IRIS_SHADER_PACK_IN_USE);
        }
        if (OPTIFINE_AVAILABLE) {
            // OptiFine detected — we can't query runtime status via reflection,
            // so assume active when the class exists.
            return Boolean.TRUE;
        }
        return null;
    }

    public static boolean isShadowPass() {
        return invokeIrisBoolean(IRIS_SHADOW_PASS);
    }

    /**
     * Returns {@code true} when Embeddium + active Oculus shader pack pipeline
     * should be used (the combination that previously required special handling).
     */
    public static boolean shouldUseShaderPackPipeline() {
        Boolean active = isShaderPackActive();
        return active != null && active;
    }

    // ── effective mode resolution ──────────────────────────────────

    /**
     * Resolves the effective outline mode for the current environment.
     * When {@link HeldItemOutlineSettings.OutlineMode#AUTO} is selected,
     * this determines whether FBO or simplified post-processing should be used.
     *
     * @return {@code true} for FBO path (no shader pack), {@code false} for post-process path
     */
    public static boolean shouldUseFboPipeline() {
        return !shouldUseShaderPackPipeline();
    }

    // ── reflection helpers ─────────────────────────────────────────

    private static boolean invokeIrisBoolean(@Nullable Method method) {
        if (!IRIS_AVAILABLE || IRIS_GET_INSTANCE == null || method == null) {
            return false;
        }
        try {
            Object irisApi = IRIS_GET_INSTANCE.invoke(null);
            return Boolean.TRUE.equals(method.invoke(irisApi));
        } catch (ReflectiveOperationException e) {
            ModUtils.LOGGER.debug("[ShaderPackDetector] Iris API call failed: {}", method.getName());
            return false;
        }
    }
}
