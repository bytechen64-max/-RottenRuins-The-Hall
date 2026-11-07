package org.bytechen.rottenruins.config;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.config.data.MeteorShowerConfig;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
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

            // ── 版本 <3：把描边距离自适应的比例重置为 0.04 ──
            //  又一个"改了代码默认值却没反应"的实例，和上面那次同源：
            //  0.15 是我第一版实现写进配置文件的默认值，而 cloth-config 以文件为准 ——
            //  之后把代码默认值改成 0.10 对已有文件的机器完全不生效，表现是
            //  "描边距离自适应改了两轮，看上去一直是恒定 4.5px"。
            //  0.15 这个值本身也定得太宽松（分界点在表观尺寸 30 像素，实测 20 格外的
            //  物品正好卡在分界点附近，等于没生效），所以直接按新默认值重置。
            if (from < 3) {
                cfg.outlineWidthMaxRatio = SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO;
                HallMod.LOGGER.info("[Config] outlineWidthMaxRatio 已重置为 {}（版本 {} → 3）",
                        SplendidingConfig.DEFAULT_OUTLINE_WIDTH_MAX_RATIO, from);
            }

            // ── 版本 <4：火焰高度三件套重调 ──
            //  同一类问题：这三个值在第一次跑火焰时就写进配置文件了，之后改代码默认值
            //  对已有文件的机器完全不生效。这次是把"向上扩散距离"整体压小
            //  （火羽不再一屏高、也不再超出物品栏那种离谱比例）。
            if (from < 4) {
                cfg.flameRise = 0.16f;
                cfg.flameBuoyancy = 0.30f;
                cfg.flameCooling = 4.0f;
                HallMod.LOGGER.info("[Config] 火焰高度已重调：rise=0.16 buoyancy=0.30 cooling=4.0（版本 {} → 4）", from);
            }

            // ── 版本 <5：火焰高度再配平 ──
            //  版本 4 那组把火压过头了：小物品（掉落物 / 三手 / 远处）上只剩贴着轮廓的一层雾。
            //  这次把三个值往回抬，同时下调可见性包络门槛、放宽尺寸比例下限。
            //  （又是同一类：值已经在文件里了，不迁移就不生效。）
            if (from < 5) {
                cfg.flameRise = 0.20f;
                cfg.flameBuoyancy = 0.35f;
                cfg.flameCooling = 3.4f;
                HallMod.LOGGER.info("[Config] 火焰高度再配平：rise=0.20 buoyancy=0.35 cooling=3.4（版本 {} → 5）", from);
            }

            // ── 版本 <6：电弧密度拉满 ──
            //  用户要求"尽量每一个地方都有电弧"：早期默认值太稀（scale 9 / threshold 0.22），
            //  细丝只在噪声恰好穿过阈值的地方零星出现。这四个键同样是"已经写进文件"的。
            if (from < 6) {
                cfg.arcScale = 16.0f;
                cfg.arcThreshold = 0.10f;
                cfg.arcGain = 2.2f;
                cfg.arcReach = 0.035f;
                HallMod.LOGGER.info("[Config] 电弧密度已拉满：scale=16 threshold=0.10 gain=2.2 reach=0.12（版本 {} → 6）", from);
            }

            // ── 版本 <7：电弧从"贴着轮廓的一条缝"改成"完整电弧带" ──
            //  根因：环形合成的剪裁框只外扩 AURA_REACH_PX(52px)，而且 reach 挂在光雾
            //  模糊图上（同一个 52px 支撑域）—— 电弧再密也只能挤在那条缝里。
            //  现在改成按物品包围盒算的软椭圆带 + 剪裁框按 arcBandPx 放大。
            //  scale 也一并提高：特征尺度 = 屏高/scale，16 时约 100px（比带宽还大，等于看不到细丝）。
            // ── 版本 <8：修正电弧带的错配 ──
            //  v7 把 arcReach 设成 0（当时改成"包围盒椭圆带"），但着色器里 `ArcReach > 0`
            //  才是「锚定剪影」的开关 —— 0 等于完全不限，电弧会铺满整个剪裁框。
            //  教训：锚点必须是**剪影**，不能是包围盒（剑在盒子里是斜的，盒子角落离剑很远）。
            // ── 版本 <9：电弧从"铺满包围盒"收回到"剪影周围的窄带"（那才叫描边）──
            // ── 版本 <10：电弧正式启用 ──
            //  又是"文件优先"那口坑：arcEnabled 曾以 false 的默认值被写进文件，改代码
            //  默认值不生效。症状是电弧怎么都不显示，而日志里 已注册效果=[flame] 少了 arc。
            // ── 版本 <11：裂纹线宽/辉光提高（"太细看不清"）──
            //  顺带修了一个量纲 bug：edgeDist 在单元尺度上，屏幕线宽必须乘 CrackScale ——
            //  着色器侧已改，这里把文件里的旧值一并提到新的默认。
            // ── 版本 <12：裂纹可以铺满整个物品（不再只贴着边缘）──
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