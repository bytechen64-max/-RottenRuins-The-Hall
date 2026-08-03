package org.bytechen.hall.client.rend.glint;

import org.bytechen.hall.utils.ModUtils;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.bytechen.hall.mixin.GameRendererAccessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class HeldItemOutlineRenderer {
    private static final int DEBUG_LOG_INTERVAL = 120;
    private static final long RULE_SWITCH_DELAY_MILLIS = 200L;
    private static final long ANIMATION_TIME_WRAP_TICKS = 240000L;
    private static final float BLOOM_BLUR_PASS_RADIUS = 2.5F;
    private static final float BLOOM_BLUR_KERNEL_RADIUS = 6.9230769F;
    private static final float SMALL_BLOOM_FAST_PATH_RADIUS = 1.0F;
    private static final int MIN_BLOOM_TARGET_SIZE = 64;
    // Increased from 8 to 28 to prevent outline clipping for items whose display
    // transform places them near the edge of the scissor rect (e.g. "handheld"
    // parent models with large first-person translations).
    private static final int SCISSOR_BASE_PADDING = 28;
    private static final int SCISSOR_BLOOM_PADDING = 4;
    private static final boolean ENABLE_MASK_DEBUG_READBACK = Boolean.getBoolean("hall.debugHeldOutlineReadback");
    private static final boolean ENABLE_COMPAT_DEBUG_LOG = Boolean.getBoolean("hall.debugCompat");
    private static final float OCULUS_HAND_DEPTH = 0.125F;
    private static final float[][] HAND_RENDER_BOUNDS_SAMPLES = createHandRenderBoundsSamples();
    @Nullable
    private static Object oculusHandRendererInstance;
    @Nullable
    private static Field oculusHandRendererActiveField;
    @Nullable
    private static Field oculusHandRendererRenderingSolidField;
    private static final CaptureState MAIN_HAND_STATE = new CaptureState(InteractionHand.MAIN_HAND);
    private static final CaptureState OFF_HAND_STATE = new CaptureState(InteractionHand.OFF_HAND);
    @Nullable
    private static CaptureState activeCaptureState;
    private static TextureTarget bloomBlurTargetA;
    private static TextureTarget bloomBlurTargetB;
    private static TextureTarget bloomBlurNearTarget;
    private static long lastDebugLogGameTime = Long.MIN_VALUE;
    private static long lastCompatDebugMillis = Long.MIN_VALUE;
    @Nullable
    private static RenderBuffers embeddiumCaptureRenderBuffers;
    @Nullable
    private static Matrix4f activeHandProjectionMatrix;

    private HeldItemOutlineRenderer() {}

    public static boolean shouldRenderOutlinePass(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return HeldItemOutlineShaderRegistry.getShader() != null && minecraft.level != null && player != null
                && shouldRenderOutline(minecraft) && !getRenderableHands(player).isEmpty();
    }

    public static List<HandEffectTarget> getRenderableHands(LocalPlayer player) {
        if (player == null) return List.of();
        List<HandEffectTarget> targets = new ArrayList<>(2);
        addHandTarget(targets, InteractionHand.MAIN_HAND, HeldItemOutlineSettings.isMainHandEnabled(), player.getMainHandItem());
        addHandTarget(targets, InteractionHand.OFF_HAND, HeldItemOutlineSettings.isOffHandEnabled(), player.getOffhandItem());
        return targets;
    }

    public static void beginItemInHandRender(@Nullable Matrix4f projectionMatrix) { activeHandProjectionMatrix = projectionMatrix == null ? null : new Matrix4f(projectionMatrix); }
    public static void endItemInHandRender() { activeHandProjectionMatrix = null; }

    public static boolean shouldBatchHands(@Nullable HandEffectTarget c, @Nullable HandEffectTarget n) {
        if (c == null || n == null) return false;
        return c.profile().equals(n.profile()) && sameSampledColors(c.sampledColors(), n.sampledColors());
    }

    public static boolean beginCapture(Minecraft minecraft, RenderTarget mainTarget, InteractionHand stateHand,
                                       @Nullable InteractionHand handFilter, @Nullable Matrix4f modelViewMatrix,
                                       HeldItemOutlineEffectProfile profile, HeldItemOutlineColorSampler.SampledColors sc) {
        ShaderInstance shader = HeldItemOutlineShaderRegistry.getShader();
        if (shader == null || minecraft.level == null || minecraft.player == null || !shouldRenderOutline(minecraft)) { clearAllFrameStates(); return false; }
        CaptureState state = stateFor(stateHand);
        state.resetImmediateFrameState();
        state.captureHandFilter = handFilter;
        state.capturedRenderState = new ResolvedRenderState(profile, sc);
        state.capturedModelViewMatrix = modelViewMatrix == null ? null : new Matrix4f(modelViewMatrix);
        state.scissorRect = handFilter != null ? resolveHandScissorRect(mainTarget, state.capturedModelViewMatrix, state.capturedRenderState) : null;
        ensureOutlineMaskTarget(mainTarget, state);
        state.outlineMaskTarget.setClearColor(0F, 0F, 0F, 0F);
        state.outlineMaskTarget.clear(Minecraft.ON_OSX);
        state.outlineMaskTarget.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        state.restoreTarget = mainTarget;
        state.captureActive = true;
        activeCaptureState = state;
        return true;
    }

    public static boolean beginEmbeddiumCompatCapture(Minecraft minecraft, RenderTarget mainTarget, InteractionHand stateHand,
                                                      @Nullable InteractionHand handFilter, @Nullable Matrix4f modelViewMatrix,
                                                      HeldItemOutlineEffectProfile profile, HeldItemOutlineColorSampler.SampledColors sc) {
        ShaderInstance shader = HeldItemOutlineShaderRegistry.getShader();
        if (shader == null || minecraft.level == null || minecraft.player == null || !shouldRenderOutline(minecraft)) {
            if (!isEmbeddiumCompatFrameQueued()) clearAllFrameStates();
            return false;
        }
        CaptureState state = stateFor(stateHand);
        ensureOutlineMaskTarget(mainTarget, state);
        if (!state.embeddiumCompatFramePrepared) {
            state.outlineMaskTarget.setClearColor(0F, 0F, 0F, 0F);
            state.outlineMaskTarget.clear(Minecraft.ON_OSX);
            state.capturedThisFrame = false; state.capturedHandCount = 0; state.lastSampledCoverage = 0F;
            state.embeddiumCompatFramePrepared = true;
        }
        state.captureHandFilter = handFilter;
        state.capturedRenderState = new ResolvedRenderState(profile, sc);
        state.capturedModelViewMatrix = modelViewMatrix == null ? null : new Matrix4f(modelViewMatrix);
        state.scissorRect = handFilter != null ? resolveHandScissorRect(mainTarget, state.capturedModelViewMatrix, state.capturedRenderState) : null;
        state.outlineMaskTarget.bindWrite(true);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        state.restoreTarget = mainTarget; state.captureActive = true; state.embeddiumCompatFrameQueued = true;
        activeCaptureState = state;
        return true;
    }

    public static void endCapture() {
        CaptureState state = activeCaptureState;
        if (state == null || !state.captureActive || state.restoreTarget == null) { if (state != null) { state.captureActive = false; state.restoreTarget = null; } activeCaptureState = null; return; }
        state.restoreTarget.bindWrite(true);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        state.captureActive = false; state.restoreTarget = null; activeCaptureState = null;
        Minecraft minecraft = Minecraft.getInstance();
        inspectMaskCoverage(minecraft, state);
        debugLog(minecraft, "Captured held-item mask: hand=" + state.hand + ", completed=" + state.capturedHandCount);
    }

    public static void endEmbeddiumCompatCapture() {
        CaptureState state = activeCaptureState;
        if (state == null || !state.captureActive || state.restoreTarget == null) { if (state != null) { state.captureActive = false; state.restoreTarget = null; } activeCaptureState = null; return; }
        state.restoreTarget.bindWrite(true);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        state.captureActive = false; state.restoreTarget = null; activeCaptureState = null;
    }

    public static void finishEmbeddiumCompatFrame(Minecraft minecraft, RenderTarget mainTarget) {
        if (!isEmbeddiumCompatFrameQueued()) { clearAllFrameStates(); return; }
        LocalPlayer player = minecraft.player;
        if (player == null) { clearAllFrameStates(); return; }
        List<HandEffectTarget> targets = getRenderableHands(player);
        for (int i = 0; i < targets.size(); i++) {
            HandEffectTarget t = targets.get(i);
            CaptureState s = stateFor(t.hand());
            if (!s.embeddiumCompatFrameQueued) continue;
            inspectMaskCoverage(minecraft, s);
            boolean shared = s.captureHandFilter == null;
            composite(minecraft, mainTarget, t.hand());
            if (shared && i + 1 < targets.size() && shouldBatchHands(t, targets.get(i + 1))) i++;
        }
    }

    public static MultiBufferSource.BufferSource getEmbeddiumCaptureBufferSource() {
        if (embeddiumCaptureRenderBuffers == null) embeddiumCaptureRenderBuffers = new RenderBuffers();
        return embeddiumCaptureRenderBuffers.bufferSource();
    }

    public static void renderOculusShaderpackCompatPass(Minecraft minecraft, PoseStack poseStack, float tickDelta,
                                                        Camera camera, GameRenderer gameRenderer, ItemInHandRenderer itemInHandRenderer,
                                                        LightTexture lightTexture, Matrix4f projectionMatrix, boolean solidPass) {
        if (!shouldRenderOutlinePass(minecraft)) return;
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;
        if (!accessor.getRenderHand() || accessor.getPanoramicMode() || camera.isDetached()
                || !(camera.getEntity() instanceof net.minecraft.world.entity.player.Player) || minecraft.gameMode == null) return;

        MultiBufferSource.BufferSource captureBufferSource = getEmbeddiumCaptureBufferSource();
        Matrix4f scaledProjection = new Matrix4f().scale(1F, 1F, OCULUS_HAND_DEPTH)
                .mul(gameRenderer.getProjectionMatrix(accessor.invokeGetFov(camera, tickDelta, false)));
        beginItemInHandRender(scaledProjection);
        int packedLight = minecraft.getEntityRenderDispatcher().getPackedLightCoords(camera.getEntity(), tickDelta);
        List<HandEffectTarget> targets = getRenderableHands(player);
        try {
            for (int i = 0; i < targets.size(); i++) {
                HandEffectTarget target = targets.get(i);
                boolean batch = shouldBatchHands(target, i + 1 < targets.size() ? targets.get(i + 1) : null);
                poseStack.pushPose();
                gameRenderer.resetProjectionMatrix(scaledProjection);
                PoseStack.Pose pose = poseStack.last(); pose.pose().identity(); pose.normal().identity();
                accessor.invokeBobHurt(poseStack, tickDelta);
                if (minecraft.options.bobView().get()) accessor.invokeBobView(poseStack, tickDelta);
                Matrix4f captureModelView = new Matrix4f(pose.pose());
                if (!beginEmbeddiumCompatCapture(minecraft, minecraft.getMainRenderTarget(), target.hand(),
                        batch ? null : target.hand(), captureModelView, target.profile(), target.sampledColors())) {
                    gameRenderer.resetProjectionMatrix(projectionMatrix); poseStack.popPose(); continue;
                }
                if (!setOculusHandRendererState(false, solidPass)) {
                    endEmbeddiumCompatCapture(); gameRenderer.resetProjectionMatrix(projectionMatrix); poseStack.popPose(); continue;
                }
                lightTexture.turnOnLightLayer();
                try {
                    itemInHandRenderer.renderHandsWithItems(tickDelta, poseStack, captureBufferSource, player, packedLight);
                    captureBufferSource.endBatch();
                } finally { setOculusHandRendererState(false, false); endEmbeddiumCompatCapture(); lightTexture.turnOffLightLayer(); gameRenderer.resetProjectionMatrix(projectionMatrix); poseStack.popPose(); }
                if (batch) i++;
            }
        } finally { endItemInHandRender(); }
    }

    public static void markHandCaptured() {
        if (activeCaptureState != null && activeCaptureState.captureActive) { activeCaptureState.capturedThisFrame = true; activeCaptureState.capturedHandCount++; }
    }

    public static void composite(Minecraft minecraft, RenderTarget mainTarget, InteractionHand hand) {
        CaptureState state = stateFor(hand);
        ResolvedRenderState rs = state.capturedRenderState;
        ShaderInstance outlineShader = HeldItemOutlineShaderRegistry.getShader();
        if (!state.capturedThisFrame || outlineShader == null || state.outlineMaskTarget == null || rs == null) { state.resetAfterComposite(); return; }
        mainTarget.bindWrite(true);
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        applyOutlineUniforms(minecraft, mainTarget, outlineShader, state.outlineMaskTarget, rs.profile(), rs.sampledColors());
        state.outlineMaskTarget.setFilterMode(9728); RenderSystem.defaultBlendFunc();
        applyScissor(state.scissorRect); drawFullscreenQuad(outlineShader); clearScissor();
        if (rs.profile().bloom()) compositeBloom(minecraft, mainTarget, state.outlineMaskTarget, rs.profile(), rs.sampledColors(), state.scissorRect);
        RenderSystem.disableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        state.resetAfterComposite();
    }

    public static boolean isCaptureActive() { return activeCaptureState != null && activeCaptureState.captureActive; }
    public static boolean shouldSkipHand(InteractionHand hand) { return activeCaptureState != null && activeCaptureState.captureActive && activeCaptureState.captureHandFilter != null && activeCaptureState.captureHandFilter != hand; }
    public static boolean hasCapturedThisFrame() { return MAIN_HAND_STATE.capturedThisFrame || OFF_HAND_STATE.capturedThisFrame; }
    public static int getCapturedHandCount() { return MAIN_HAND_STATE.capturedHandCount + OFF_HAND_STATE.capturedHandCount; }
    public static boolean isEmbeddiumCompatFrameQueued() { return MAIN_HAND_STATE.embeddiumCompatFrameQueued || OFF_HAND_STATE.embeddiumCompatFrameQueued; }

    private static boolean shouldRenderOutline(Minecraft minecraft) {
        if (HeldItemOutlineSettings.getOutlineMode() == HeldItemOutlineSettings.OutlineMode.VERTEX_SHADER) return false;
        if (!minecraft.options.getCameraType().isFirstPerson()) return false;
        if (minecraft.options.hideGui) return false;
        if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
        return !(minecraft.getCameraEntity() instanceof LivingEntity le && le.isSleeping());
    }

    // --- internal helpers (same logic, condensed) ---
    private static void addHandTarget(List<HandEffectTarget> t, InteractionHand h, boolean en, ItemStack s) { HandEffectTarget r = resolveHandTarget(h, en, s); if (r != null) t.add(r); }
    @Nullable private static HandEffectTarget resolveHandTarget(InteractionHand h, boolean en, ItemStack s) { CaptureState st = stateFor(h); ItemStack os = en ? s : ItemStack.EMPTY; ResolvedRenderState ns = resolveCachedRenderState(st, en, os, s); long now = System.currentTimeMillis(); if (st.transitionEndMillis != 0L && (!HeldItemOutlineSettings.isRuleSwitchDelayEnabled() || now >= st.transitionEndMillis)) completePendingTransition(st); if (st.lastObservedHandEnabled != en || !ItemStack.isSameItemSameTags(st.lastObservedStack, os)) { st.lastObservedHandEnabled = en; st.lastObservedStack = os.copy(); beginRenderTransition(st, ns, now); } if (st.transitionEndMillis != 0L) return toHandEffectTarget(h, st.lastEffectiveState); st.lastEffectiveState = ns; return toHandEffectTarget(h, st.lastEffectiveState); }
    @Nullable private static ResolvedRenderState createResolvedRenderState(ItemStack s, @Nullable HeldItemOutlineEffectProfile p) { return p == null ? null : new ResolvedRenderState(p, resolveSampledColors(s, p)); }
    @Nullable private static ResolvedRenderState resolveCachedRenderState(CaptureState st, boolean en, ItemStack os, ItemStack ls) { HeldItemOutlineEffectProfile bp = HeldItemOutlineEffectProfile.captureCurrent(); long rev = HeldItemRuleManager.getRevision(); if (st.cachedBaseProfile != null && st.cachedRuleRevision == rev && st.cachedObservedHandEnabled == en && st.cachedBaseProfile.equals(bp) && ItemStack.isSameItemSameTags(st.cachedObservedResolvedStack, os)) return st.cachedResolvedState; st.cachedObservedHandEnabled = en; st.cachedObservedResolvedStack = os.copy(); st.cachedBaseProfile = bp; st.cachedRuleRevision = rev; HeldItemRuleManager.ResolvedMatch m = en && !ls.isEmpty() ? HeldItemRuleManager.resolveMatch(ls, bp) : HeldItemRuleManager.ResolvedMatch.NO_MATCH; st.cachedResolvedState = createResolvedRenderState(ls, m.profile()); return st.cachedResolvedState; }
    @Nullable private static HandEffectTarget toHandEffectTarget(InteractionHand h, @Nullable ResolvedRenderState s) { return s == null ? null : new HandEffectTarget(h, s.profile(), s.sampledColors()); }
    private static void completePendingTransition(CaptureState s) { s.lastEffectiveState = s.pendingState; s.pendingState = null; s.transitionEndMillis = 0L; }
    private static void beginRenderTransition(CaptureState s, @Nullable ResolvedRenderState ns, long now) { ResolvedRenderState cs = s.lastEffectiveState; boolean cr = cs != null, nr = ns != null; boolean se = sameRenderedEffect(cs, ns); if (cr == nr && se) { s.lastEffectiveState = ns; s.pendingState = null; s.transitionEndMillis = 0L; return; } if (!HeldItemOutlineSettings.isRuleSwitchDelayEnabled()) { s.lastEffectiveState = ns; s.pendingState = null; s.transitionEndMillis = 0L; return; } s.pendingState = ns; s.transitionEndMillis = now + RULE_SWITCH_DELAY_MILLIS; }
    private static boolean sameRenderedEffect(@Nullable ResolvedRenderState a, @Nullable ResolvedRenderState b) { if (a == b) return true; if (a == null || b == null) return false; return a.profile().equals(b.profile()) && sameSampledColors(a.sampledColors(), b.sampledColors()); }
    private static boolean sameSampledColors(HeldItemOutlineColorSampler.SampledColors a, HeldItemOutlineColorSampler.SampledColors b) { if (a == b) return true; if (a == null || b == null || a.size() != b.size()) return false; for (int i = 0; i < a.size(); i++) { float[] ca = a.color(i), cb = b.color(i); if (ca.length != cb.length) return false; for (int j = 0; j < ca.length; j++) if (Float.compare(ca[j], cb[j]) != 0) return false; } return true; }
    private static CaptureState stateFor(InteractionHand h) { return h == InteractionHand.MAIN_HAND ? MAIN_HAND_STATE : OFF_HAND_STATE; }
    private static HeldItemOutlineColorSampler.SampledColors resolveSampledColors(ItemStack s, HeldItemOutlineEffectProfile p) { return p.colorMode() == HeldItemOutlineSettings.ColorMode.AUTO_SAMPLE_SCROLL ? HeldItemOutlineColorSampler.sample(s, p) : HeldItemOutlineColorSampler.SampledColors.EMPTY; }

    private static void ensureOutlineMaskTarget(RenderTarget mt, CaptureState s) {
        if (s.outlineMaskTarget == null) { s.outlineMaskTarget = new TextureTarget(mt.width, mt.height, true, Minecraft.ON_OSX); s.outlineMaskTarget.setFilterMode(9729); }
        else if (s.outlineMaskTarget.width != mt.width || s.outlineMaskTarget.height != mt.height) { s.outlineMaskTarget.resize(mt.width, mt.height, Minecraft.ON_OSX); s.outlineMaskTarget.setFilterMode(9729); }
    }

    // --- bloom / scissor / blur chain (unchanged) ---
    private static void ensureBloomTargets(RenderTarget mt, HeldItemOutlineEffectProfile p) {
        int bw = Math.max(MIN_BLOOM_TARGET_SIZE, Math.max(1, mt.width / Math.max(1, p.bloomResolution().downsampleFactor())));
        int bh = Math.max(MIN_BLOOM_TARGET_SIZE, Math.max(1, mt.height / Math.max(1, p.bloomResolution().downsampleFactor())));
        if (bloomBlurTargetA == null) { bloomBlurTargetA = new TextureTarget(bw, bh, false, Minecraft.ON_OSX); bloomBlurTargetA.setFilterMode(9729); } else if (bloomBlurTargetA.width != bw || bloomBlurTargetA.height != bh) { bloomBlurTargetA.resize(bw, bh, Minecraft.ON_OSX); bloomBlurTargetA.setFilterMode(9729); }
        if (bloomBlurTargetB == null) { bloomBlurTargetB = new TextureTarget(bw, bh, false, Minecraft.ON_OSX); bloomBlurTargetB.setFilterMode(9729); } else if (bloomBlurTargetB.width != bw || bloomBlurTargetB.height != bh) { bloomBlurTargetB.resize(bw, bh, Minecraft.ON_OSX); bloomBlurTargetB.setFilterMode(9729); }
        if (bloomBlurNearTarget == null) { bloomBlurNearTarget = new TextureTarget(bw, bh, false, Minecraft.ON_OSX); bloomBlurNearTarget.setFilterMode(9729); } else if (bloomBlurNearTarget.width != bw || bloomBlurNearTarget.height != bh) { bloomBlurNearTarget.resize(bw, bh, Minecraft.ON_OSX); bloomBlurNearTarget.setFilterMode(9729); }
    }

    private static void applyOutlineUniforms(Minecraft mc, RenderTarget mt, ShaderInstance s, TextureTarget omt, HeldItemOutlineEffectProfile p, HeldItemOutlineColorSampler.SampledColors sc) {
        s.setSampler("DiffuseSampler", omt.getColorTextureId()); s.setSampler("DepthSampler", omt.getDepthTextureId());
        if (s.getUniform("ScreenSize") != null) s.getUniform("ScreenSize").set((float) mt.width, (float) mt.height);
        if (s.getUniform("OutlineColor") != null) s.getUniform("OutlineColor").set(p.red(), p.green(), p.blue(), 1F);
        if (s.getUniform("SecondaryColor") != null) s.getUniform("SecondaryColor").set(p.secondaryRed(), p.secondaryGreen(), p.secondaryBlue(), 1F);
        applySamplePalette(s, sc, p);
        if (s.getUniform("ColorMode") != null) s.getUniform("ColorMode").set(p.colorMode().shaderValue());
        if (s.getUniform("ColorScrollSpeed") != null) s.getUniform("ColorScrollSpeed").set(p.colorScrollSpeed());
        if (s.getUniform("OutlineWidth") != null) s.getUniform("OutlineWidth").set(p.width());
        if (s.getUniform("Softness") != null) s.getUniform("Softness").set(p.softness());
        if (s.getUniform("AlphaThreshold") != null) s.getUniform("AlphaThreshold").set(p.alphaThreshold());
        if (s.getUniform("Opacity") != null) s.getUniform("Opacity").set(p.opacity());
        if (s.getUniform("DepthWeight") != null) s.getUniform("DepthWeight").set(p.depthWeight());
        if (s.getUniform("GlowStrength") != null) s.getUniform("GlowStrength").set(p.glowStrength());
        if (s.getUniform("Time") != null) s.getUniform("Time").set(resolveAnimationTime(mc));
    }

    private static void clearAllFrameStates() { MAIN_HAND_STATE.resetAfterComposite(); OFF_HAND_STATE.resetAfterComposite(); activeCaptureState = null; activeHandProjectionMatrix = null; }

    private static boolean setOculusHandRendererState(boolean active, boolean renderingSolid) {
        if (!HeldItemOutlineCompat.isOculusLoaded()) return false;
        try {
            if (oculusHandRendererInstance == null || oculusHandRendererActiveField == null || oculusHandRendererRenderingSolidField == null) {
                Class<?> hrc = Class.forName("net.irisshaders.iris.pathways.HandRenderer");
                Field inf = hrc.getField("INSTANCE"), af = hrc.getDeclaredField("ACTIVE"), rsf = hrc.getDeclaredField("renderingSolid");
                af.setAccessible(true); rsf.setAccessible(true);
                oculusHandRendererInstance = inf.get(null); oculusHandRendererActiveField = af; oculusHandRendererRenderingSolidField = rsf;
            }
            oculusHandRendererActiveField.setBoolean(oculusHandRendererInstance, active);
            oculusHandRendererRenderingSolidField.setBoolean(oculusHandRendererInstance, renderingSolid);
            return true;
        } catch (ReflectiveOperationException e) { return false; }
    }

    public static void debugCompat(@Nullable Minecraft mc, String msg) {
        if (!ENABLE_COMPAT_DEBUG_LOG) return;
        long now = System.currentTimeMillis();
        if (lastCompatDebugMillis != Long.MIN_VALUE && now - lastCompatDebugMillis < 2000L) return;
        lastCompatDebugMillis = now;
        if (mc != null && mc.level != null) ModUtils.LOGGER.warn("[HeldItemOutlineCompat][gameTime=" + mc.level.getGameTime() + "] " + msg);
        else ModUtils.LOGGER.warn("[HeldItemOutlineCompat] " + msg);
    }

    private static void inspectMaskCoverage(Minecraft mc, CaptureState s) { /* debug readback, unchanged */ if (!ENABLE_MASK_DEBUG_READBACK || !ModUtils.LOGGER.isDebugEnabled() || s.outlineMaskTarget == null || mc.level == null) return; long gt = mc.level.getGameTime(); if (gt % DEBUG_LOG_INTERVAL != 0L) return; s.outlineMaskTarget.bindRead(); try (NativeImage img = new NativeImage(NativeImage.Format.RGBA, s.outlineMaskTarget.width, s.outlineMaskTarget.height, false)) { img.downloadTexture(0, false); int active = 0, total = 24 * 14; for (int y = 0; y < 14; y++) { int py = Math.min(img.getHeight() - 1, Math.max(0, Math.round((y + 0.5F) * img.getHeight() / 14))); for (int x = 0; x < 24; x++) { int px = Math.min(img.getWidth() - 1, Math.max(0, Math.round((x + 0.5F) * img.getWidth() / 24))); if ((img.getPixelRGBA(px, py) >>> 24 & 0xFF) > 8) active++; } } s.lastSampledCoverage = total > 0 ? active / (float) total : 0F; } catch (Exception e) { ModUtils.LOGGER.warn("[HeldItemOutline] Failed to inspect outline mask texture", e); } finally { s.outlineMaskTarget.unbindRead(); } }
    private static void debugLog(Minecraft mc, String msg) { if (mc.level == null) return; long gt = mc.level.getGameTime(); if (gt == lastDebugLogGameTime || gt % DEBUG_LOG_INTERVAL != 0L) return; lastDebugLogGameTime = gt; ModUtils.LOGGER.debug("[HeldItemOutline] {}", msg); }

    private static void compositeBloom(Minecraft mc, RenderTarget mt, TextureTarget omt, HeldItemOutlineEffectProfile p, HeldItemOutlineColorSampler.SampledColors sc, @Nullable ScissorRect sr) {
        ShaderInstance bs = HeldItemBloomBlurShaderRegistry.getShader(), bls = HeldItemBloomShaderRegistry.getShader();
        if (bs == null || bls == null) return;
        ensureBloomTargets(mt, p);
        RenderSystem.disableBlend();
        if (bs.getUniform("ScreenSize") != null) bs.getUniform("ScreenSize").set((float) bloomBlurTargetA.width, (float) bloomBlurTargetA.height);
        float nr = Math.max(1F, 1.2F + p.width() * 0.35F), fr = Math.max(1F, p.bloomRadius() * 3.25F);
        int fp = Mth.clamp((int) Math.ceil(fr / BLOOM_BLUR_PASS_RADIUS), HeldItemOutlineSettings.MIN_BLOOM_MAX_PASSES, p.bloomMaxPasses());
        float fpr = fr / fp;
        ScissorRect bsr = scaleScissorRect(sr, omt, bloomBlurTargetA);
        int nt = applyBlurChain(bs, omt.getColorTextureId(), bloomBlurTargetA, bloomBlurNearTarget, nr, 1, bsr);
        int ft = (p.bloomRadius() <= SMALL_BLOOM_FAST_PATH_RADIUS && fp <= 2) ? nt : applyBlurChain(bs, omt.getColorTextureId(), bloomBlurTargetA, bloomBlurTargetB, fpr, fp, bsr);
        mt.bindWrite(true); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        bls.setSampler("DiffuseSampler", omt.getColorTextureId()); bls.setSampler("NearBlurSampler", nt); bls.setSampler("FarBlurSampler", ft);
        if (bls.getUniform("OutlineColor") != null) bls.getUniform("OutlineColor").set(p.red(), p.green(), p.blue(), 1F);
        if (bls.getUniform("SecondaryColor") != null) bls.getUniform("SecondaryColor").set(p.secondaryRed(), p.secondaryGreen(), p.secondaryBlue(), 1F);
        applySamplePalette(bls, sc, p);
        if (bls.getUniform("ColorMode") != null) bls.getUniform("ColorMode").set(p.colorMode().shaderValue());
        if (bls.getUniform("ColorScrollSpeed") != null) bls.getUniform("ColorScrollSpeed").set(p.colorScrollSpeed());
        if (bls.getUniform("AlphaThreshold") != null) bls.getUniform("AlphaThreshold").set(p.alphaThreshold());
        if (bls.getUniform("Opacity") != null) bls.getUniform("Opacity").set(p.opacity());
        if (bls.getUniform("GlowStrength") != null) bls.getUniform("GlowStrength").set(p.glowStrength());
        if (bls.getUniform("Time") != null) bls.getUniform("Time").set(resolveAnimationTime(mc));
        if (bls.getUniform("BloomStrength") != null) bls.getUniform("BloomStrength").set(p.bloomStrength());
        if (bls.getUniform("BloomRadius") != null) bls.getUniform("BloomRadius").set(p.bloomRadius());
        applyScissor(sr); drawFullscreenQuad(bls); clearScissor();
    }

    private static int applyBlurChain(ShaderInstance bs, int src, TextureTarget tmp, TextureTarget dst, float r, int passes, @Nullable ScissorRect sr) { int cur = src; for (int i = 0; i < passes; i++) { if (bs.getUniform("BlurRadius") != null) bs.getUniform("BlurRadius").set(r); tmp.bindWrite(true); bs.setSampler("DiffuseSampler", cur); if (bs.getUniform("BlurDirection") != null) bs.getUniform("BlurDirection").set(1F, 0F); applyScissor(sr); drawFullscreenQuad(bs); clearScissor(); dst.bindWrite(true); bs.setSampler("DiffuseSampler", tmp.getColorTextureId()); if (bs.getUniform("BlurDirection") != null) bs.getUniform("BlurDirection").set(0F, 1F); applyScissor(sr); drawFullscreenQuad(bs); clearScissor(); cur = dst.getColorTextureId(); } return cur; }

    @Nullable private static ScissorRect resolveHandScissorRect(RenderTarget mt, @Nullable Matrix4f mvm, @Nullable ResolvedRenderState rs) {
        if (activeHandProjectionMatrix == null || mvm == null || mt.width <= 0 || mt.height <= 0) return null;
        Matrix4f tx = new Matrix4f(activeHandProjectionMatrix).mul(mvm);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean found = false;
        for (float[] s : HAND_RENDER_BOUNDS_SAMPLES) { Vector4f c = tx.transform(new Vector4f(s[0], s[1], s[2], 1F)); float w = c.w; if (!Float.isFinite(w) || Math.abs(w) < 0.0001F) continue; float iw = 1F / w; float nx = c.x * iw, ny = c.y * iw; if (!Float.isFinite(nx) || !Float.isFinite(ny)) continue; minX = Math.min(minX, nx); minY = Math.min(minY, ny); maxX = Math.max(maxX, nx); maxY = Math.max(maxY, ny); found = true; }
        if (!found) return null;
        int x0 = Mth.floor((Mth.clamp(minX, -1.3F, 1.3F) * 0.5F + 0.5F) * mt.width), y0 = Mth.floor((Mth.clamp(minY, -1.3F, 1.3F) * 0.5F + 0.5F) * mt.height);
        int x1 = Mth.ceil((Mth.clamp(maxX, -1.3F, 1.3F) * 0.5F + 0.5F) * mt.width), y1 = Mth.ceil((Mth.clamp(maxY, -1.3F, 1.3F) * 0.5F + 0.5F) * mt.height);
        ScissorRect pr = ScissorRect.fromCorners(x0, y0, x1, y1, mt.width, mt.height);
        if (pr == null) return null;
        int pad = SCISSOR_BASE_PADDING;
        if (rs != null) { pad += Mth.ceil(rs.profile().width() * 10F + rs.profile().softness() * 12F); if (rs.profile().bloom()) pad += Mth.ceil(BLOOM_BLUR_KERNEL_RADIUS * (rs.profile().bloomRadius() <= SMALL_BLOOM_FAST_PATH_RADIUS && Mth.clamp((int) Math.ceil(Math.max(1F, rs.profile().bloomRadius() * 3.25F) / BLOOM_BLUR_PASS_RADIUS), HeldItemOutlineSettings.MIN_BLOOM_MAX_PASSES, rs.profile().bloomMaxPasses()) <= 2 ? Math.max(1F, 1.2F + rs.profile().width() * 0.35F) : Math.max(1F, rs.profile().bloomRadius() * 3.25F)) * rs.profile().bloomResolution().downsampleFactor()) + SCISSOR_BLOOM_PADDING; }
        return pr.expand(pad, mt.width, mt.height);
    }

    private static void applyScissor(@Nullable ScissorRect r) { if (r != null) RenderSystem.enableScissor(r.x(), r.y(), r.width(), r.height()); }
    private static void clearScissor() { RenderSystem.disableScissor(); }
    @Nullable private static ScissorRect scaleScissorRect(@Nullable ScissorRect r, RenderTarget src, @Nullable RenderTarget tgt) { if (r == null || src == null || tgt == null) return r; return r.scale(src.width, src.height, tgt.width, tgt.height); }
    private static float[][] createHandRenderBoundsSamples() {
        // Expanded Y range (up to 4.50) to cover items with "handheld"-style display
        // transforms whose first-person translation puts the item at Y ≈ 3.2–3.5 in
        // hand-local space (vs the vanilla-generated parent which stays near Y ≈ 0–2).
        // Without this extra headroom the scissor rect clips the top of the outline,
        // making it appear shifted downward for custom-model items like the void sword.
        float[][] s = new float[27][3];
        int idx = 0;
        for (float x : new float[]{-2.00F, 0.5F, 3.00F})
            for (float y : new float[]{-2.00F, 0.5F, 4.50F})
                for (float z : new float[]{-2.00F, 0.5F, 3.00F})
                    s[idx++] = new float[]{x, y, z};
        return s;
    }

    private static void drawFullscreenQuad(ShaderInstance s) { BufferBuilder bb = Tesselator.getInstance().getBuilder(); bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX); bb.vertex(-1D, -1D, 0D).uv(0F, 0F).endVertex(); bb.vertex(1D, -1D, 0D).uv(1F, 0F).endVertex(); bb.vertex(1D, 1D, 0D).uv(1F, 1F).endVertex(); bb.vertex(-1D, 1D, 0D).uv(0F, 1F).endVertex(); RenderSystem.setShader(() -> s); BufferUploader.drawWithShader(bb.end()); }

    private static void applySamplePalette(ShaderInstance s, HeldItemOutlineColorSampler.SampledColors sc, HeldItemOutlineEffectProfile p) { int sz = Math.max(0, Math.min(8, sc.size())); if (sz <= 0) { setPaletteColor(s, 0, new float[]{p.red(), p.green(), p.blue()}); setPaletteColor(s, 1, new float[]{p.secondaryRed(), p.secondaryGreen(), p.secondaryBlue()}); sz = 2; } else { for (int i = 0; i < 8; i++) setPaletteColor(s, i, sc.color(Math.min(i, sz - 1))); } if (s.getUniform("PaletteSize") != null) s.getUniform("PaletteSize").set((float) sz); }
    private static void setPaletteColor(ShaderInstance s, int i, float[] c) { if (s.getUniform("PaletteColor" + i) != null) s.getUniform("PaletteColor" + i).set(c[0], c[1], c[2], 1F); }
    private static float resolveAnimationTime(Minecraft mc) { if (mc.level == null) return 0F; long wgt = Math.floorMod(mc.level.getGameTime(), ANIMATION_TIME_WRAP_TICKS); return (wgt + mc.getFrameTime()) * 0.05F; }

    public record HandEffectTarget(InteractionHand hand, HeldItemOutlineEffectProfile profile, HeldItemOutlineColorSampler.SampledColors sampledColors) {}
    private record ResolvedRenderState(HeldItemOutlineEffectProfile profile, HeldItemOutlineColorSampler.SampledColors sampledColors) {}
    private record ScissorRect(int x, int y, int width, int height) {
        @Nullable private static ScissorRect fromCorners(int x0, int y0, int x1, int y1, int mw, int mh) { int mx = Mth.clamp(Math.min(x0, x1), 0, mw), my = Mth.clamp(Math.min(y0, y1), 0, mh), Mx = Mth.clamp(Math.max(x0, x1), 0, mw), My = Mth.clamp(Math.max(y0, y1), 0, mh); int w = Mx - mx, h = My - my; return w > 0 && h > 0 ? new ScissorRect(mx, my, w, h) : null; }
        private ScissorRect expand(int p, int mw, int mh) { return fromCorners(this.x - p, this.y - p, this.x + this.width + p, this.y + this.height + p, mw, mh); }
        @Nullable private ScissorRect scale(int sw, int sh, int tw, int th) { if (sw <= 0 || sh <= 0 || tw <= 0 || th <= 0) return null; return fromCorners(Mth.floor(this.x * (tw / (float) sw)), Mth.floor(this.y * (th / (float) sh)), Mth.ceil((this.x + this.width) * (tw / (float) sw)), Mth.ceil((this.y + this.height) * (th / (float) sh)), tw, th); }
    }

    private static final class CaptureState {
        private final InteractionHand hand;
        @Nullable private TextureTarget outlineMaskTarget; @Nullable private RenderTarget restoreTarget;
        private boolean captureActive, capturedThisFrame; private int capturedHandCount; private float lastSampledCoverage;
        private boolean embeddiumCompatFramePrepared, embeddiumCompatFrameQueued;
        private boolean lastObservedHandEnabled = true; private ItemStack lastObservedStack = ItemStack.EMPTY;
        @Nullable private InteractionHand captureHandFilter;
        @Nullable private ResolvedRenderState lastEffectiveState, pendingState, capturedRenderState;
        @Nullable private Matrix4f capturedModelViewMatrix; @Nullable private ScissorRect scissorRect;
        private boolean cachedObservedHandEnabled = true; private ItemStack cachedObservedResolvedStack = ItemStack.EMPTY;
        @Nullable private HeldItemOutlineEffectProfile cachedBaseProfile; private long cachedRuleRevision = Long.MIN_VALUE;
        @Nullable private ResolvedRenderState cachedResolvedState; private long transitionEndMillis;
        private CaptureState(InteractionHand hand) { this.hand = hand; }
        private void resetImmediateFrameState() { capturedThisFrame = false; capturedHandCount = 0; lastSampledCoverage = 0F; captureActive = false; restoreTarget = null; captureHandFilter = null; capturedRenderState = null; capturedModelViewMatrix = null; scissorRect = null; embeddiumCompatFramePrepared = false; embeddiumCompatFrameQueued = false; }
        private void resetAfterComposite() { capturedThisFrame = false; capturedHandCount = 0; lastSampledCoverage = 0F; captureActive = false; restoreTarget = null; captureHandFilter = null; capturedRenderState = null; capturedModelViewMatrix = null; scissorRect = null; embeddiumCompatFramePrepared = false; embeddiumCompatFrameQueued = false; }
    }
}
