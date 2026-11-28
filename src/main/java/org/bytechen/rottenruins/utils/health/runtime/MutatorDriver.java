package org.bytechen.rottenruins.utils.health.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MutatorDriver {

    public interface Health {
        float read();
    }

    private static final float EPS = 0.005f;
    private static final int MAX_CANDIDATES = 6;

    public record Result(boolean success, boolean partial, String method, String mode,
                         float before, float after, float target) {
        public String describe() {
            String head = success ? "成功" : partial ? "部分成功" : "失败";
            String s = head + " [MUTATOR " + method + "/" + mode + "] " + before + " → " + after;
            if (partial) s += "（目标 " + target + "，被目标模组自身的限制挡住）";
            return s;
        }
    }

    private MutatorDriver() {}

    public static Result drive(Object entity, List<Object> holders, Health health,
                               List<MutatorFinder.Candidate> candidates, float target,
                               Consumer<String> log, Consumer<String> summary) {
        if (entity == null || health == null || candidates == null || candidates.isEmpty()) return null;
        List<Object> carriers = (holders == null || holders.isEmpty()) ? List.of(entity) : holders;

        int tried = 0;
        int combos = 0;
        int rejected = 0;
        int noEffect = 0;
        int threw = 0;
        int overshoot = 0;
        boolean wrongWay = false;
        for (MutatorFinder.Candidate c : candidates) {
            if (tried++ >= MAX_CANDIDATES) break;
            Class<?> p0 = c.paramTypes()[0];
            List<Object> use = p0.isAssignableFrom(entity.getClass()) ? List.of(entity) : carriers;
            for (Object carrier : use) {
                if (carrier == null || !p0.isAssignableFrom(carrier.getClass())) continue;
                if (c.hasSlot()) {
                    for (int slot : SLOTS) {
                        combos++;
                        Verdict v = new Verdict();
                        Result r = tryCandidate(carrier, slot, health, c, target, log, v);
                        if (r != null) return r;
                        switch (v.kind) {
                            case REJECTED -> rejected++;
                            case NO_EFFECT -> noEffect++;
                            case THREW -> threw++;
                            case OVERSHOOT -> overshoot++;
                            case WRONG_WAY -> wrongWay = true;
                            default -> { }
                        }
                    }
                } else {
                    combos++;
                    Verdict v = new Verdict();
                    Result r = tryCandidate(carrier, NO_SLOT, health, c, target, log, v);
                    if (r != null) return r;
                    switch (v.kind) {
                        case REJECTED -> rejected++;
                        case NO_EFFECT -> noEffect++;
                        case THREW -> threw++;
                        case OVERSHOOT -> overshoot++;
                        case WRONG_WAY -> wrongWay = true;
                        default -> { }
                    }
                }
            }
        }
        if (summary != null) {
            StringBuilder sb = new StringBuilder("试过 ").append(tried).append(" 个候选原语 / ")
                    .append(combos).append(" 种调用组合：明确拒绝 ").append(rejected)
                    .append(" 个、调了没反应 ").append(noEffect)
                    .append(" 个、调用抛异常 ").append(threw).append(" 个");
            if (overshoot > 0) sb.append("；有 ").append(overshoot).append(" 个候选把读数推得远超请求幅度")
                    .append("（像是重载/表示不匹配）");
            if (wrongWay) sb.append("；有候选把读数推向了反方向");
            if (rejected > 0 && noEffect == 0 && threw == 0) {
                sb.append("（全是明确拒绝 —— 最像是目标模组自己挡住了外部调用）");
            }
            summary.accept(sb.toString());
        }
        return null;
    }

    private static final int[] SLOTS = {0, 1, 2, 3};

    private static final int NO_SLOT = -1;

    private static final float OVERSHOOT_FACTOR = 3f;

    private enum Kind { NONE, REJECTED, NO_EFFECT, THREW, OVERSHOOT, WRONG_WAY }

    private static final class Verdict {
        Kind kind = Kind.NONE;
    }

    private static Result tryCandidate(Object carrier, int slot, Health health, MutatorFinder.Candidate c, float target,
                                       Consumer<String> log, Verdict out) {
        float before = health.read();
        if (Float.isNaN(before)) return null;

        for (Attempt a : attemptsFor(c, before, target)) {
            float now = health.read();
            if (Float.isNaN(now) || Math.abs(now - before) > EPS) {
                out.kind = Kind.WRONG_WAY;
                return null;
            }

            InvokeResult inv = invoke(carrier, slot, c, a.arg(), log);
            if (!inv.ok) {
                out.kind = Kind.THREW;
                continue;
            }
            float after = health.read();
            if (Float.isNaN(after)) continue;
            if (Math.abs(after - now) < EPS) {
                out.kind = inv.rejected ? Kind.REJECTED : Kind.NO_EFFECT;
                if (log != null) {
                    log.accept("原语无效果（" + a.mode() + "）：" + c.display() + "(" + a.arg() + ")"
                            + (inv.rejected ? "，它返回 0，说明是主动拒绝（校验失败/冷却中）"
                            : "，返回 " + inv.ret));
                }
                continue;
            }

            String method = c.display();
            if (Math.abs(after - target) <= EPS) {
                if (log != null) log.accept("原语命中（" + a.mode() + "）：" + method + "(" + a.arg() + ") → " + after);
                return new Result(true, false, method, a.mode(), before, after, target);
            }
            boolean down = target < now;
            boolean movedRight = down ? after < now - EPS : after > now + EPS;
            if (movedRight) {
                float change = Math.abs(after - now);
                float wanted = Math.abs(target - now);
                if (change > wanted * OVERSHOOT_FACTOR + 1f) {
                    out.kind = Kind.OVERSHOOT;
                    if (log != null) {
                        log.accept("原语幅度异常（" + a.mode() + "）：" + c.display() + "(" + a.arg() + ") "
                                + now + " → " + after + "，请求的跨度只有 " + wanted
                                + " —— 像是重载/表示不匹配，换下一个候选");
                    }
                    return null;
                }
                if (log != null) {
                    log.accept("原语部分生效（" + a.mode() + "）：" + c.display() + "(" + a.arg() + ") "
                            + now + " → " + after + "，目标 " + target);
                }
                return new Result(false, true, c.display(), a.mode(), before, after, target);
            }

            if (log != null) {
                log.accept("原语方向不对（" + a.mode() + "）：" + method + "(" + a.arg() + ") "
                        + now + " → " + after + "，尝试还原");
            }
            if (!restore(carrier, slot, health, c, now, log)) {
                out.kind = Kind.WRONG_WAY;
                return new Result(false, false, method, a.mode() + "/方向错误且无法还原",
                        before, after, target);
            }
        }
        return null;
    }

    private static boolean restore(Object carrier, int slot, Health health, MutatorFinder.Candidate c,
                                   float was, Consumer<String> log) {
        if (c.numericReturn()) return false;
        if (!invoke(carrier, slot, c, was, null).ok) return false;
        float back = health.read();
        boolean ok = !Float.isNaN(back) && Math.abs(back - was) <= EPS;
        if (!ok && log != null) {
            log.accept("还原失败：读数为 " + back + "，期望 " + was + "（该原语只能单向改动）");
        }
        return ok;
    }

    private record Attempt(String mode, float arg) {}

    private static List<Attempt> attemptsFor(MutatorFinder.Candidate c, float before, float target) {
        float delta = target - before;
        List<Attempt> list = new ArrayList<>(3);
        if (c.numericReturn()) {
            list.add(new Attempt("增量", Math.abs(delta)));
            list.add(new Attempt("绝对值", target));
            list.add(new Attempt("带符号增量", delta));
        } else {
            list.add(new Attempt("绝对值", target));
            list.add(new Attempt("增量", Math.abs(delta)));
        }
        return list;
    }

    private record InvokeResult(boolean ok, Object ret, boolean rejected) {}

    private static InvokeResult invoke(Object carrier, int slot, MutatorFinder.Candidate c, float arg,
                                       Consumer<String> log) {
        Class<?> pLast = c.valueType();
        Object value = switch (pLast.getName()) {
            case "double", "java.lang.Double" -> (Object) (double) arg;
            case "int", "java.lang.Integer" -> (Object) (int) arg;
            case "long", "java.lang.Long" -> (Object) (long) arg;
            default -> (Object) arg;
        };
        Object[] args = c.hasSlot() && slot >= 0
                ? new Object[]{carrier, slot, value}
                : new Object[]{carrier, value};
        try {
            Object ret = c.invokeStatic(args);
            boolean rejected = (ret instanceof Number n && n.doubleValue() == 0.0d)
                    || Boolean.FALSE.equals(ret);
            return new InvokeResult(true, ret, rejected);
        } catch (Throwable t) {
            if (log != null) log.accept("调用 " + c.display() + " 失败：" + t);
            return new InvokeResult(false, null, false);
        }
    }
}
