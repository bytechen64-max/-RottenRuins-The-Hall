package org.bytechen.rottenruins.client.fx.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.bytechen.rottenruins.client.fx.ItemFxBlend;
import org.bytechen.rottenruins.client.fx.ItemFxDomain;
import org.bytechen.rottenruins.client.fx.ItemFxEffect;
import org.bytechen.rottenruins.client.fx.ItemFxFieldPool;
import org.bytechen.rottenruins.client.fx.ItemFxFieldSpec;
import org.bytechen.rottenruins.client.fx.ItemFxFloatTarget;
import org.bytechen.rottenruins.client.fx.ItemFxFormat;
import org.bytechen.rottenruins.client.fx.ItemFxQuad;
import org.bytechen.rottenruins.client.fx.ItemFxRegistry;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import org.bytechen.rottenruins.utils.ModUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 火焰效果：注入 + 平流 + 冷却 的反馈场 + 黑体合成。
 *
 * <p>与描边（无状态的形态学算子）不同，这里是有状态的输运过程，火舌的上升、拉伸、
 * 断裂由流动历史涌现。sim 场跨帧保留且必须浮点（8bit 会把低值截断，羽流尾部还没飘出去就死了）；
 * fuel 场每帧重画，只含"这件物品要烧"的剪影，同屏其它描边物品不会被一起点着。
 * sim 与合成都在实体通道之外跑（整屏一次，同屏燃烧物共用一个场）。</p>
 */
public final class FlameFxEffect implements ItemFxEffect {

    public static final String ID = "flame";

    public static final String FIELD_SIM_A = "simA";
    public static final String FIELD_SIM_B = "simB";
    public static final String FIELD_FUEL = "fuel";

    /** 合成时 scissor 的外扩：火羽能升到 ~Rise/Cooling 个屏幕高，再加横向扩散的余量。 */
    public static final int SCISSOR_PAD_PX = 320;

    private static final List<ItemFxFieldSpec> FIELDS = List.of(
            ItemFxFieldSpec.state(FIELD_SIM_A, ItemFxFormat.RG16F, 4, 64),
            ItemFxFieldSpec.state(FIELD_SIM_B, ItemFxFormat.RG16F, 4, 64),
            ItemFxFieldSpec.scaled(FIELD_FUEL, ItemFxFormat.RGBA8, 4, 64));

    private static final FlameFxEffect INSTANCE = new FlameFxEffect();

    static {
        ItemFxRegistry.register(INSTANCE);
    }

    private static boolean burningThisFrame;
    private static boolean fuelNeedsClear;
    private static int parity;
    private static long lastStepNanos;
    private static long clockStartNanos;
    private static float idleSeconds = Float.MAX_VALUE;
    /**
     * 初值必须是 true：浮点场是用 {@code glTexImage2D(..., null)} 建的，内容未定义，
     * 第一帧不清就是拿显存垃圾当"上一帧的场"。
     */
    private static boolean fieldsDirty = true;
    private static float tintR = 1.00F;
    private static float tintG = 0.42F;
    private static float tintB = 0.12F;

    private FlameFxEffect() { }

    public static FlameFxEffect instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemFxDomain domain() {
        return ItemFxDomain.SILHOUETTE;
    }

    @Override
    public List<ItemFxFieldSpec> fields() {
        return FIELDS;
    }

    @Override
    public ItemFxBlend blend() {
        return ItemFxBlend.ADD;
    }

    @Override
    public float order() {
        // 火在描边之下：先有火体，再压那条硬边火芯。
        return 150.0f;
    }

    //  管线调用（由 ItemOutlinePipeline 在 TAIT 与捕获阶段调用）

    public static void markBurning() {
        burningThisFrame = true;
    }

    /**
     * 火焰染色（冷尾与主体的色相，核心始终推向白）：同一帧多个燃烧物时后者生效 ——
     * 共享场只认一套参数。
     */
    public static void setTint(float r, float g, float b) {
        tintR = r;
        tintG = g;
        tintB = b;
    }

    public static boolean isBurningThisFrame() {
        return burningThisFrame;
    }

    @Nullable
    public static TextureTarget fuelTarget(RenderTarget main) {
        return ItemFxFieldPool.get(ID, ItemFxFieldSpec.scaled(FIELD_FUEL, ItemFxFormat.RGBA8, 4, 64), main);
    }

