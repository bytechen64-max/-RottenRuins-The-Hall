package org.bytechen.rottenruins.core.magic.state;

import org.bytechen.rottenruins.core.magic.rules.MagicMath;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** Selection and wheel bindings. Inventory contents and game objects are supplied by the game layer. */
public final class SpellLoadoutState {
    public static final int WHEEL_PAGES = 3;
    public static final int SLOTS_PER_PAGE = 8;
    public static final int TOTAL_WHEEL_SLOTS = WHEEL_PAGES * SLOTS_PER_PAGE;
    public static final int EMPTY = -1;

    public record Snapshot(int selected, int[] wheelSlots) {
        public Snapshot { wheelSlots = wheelSlots.clone(); }
        @Override public int[] wheelSlots() { return wheelSlots.clone(); }
    }

    private int selected;
    private final int[] wheel = new int[TOTAL_WHEEL_SLOTS];

    public SpellLoadoutState() { Arrays.fill(wheel, EMPTY); }

    public static boolean isValidPage(int page) { return page >= 0 && page < WHEEL_PAGES; }
    public static boolean isValidIndex(int index) { return index >= 0 && index < SLOTS_PER_PAGE; }
    public static boolean isValidPosition(int page, int index) { return isValidPage(page) && isValidIndex(index); }
    public static boolean isValidSlot(int slot, int count) { return slot >= 0 && slot < MagicMath.slotCount(count); }

    public int selected(int count) { return isValidSlot(selected, count) ? selected : 0; }
    public void select(int slot, int count) { selected = Math.floorMod(slot, MagicMath.slotCount(count)); }
    public void selectRelative(int delta, int count) {
        selected = (int) Math.floorMod((long) selected(count) + delta, MagicMath.slotCount(count));
    }

    public int binding(int page, int index) {
        return isValidPosition(page, index) ? wheel[page * SLOTS_PER_PAGE + index] : EMPTY;
    }

    public int[] copyPage(int page) {
        int[] copy = new int[SLOTS_PER_PAGE];
        Arrays.fill(copy, EMPTY);
        if (isValidPage(page)) System.arraycopy(wheel, page * SLOTS_PER_PAGE, copy, 0, SLOTS_PER_PAGE);
        return copy;
    }

    public int[] copyWheel() { return wheel.clone(); }

    /** Normalization is shared by loaded data and user edits, not duplicated in packet handlers. */
    public void setPage(int page, int[] mapping, int count, IntPredicate eligible) {
        if (!isValidPage(page) || mapping == null) return;
        int offset = page * SLOTS_PER_PAGE;
        for (int i = 0; i < SLOTS_PER_PAGE; i++) wheel[offset + i] = i < mapping.length ? mapping[i] : EMPTY;
        sanitize(count, eligible);
    }

    /** Keeps the first binding in page/index order and clears invalid or repeated inventory slots. */
    public boolean sanitize(int count, IntPredicate eligible) {
        boolean changed = false;
        boolean[] used = new boolean[MagicMath.slotCount(count)];
        for (int i = 0; i < wheel.length; i++) {
            int slot = wheel[i];
            if (slot == EMPTY) continue;
            if (!isValidSlot(slot, count) || used[slot] || !eligible.test(slot)) {
                wheel[i] = EMPTY;
                changed = true;
            } else used[slot] = true;
        }
        return changed;
    }

    public boolean reconcileSelection(int count) {
        if (isValidSlot(selected, count)) return false;
        selected = 0;
        return true;
    }

    public Snapshot snapshot() { return new Snapshot(selected, wheel); }

    public void load(Snapshot snapshot, int count, IntPredicate eligible) {
        selected = isValidSlot(snapshot.selected(), count) ? snapshot.selected() : 0;
        Arrays.fill(wheel, EMPTY);
        int[] saved = snapshot.wheelSlots();
        System.arraycopy(saved, 0, wheel, 0, Math.min(saved.length, wheel.length));
        sanitize(count, eligible);
    }
}
