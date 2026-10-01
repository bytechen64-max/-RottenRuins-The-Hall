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

        // 迁移旧版本的配置（必须在读取配置之前完成，否则本局仍然用旧值）
        migrateSplendidingConfig();

        // 打印日志
        if (configHolder.get() != null) {
            HallMod.LOGGER.info("Splendiding config loaded");
        }
        if (meteorConfigHolder.get() != null) {
            HallMod.LOGGER.info("MeteorShower config loaded");
        }
    }

    /**
     * 把旧版本的 {@link SplendidingConfig} 迁移到当前版本。
     *
     * <h3>为什么必须有这一步</h3>
     * <p>cloth-autoconfig 只在<b>首次运行</b>时写入默认值，之后一律以文件为准。
     * 也就是说"改了代码里的默认值"对已经有过配置文件的机器<b>不生效</b> ——
     * 天穹裁决的冷却就踩了这个坑：代码里改成 1.5 秒，而玩家文件里仍是 3.0，
     * 连续两轮调参都"看起来没反应"。</p>
     *
     * <p>迁移策略是<b>只重置这次真正改过的那几项</b>，而不是整个文件回默认 ——
     * 玩家的渲染设置、冲击波强度、描边微调等等都应当保留。</p>
     *
     * <p>整个过程包在 try/catch 里：迁移失败只是"沿用旧值"，
     * 绝不该让配置环节把游戏启动带崩。</p>
     */
    private static void migrateSplendidingConfig() {
        try {
            if (configHolder == null) return;
            SplendidingConfig cfg = configHolder.get();
            if (cfg == null) return;

            if (cfg.configVersion >= SplendidingConfig.CONFIG_VERSION) return;

            int from = cfg.configVersion;
            // 先无条件写一条：迁移到底有没有跑，必须能从日志确认。
            // 上一版就是"迁移静默返回"，导致查了半天才发现是版本号判断把自己挡住了。
            HallMod.LOGGER.info("[Config] SplendidingConfig 当前版本 {}（最新 {}），冷却值 {}",
                    from, SplendidingConfig.CONFIG_VERSION, cfg.verdictCooldownSeconds);

            // ── 版本 <2：把基础冷却重置为 1.5 秒 ──
            //  为什么是"<2"而不是"<1"：版本 1 的那次迁移写了个 bug ——
            //  它把 configVersion 递增到了 1，却没有真正重置冷却值，
            //  于是文件卡在"版本号是新的、值还是旧的 3.0"这个矛盾状态。
            //  而迁移判断依据是版本号，所以那条错误的值永远修不回来了。
            //  版本 2 就是用来把这次修正再跑一遍的。
            //
            //  这里不再做"值等于旧默认才重置"的判断：那个判断在版本 1 已经走过一遍，
            //  无法区分"玩家没改过的 3.0"和"玩家手动设成 3.0"。考虑到这一项是
            //  开发期刚引入的，且 3.0 正是我们想淘汰的值，直接重置更符合预期。
            if (from < 2) {
                cfg.verdictCooldownSeconds = 1.5f;
                HallMod.LOGGER.info("[Config] verdictCooldownSeconds 已重置为 1.5（版本 {} → 2）", from);
            }

            cfg.configVersion = SplendidingConfig.CONFIG_VERSION;
            configHolder.save();
            HallMod.LOGGER.info("[Config] SplendidingConfig 迁移完成并已存盘（版本 {}，冷却 {}）",
                    cfg.configVersion, cfg.verdictCooldownSeconds);
        } catch (Throwable t) {
            // 迁移是尽力而为：失败就沿用旧值，不影响启动
            HallMod.LOGGER.warn("[Config] SplendidingConfig 迁移失败，将沿用现有值：{}", t.toString());
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