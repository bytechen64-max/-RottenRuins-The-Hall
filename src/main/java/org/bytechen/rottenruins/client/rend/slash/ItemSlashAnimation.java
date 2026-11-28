package org.bytechen.rottenruins.client.rend.slash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bytechen.rottenruins.config.ConfigHelper;

/** Render transforms for server-confirmed interface slashes; render passes never advance state. */
public final class ItemSlashAnimation {
    private ItemSlashAnimation() {}
    public static boolean enabled() {
        return ConfigHelper.configHolder == null || ConfigHelper.configHolder.get().itemOnlySwordSlashes;
    }
    public static SlashMotion.Pose pose(LivingEntity entity, InteractionHand hand, ItemStack stack, float partialTick) {
        return pose(entity, hand, stack, partialTick, false);
    }
    public static SlashMotion.Pose pose(LivingEntity entity, InteractionHand hand, ItemStack stack, float partialTick,
                                       boolean firstPerson) {
        if (!enabled() || hand != InteractionHand.MAIN_HAND || !(entity instanceof AbstractClientPlayer player)
                || !(stack.getItem() instanceof org.bytechen.rottenruins.api.combat.slash.ISlashWeapon)
                || player.isUsingItem() || player.isAutoSpinAttack() || player.isScoping()) return null;
        return org.bytechen.rottenruins.client.combat.slash.SlashClient.pose(player, stack, partialTick, firstPerson);
    }

    /** Absolute frame during a swing only. idleHand is the vanilla hand matrix before display transforms. */
    public static boolean apply(PoseStack poses, LivingEntity entity, ItemStack stack,
                                ItemDisplayContext context, boolean left, SlashMotion.Pose motion,
                                float equipProgress, Matrix4f idleHand) {
        if (motion == null || motion.weight() <= 0) return false;
        boolean firstPerson = context.firstPerson();
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(),
                entity, entity.getId() + context.ordinal());
        if (model.isCustomRenderer()) return false;
        PoseStack displayPoses = new PoseStack();
        model.applyTransform(context, displayPoses, left);
        Matrix4f display = new Matrix4f(displayPoses.last().pose());
        if (Math.abs(display.determinant()) < 0.000001F) return false;
        Matrix4f idleModel = idleHand == null ? new Matrix4f(poses.last().pose())
                .translate(left ? -0.56F : 0.56F, -0.52F - equipProgress * 0.6F, -0.72F)
                : new Matrix4f(idleHand);
        idleModel.mul(display);
        Vector3f scale = idleModel.getScale(new Vector3f());
        var detected = BladeModelGeometry.profile(model, stack, scale);
        if (detected.isEmpty()) return false;
        BladeGeometry.Profile geometry = detected.get();
        Vector3f rawGrip = vector(geometry.grip()).div(scale);
        Vector3f grip = idleModel.transformPosition(new Vector3f(rawGrip));
        Quaternionf rotation = idleModel.getUnnormalizedRotation(new Quaternionf());
        Quaternionf sourceInverse = SlashProjection.orientation(vector(geometry.edge()),
                vector(geometry.blade()), vector(geometry.normal())).conjugate();
        Vector3f cutGrip;
        Quaternionf cutRotation;
        if (firstPerson) {
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            cutGrip = SlashProjection.weaponPoint(motion, 0.85F, projection);
            cutRotation = SlashProjection.screenOrientation(motion, projection).mul(sourceInverse);
        } else {
            // The active swing is body-relative; the original arm matrix is used only for blending in/out.
            float partial = Minecraft.getInstance().getFrameTime();
            float bodyYaw = net.minecraft.util.Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            Matrix4f body = SlashProjection.viewRelativeFrame(poses.last().pose(), bodyYaw,
                    entity.getViewYRot(partial), entity.getViewXRot(partial), entity.isCrouching() ? 0.82F : 0.65F);
            float across = motion.screenX() / 1.05F * 0.65F;
            float vertical = motion.edge().y() == 0 ? 0.03F : motion.screenY() / 0.82F * 0.65F;
            cutGrip = body.transformPosition(new Vector3f(across, vertical - 0.04F, -0.55F));
            cutRotation = body.getUnnormalizedRotation(new Quaternionf())
                    .mul(SlashProjection.orientation(vector(motion.edge()), vector(motion.blade()), vector(motion.normal())))
                    .mul(sourceInverse);
        }
        if (firstPerson && motion.progress() < SlashMotion.PREPARE_END) {
            SlashRecovery.Frame entry = SlashRecovery.entry(motion.progress(), cutGrip, cutRotation,
                    (float) geometry.radius(), RenderSystem.getProjectionMatrix());
            grip.set(entry.grip()); rotation.set(entry.rotation());
        } else if (firstPerson && motion.progress() >= SlashMotion.CUT_END) {
            SlashRecovery.Frame recovery = SlashRecovery.sample(motion.progress(), cutGrip, cutRotation,
                    grip, rotation, (float) geometry.radius(), RenderSystem.getProjectionMatrix());
            grip.set(recovery.grip());
            rotation.set(recovery.rotation());
        } else {
            grip.lerp(cutGrip, motion.weight());
            rotation.slerp(cutRotation, motion.weight());
        }
        SlashPoseFrame solved=SlashPoseFrame.of(grip,rotation,scale,rawGrip);
        Matrix4f frame = solved.itemPose(display);
        // Absolute camera/body frame avoids inherited hand sway shifting screen-relative cuts.
        poses.last().pose().set(frame);
        poses.last().normal().set(new Matrix3f(frame).invert().transpose());
        if (entity instanceof AbstractClientPlayer player) {
            var active = org.bytechen.rottenruins.client.combat.slash.SlashClient.active(player);
            if (active != null) org.bytechen.rottenruins.client.combat.slash.SlashVisuals.get(stack.getItem()).transform(
                    org.bytechen.rottenruins.client.combat.slash.SlashClient.context(player, active,
                            Minecraft.getInstance().getFrameTime(), firstPerson), poses);
        }
        org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrackedSlashTrails.publishFrame(entity,stack,context,
                SlashPoseFrame.modelPose(poses.last().pose(),display));
        return true;
    }
    private static Vector3f vector(SlashMotion.Vec v) { return new Vector3f(v.x(), v.y(), v.z()); }
    private static Vector3f vector(BladeGeometry.Vec v) {
        return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
    }
}
