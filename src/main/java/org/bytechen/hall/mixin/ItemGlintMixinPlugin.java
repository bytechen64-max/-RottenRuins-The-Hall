package org.bytechen.hall.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ItemGlintMixinPlugin implements IMixinConfigPlugin {
    private static final String EMBEDDIUM_CLASS = "org.embeddedt.embeddium.api.EmbeddiumConstants";
    private static final String OCULUS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("GameRendererMixin")) {
            return !classExists(OCULUS_API_CLASS);
        }
        if (mixinClassName.endsWith("GameRendererOculusMixin")) {
            return classExists(OCULUS_API_CLASS);
        }
        if (mixinClassName.endsWith("LevelRendererEmbeddiumMixin")) {
            return classExists(EMBEDDIUM_CLASS);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, ItemGlintMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
