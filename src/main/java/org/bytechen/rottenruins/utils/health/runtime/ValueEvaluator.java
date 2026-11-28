package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.Arith;
import org.bytechen.rottenruins.utils.health.analysis.RuntimeLookup;
import org.bytechen.rottenruins.utils.health.analysis.Sym;

import java.lang.reflect.Method;

public final class ValueEvaluator implements RuntimeLookup {

    private static final int MAX_DEPTH = 24;

    private final Object self;
    private final ClassLoader loader;

    public ValueEvaluator(Object self) {
        this.self = self;
        this.loader = self != null ? self.getClass().getClassLoader() : ValueEvaluator.class.getClassLoader();
    }

    @Override
    public Object eval(Sym sym) {
        return eval(sym, 0);
    }

    public Object self() { return self; }

    private Object eval(Sym s, int depth) {
        if (s == null || depth > MAX_DEPTH) return null;
        switch (s.kind) {
            case THIS:
                return self;
            case CONST:
                return s.cst;
            case PARAM:
                return null;
            case FIELD: {
                Object recv = s.statik ? null : eval(s.a, depth + 1);
                if (!s.statik && recv == null) return null;
                Class<?> owner = MemberResolver.load(s.owner, loader);
                return FieldAccess.read(owner, s.name, s.desc, recv);
            }
            case CALL: {
                Object recv = s.statik ? null : eval(s.a, depth + 1);
                if (!s.statik && recv == null) return null;
                Class<?> owner = MemberResolver.load(s.owner, loader);
                Method m = MemberResolver.findMethod(owner, s.name, s.desc);
                if (m == null && recv != null) {
                    m = MemberResolver.findMethod(recv.getClass(), s.name, s.desc);
                }
                if (m == null) return null;
                Object[] args = new Object[s.args.size()];
                for (int i = 0; i < args.length; i++) args[i] = eval(s.args.get(i), depth + 1);
                try {
                    return m.invoke(recv, coerce(args, m.getParameterTypes()));
                } catch (Throwable t) {
                    return null;
                }
            }
            case OP: {
                Object a = eval(s.a, depth + 1);
                Object b = s.b == null ? null : eval(s.b, depth + 1);
                return Arith.apply(s.op, a, b);
            }
            case CAST: {
                Object v = eval(s.a, depth + 1);
                return convert(v, s.desc);
            }
            default:
                return null;
        }
    }

    public static Object convert(Object v, String desc) {
        if (desc == null || v == null) return v;
        switch (desc.charAt(0)) {
            case 'F': return Arith.f(v);
            case 'D': return Arith.d(v);
            case 'I': return Arith.i(v);
            case 'J': return Arith.l(v);
            case 'S': return (short) Arith.i(v);
            case 'B': return (byte) Arith.i(v);
            case 'C': return (char) Arith.i(v);
            case 'Z': return Arith.i(v) != 0;
            default: return v;
        }
    }

    public static Object[] coerce(Object[] args, Class<?>[] types) {
        if (args == null) return new Object[0];
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            Class<?> t = i < types.length ? types[i] : Object.class;
            out[i] = coerceOne(a, t);
        }
        return out;
    }

    private static Object coerceOne(Object a, Class<?> t) {
        if (a == null) return null;
        if (t.isInstance(a)) return a;
        if (t == float.class) return Arith.f(a);
        if (t == double.class) return Arith.d(a);
        if (t == int.class) return Arith.i(a);
        if (t == long.class) return Arith.l(a);
        if (t == short.class) return (short) Arith.i(a);
        if (t == byte.class) return (byte) Arith.i(a);
        if (t == char.class) return (char) Arith.i(a);
        if (t == boolean.class) return Arith.i(a) != 0;
        return a;
    }
}
