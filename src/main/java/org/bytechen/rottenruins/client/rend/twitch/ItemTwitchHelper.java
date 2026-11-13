package org.bytechen.rottenruins.client.rend.twitch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.client.cosmic.BakedModelCosmic;

// 给物品渲染叠一层"故障抖动"：周期性短促的位置抖动 + 非均匀缩放 + 快速旋转，爆发之外完全正常；
// 各物品按自身 hash 错开相位，由 MixinItemRendererTwitch 在 ItemRenderer.render() 头部施加到 PoseStack。
public final class ItemTwitchHelper {

    private static final float BURST_INTERVAL_TICKS = 60f;   // ~3s
    private static final float BURST_DURATION_TICKS = 5f;    // ~0.25s, shorter = more violent
    private static final float JITTER_FREQ = 55f;

    /** 最大位移偏移（模型空间单位）。 */
    private static final float MAX_TRANSLATE = 0.30f;

    /** 最大缩放偏差（0.40 = ±40%）。 */
    private static final float MAX_SCALE = 0.40f;

    /** 最大旋转量（弧度，≈30°）。 */
    private static final float MAX_ROTATION_RAD = 0.55f;

    private ItemTwitchHelper() {}

    /** 是否要抖动：被 {@link BakedModelCosmic} 包过的模型（除非模型 json 写了 {@code "twitch": false}）或实现了 {@link ITwitchItem} 的物品。 */
    public static boolean shouldTwitch(ItemStack stack, BakedModel model) {
        if (stack == null || stack.isEmpty()) return false;
        if (model instanceof BakedModelCosmic bc) return bc.isTwitchEnabled();
        return stack.getItem() instanceof ITwitchItem;
    }

    /** 从<b>烤好的模型</b>解析退出位：延迟渲染（Oculus/Iris）下渲染器拿到的是包装模型、cosmic 层另排队，
     *  所以只能拿 item stack 重新解析；物品层面的 {@code ITwitchItem.twitchDisabled()} 优先，解析不到模型时返回 true。 */
    public static boolean twitchEnabledFor(ItemStack stack, BakedModel model) {
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

    /** 当前爆发强度 0.0~1.0（0 = 不在爆发期）；与 {@link #applyTwitch} 内部同一条曲线，供故障着色器同步节奏。 */
    public static float getTwitchIntensity(ItemStack stack, long gameTime) {
        float t = (float) (gameTime % Integer.MAX_VALUE);
        int itemSeed = stack.getItem().hashCode();

        float phaseOff = Math.abs(itemSeed % 9973) / 9973f * BURST_INTERVAL_TICKS;
        float phase = ((t + phaseOff) % BURST_INTERVAL_TICKS) / BURST_INTERVAL_TICKS;

        float burstFrac = BURST_DURATION_TICKS / BURST_INTERVAL_TICKS;
        if (phase >= burstFrac) return 0f;

        float burstProg = phase / burstFrac;
        float spike = (float) Math.abs(Math.sin(burstProg * Math.PI * 10));
        return (1f - burstProg * burstProg) * (0.5f + 0.5f * spike);
    }

    /** 处于爆发窗口时把抖动变换施加到 poseStack 上；不在窗口内立刻返回，可每帧调用。 */
    public static void applyTwitch(PoseStack ps, ItemStack stack, long gameTime) {
        float intensity = getTwitchIntensity(stack, gameTime);
        if (intensity < 0.005f) return;

        float t = (float) (gameTime % Integer.MAX_VALUE);
        int itemSeed = stack.getItem().hashCode();
        float jp = t * JITTER_FREQ;
        float s0 = itemSeed;
        float s1 = itemSeed * 1.37f + 17f;
        float s2 = itemSeed * 2.71f + 31f;

        float jx = (float) Math.sin(jp + s0) * intensity * MAX_TRANSLATE;
        float jy = (float) Math.cos(jp * 1.37f + s1) * intensity * MAX_TRANSLATE;
        float jz = (float) Math.sin(jp * 0.73f + s2) * intensity * MAX_TRANSLATE * 0.5f;
        ps.translate(jx, jy, jz);

        float sx = 1f + (float) Math.sin(jp * 1.71f + s0 * 0.7f) * intensity * MAX_SCALE;
        float sy = 1f + (float) Math.cos(jp * 2.13f + s1 * 1.1f) * intensity * MAX_SCALE;
        float sz = 1f + (float) Math.sin(jp * 1.33f + s2 * 1.9f) * intensity * MAX_SCALE * 0.6f;
        ps.scale(sx, sy, sz);

        float rx = (float) Math.sin(jp * 0.91f + s0 * 2.1f) * intensity * MAX_ROTATION_RAD;
        float ry = (float) Math.cos(jp * 1.53f + s1 * 0.3f) * intensity * MAX_ROTATION_RAD;
        float rz = (float) Math.sin(jp * 1.17f + s2 * 3.3f) * intensity * MAX_ROTATION_RAD * 0.5f;
        if (Math.abs(rx) > 0.0001f) ps.mulPose(Axis.XP.rotation(rx));
        if (Math.abs(ry) > 0.0001f) ps.mulPose(Axis.YP.rotation(ry));
        if (Math.abs(rz) > 0.0001f) ps.mulPose(Axis.ZP.rotation(rz));
    }
}
