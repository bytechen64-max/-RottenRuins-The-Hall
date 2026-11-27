package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HealthAnalyzer {

    private static final int MAX_DEPTH = 32;

    public static final class Result {

        public final String declaringClass;

        public final List<Sym> returns;

        public final List<WritePlan> plans;

        public final String note;

        Result(String declaringClass, List<Sym> returns, List<WritePlan> plans, String note) {
            this.declaringClass = declaringClass;
            this.returns = returns;
            this.plans = plans;
            this.note = note;
        }

        public boolean resolved() { return !plans.isEmpty(); }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("getHealth 声明于 ").append(declaringClass);
            if (note != null) sb.append("  [").append(note).append(']');
            sb.append('\n');
            for (Sym s : returns) sb.append("  return → ").append(s).append('\n');
            if (plans.isEmpty()) {
                sb.append("  (无可写定位——将回退到探测策略)");
            } else {
                int i = 0;
                for (WritePlan p : plans) {
                    sb.append("  #").append(i++).append(" [").append(PlanBuilder.score(p)).append("] ")
                            .append(p.describe()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private HealthAnalyzer() {}

    public static Result analyze(ClassNode cn, ClassResolver resolver, Names names) {
        return analyze(cn, resolver, names, 0, new LinkedHashSet<>());
    }

    private static Result analyze(ClassNode cn, ClassResolver resolver, Names names,
                                  int depth, Set<String> visited) {
        if (cn == null || depth > MAX_DEPTH) return null;
        if (!visited.add(cn.name)) return null;

        MethodNode target = declaredGetHealth(cn, names);
        if (target == null) {
            ClassNode node = resolver.findSuper(cn);
            int hops = 0;
            while (node != null && hops++ < MAX_DEPTH) {
                if (declaredGetHealth(node, names) != null) {
                    Result up = analyze(node, resolver, names, depth + 1, visited);
                    if (up != null) return up;
                    return new Result(cn.name, List.of(), List.of(),
                            "继承自 " + node.name + " 的 getHealth() 无法解析");
                }
                node = resolver.findSuper(node);
            }
            return new Result(cn.name, List.of(), List.of(), "继承链上未找到 getHealth() 实现");
        }

        List<Sym> returns = MethodAnalysis.returnValues(cn, target);
        if (returns.isEmpty()) {
            return new Result(cn.name, List.of(), List.of(), "字节码无法解析");
        }

        List<Sym> delegating = new ArrayList<>();
        List<Sym> direct = new ArrayList<>();
        for (Sym s : returns) {
            if (isDelegation(s, names)) delegating.add(s); else direct.add(s);
        }

        PlanBuilder builder = new PlanBuilder(names, resolver);
        List<WritePlan> plans = new ArrayList<>();
        for (Sym s : direct) plans.addAll(builder.plans(s));

        if (!plans.isEmpty()) {
            return new Result(cn.name, returns, dedupe(plans), null);
        }

        if (!delegating.isEmpty()) {
            ClassNode superNode = null;
            for (Sym s : delegating) {
                if (s.owner != null) {
                    ClassNode candidate = resolver.find(s.owner);
                    if (candidate != null) { superNode = candidate; break; }
                }
            }
            if (superNode == null) superNode = resolver.findSuper(cn);
            Result up = analyze(superNode, resolver, names, depth + 1, visited);
            if (up != null) return up;
            return new Result(cn.name, returns, List.of(), "委托链无法继续解析");
        }

        String note = "无法求逆（可能被钉死在上限或使用了不可逆调用）";
        for (Sym s : direct) {
            if (names.isGetHealth(s) || (s.kind == Sym.K.CALL && names.getMaxHealthName.equals(s.name))) {
                note = "血量被钉死在 getMaxHealth() 上";
                break;
            }
        }
        return new Result(cn.name, returns, List.of(), note);
    }

    static MethodNode declaredGetHealth(ClassNode cn, Names names) {
        if (cn == null || cn.methods == null) return null;
        for (MethodNode m : cn.methods) {
            if (names.getHealthName.equals(m.name) && names.getHealthDesc.equals(m.desc)) return m;
        }
        return null;
    }

    static boolean isDelegation(Sym s, Names names) {
        return s != null && s.kind == Sym.K.CALL
                && names.getHealthName.equals(s.name)
                && names.getHealthDesc.equals(s.desc)
                && !s.statik;
    }

    private static List<WritePlan> dedupe(List<WritePlan> in) {
        Set<String> seen = new LinkedHashSet<>();
        List<WritePlan> out = new ArrayList<>();
        for (WritePlan p : in) if (seen.add(p.describe())) out.add(p);
        return out;
    }
}
