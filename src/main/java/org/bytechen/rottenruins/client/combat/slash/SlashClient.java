package org.bytechen.rottenruins.client.combat.slash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.api.combat.slash.ISlashWeapon;
import org.bytechen.rottenruins.client.rend.slash.*;
import org.bytechen.rottenruins.platform.forge.combat.slash.SlashNetwork;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "rotten_ruins", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlashClient {
    public record Active(SlashNetwork.Visual visual, int startTick) {}
    private static final Map<AbstractClientPlayer, Active> ACTIVE = new WeakHashMap<>();
    private SlashClient() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof ISlashWeapon)) return;
        event.setCanceled(true); event.setSwingHand(false);
        if (!mc.player.isUsingItem()) SlashNetwork.request();
    }
    public static void accept(SlashNetwork.Visual visual) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(visual.entityId()) instanceof AbstractClientPlayer player)) return;
        Active old = ACTIVE.get(player);
        if (visual.cancelled()) {
            if (old != null && old.visual.sequence() == visual.sequence()) end(player, true);
            return;
        }
        if (!(visual.stack().getItem() instanceof ISlashWeapon)) return;
        if (old != null) end(player, true);
        Active active = new Active(visual, player.tickCount);
        ACTIVE.put(player, active);
        SlashVisuals.get(visual.stack().getItem()).onStart(context(player, active, 0, true));
    }
    public static Active active(AbstractClientPlayer player) { return ACTIVE.get(player); }
    public static List<Map.Entry<AbstractClientPlayer, Active>> activeEntries() { return List.copyOf(ACTIVE.entrySet()); }
    public static SlashVisuals.Context context(AbstractClientPlayer player, Active active, float partialTick, boolean firstPerson) {
        float elapsed = player.tickCount - active.startTick + partialTick;
        float progress = firstPerson ? SlashTimeline.progress(elapsed, active.visual.animationTicks()) : elapsed / active.visual.animationTicks();
        return new SlashVisuals.Context(player, active.visual.stack(), active.visual.pose(), active.visual.combo(), elapsed,
                Math.min(1, Math.max(0, progress)), firstPerson, active.visual.trail());
    }
    public static SlashMotion.Pose pose(AbstractClientPlayer player, ItemStack stack, float partialTick, boolean firstPerson) {
        Active active = ACTIVE.get(player);
        if (active == null || !ItemStack.isSameItem(stack, active.visual.stack())) return null;
        var context = context(player, active, partialTick, firstPerson);
        var pose = SlashVisuals.get(stack.getItem()).sample(context);
        return pose != null && pose.weight() > 0 ? pose : null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        for (var entry : activeEntries()) {
            var player = entry.getKey(); var active = entry.getValue();
            if (mc.level == null || player.level() != mc.level || player.isRemoved() || !player.isAlive()
                    || !(player.getMainHandItem().getItem() instanceof ISlashWeapon)
                    || !ItemStack.isSameItem(player.getMainHandItem(), active.visual.stack()) || player.isUsingItem()) {
                end(player, true); continue;
            }
            var anchors=org.bytechen.rottenruins.client.combat.slash.trail.anchors.AnchorProfiles.get(active.visual.stack());
            float trailEnd=active.visual.animationTicks()*SlashMotion.CUT_END+active.visual.trail().lifetimeTicks();
            if(anchors!=null)trailEnd=Math.max(active.visual.animationTicks()*anchors.firstPerson().end()+anchors.firstPerson().lifetimeTicks(),
                    active.visual.animationTicks()*anchors.thirdPerson().end()+anchors.thirdPerson().lifetimeTicks());
            trailEnd=Math.max(trailEnd,active.visual.animationTicks()*(anchors==null?SlashMotion.CUT_END:Math.max(anchors.firstPerson().end(),anchors.thirdPerson().end()))
                    +org.bytechen.rottenruins.client.combat.slash.trail.anchors.SlashEffects.get(active.visual.stack()).lifetimeTicks());
            float end = Math.max(active.visual.animationTicks() * SlashMotion.CUT_END + SlashTimeline.RECOVERY_TICKS,trailEnd);
            if (player.tickCount - active.startTick > end + 1) end(player, false);
        }
    }
    private static void end(AbstractClientPlayer player, boolean cancelled) {
        org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrackedSlashTrails.clear(player);
        Active old = ACTIVE.remove(player);
        if (old != null) SlashVisuals.get(old.visual.stack().getItem()).onEnd(context(player, old, 0, true), cancelled);
    }
}
