package org.bytechen.rottenruins.utils.health.runtime;

import com.mojang.logging.LogUtils;
import org.bytechen.rottenruins.utils.health.analysis.Names;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class NameRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NameRegistry() {}

    public static Names resolve() {
        Names names = new Names();
        Map<String, Member> probe = mine();
        if (probe.isEmpty()) {
            LOGGER.warn("[改血引擎] 成员名探针解析失败，回退到官方映射默认名字");
            return names;
        }

        Names fromProbe = new Names();
        Member getHealth = probe.get("p$getHealth");
        if (getHealth != null) {
            fromProbe.livingEntityOwner = getHealth.owner;
            fromProbe.getHealthName = getHealth.name;
            fromProbe.getHealthDesc = getHealth.desc;
        }
        Member setHealth = probe.get("p$setHealth");
        if (setHealth != null) {
            fromProbe.setHealthName = setHealth.name;
            fromProbe.setHealthDesc = setHealth.desc;
        }
        Member getMaxHealth = probe.get("p$getMaxHealth");
        if (getMaxHealth != null) {
            fromProbe.getMaxHealthName = getMaxHealth.name;
            fromProbe.getMaxHealthDesc = getMaxHealth.desc;
        }
        Member dataGet = probe.get("p$entityDataGet");
        if (dataGet != null) {
            fromProbe.entityDataOwner = dataGet.owner;
            fromProbe.entityDataGetName = dataGet.name;
            fromProbe.entityDataGetDesc = dataGet.desc;
        }
        Member dataSet = probe.get("p$entityDataSet");
        if (dataSet != null) {
            fromProbe.entityDataSetName = dataSet.name;
            fromProbe.entityDataSetDesc = dataSet.desc;
        }

        if (verifies(fromProbe)) {
            log(fromProbe);
            return fromProbe;
        }

        boolean defaultsOk = verifies(names);
        if (defaultsOk) {
            LOGGER.warn("[改血引擎] ⚠ 命名空间不匹配：jar 里写的是 {}{}，但当前运行时的 {} 上没有这个方法。",
                    fromProbe.getHealthName, fromProbe.getHealthDesc, fromProbe.livingEntityOwner);
            LOGGER.warn("[改血引擎]   已自动改用官方名 {}{} —— 这说明这个 jar 的命名空间和环境对不上"
                            + "（多半是把 reobf 过的生产 jar 放进了开发环境）。",
                    names.getHealthName, names.getHealthDesc);
            LOGGER.warn("[改血引擎]   注意：本模组里那些【直接调用】MC 方法的代码没法这样救回来，"
                            + "开发环境请用 installToHall 装出来的 dev jar。");
            log(names);
            return names;
        }

        LOGGER.error("[改血引擎] ✗ 命名空间自检失败：jar 里的名字和默认官方名在运行时都查不到，"
                + "分析多半会全程失效。请确认 Minecraft/Forge 版本是否匹配，或改用 dev jar。");
        log(fromProbe);
        return fromProbe;
    }

    private static boolean verifies(Names n) {
        try {
            return hasMethod(n.livingEntityOwner, n.getHealthName, n.getHealthDesc)
                    && hasMethod(n.livingEntityOwner, n.setHealthName, n.setHealthDesc)
                    && hasMethod(n.livingEntityOwner, n.getMaxHealthName, n.getMaxHealthDesc)
                    && hasMethod(n.entityDataOwner, n.entityDataGetName, n.entityDataGetDesc)
                    && hasMethod(n.entityDataOwner, n.entityDataSetName, n.entityDataSetDesc);
        } catch (Throwable t) {
            LOGGER.warn("[改血引擎] 命名空间自检时出异常（按不通过处理）", t);
            return false;
        }
    }

    private static boolean hasMethod(String internalOwner, String name, String desc) {
        Class<?> c = MemberResolver.load(internalOwner, NameRegistry.class.getClassLoader());
        if (c == null) return false;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void log(Names names) {
        LOGGER.info("[改血引擎] 运行时成员名解析完成: getHealth={}{} setHealth={}{} getMaxHealth={}{} | "
                        + "EntityData {}.{}{} / {}{}",
                names.getHealthName, names.getHealthDesc,
                names.setHealthName, names.setHealthDesc,
                names.getMaxHealthName, names.getMaxHealthDesc,
                names.entityDataOwner, names.entityDataGetName, names.entityDataGetDesc,
                names.entityDataSetName, names.entityDataSetDesc);
    }

    private static Map<String, Member> mine() {
        Map<String, Member> out = new HashMap<>();
        try (InputStream in = MappingProbe.class.getResourceAsStream("MappingProbe.class")) {
            if (in == null) return out;
            ClassNode cn = new ClassNode();
            new ClassReader(in).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode m : cn.methods) {
                if (m.name == null || !m.name.startsWith("p$")) continue;
                for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof MethodInsnNode mi) {
                        out.put(m.name, new Member(mi.owner, mi.name, mi.desc));
                        break;
                    }
                    if (insn instanceof FieldInsnNode fi) {
                        out.put(m.name, new Member(fi.owner, fi.name, fi.desc));
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[改血引擎] 无法反汇编探针类", t);
        }
        return out;
    }

    private record Member(String owner, String name, String desc) {}
}
