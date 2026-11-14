package org.bytechen.rottenruins.gameplay.magic.state;

import org.bytechen.rottenruins.gameplay.magic.MagicRuntime;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.api.magic.ManaRestoreEvent;
import org.bytechen.rottenruins.core.magic.state.ManaState;
import org.jetbrains.annotations.Nullable;

/** Player mana business state: attributes, restore policy and synchronization snapshots. */
public final class ManaAccount {
    private final Player owner;
    private final ManaState state;
    private int syncedMaximum = -1;
    private int lastSentMaximum = -1;

    public ManaAccount(Player owner) {
        this(owner, MagicStats.maxMana(owner));
    }

    public ManaAccount(Player owner, int initialMana) {
        this.owner = owner;
        state = new ManaState(initialMana, MagicStats.maxMana(owner));
    }

    public Player getOwner() { return owner; }
    public int getMana() { return state.mana(); }
    public int getMaxMana() {
        return owner.level().isClientSide() && syncedMaximum > 0 ? syncedMaximum : MagicStats.maxMana(owner);
    }
    public double getRegenPerSecond() { return MagicStats.manaRegenPerSecond(owner); }
    public void setMana(int value) { state.set(value, getMaxMana()); }
    public boolean spend(int amount) { return state.spend(amount); }
    public boolean isFull() { return state.isFull(getMaxMana()); }
    public float getFillRatio() { return state.fillRatio(getMaxMana()); }

    public int addMana(int amount) { return applyRestore(amount, ManaRestoreEvent.Cause.OTHER, null); }
    public int addMana(int amount, ManaRestoreEvent.Cause cause, @Nullable LivingEntity victim) {
        return applyRestore(amount, cause, victim);
    }
    public int addKillRefund(LivingEntity victim) {
        return applyRestore(MagicStats.killMana(owner, victim), ManaRestoreEvent.Cause.KILL, victim);
    }

    public void tickRegen() {
        if (isFull()) {
            state.clearRestoreFraction();
            return;
        }
        applyRestore(getRegenPerSecond() / 20.0d, ManaRestoreEvent.Cause.REGEN, null);
    }

    private int applyRestore(double amount, ManaRestoreEvent.Cause cause, @Nullable LivingEntity victim) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        var event = new ManaRestoreEvent(owner, cause, victim, (float) amount);
        if (MagicRuntime.services().beforeRestore(event) || event.isCanceled()) return 0;
        float requested = event.getAmount();
        int added = state.restore(requested, getMaxMana());
        if (added > 0) MagicRuntime.services().afterRestore(new ManaRestoreEvent.Post(owner, cause, requested, added));
        return added;
    }

    /** Attribute-only maximum changes must also update the client HUD. */
    public boolean isDirty() { return state.isDirty() || lastSentMaximum != getMaxMana(); }
    public void clearDirty() {
        state.clearDirty();
        lastSentMaximum = getMaxMana();
    }
    public ManaState.View networkState() { return state.view(getMaxMana()); }
    public void applyServerState(ManaState.View view) {
        syncedMaximum = Math.max(1, view.maximum());
        state.applyRemote(view);
        lastSentMaximum = syncedMaximum;
    }

    public ManaState.Stored storedState() { return state.stored(); }
    public void loadState(ManaState.Stored stored) { state.load(stored, getMaxMana()); }
}
