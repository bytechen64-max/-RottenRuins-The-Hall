package org.bytechen.hall.config;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.config.data.MeteorShowerConfig;
import org.bytechen.hall.config.data.SplendidingConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

public class ConfigHelper {

    // 原有配置（保持不变）
    public static ConfigHolder<SplendidingConfig> configHolder;          // 建议改名，但保持兼容也可
    public static ConfigHolder<SplendidingConfig> shaderHolder;          // 如果原来用 ShaderHolder，可保留

    // 新增：陨石配置
    public static ConfigHolder<MeteorShowerConfig> meteorConfigHolder;

    public static void registry() {
        // 注册原有配置
        configHolder = AutoConfig.register(SplendidingConfig.class, JanksonConfigSerializer::new);
        shaderHolder = configHolder;   // 如果 ShaderHolder 是别名，照旧

        // 注册陨石配置
        meteorConfigHolder = AutoConfig.register(MeteorShowerConfig.class, JanksonConfigSerializer::new);

        // 打印日志
        if (configHolder.get() != null) {
            HallMod.LOGGER.info("Splendiding config loaded");
        }
        if (meteorConfigHolder.get() != null) {
            HallMod.LOGGER.info("MeteorShower config loaded");
        }
    }

    public static void loadAll() {
        if (configHolder != null) configHolder.load();
        if (meteorConfigHolder != null) meteorConfigHolder.load();
    }

    public static void saveAll() {
        if (configHolder != null) configHolder.save();
        if (meteorConfigHolder != null) meteorConfigHolder.save();
    }


}