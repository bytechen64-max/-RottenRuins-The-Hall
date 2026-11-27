package org.bytechen.rottenruins.utils.health.analysis;

import org.objectweb.asm.tree.ClassNode;

public interface ClassResolver {

    ClassNode find(String internalName);

    default ClassNode findSuper(ClassNode cn) {
        if (cn == null || cn.superName == null) return null;
        return find(cn.superName);
    }
}
