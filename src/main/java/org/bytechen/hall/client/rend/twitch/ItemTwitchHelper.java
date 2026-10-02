package org.bytechen.hall.client.rend.twitch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.client.cosmic.BakedModelCosmic;

/**
 * Calculates and applies a "glitch twitch" distortion to item rendering.
 *
 * <h3>Effect</h3>
 * The item periodically enters a short burst (~0.3s) of violent
 * position jitter, non-uniform scale stretch, and rapid rotation.
 * Between bursts the item appears completely normal.  The burst
 * cycles are staggered per-item-type so items don't twitch in sync.
 *
 * <h3>Design</h3>
 * Intended for horror/void-themed items like {@code VoidSword}.
 * The rapid, unpredictable motion creates a "corrupted / glitchy"
 * aesthetic reminiscent of creepypasta-style visual distortion.
 *
 * <h3>Usage</h3>
 * Called from {@code MixinItemRendererTwitch} at the HEAD of
 * {@code ItemRenderer.render()}.  Applies transforms to the
 * {@link PoseStack} before the vanilla rendering pipeline runs.
 */
public final class ItemTwitchHelper {

    // —— timing ——
    /** Ticks between consecutive burst cycles. */
    private static final float BURST_INTERVAL_TICKS = 60f;   // ~3s
    /** Duration of the twitch burst in ticks. */
    private static final float BURST_DURATION_TICKS = 5f;    // ~0.25s, shorter = more violent
    /** High-frequency oscillation for glitchy sub-frame jitter. */
    private static final float JITTER_FREQ = 55f;

    // —— amplitude ——
    /** Maximum positional offset in model-space units. */
    private static final float MAX_TRANSLATE = 0.30f;
    /** Maximum scale deviation from 1.0 (e.g. 0.40 = ±40%). */
    private static final float MAX_SCALE = 0.40f;
    /** Maximum rotation in radians (~30°). */
    private static final float MAX_ROTATION_RAD = 0.55f;

    private ItemTwitchHelper() {}

    // ── public API ──────────────────────────────────────────────

    /**
     * Returns true if this item/model combination should receive
     * the glitch twitch effect.
     *
     * <p>Currently applies to:
     * <ul>
     *   <li>Models wrapped by {@link BakedModelCosmic} (cosmic shader items)
     *       — unless the model JSON opted out with {@code "twitch": false}</li>
     *   <li>Items implementing {@link ITwitchItem}</li>
     * </ul>
     */
    public static boolean shouldTwitch(ItemStack stack, BakedModel model) {
        if (stack == null || stack.isEmpty()) return false;
        if (model instanceof BakedModelCosmic bc) return bc.isTwitchEnabled();
        return stack.getItem() instanceof ITwitchItem;
    }

    /**
     * Resolve the twitch opt-out from the item's <b>baked model</b>.
     *
     * <p>{@link #shouldTwitch} can only see the model instance that
     * {@code ItemRenderer.render()} was handed.  In the deferred path
     * (Oculus/Iris active) the renderer is invoked with the wrapper model, and
     * the cosmic layer is enqueued separately — so the caller may not be
     * holding the {@link BakedModelCosmic} at all.  This helper re-resolves it
     * from the item stack, which is the reliable route.
     *
     * <p>Returns {@code true} when there is nothing to opt out of.
     *
     * @param stack the item stack being rendered (may be null/empty)
     * @param model the model passed to the renderer (may be null)
     * @return false only when the resolved model explicitly disabled twitch
     */
    public static boolean twitchEnabledFor(ItemStack stack, BakedModel model) {
        // 物品层面的硬声明最先检查 —— 没有任何包装器能覆盖它
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ITwitchItem ti && ti.twitchDisabled()) return false;

