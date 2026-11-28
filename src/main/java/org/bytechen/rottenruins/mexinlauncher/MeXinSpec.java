package org.bytechen.rottenruins.mexinlauncher;

import org.jetbrains.annotations.NotNull;

/**
 * 一条"钩子 → 目标方法"的转换请求（由 {@link MeXinLuncher} 扫描注解得到）。
 *
 * @param targetClass   目标类内部名（{@code net/minecraft/...}）
 * @param devName       目标方法名（开发环境可读名）
 * @param srgName       目标方法在生产环境的 SRG 名（空表示不按它匹配）
 * @param targetDesc    目标方法描述符（空表示按名字匹配所有重载）
 * @param type          注入时机
 * @param includeExtend 是否也作用于子类里的覆盖实现
 * @param packageTarget 总包目标：该包<b>及其所有子包</b>下的类都算目标（内部名写法，如
 *                      {@code org/bytechen/rottenruins}）；空表示不用
 * @param hostClass     钩子所在类内部名
 * @param hookName      钩子方法名
 * @param hookDesc      钩子方法的实际描述符（注入前会与推导出的期望值比对）
 */
public record MeXinSpec(@NotNull String targetClass,
                        @NotNull String devName,
                        @NotNull String srgName,
                        @NotNull String targetDesc,
                        @NotNull MeXinType type,
                        boolean includeExtend,
                        @NotNull String packageTarget,
                        @NotNull String hostClass,
                        @NotNull String hookName,
                        @NotNull String hookDesc) {

    /** 是否按"名字 + 可选描述符"匹配到该方法。 */
    public boolean matchesMethodName(@NotNull String name) {
        return name.equals(devName) || (!srgName.isEmpty() && name.equals(srgName));
    }

    /** 是否有"总包"目标（{@code includmy = true}）。 */
    public boolean hasPackageTarget() {
        return !packageTarget.isEmpty();
    }

    /** 某个类名是否落在总包目标里（含所有子包）。 */
    public boolean inPackage(@NotNull String className) {
        return hasPackageTarget() && className.startsWith(packageTarget + "/");
    }

    @Override
    public String toString() {
        String where = hasPackageTarget() ? packageTarget + "/*" : targetClass;
        return hostClass + "#" + hookName + " -> " + where + "#"
                + (srgName.isEmpty() ? devName : srgName) + " [" + type + "]";
    }
}
