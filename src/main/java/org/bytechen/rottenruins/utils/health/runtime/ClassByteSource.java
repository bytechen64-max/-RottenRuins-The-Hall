package org.bytechen.rottenruins.utils.health.runtime;

import org.bytechen.rottenruins.utils.health.analysis.ClassResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassByteSource implements ClassResolver {

    private final ClassLoader loader;
    private final Map<String, ClassNode> cache = new ConcurrentHashMap<>();

    public ClassByteSource(ClassLoader loader) {
        this.loader = loader != null ? loader : ClassByteSource.class.getClassLoader();
    }

    public ClassLoader loader() {
        return loader;
    }

    @Override
    public ClassNode find(String internalName) {
        if (internalName == null) return null;
        ClassNode cached = cache.get(internalName);
        if (cached != null) return cached;

        ClassNode node = parse(loader.getResourceAsStream(internalName + ".class"));
        if (node == null) {
            Class<?> c = MemberResolver.load(internalName, loader);
            if (c != null) node = parse(c.getResourceAsStream("/" + internalName + ".class"));
        }
        if (node != null) cache.put(internalName, node);
        return node;
    }

    private static ClassNode parse(InputStream in) {
        if (in == null) return null;
        try (InputStream stream = in) {
            ClassNode cn = new ClassNode();
            new ClassReader(stream).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return cn;
        } catch (Throwable t) {
            return null;
        }
    }
}
