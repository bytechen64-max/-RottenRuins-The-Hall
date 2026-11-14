package org.bytechen.rottenruins.platform.forge.combat.slash;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.api.combat.slash.*;
import org.bytechen.rottenruins.gameplay.combat.slash.SlashHitResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "rotten_ruins", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlashServer {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final ThreadLocal<Boolean> RESOLVING_TARGET = ThreadLocal.withInitial(() -> false);
    private static final class State {
        long ready, lastStart = Long.MIN_VALUE; int combo, sequence; Item lastItem; Pending pending;
    }
    private static final class Pending {
        final SlashContext context; final ISlashWeapon weapon; final ServerLevel level; final long start; final int sequence;
        boolean resolved; int hits; int criticalHits;
        boolean criticalEligible;
        Pending(SlashContext c, ISlashWeapon w, long start, int seq) {
            context = c; weapon = w; level = c.player().serverLevel(); this.start = start; sequence = seq;
            criticalEligible = SlashCriticals.eligible(c.player());
        }
    }
    private SlashServer() {}

    /** Shared across weapon swaps, separate from ItemCooldowns used by right-click skills. */
    public static int remainingCooldownTicks(ServerPlayer player) {
        State state=STATES.get(player.getUUID());
        return state==null?0:(int)Math.max(0,state.ready-player.server.getTickCount());
    }
    public static void request(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem()) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ISlashWeapon weapon)) return;
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        long now = player.server.getTickCount();
        if (now < state.ready) return;
        SlashSpec base = weapon.slashSpec(player, stack);
        int combo = state.lastItem == stack.getItem() && state.lastStart != Long.MIN_VALUE
                && now - state.lastStart <= base.comboResetTicks() ? state.combo + 1 : 0;
        SlashPose pose = java.util.Objects.requireNonNull(weapon.selectSlashPose(player, stack, base, combo));
        SlashSpec spec = weapon.slashSpecForPose(player, stack, pose, base);
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-8) {
            double yaw = Math.toRadians(player.getYRot()); right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 direction = right.scale(pose.right()).add(up.scale(pose.up())).normalize();
        var context = new SlashContext(player, InteractionHand.MAIN_HAND, stack, pose, combo, spec, forward, direction);
        if (!weapon.canStartSlash(context)) return;
        if (state.pending != null) cancel(state);
        state.ready = now + spec.cooldownTicks(); state.lastStart = now; state.lastItem = stack.getItem(); state.combo = combo;
        state.pending = new Pending(context, weapon, now, ++state.sequence);
        player.resetAttackStrengthTicker();
        weapon.onSlashPose(context); weapon.onSlashStart(context);
        SlashNetwork.broadcast(player, visual(state.pending, false));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventVanillaDuplicate(AttackEntityEvent event) {
        if (!RESOLVING_TARGET.get() && event.getEntity().getMainHandItem().getItem() instanceof ISlashWeapon) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        State state = STATES.get(player.getUUID());
        if (state == null || state.pending == null) return;
        Pending pending = state.pending; var c = pending.context; var spec = c.spec();
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem() || player.serverLevel() != pending.level
                || player.getMainHandItem() != c.stack() || c.stack().isEmpty()) { cancel(state); return; }
        int elapsed = (int) (player.server.getTickCount() - pending.start);
        pending.weapon.onSlashTick(c, elapsed);
        if (!pending.resolved) pending.criticalEligible |= SlashCriticals.eligible(player);
        if (!pending.resolved && elapsed >= Math.ceil(spec.animationTicks() * spec.impactProgress())) {
            pending.resolved = true;
            sound(player, spec.swingSound(), spec);
            int attempts = 0;
            for (LivingEntity target : SlashHitResolver.collect(c)) {
                if (attempts >= spec.maxTargets()) break;
                if (!pending.weapon.canHitWithSlash(c, target) || !pending.weapon.beforeSlashHit(c, target)) continue;
                // Honor ordinary Forge target vetoes/protection hooks without allowing
                // the vanilla single-target damage path to run alongside this resolver.
                boolean allowed;
                RESOLVING_TARGET.set(true);
                try { allowed = net.minecraftforge.common.ForgeHooks.onPlayerAttackTarget(player, target); }
                finally { RESOLVING_TARGET.remove(); }
                if (!allowed) continue;
                attempts++;
                var hit = SlashCriticals.resolve(c, pending.weapon, target, pending.criticalEligible);
                float damage = hit.damage();
                if (!Float.isFinite(damage) || damage < 0) continue;
                boolean dealt = pending.weapon.applySlashDamage(c, target, damage);
                if (dealt) {
                    pending.hits++;
                    if (hit.critical()) {
                        pending.criticalHits++;
                        player.crit(target);
                        pending.weapon.onSlashCritical(c, target, hit.multiplier());
                    }
                    player.setLastHurtMob(target);
                    if (spec.knockback() > 0) target.knockback(spec.knockback(), player.getX() - target.getX(), player.getZ() - target.getZ());
                    EnchantmentHelper.doPostHurtEffects(target, player);
                    EnchantmentHelper.doPostDamageEffects(player, target);
                }
                pending.weapon.onSlashHit(c, target, damage, dealt);
            }
            if (pending.hits == 0) pending.weapon.onSlashMiss(c);
            else {
                sound(player, pending.criticalHits > 0 ? pending.weapon.slashCriticalSound(c) : spec.hitSound(), spec);
                if (spec.durabilityCost() > 0) c.stack().hurtAndBreak(spec.durabilityCost(), player,
                        p -> p.broadcastBreakEvent(c.hand()));
            }
        }
        if (elapsed >= spec.animationTicks()) {
            state.pending = null;
            pending.weapon.onSlashEnd(c, pending.hits);
        }
    }
    private static void cancel(State state) {
        Pending pending = state.pending; state.pending = null;
        pending.weapon.onSlashCancelled(pending.context);
        SlashNetwork.broadcast(pending.context.player(), visual(pending, true));
    }
    private static SlashNetwork.Visual visual(Pending p, boolean cancel) {
        var c = p.context; var s = c.spec();
        return new SlashNetwork.Visual(c.player().getId(), p.sequence, cancel, c.pose(), c.comboIndex(),
                s.animationTicks(), s.impactProgress(), (float) s.arcDegrees(), c.stack().copy(), s.trail(), c.forward(), c.cutDirection());
    }
    private static void sound(ServerPlayer player, SoundEvent sound, SlashSpec spec) {
        if (sound != null) player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, spec.volume(), spec.pitch());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { STATES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { STATES.clear(); }
}