    /** 必须在<b>第一批</b>燃料写入之前调用，否则第二批（一手）盖上去时会把第一批抹掉。 */
    public static void beginFuelFrame(RenderTarget main) {
        if (!fuelNeedsClear) return;
        TextureTarget fuel = fuelTarget(main);
        fuelNeedsClear = false;
        if (fuel == null) return;
        int previousFbo = org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        fuel.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.clearColor(0F, 0F, 0F, 0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
        // ★ viewport 必须一起还原：只还 framebuffer 的话 viewport 会停在燃料场的 1/4 尺寸上，
        //   且被 captureAll 记成"上一帧值"固化下来，于是本帧后续所有绘制（含暂停菜单）
        //   都被塞进画面左下角那 1/4 区域。
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previousFbo);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    /** 是否还值得跑 sim：本帧有火，或刚熄灭不久（让余火自然烧完）。 */
    public static boolean wantsSimulation() {
        SplendidingConfig cfg = config();
        if (cfg != null && !cfg.flameEnabled) return false;
        return burningThisFrame || idleSeconds < 2.0F;
    }

    /**
     * 推进一步平流（1/4 分辨率，整屏一次）。
     *
     * <p>{@code dtSeconds} 帧间隔，调用方必须已 clamp；{@code sizeScale} 火焰高度随物品观感
     * 尺寸缩放（小物品 / 远处物品不该冒出一屏高的火）。</p>
     */
    public static void step(RenderTarget main, float dtSeconds, float anchorU, float anchorV,
                            float sizeScale) {
        ShaderInstance sim = SplendidingShaders.flameSimShader;
        ItemFxFloatTarget src = target(main, parity);
        ItemFxFloatTarget dst = target(main, parity ^ 1);
        TextureTarget fuel = fuelTarget(main);
        if (sim == null || src == null || dst == null || fuel == null) return;

        // 第一次 / 刚切过世界 → 清空两张场，否则上一处的火会跟着新场景继续烧。
        if (fieldsDirty) {
            src.clear(0F, 0F, 0F, 1F);
            dst.clear(0F, 0F, 0F, 1F);
            fieldsDirty = false;
        }

        SplendidingConfig cfg = config();
        // 火焰高度 ≈ Rise/Cooling 个屏幕高，再乘 sizeScale（掉落物在远处只占几十像素，
        // 1.5 屏高的火显然是错的）。
        float rise = (cfg == null ? 0.30F : cfg.flameRise) * sizeScale;
        float buoy = (cfg == null ? 0.55F : cfg.flameBuoyancy) * sizeScale;
        float turb = cfg == null ? 0.55F : cfg.flameTurbulence;
        float inject = cfg == null ? 0.55F : cfg.flameInject;
        float cooling = cfg == null ? 2.10F : cfg.flameCooling;

        dst.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        sim.setSampler("Prev", src.colorTextureId());
        sim.setSampler("Fuel", fuel.getColorTextureId());
        ItemFxQuad.setVec2(sim, "ScreenSize", (float) main.width, (float) main.height);
        ItemFxQuad.setVec2(sim, "AnchorUv", anchorU, anchorV);
        // v1：屏幕上方 = 世界上方。手持/掉落物在常见机位下两者接近；
        // 真正的"世界 up 投影"要按物品的捕获矩阵算，留给后续（Kimi 的 R3 也是这么兜的）。
        ItemFxQuad.setVec2(sim, "UpUv", 0F, 1F);
        ItemFxQuad.setFloat(sim, "Time", clockSeconds());
        ItemFxQuad.setFloat(sim, "Dt", dtSeconds);
        ItemFxQuad.setFloat(sim, "Rise", rise);
        ItemFxQuad.setFloat(sim, "Buoyancy", buoy);
        ItemFxQuad.setFloat(sim, "Turbulence", turb);
        ItemFxQuad.setFloat(sim, "Inject", inject);
        ItemFxQuad.setFloat(sim, "Cooling", cooling);
        ItemFxQuad.setFloat(sim, "SmokeYield", cfg == null ? 0.35F : cfg.flameSmokeYield);
        ItemFxQuad.setFloat(sim, "VentSpeed", 1.6F);
        ItemFxQuad.draw(sim);

        parity ^= 1;
    }

    /**
     * 把当前那一张场合成到主 RT（调用方负责 scissor 与混合）。
     *
     * <p>{@code visibility} 为 null 时不做遮挡包围 —— 火焰会盖在挡在前面的方块上。
     * {@code freeAboveV} 是物品上沿的屏幕 UV.y，它以上一律放行：包络的模糊核只够到约 52px
     * （AURA_REACH_PX），不放开这段火羽会被整段切掉。</p>
     */
    public static void composite(RenderTarget main, @Nullable TextureTarget mask,
                                 @Nullable TextureTarget visibility, float freeAboveV, float scissorTopV,
                                 int debugMode, float depthNdc) {
        ShaderInstance shader = SplendidingShaders.flameCompositeShader;
        ItemFxFloatTarget sim = target(main, parity);
        if (shader == null || sim == null || mask == null) return;

        SplendidingConfig cfg = config();
        shader.setSampler("Sim", sim.colorTextureId());
        shader.setSampler("Mask", mask.getColorTextureId());
        if (visibility != null) {
            shader.setSampler("Visibility", visibility.getColorTextureId());
        }
        ItemFxQuad.setFloat(shader, "VisibilityGate", visibility == null ? 0F : 1F);
        ItemFxQuad.setFloat(shader, "FlameFreeAboveV", freeAboveV);
        ItemFxQuad.setFloat(shader, "FlameScissorTopV", scissorTopV);
        ItemFxQuad.setFloat(shader, "DebugMode", (float) debugMode);
        // 世界物品：四边形带着物品的深度平面，配合 LEQUAL 就能被前面的方块真正挡住
        ItemFxQuad.setFloat(shader, "FlameDepthNdc", depthNdc);
        ItemFxQuad.setVec2(shader, "ScreenSize", (float) main.width, (float) main.height);
        ItemFxQuad.setFloat(shader, "Gain", cfg == null ? 2.2F : cfg.flameGain);
        ItemFxQuad.setFloat(shader, "TempScale", cfg == null ? 0.85F : cfg.flameTempScale);
        ItemFxQuad.setFloat(shader, "SmokeDarken", cfg == null ? 0.45F : cfg.flameSmokeDarken);
        ItemFxQuad.setFloat(shader, "Sparkle", cfg == null ? 0.55F : cfg.flameSparkle);
        if (shader.getUniform("FlameTint") != null) {
            shader.getUniform("FlameTint").set(tintR, tintG, tintB);
        }
        ItemFxQuad.draw(shader);
    }

    /** 一帧结束：结算 idle（决定下帧还要不要跑 sim）。 */
    public static void endFrame(float dtSeconds) {
        if (burningThisFrame) {
            idleSeconds = 0F;
        } else {
            float before = idleSeconds;
            idleSeconds = Math.min(idleSeconds + Math.max(dtSeconds, 0F), Float.MAX_VALUE / 2);
            // 刚越过熄灭门槛就清空两张场：物品不可见期间 sim 不推进，旧密度会留着，
            // 下次再出现时先闪一下"上次的火"。
            if (before < 2.0F && idleSeconds >= 2.0F) {
                fieldsDirty = true;
            }
        }
        burningThisFrame = false;
        fuelNeedsClear = true;
    }

    public static float nextDeltaSeconds() {
        long now = System.nanoTime();
        if (lastStepNanos == 0L) {
            lastStepNanos = now;
            return 1F / 60F;
        }
        float dt = (now - lastStepNanos) / 1.0E9F;
        lastStepNanos = now;
        if (!(dt > 0F)) return 1F / 60F;
        if (dt > 0.25F) {
            // 顿卡 / 世界加载 / 暂停恢复：让火从干净状态重新烧，别把半小时前的密度搬过来
            fieldsDirty = true;
            dt = 1F / 60F;
        }
        return Math.min(dt, 0.05F);
    }

    public static boolean consumeDirty() {
        boolean dirty = fieldsDirty;
        return dirty;
    }

    public static void resetForNewWorld() {
        fieldsDirty = true;
        idleSeconds = Float.MAX_VALUE;
    }

    @Nullable
    private static ItemFxFloatTarget target(RenderTarget main, int which) {
        String name = which == 0 ? FIELD_SIM_A : FIELD_SIM_B;
        return ItemFxFieldPool.getFloat(ID, ItemFxFieldSpec.state(name, ItemFxFormat.RG16F, 4, 64), main);
    }

    private static float clockSeconds() {
        if (clockStartNanos == 0L) clockStartNanos = System.nanoTime();
        return (System.nanoTime() - clockStartNanos) / 1.0E9F;
    }

    @Nullable
    private static SplendidingConfig config() {
        try {
            var holder = ConfigHelper.configHolder;
            return holder == null ? null : holder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void logStateOnce(RenderTarget main) {
        ModUtils.LOGGER.info("[ItemFlame] sim 场 = {} ；idle={}s ；burning={}",
                ItemFxFieldPool.describe(), String.format(java.util.Locale.ROOT, "%.2f", idleSeconds),
                burningThisFrame);
    }
}
