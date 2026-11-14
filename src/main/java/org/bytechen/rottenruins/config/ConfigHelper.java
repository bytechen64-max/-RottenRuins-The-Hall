package org.bytechen.rottenruins.config;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.config.data.MeteorShowerConfig;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

public class ConfigHelper {

    public static ConfigHolder<SplendidingConfig> configHolder;
    public static ConfigHolder<SplendidingConfig> shaderHolder;

    public static ConfigHolder<MeteorShowerConfig> meteorConfigHolder;

    public static void registry() {
        configHolder = AutoConfig.register(SplendidingConfig.class, JanksonConfigSerializer::new);
        shaderHolder = configHolder;

        meteorConfigHolder = AutoConfig.register(MeteorShowerConfig.class, JanksonConfigSerializer::new);

        // 迁移必须在读取配置之前完成，否则本局仍然用旧值
        migrateSplendidingConfig();

        // 诊断写盘开关跟着配置走：发布态默认关，排查时把 debug 打开才落盘。
        applyDebugSwitches();

        if (configHolder.get() != null) {
            HallMod.LOGGER.info("Splendiding config loaded");
        }
        if (meteorConfigHolder.get() != null) {
            HallMod.LOGGER.info("MeteorShower config loaded");
        }
    }

    /**
     * 把 {@code SplendidingConfig.debug} 同步给那些"会往磁盘写诊断日志"的静态开关。
     *
     * <p>这些开关原先硬编码为 {@code true}，发布版一玩就在工作目录里长出一个日志文件。
     * 现在默认关闭，只有玩家/开发者在配置里打开 {@code debug} 才会写。</p>
     */
    private static void applyDebugSwitches() {
        SplendidingConfig cfg = configHolder == null ? null : configHolder.get();
        boolean on = cfg != null && cfg.debug;
        org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDebug.ENABLED = on;
    }

    /**
     * 把旧版本的 {@link SplendidingConfig} 迁移到当前版本。cloth-autoconfig 只在首次运行写入默认值，
     * 之后一律以文件为准 —— 改代码里的默认值对已有配置文件不生效，故只能靠版本号迁移。
     * 只重置本次真正改过的项（保留玩家的渲染/冲击波/描边微调等），且失败只是沿用旧值，不影响启动。
     */
    private static void migrateSplendidingConfig() {
        try {
            if (configHolder == null) return;
            SplendidingConfig cfg = configHolder.get();
            if (cfg == null) return;

            if (cfg.configVersion >= SplendidingConfig.CONFIG_VERSION) return;

            int from = cfg.configVersion;
            // 先无条件记一条：迁移到底跑没跑必须能从日志确认，静默返回排查成本极高。
            HallMod.LOGGER.info("[Config] SplendidingConfig 当前版本 {}（最新 {}），冷却值 {}",
                    from, SplendidingConfig.CONFIG_VERSION, cfg.verdictCooldownSeconds);

            if (from < 2) {
                cfg.verdictCooldownSeconds = 1.5f;
                HallMod.LOGGER.info("[Config] verdictCooldownSeconds 已重置为 1.5（版本 {} → 2）", from);
            }

            if (from < 3) {
                cfg.outlineWidthMaxRatio = SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO;
                HallMod.LOGGER.info("[Config] outlineWidthMaxRatio 已重置为 {}（版本 {} → 3）",
                        SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO, from);
            }

            if (from < 4) {
                cfg.flameRise = 0.16f;
                cfg.flameBuoyancy = 0.30f;
                cfg.flameCooling = 4.0f;
                HallMod.LOGGER.info("[Config] 火焰高度已重调：rise=0.16 buoyancy=0.30 cooling=4.0（版本 {} → 4）", from);
            }

            if (from < 5) {
                cfg.flameRise = 0.20f;
                cfg.flameBuoyancy = 0.35f;
                cfg.flameCooling = 3.4f;
                HallMod.LOGGER.info("[Config] 火焰高度再配平：rise=0.20 buoyancy=0.35 cooling=3.4（版本 {} → 5）", from);
            }

            if (from < 6) {
                cfg.arcScale = 16.0f;
                cfg.arcThreshold = 0.10f;
                cfg.arcGain = 2.2f;
                cfg.arcReach = 0.035f;
                HallMod.LOGGER.info("[Config] 电弧密度已拉满：scale=16 threshold=0.10 gain=2.2 reach=0.12（版本 {} → 6）", from);
            }

            // v8：arcReach>0 才是着色器里「锚定剪影」的开关，为 0 等于不限、电弧会铺满剪裁框；
            //      锚点必须是剪影而非包围盒（剑在盒子里是斜的，盒角离剑很远）。
            // v11：edgeDist 在单元尺度上，屏幕线宽必须乘 CrackScale（着色器侧已改）。
            if (from < 12) {
                cfg.crackReach = 1.0f;
                HallMod.LOGGER.info("[Config] 裂纹可覆盖整个物品：crackReach=1.0（版本 {} → 12）", from);
            }

            if (from < 11) {
                cfg.crackLinePx = 3.5f;
                cfg.crackGlow = 0.95f;
                HallMod.LOGGER.info("[Config] 裂纹加粗：line=3.5px glow=0.95（版本 {} → 11）", from);
            }

            if (from < 10) {
                cfg.arcEnabled = true;
                HallMod.LOGGER.info("[Config] 电弧已启用：arcEnabled=true（版本 {} → 10）", from);
            }

            if (from < 9) {
                cfg.arcScale = 26f;
                cfg.arcGain = 2f;
                cfg.arcReach = 0.10f;
                cfg.arcLinePx = 2.5f;
                cfg.arcBandPx = 0f;
                HallMod.LOGGER.info("[Config] 电弧收窄：scale=26 gain=2 reach=0.10 line=2.5px（版本 {} → 9）", from);
            }

            if (from < 8) {
                cfg.arcReach = 0.035f;
                cfg.arcGain = 5.0f;
                cfg.arcSub = 0.12f;
                cfg.arcBandPx = 0f;
                HallMod.LOGGER.info("[Config] 电弧回到剪影锚定：reach=0.12 gain=2.6 sub=0.12（版本 {} → 8）", from);
            }

            if (from < 7) {
                cfg.arcScale = 30.0f;
                cfg.arcThreshold = 0.15f;
                cfg.arcGain = 1.8f;
                cfg.arcReach = 0.0f;
                HallMod.LOGGER.info("[Config] 电弧带：scale=30 threshold=0.15 gain=1.8 bandPx={}（版本 {} → 7）",
                        cfg.arcBandPx, from);
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