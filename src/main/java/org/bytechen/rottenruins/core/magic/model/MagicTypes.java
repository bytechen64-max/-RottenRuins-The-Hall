package org.bytechen.rottenruins.core.magic.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Nonempty immutable combination of enum schools. First school is the display/legacy primary. */
public final class MagicTypes {
    private final List<MagicType> values;

    private MagicTypes(MagicType[] types) {
        if (types.length == 0) throw new IllegalArgumentException("A spell needs at least one magic type");
        Arrays.stream(types).forEach(Objects::requireNonNull);
        values = List.copyOf(new LinkedHashSet<>(Arrays.asList(types)));
    }

    public static MagicTypes of(MagicType... types) {
        return new MagicTypes(types);
    }

    public List<MagicType> values() { return values; }
    public MagicType primary() { return values.get(0); }
    public boolean contains(MagicType type) { return values.contains(type); }
    public boolean anyMatch(Predicate<MagicType> predicate) { return values.stream().anyMatch(predicate); }

    @Override
    public boolean equals(Object other) {
        return other instanceof MagicTypes types && values.equals(types.values);
    }

    @Override
    public int hashCode() { return values.hashCode(); }

    @Override
    public String toString() { return values.toString(); }
}