        if (model instanceof BakedModelCosmic bc) return bc.isTwitchEnabled();
        if (stack == null || stack.isEmpty()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getItemRenderer() == null) return true;
        BakedModel resolved = mc.getItemRenderer().getModel(stack, null, null, 0);
        if (resolved instanceof BakedModelCosmic bc) return bc.isTwitchEnabled();
        return true;
    }

    /**
     * Returns the current burst intensity (0.0–1.0) for the given item and
     * game time.  0.0 means outside a burst — no corruption should be applied.
     *
     * <p>This is the same intensity curve used internally by
     * {@link #applyTwitch}, exposed so the corruption shader can sync to the
     * same burst cycle.
     *
     * @param stack    the item stack being rendered
     * @param gameTime the current {@code level.getGameTime()} value
     * @return 0.0 when outside burst, ~0.0–1.0 during a burst
     */
    public static float getTwitchIntensity(ItemStack stack, long gameTime) {
        float t = (float) (gameTime % Integer.MAX_VALUE);
        int itemSeed = stack.getItem().hashCode();

        float phaseOff = Math.abs(itemSeed % 9973) / 9973f * BURST_INTERVAL_TICKS;
        float phase = ((t + phaseOff) % BURST_INTERVAL_TICKS) / BURST_INTERVAL_TICKS;

        float burstFrac = BURST_DURATION_TICKS / BURST_INTERVAL_TICKS;
        if (phase >= burstFrac) return 0f; // outside burst

        float burstProg = phase / burstFrac;
        float spike = (float) Math.abs(Math.sin(burstProg * Math.PI * 10));
        return (1f - burstProg * burstProg) * (0.5f + 0.5f * spike);
    }

    /**
     * Apply the twitch transforms to {@code poseStack} if the current
     * game tick falls within an active burst window.
     *
     * <p>Safe to call every frame — returns immediately when not in a
     * burst (the common case).
     *
     * @param ps       the {@link PoseStack} to mutate
     * @param stack    the item stack being rendered
     * @param gameTime the current {@code level.getGameTime()} value
     */
    public static void applyTwitch(PoseStack ps, ItemStack stack, long gameTime) {
        float intensity = getTwitchIntensity(stack, gameTime);
        if (intensity < 0.005f) return;

        float t = (float) (gameTime % Integer.MAX_VALUE);
        int itemSeed = stack.getItem().hashCode();
        float jp = t * JITTER_FREQ;
        float s0 = itemSeed;
        float s1 = itemSeed * 1.37f + 17f;
        float s2 = itemSeed * 2.71f + 31f;

        // —— 1. Position jitter ——
        float jx = (float) Math.sin(jp + s0) * intensity * MAX_TRANSLATE;
        float jy = (float) Math.cos(jp * 1.37f + s1) * intensity * MAX_TRANSLATE;
        float jz = (float) Math.sin(jp * 0.73f + s2) * intensity * MAX_TRANSLATE * 0.5f;
        ps.translate(jx, jy, jz);

        // —— 2. Non-uniform scale stretch ——
        float sx = 1f + (float) Math.sin(jp * 1.71f + s0 * 0.7f) * intensity * MAX_SCALE;
        float sy = 1f + (float) Math.cos(jp * 2.13f + s1 * 1.1f) * intensity * MAX_SCALE;
        float sz = 1f + (float) Math.sin(jp * 1.33f + s2 * 1.9f) * intensity * MAX_SCALE * 0.6f;
        ps.scale(sx, sy, sz);

        // —— 3. Rapid rotation jitter ——
        float rx = (float) Math.sin(jp * 0.91f + s0 * 2.1f) * intensity * MAX_ROTATION_RAD;
        float ry = (float) Math.cos(jp * 1.53f + s1 * 0.3f) * intensity * MAX_ROTATION_RAD;
        float rz = (float) Math.sin(jp * 1.17f + s2 * 3.3f) * intensity * MAX_ROTATION_RAD * 0.5f;
        if (Math.abs(rx) > 0.0001f) ps.mulPose(Axis.XP.rotation(rx));
        if (Math.abs(ry) > 0.0001f) ps.mulPose(Axis.YP.rotation(ry));
        if (Math.abs(rz) > 0.0001f) ps.mulPose(Axis.ZP.rotation(rz));
    }
}
