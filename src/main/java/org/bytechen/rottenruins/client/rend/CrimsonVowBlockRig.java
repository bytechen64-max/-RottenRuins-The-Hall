package org.bytechen.rottenruins.client.rend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 低版本（1.8 式）举剑格挡的姿态增量：让原版照常渲染，只在传给 {@code ItemRenderer} 的 PoseStack 上叠加偏移。
 * 不做整套替换 —— 模型自带 firstperson display 变换，整套替换会与格挡旋转叠加、把剑刃拧向镜头。
 */
public final class CrimsonVowBlockRig {

    // 姿态增量：单位与相机空间一致（1.0 ≈ 一个方块），数值偏保守 —— 目标是"看得见、像在格挡"。
    // SWEEP_Z 绕 Z 把剑身横过来（符号 = 朝哪边斜），FACE_Y 绕 Y 让薄片剑面正对镜头；实机反了就取反对应符号。
    private static final float LIFT_Y = 0.26F;

    private static final float INWARD_X = -0.18F;

    private static final float NEAR_Z = 0.12F;

    private static final float SWEEP_Z = 38.0F;

    private static final float FACE_Y = 60.0F;

    private static final float TILT_X = -12.0F;

    private static final float SCALE = 0.95F;

    // 蓄力期间叠加的增量（纯表现层，进度由服务端 CrimsonVowChargeEntity 推进）；量都很小，第一人称大幅位移会让剑"脱手"。
    private static final float CHARGE_PUSH = 0.17F;
    private static final float CHARGE_LIFT = 0.055F;
    private static final float CHARGE_TILT = -9.0F;

    private CrimsonVowBlockRig() {}

    /** 该实体此刻是否正在用某把 {@link WeaponBlock} 武器格挡（与减伤判定同源，视觉和数值不会不一致）。 */
    public static boolean isBlocking(LivingEntity entity) {
        return org.bytechen.rottenruins.api.IBlockingWeapon.isBlocking(entity);
    }

    public static void applyBlockDelta(PoseStack poseStack) {
        applyBlockDelta(poseStack, false);
    }

    /** 叠加格挡增量，mirrored=这只手渲染在屏幕左侧（左手）：X 平移与绕 Z/Y 旋转取反，Y/Z 平移、绕 X 俯仰、缩放不变。 */
    public static void applyBlockDelta(PoseStack poseStack, boolean mirrored) {
        float sign = mirrored ? -1.0F : 1.0F;

        poseStack.translate(INWARD_X * sign, LIFT_Y, NEAR_Z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(SWEEP_Z * sign));
        // Y 轴必须放在 Z 之后：先把剑横过来，再绕竖直轴把剑面转向镜头；顺序反过来 Z 会把 Y 的结果再转侧。
        poseStack.mulPose(Axis.YP.rotationDegrees(FACE_Y * sign));
        poseStack.mulPose(Axis.XP.rotationDegrees(TILT_X));
        poseStack.scale(SCALE, SCALE, SCALE);
        applyChargeDelta(poseStack);
    }

    public static boolean rendersOnLeftSide(AbstractClientPlayer player, InteractionHand hand) {
        if (player == null) return false;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        return arm == HumanoidArm.LEFT;
    }

    /** 蓄力增量：往前推、略微抬高并压低剑尖；叠在格挡姿态之后（相加，不替换）。进度读客户端蓄力实体的
     *  同步数据 {@code DATA_CHARGE} — 两端一致，不需要新网络包。 */
    private static void applyChargeDelta(PoseStack poseStack) {
        float charge = localCharge();
        if (charge <= 0.001F) return;

        poseStack.translate(0.0F, CHARGE_LIFT * charge, -CHARGE_PUSH * charge);
        poseStack.mulPose(Axis.XP.rotationDegrees(CHARGE_TILT * charge));
    }

    /** 本地玩家当前举着的剑的蓄力进度（0..1）。按 {@code ownerUUID} 认人而不是找最近的一个 —— 附近别的玩家也在蓄力时认错人会让剑跟着抖。 */
    public static float localCharge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0f;
        java.util.UUID self = mc.player.getUUID();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowChargeEntity c
                    && self.equals(c.getOwnerUUID())) {
                return c.charge(mc.getFrameTime());
            }
        }
        return 0f;
    }

    /** 供混入类判断：这次渲染是否要套格挡姿态。不必判 hand —— isBlocking 已用 {@code getUseItem()} 锁定使用中的那只手。 */
    public static boolean shouldApply(AbstractClientPlayer player, ItemStack stack,
                                      InteractionHand hand) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof org.bytechen.rottenruins.api.IBlockingWeapon)) return false;
        // 双手都可能拿到同一个物品实例（副手），这里再确认一次"这只手就是使用中的那只"
        if (hand != player.getUsedItemHand()) return false;
        return isBlocking(player);
    }

    public static PoseStack modified(PoseStack poseStack) {
        applyBlockDelta(poseStack);
        return poseStack;
    }

    @SuppressWarnings("unused")
    private static ItemRenderer itemRenderer() {
        return Minecraft.getInstance().getItemRenderer();
    }
}
