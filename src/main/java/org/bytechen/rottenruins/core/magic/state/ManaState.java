package org.bytechen.rottenruins.core.magic.state;

/** Authoritative numeric state; attributes, events, serialization and networking live outside. */
public final class ManaState {
    public record Stored(int mana, double restoreFraction) {}
    public record View(int mana, int maximum) {}

    private int mana;
    private double restoreFraction;
    private boolean dirty;

    public ManaState(int initial, int maximum) {
        mana = clamp(initial, maximum);
    }

    public int mana() { return mana; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void set(int amount, int maximum) {
        int next = clamp(amount, maximum);
        if (next != mana) {
            mana = next;
            dirty = true;
        }
    }

    public boolean spend(int amount) {
        if (amount <= 0) return true;
        if (mana < amount) return false;
        mana -= amount;
        dirty = true;
        return true;
    }

    public boolean isFull(int maximum) { return mana >= maximum; }

    public float fillRatio(int maximum) {
        return maximum <= 0 ? 0f : Math.max(0f, Math.min(1f, mana / (float) maximum));
    }

    public void clearRestoreFraction() { restoreFraction = 0; }

    /** Receives the final event-adjusted amount. Fractions accumulate before integer mana is applied. */
    public int restore(double amount, int maximum) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        double accumulated = restoreFraction + amount;
        double whole = Math.floor(accumulated);
        restoreFraction = accumulated - whole;
        if (whole <= 0) return 0;
        int before = mana;
        // Use floating-point addition followed by a saturating cast to avoid integer overflow.
        mana = clamp((int) Math.min(Integer.MAX_VALUE, mana + whole), maximum);
        int added = Math.max(0, mana - before);
        if (mana != before) dirty = true;
        if (added < whole) restoreFraction = 0;
        return added;
    }

    public Stored stored() { return new Stored(mana, restoreFraction); }
    public View view(int maximum) { return new View(mana, Math.max(1, maximum)); }

    public void load(Stored stored, int maximum) {
        mana = clamp(stored.mana(), maximum);
        double fraction = stored.restoreFraction();
        restoreFraction = Double.isFinite(fraction) && fraction >= 0 && fraction < 1 ? fraction : 0;
        dirty = false;
    }

    public void applyRemote(View view) {
        mana = clamp(view.mana(), view.maximum());
        restoreFraction = 0;
        dirty = false;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(Math.max(1, maximum), value));
    }
}
