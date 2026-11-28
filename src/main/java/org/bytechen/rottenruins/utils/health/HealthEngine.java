package org.bytechen.rottenruins.utils.health;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.utils.health.runtime.HealthController;
import org.slf4j.Logger;

/**
 * 改血引擎的装配入口（从 bccore 整体搬过来的那套字节码分析 / 写入 / 回读校验机制）。
 *
 * <p>引擎本体在 {@link org.bytechen.rottenruins.utils.health.runtime} 与
 * {@link org.bytechen.rottenruins.utils.health.analysis}，原样保留 bccore 的类结构与行为；
 * 这里只提供它需要的 {@link #LOGGER} / {@link #MODID}，并负责一次性的
 * {@link HealthController#bootstrap()}。</p>
 *
 * <p>平台接线（每 tick 的粘性重写）在
 * {@code org.bytechen.rottenruins.platform.forge.health.HealthTickHandler}，所以这个包不依赖 Forge。</p>
 */
public final class HealthEngine {

    public static final String MODID = HallMod.MODID;

    public static final Logger LOGGER = HallMod.LOGGER;

    private static boolean installed;

    private HealthEngine() {}

    /** 幂等初始化：解析 SRG/官方名映射、构建类字节码来源。 */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            HealthController.INSTANCE.bootstrap();
            LOGGER.info("[改血引擎] 就绪，当前强度 = {}", HealthController.INSTANCE.strength());
        } catch (Throwable failure) {
            LOGGER.error("[改血引擎] 初始化失败，改血会退化到原版 setHealth", failure);
        }
    }

    public static boolean isInstalled() {
        return installed;
    }
}
