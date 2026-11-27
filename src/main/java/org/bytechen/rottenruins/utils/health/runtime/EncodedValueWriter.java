package org.bytechen.rottenruins.utils.health.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class EncodedValueWriter {

    public interface Reader {
        double health();
    }

    public interface Applier {
        boolean apply(char[] flat);
    }

    public static final class Codec {
        final char[] flat;
        final Applier applier;
        final String desc;

        Codec(char[] flat, Applier applier, String desc) {
            this.flat = flat;
            this.applier = applier;
            this.desc = desc;
        }

        public int length() { return flat.length; }
    }

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static final double EPS = 0.005;
    private static final int MAX_PROBES = 60_000;
    private static final int MAX_ADDITIVE_SLOTS = 8;
    private static final long MAX_SEARCH_NODES = 200_000;

    private EncodedValueWriter() {}

    public static boolean supported(Object value) {
        return value instanceof String || value instanceof String[] || value instanceof char[]
                || value instanceof StringBuilder;
    }

    public static Codec codecFor(Object value, NodeContainer container, Object node) {
        if (value instanceof String s) {
            if (s.isEmpty()) return null;
            return new Codec(s.toCharArray(), buf -> container.replaceValue(node, new String(buf)),
                    "String(" + s.length() + ")");
        }
        return codecForMutable(value);
    }

    public static Codec codecForMap(Object value, Object map, Object key) {
        if (value instanceof String s) {
            if (s.isEmpty()) return null;
            return new Codec(s.toCharArray(),
                    buf -> PlanApplier.setEntryValue(map, key, new String(buf)),
                    "String(" + s.length() + ")@Map");
        }
        return codecForMutable(value);
    }

    private static Codec codecForMutable(Object value) {
        if (value instanceof String[] arr) return codecForArray(arr);
        if (value instanceof char[] chars) {
            if (chars.length == 0) return null;
            char[] flat = chars.clone();
            return new Codec(flat, buf -> {
                try {
                    System.arraycopy(buf, 0, chars, 0, Math.min(buf.length, chars.length));
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }, "char[" + chars.length + "]");
        }
        if (value instanceof StringBuilder sb) {
            if (sb.length() == 0) return null;
            char[] flat = new char[sb.length()];
            sb.getChars(0, sb.length(), flat, 0);
            return new Codec(flat, buf -> {
                try {
                    sb.setLength(0);
                    sb.append(buf);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }, "StringBuilder(" + sb.length() + ")");
        }
        return null;
    }

    private static Codec codecForArray(String[] arr) {
        int total = 0;
        for (String s : arr) if (s != null) total += s.length();
        if (total == 0) return null;
        char[] flat = new char[total];
        int p = 0;
        for (String s : arr) {
            if (s == null) continue;
            s.getChars(0, s.length(), flat, p);
            p += s.length();
        }
        Applier applier = buf -> {
            try {
                int q = 0;
                for (int i = 0; i < arr.length; i++) {
                    String s = arr[i];
                    if (s == null) continue;
                    int len = s.length();
                    arr[i] = new String(buf, q, len);
                    q += len;
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        };
        return new Codec(flat, applier, "String[" + arr.length + "]");
    }

    public static boolean write(Codec codec, Reader reader, double target, Consumer<String> log) {
        if (codec == null || codec.flat.length == 0) return false;
        char[] work = codec.flat;
        char[] orig = work.clone();

        double base = safeRead(reader);
        if (Double.isNaN(base)) return false;
        if (hit(base, target)) return true;

        char[] observed = observedAlphabet(orig);
        int[] budget = {MAX_PROBES};
        Map<Integer, Map<Character, Double>> effects = new LinkedHashMap<>();

        for (char[] alpha : new char[][]{observed, ALPHABET}) {
            for (int slot = 0; slot < work.length; slot++) {
                Map<Character, Double> got = effects.computeIfAbsent(slot, LinkedHashMap::new);
                for (char c : alpha) {
                    if (c == orig[slot] || got.containsKey(c)) continue;
                    if (budget[0]-- <= 0) break;
                    work[slot] = c;
                    if (!codec.applier.apply(work)) { restore(codec, work, orig, slot); continue; }
                    double v = safeRead(reader);
                    if (hit(v, target)) {
                        if (log != null) log.accept("单格命中：位置 " + slot + " → '" + c + "'");
                        return true;
                    }
                    if (!Double.isNaN(v)) got.put(c, v);
                    restore(codec, work, orig, slot);
                }
            }
        }

        List<Integer> sensitive = new ArrayList<>();
        for (Map.Entry<Integer, Map<Character, Double>> e : effects.entrySet()) {
            for (double v : e.getValue().values()) {
                if (Math.abs(v - base) > EPS) { sensitive.add(e.getKey()); break; }
            }
        }
        if (log != null) {
            log.accept("有效位 " + sensitive + "（共 " + work.length + " 格，累计探测 "
                    + (MAX_PROBES - budget[0]) + " 次，原值字符集 " + new String(observed) + "）");
        }
        if (sensitive.isEmpty()) {
            restoreAll(codec, work, orig);
            return false;
        }

        if (additiveSearch(codec, reader, target, base, work, orig, effects, sensitive, log)) return true;

        if (pairSearch(codec, reader, target, work, orig, sensitive, observed, budget, log)) return true;

        restoreAll(codec, work, orig);
        if (log != null) log.accept("三轮探测都没命中 " + target + "，已还原");
        return false;
    }

    private static boolean additiveSearch(Codec codec, Reader reader, double target, double base,
                                          char[] work, char[] orig,
                                          Map<Integer, Map<Character, Double>> effects,
                                          List<Integer> sensitive, Consumer<String> log) {
        int k = Math.min(sensitive.size(), MAX_ADDITIVE_SLOTS);
        List<Integer> slots = sensitive.subList(0, k);

        List<char[]> candChars = new ArrayList<>();
        List<double[]> candDelta = new ArrayList<>();
        double[] suffixMin = new double[k + 1];
        double[] suffixMax = new double[k + 1];
        for (int i = k - 1; i >= 0; i--) {
            Map<Character, Double> got = effects.get(slots.get(i));
            List<Character> cs = new ArrayList<>();
            List<Double> ds = new ArrayList<>();
            cs.add(orig[slots.get(i)]);
            ds.add(0.0);
            double lo = 0, hi = 0;
            for (Map.Entry<Character, Double> e : got.entrySet()) {
                double d = e.getValue() - base;
                cs.add(e.getKey());
                ds.add(d);
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
            }
            char[] cc = new char[cs.size()];
            double[] dd = new double[ds.size()];
            for (int j = 0; j < cs.size(); j++) { cc[j] = cs.get(j); dd[j] = ds.get(j); }
            candChars.add(0, cc);
            candDelta.add(0, dd);
            suffixMin[i] = suffixMin[i + 1] + lo;
            suffixMax[i] = suffixMax[i + 1] + hi;
        }

        char[] pick = new char[k];
        long[] nodes = {0};
        double need = target - base;
        if (!additiveDfs(0, 0.0, need, k, candChars, candDelta, suffixMin, suffixMax, pick, nodes)) {
            return false;
        }
        for (int i = 0; i < k; i++) work[slots.get(i)] = pick[i];
        if (!codec.applier.apply(work)) { restoreAll(codec, work, orig); return false; }
        if (hit(safeRead(reader), target)) {
            if (log != null) log.accept("按位求解命中：" + describePicks(slots, pick));
            return true;
        }
        restoreAll(codec, work, orig);
        return false;
    }

    private static boolean additiveDfs(int idx, double sum, double need, int k,
                                       List<char[]> candChars, List<double[]> candDelta,
                                       double[] suffixMin, double[] suffixMax,
                                       char[] pick, long[] nodes) {
        if (nodes[0]++ > MAX_SEARCH_NODES) return false;
        double rem = need - sum;
        if (idx == k) return Math.abs(rem) <= EPS;
        if (rem < suffixMin[idx] - EPS || rem > suffixMax[idx] + EPS) return false;
        char[] cs = candChars.get(idx);
        double[] ds = candDelta.get(idx);
        for (int i = 0; i < cs.length; i++) {
            pick[idx] = cs[i];
            if (additiveDfs(idx + 1, sum + ds[i], need, k, candChars, candDelta,
                    suffixMin, suffixMax, pick, nodes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pairSearch(Codec codec, Reader reader, double target, char[] work, char[] orig,
                                      List<Integer> sensitive, char[] observed, int[] budget,
                                      Consumer<String> log) {
        int lim = Math.min(sensitive.size(), 4);
        for (char[] alpha : new char[][]{observed, ALPHABET}) {
            for (int i = 0; i < lim; i++) {
                int s1 = sensitive.get(i);
                for (char c1 : alpha) {
                    if (budget[0]-- <= 0) return false;
                    work[s1] = c1;
                    if (!codec.applier.apply(work)) { restore(codec, work, orig, s1); continue; }
                    for (int j = i + 1; j < lim; j++) {
                        int s2 = sensitive.get(j);
                        for (char c2 : alpha) {
                            if (budget[0]-- <= 0) return false;
                            work[s2] = c2;
                            if (!codec.applier.apply(work)) { restore(codec, work, orig, s2); continue; }
                            if (hit(safeRead(reader), target)) {
                                if (log != null) {
                                    log.accept("双格命中：位置 " + s1 + " → '" + c1
                                            + "'，位置 " + s2 + " → '" + c2 + "'");
                                }
                                return true;
                            }
                            restore(codec, work, orig, s2);
                        }
                    }
                    restore(codec, work, orig, s1);
                }
            }
        }
        return false;
    }

    private static String describePicks(List<Integer> slots, char[] pick) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pick.length; i++) {
            if (i > 0) sb.append('，');
            sb.append("位置 ").append(slots.get(i)).append(" → '").append(pick[i]).append('\'');
        }
        return sb.toString();
    }

    private static char[] observedAlphabet(char[] orig) {
        StringBuilder sb = new StringBuilder();
        outer:
        for (char c : orig) {
            for (int i = 0; i < sb.length(); i++) {
                if (sb.charAt(i) == c) continue outer;
            }
            sb.append(c);
            if (sb.length() >= ALPHABET.length) break;
        }
        return sb.toString().toCharArray();
    }

    private static void restore(Codec codec, char[] work, char[] orig, int slot) {
        work[slot] = orig[slot];
        codec.applier.apply(work);
    }

    private static void restoreAll(Codec codec, char[] work, char[] orig) {
        System.arraycopy(orig, 0, work, 0, work.length);
        codec.applier.apply(work);
    }

    private static boolean hit(double v, double target) {
        return !Double.isNaN(v) && Math.abs(v - target) <= EPS;
    }

    private static double safeRead(Reader reader) {
        try {
            return reader.health();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}
