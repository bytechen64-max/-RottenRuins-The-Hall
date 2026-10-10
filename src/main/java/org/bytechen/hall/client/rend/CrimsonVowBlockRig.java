package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.overworld.registry.items.WeaponBlock;

/**
 * 低版本（1.8 式）举剑格挡的<b>姿态增量</b>。
 *
 * <h3>为什么是"增量"，而不是像参考模组那样"整套替换"</h3>
 * <p>参考模组（ArcaneVortex）的做法是：在 {@code renderArmWithItem} 头部取消原版，
 * 然后自己 pushPose → 套一组固定变换 → {@code ItemRenderer.renderStatic(stack,
 * FIRST_PERSON_RIGHT_HAND, ...)}。它<b>能那样做的前提是它的武器没有自定义
 * first-person display 变换</b>（照抄原版手持观感）。</p>
 *
 * <p>而本模组的 crimson_vow 模型自带：</p>
 * <pre>
 *   "firstperson_righthand": { "rotation": [-68.98, -78.66, -29.26], "translation": [2.25, 5.25, 0] }
 * </pre>
 * <p>那组变换本来是为了"正常手持"调好的。用整套替换时，它会被
 * {@code FIRST_PERSON_RIGHT_HAND} 上下文<b>再套一遍</b>，与格挡姿态的旋转叠加，
 * 结果就是剑刃被拧向镜头 —— 实机反馈的"整个剑往摄像机戳"正是这个。</p>
 *
 * <p>所以这里改成：<b>让原版照常渲染</b>（它的 arm transform 与模型 display
 * 变换都按设计生效），我们只在传给
 * {@code ItemRenderer.render(...)} 的 PoseStack 上<b>叠加一个增量</b>，
 * 把"正常手持"抬成"举剑格挡"。这样：</p>
 * <ul>
 *   <li>模型自己的 display 变换只生效一次，剑刃方向不再被拧乱；</li>
 *   <li>增量即使微调，也只是在"正常手持"基础上偏一点，
 *       不会出现"整把剑消失"或"戳镜头"这种量级的问题；</li>
 *   <li>不需要自己算 {@code side}、也不需要重画手臂（原版那条路径本来就不画手臂）。</li>
 * </ul>
 *
 * <h3>增量的方向与量级</h3>
 * <p>原版第一人称持剑是"剑在右下、刃朝上"。格挡要的是"抬到身前、横向斜挡"，
 * 所以增量取：向上抬、往屏幕中心收、绕 Z 轴把剑身横过来。
 * 三个量都在下面的常数里，实机按观感调即可。</p>
 */
public final class CrimsonVowBlockRig {

    // ── 姿态增量常数 ──
    //
    // 单位与相机空间一致（1.0 ≈ 一个方块）。数值偏保守：
    // 目标是"看得见、像在格挡"，在正常手持基础上做小幅偏移最安全。

    /** 向上抬。原版持剑在右下，需要抬高才进入视野中央。 */
    private static final float LIFT_Y = 0.26F;

    /** 往屏幕中心收（负值 = 朝中心方向）。 */
    private static final float INWARD_X = -0.18F;

    /** 朝镜头方向略微拉近，避免剑被身体挡住。 */
    private static final float NEAR_Z = 0.12F;

    /**
     * 把剑身横过来，从"竖举"变成 1.8 那种"斜挡在身前"。
     * <b>符号 = 朝哪边斜</b>，实机看是镜像的就把符号取反。
     */
    private static final float SWEEP_Z = 38.0F;

    /**
     * 绕 Y 轴转，让<b>剑面正对镜头</b>。
     *
     * <p>剑是薄片，侧对镜头时几乎看不见（只见一条边）。原版第一人称持剑时
     * 剑面是正对镜头的，但格挡姿态叠了 {@link #SWEEP_Z} 与 {@link #TILT_X}
     * 之后剑面会被转侧，所以需要这一项把它转回来。</p>
     *
     * <p>正值朝一侧、负值朝另一侧；实机若发现转向了背面（看到剑背），取反即可。</p>
     */
    private static final float FACE_Y = 60.0F;

    /** 略微抬头/低头，让剑面朝镜头而不是侧对（剑是薄片，侧对会看不见）。 */
    private static final float TILT_X = -12.0F;

    /** 整体缩小一点，防止剑尖顶出画面。 */
    private static final float SCALE = 0.95F;

    private CrimsonVowBlockRig() {}

    /**
     * 该实体此刻是否正在用某把 {@link WeaponBlock} 武器格挡。
     * 复用接口的判定，保证"看得见的姿态"和"算得出的减伤"是同一个条件。
     */
    public static boolean isBlocking(LivingEntity entity) {
        return org.bytechen.hall.api.IBlockingWeapon.isBlocking(entity);
    }

    /**
     * 在已有（原版已摆好的）姿态上叠加格挡增量。
     *
     * <p>由混入类在 {@code ItemRenderer.render(...)} 的 PoseStack 参数上调用，
     * 因此此刻栈上就是"正常手持"的完整变换，包括玩家手臂位置与模型 display 变换。</p>
     *
     * @param poseStack 原版准备传给物品渲染的 PoseStack（就地修改）
     */
    public static void applyBlockDelta(PoseStack poseStack) {
        poseStack.translate(INWARD_X, LIFT_Y, NEAR_Z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(SWEEP_Z));
        // Y 轴放在 Z 之后：先把剑横过来，再绕竖直轴把剑面转向镜头。
        // 顺序反过来的话 Z 会把 Y 的结果再转侧，等于白转。
        poseStack.mulPose(Axis.YP.rotationDegrees(FACE_Y));
        poseStack.mulPose(Axis.XP.rotationDegrees(TILT_X));
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    /**
     * 供混入类判断：这次渲染是否要套格挡姿态。
     *
     * <p>注意<b>不需要</b>判断 {@code hand}：{@code renderArmWithItem} 每次只为
     * 一只手调用，而 {@code IBlockingWeapon.isBlocking} 已经用
     * {@code getUseItem()} 锁定"正在被使用的那只手"的物品 ——
     * 所以只有真正在举的那只手会命中，另一只手照常。</p>
     */
    public static boolean shouldApply(AbstractClientPlayer player, ItemStack stack,
                                      InteractionHand hand) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof org.bytechen.hall.api.IBlockingWeapon)) return false;
        // 双手都可能拿到同一个物品实例（副手），这里再确认一次"这只手就是使用中的那只"
        if (hand != player.getUsedItemHand()) return false;
        return isBlocking(player);
    }

    /** 便利方法：把增量应用到 {@code ItemRenderer.render} 的第 5 个参数（PoseStack）。 */
    public static PoseStack modified(PoseStack poseStack) {
        applyBlockDelta(poseStack);
        return poseStack;
    }

    @SuppressWarnings("unused")
    private static ItemRenderer itemRenderer() {
        return Minecraft.getInstance().getItemRenderer();
    }
}
