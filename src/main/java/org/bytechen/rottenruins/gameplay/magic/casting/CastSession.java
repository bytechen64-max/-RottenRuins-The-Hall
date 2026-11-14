package org.bytechen.rottenruins.gameplay.magic.casting;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.core.magic.casting.CastingState;
import org.jetbrains.annotations.Nullable;

/** Game-object context around an independent casting timeline. Owned by the server casting service. */
public final class CastSession {
    private final CastingState timing = new CastingState();
    private ItemStack spell = ItemStack.EMPTY;
    private int slot = -1;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    @Nullable private LivingEntity lockedTarget;

    public CastingState timing() { return timing; }
    public CastingState.Phase getPhase() { return timing.phase(); }
    public boolean isIdle() { return timing.isIdle(); }
    public boolean isCharging() { return timing.isCharging(); }
    public boolean isChanneling() { return timing.isChanneling(); }
    public boolean isWheelSession() { return timing.source() == CastingState.Source.WHEEL; }
    public ItemStack getSpell() { return spell; }
    public int getSlot() { return slot; }
    public InteractionHand getHand() { return hand; }
    public int getHeldTicks() { return timing.heldTicks(); }
    public void setHeldTicks(int ticks) { timing.updateHeldTicks(ticks); }
    @Nullable public LivingEntity getLockedTarget() { return lockedTarget; }

    public void beginCharging(ItemStack spell, int slot, InteractionHand hand, CastingState.Source source) {
        timing.beginCharging(source);
        bind(spell, slot, hand, null);
    }

    public void beginChanneling(ItemStack spell, int slot, InteractionHand hand,
                                @Nullable LivingEntity lockedTarget, CastingState.Source source) {
        timing.beginChanneling(source);
        bind(spell, slot, hand, lockedTarget);
    }

    private void bind(ItemStack spell, int slot, InteractionHand hand, @Nullable LivingEntity lockedTarget) {
        this.spell = spell;
        this.slot = slot;
        this.hand = hand;
        this.lockedTarget = lockedTarget;
    }

    public void reset() {
        timing.reset();
        bind(ItemStack.EMPTY, -1, InteractionHand.MAIN_HAND, null);
    }
}
