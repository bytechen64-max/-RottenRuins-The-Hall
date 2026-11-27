package org.bytechen.rottenruins.utils.health.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

public final class Alt {

    public static final int MAX = 6;

    public static final Alt UNKNOWN = new Alt(List.of(Sym.UNKNOWN));

    private final List<Sym> options;

    private Alt(List<Sym> options) {
        this.options = options;
    }

    public static Alt of(Sym s) {
        return new Alt(List.of(s == null ? Sym.UNKNOWN : s));
    }

    public static Alt of(List<Sym> list) {
        if (list == null || list.isEmpty()) return UNKNOWN;
        LinkedHashSet<Sym> set = new LinkedHashSet<>(list);
        if (set.size() == 1) return new Alt(List.of(set.iterator().next()));
        List<Sym> capped = new ArrayList<>(Math.min(set.size(), MAX));
        for (Sym s : set) {
            capped.add(s);
            if (capped.size() >= MAX) break;
        }
        return new Alt(Collections.unmodifiableList(capped));
    }

    public List<Sym> options() { return options; }

    public Sym single() { return options.size() == 1 ? options.get(0) : Sym.UNKNOWN; }

    public boolean isCertain() { return options.size() == 1; }

    public boolean isUnknownOnly() { return options.size() == 1 && options.get(0).kind == Sym.K.UNKNOWN; }

    public Alt map(Function<Sym, Sym> fn) {
        List<Sym> out = new ArrayList<>(options.size());
        for (Sym s : options) out.add(fn.apply(s));
        return Alt.of(out);
    }

    public static Alt unary(int opcode, Alt a) {
        if (a == null) return UNKNOWN;
        List<Sym> out = new ArrayList<>(a.options.size());
        for (Sym x : a.options) out.add(Sym.op(opcode, x, null));
        return Alt.of(out);
    }

    public static Alt binary(int opcode, Alt a, Alt b) {
        if (a == null || b == null) return UNKNOWN;
        List<Sym> out = new ArrayList<>(a.options.size() * b.options.size());
        for (Sym x : a.options) {
            for (Sym y : b.options) {
                out.add(Sym.op(opcode, x, y));
                if (out.size() >= MAX) return Alt.of(out);
            }
        }
        return Alt.of(out);
    }

    public static Alt cast(String desc, Alt a) {
        if (a == null) return UNKNOWN;
        List<Sym> out = new ArrayList<>(a.options.size());
        for (Sym x : a.options) out.add(Sym.cast(desc, x));
        return Alt.of(out);
    }

    public static Alt field(boolean statik, String owner, String name, String desc, Alt recv) {
        if (!statik && recv == null) return UNKNOWN;
        if (statik) return Alt.of(Sym.field(true, owner, name, desc, null));
        List<Sym> out = new ArrayList<>(recv.options.size());
        for (Sym r : recv.options) out.add(Sym.field(false, owner, name, desc, r));
        return Alt.of(out);
    }

    public static Alt call(boolean statik, String owner, String name, String desc, Alt recv, List<Alt> args) {
        List<Sym> receivers = statik ? Collections.singletonList((Sym) null)
                : (recv == null ? Collections.singletonList(Sym.UNKNOWN) : recv.options);
        List<List<Sym>> argOptions = new ArrayList<>(args.size());
        for (Alt a : args) argOptions.add(a == null ? Collections.singletonList(Sym.UNKNOWN) : a.options);

        List<Sym> out = new ArrayList<>();
        List<Sym> current = new ArrayList<>(args.size());
        for (Sym r : receivers) {
            buildCalls(statik, owner, name, desc, r, argOptions, 0, current, out);
            if (out.size() >= MAX) break;
        }
        return Alt.of(out);
    }

    private static void buildCalls(boolean statik, String owner, String name, String desc, Sym recv,
                                   List<List<Sym>> argOptions, int index, List<Sym> current, List<Sym> out) {
        if (out.size() >= MAX) return;
        if (index == argOptions.size()) {
            out.add(Sym.call(statik, owner, name, desc, recv, new ArrayList<>(current)));
            return;
        }
        for (Sym arg : argOptions.get(index)) {
            current.add(arg);
            buildCalls(statik, owner, name, desc, recv, argOptions, index + 1, current, out);
            current.remove(current.size() - 1);
            if (out.size() >= MAX) return;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alt a)) return false;
        return options.equals(a.options);
    }

    @Override
    public int hashCode() { return options.hashCode(); }

    @Override
    public String toString() {
        if (options.size() == 1) return options.get(0).toString();
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(options.get(i));
        }
        return sb.append('}').toString();
    }
}
