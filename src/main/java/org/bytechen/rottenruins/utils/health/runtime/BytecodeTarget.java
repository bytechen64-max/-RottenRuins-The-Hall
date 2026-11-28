package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.ClassResolver;
import org.objectweb.asm.tree.ClassNode;

public final class BytecodeTarget {

    public record Choice(Class<?> original, Class<?> analyzed, boolean hidden, ClassNode node) {

        public boolean viaSuperclass() { return analyzed != original; }
    }

    private BytecodeTarget() {}

    public static Choice choose(Class<?> type, ClassResolver source) {
        if (type == null || source == null) return null;

        ClassNode self = source.find(internalNameOf(type));
        if (self != null) return new Choice(type, type, isHidden(type), self);

        for (Class<?> up = type.getSuperclass(); up != null && up != Object.class; up = up.getSuperclass()) {
            ClassNode upNode = source.find(internalNameOf(up));
            if (upNode != null) return new Choice(type, up, isHidden(type), upNode);
        }
        return null;
    }

    public static String internalNameOf(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    public static boolean isHidden(Class<?> type) {
        try {
            return type.isHidden();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String reason(Class<?> type) {
        return isHidden(type)
                ? "隐藏类（defineHiddenClass 定义，不注册进任何类加载器，没有 .class 资源）"
                : "读不到本类字节码";
    }
}
