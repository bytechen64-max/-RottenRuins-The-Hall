package org.bytechen.hall.overworld.difficulty;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.config.data.SplendidingConfig;
import org.bytechen.infcore.api.difficulty.DifficultyType;
import org.jetbrains.annotations.Nullable;

/**
 * 难度 → 系数档案。
 *
 * <h3>为什么要有这一层</h3>
 * <p>四档难度的 {@link ResourceLocation}（{@code hall:easy} 等）如果直接散到各个玩法类里，
 * 每加一项受难度影响的数值就会多出四处 {@code if (id.equals(...))}，
 * 而且迟早会出现「某个类忘了处理『无法理解』、静默回退到普通」这种查不出来的 bug。
 * <p>所以难度 ID 只在这里被翻译一次，玩法侧一律只问
 * {@link #current()} 拿一个 {@link DifficultyScaleProfile}，内部具体是哪一档与调用方无关。
 *
 * <h3>取值来源</h3>
 * <p>倍率本身不写死在代码里：{@link #fallback()} / {@link #fallbackFor(Tier)} 只是
 * 「配置读不到时的兜底」，正常运行一律走 {@link #fromConfig(Tier)} 从
 * {@link SplendidingConfig} 读取，因此这四个/八个数字都可以由玩家在配置文件里改。
 *
 * <h3>背包难度 ID 的兼容</h3>
 * <p>全局难度可以被子模组或玩家用 infcore 自带的
 * {@code /infcore difficulty set <id>} 改成 {@code infcore:hard} 之类的内置 ID。
 * 这类 ID 不在本模组的四档里，{@link #tierOf(ResourceLocation)} 会把它们归到最接近的一档，
 * 免得出现「用指令把难度设成困难、结果生物还是普通档血量」这种不一致。
 *
 * @param healthScale 最大生命值倍率
 * @param damageScale 攻击伤害倍率
 */
public record DifficultyScaleProfile(float healthScale, float damageScale) {

    /**
     * 四档难度的档位。
     */
    public enum Tier {
        EASY,
        NORMAL,
        HARD,
        INCOMPREHENSIBLE;

        /**
         * 写进实体持久化数据的稳定名字。
         * <p>单独给一个方法而不是直接用 {@link #name()}：小写名是存档数据的一部分，
         * 将来枚举常量改名不应该连带把老存档里的标记读废。
         */
        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /**
         * 从持久化标记反解档位。
         *
         * @param wireName 存档里的名字，未知或 {@code null} 时返回 {@code null}
         */
        @Nullable
        public static Tier fromWireName(@Nullable String wireName) {
            if (wireName == null || wireName.isEmpty()) return null;
            for (Tier tier : values()) {
                if (tier.wireName().equals(wireName)) return tier;
            }
            return null;
        }
    }

    /** 倍率的合法下界。0 会让生物一生成就死，负值更没意义，因此钳到 0.1。 */
    private static final float MIN_SCALE = 0.1F;

    /**
     * 倍率的合法上界。
     * <p>不是防玩家，是防手滑：这个值直接乘在 800 血的萨米使徒身上，
     * 填成 1e9 会让一次属性重算就把血量顶成 {@code Infinity}，
     * 之后任何伤害都打不死它，排查起来毫无线索。
     */
    private static final float MAX_SCALE = 1000.0F;

    /**
     * 当前生效的档案缓存。
     * <p>由 {@link HallDifficultyEventHandler} 在难度切换时刷新，避免每次实体进场都读一遍配置。
     */
    private static volatile DifficultyScaleProfile current = fallbackFor(Tier.NORMAL);

    // ==================== 查询 ====================

    /**
     * 当前难度对应的系数档案。永远不为 {@code null}。
     */
    public static DifficultyScaleProfile current() {
        return current;
    }

    /**
     * 按难度 ID 取档案（读配置）。
     * <p>服务器与客户端都能调用：客户端读的是本机配置文件的同一组值，
     * 难度 ID 本身由 infcore 同步过来。
     */
    public static DifficultyScaleProfile forDifficulty(ResourceLocation difficulty) {
        return fromConfig(tierOf(difficulty));
    }

    /**
     * 重新解析当前难度对应的档案。
     * <p>调用时机有两个：难度发生切换时、以及玩家重载配置文件时。
     *
     * @param difficulty 当前全局难度，{@code null} 时按普通档处理
     * @return 刷新后的档案（同时已写入 {@link #current()} 的缓存）
     */
    public static DifficultyScaleProfile refresh(ResourceLocation difficulty) {
        DifficultyScaleProfile profile = forDifficulty(difficulty);
        current = profile;
        return profile;
    }

    /**
     * 难度 ID → 档位。
     *
     * @param difficulty 全局难度 ID，允许 {@code null}
     */
    public static Tier tierOf(ResourceLocation difficulty) {
        if (difficulty == null) return Tier.NORMAL;

        // 本模组的四档
        if (HallDifficulty.EASY.equals(difficulty)) return Tier.EASY;
        if (HallDifficulty.NORMAL.equals(difficulty)) return Tier.NORMAL;
        if (HallDifficulty.HARD.equals(difficulty)) return Tier.HARD;
        if (HallDifficulty.INCOMPREHENSIBLE.equals(difficulty)) return Tier.INCOMPREHENSIBLE;

        // infcore 内置难度（hint 顺序见 DifficultyType.getDefaultOrder）
        if (DifficultyType.PEACEFUL.getId().equals(difficulty)) return Tier.EASY;
        if (DifficultyType.EASY.getId().equals(difficulty)) return Tier.EASY;
        if (DifficultyType.NORMAL.getId().equals(difficulty)) return Tier.NORMAL;
        if (DifficultyType.HARD.getId().equals(difficulty)) return Tier.HARD;

        // 未知 ID（子模组自定义、或被手改的存档）：按普通档处理。
        // 不抛异常也不静默归零 —— 一个查不出来的 0 倍率比"偏保守"危险得多。
        return Tier.NORMAL;
    }

    // ==================== 配置读取 ====================

    /**
     * 从配置文件读取指定档位的倍率。
     * <p>配置尚未加载（例如数据生成、或注册阶段过早调用）时回退到 {@link #fallbackFor(Tier)}。
     */
    public static DifficultyScaleProfile fromConfig(Tier tier) {
        SplendidingConfig cfg = config();
        if (cfg == null) return fallbackFor(tier);

        return switch (tier) {
            case EASY -> new DifficultyScaleProfile(
                    sanitize(cfg.difficultyEasyHealthScale),
                    sanitize(cfg.difficultyEasyDamageScale));
            case NORMAL -> new DifficultyScaleProfile(
                    sanitize(cfg.difficultyNormalHealthScale),
                    sanitize(cfg.difficultyNormalDamageScale));
            case HARD -> new DifficultyScaleProfile(
                    sanitize(cfg.difficultyHardHealthScale),
                    sanitize(cfg.difficultyHardDamageScale));
            case INCOMPREHENSIBLE -> new DifficultyScaleProfile(
                    sanitize(cfg.difficultyIncomprehensibleHealthScale),
                    sanitize(cfg.difficultyIncomprehensibleDamageScale));
        };
    }

    /** 代码内置兜底值：简单 1 / 普通 1.5 / 困难 4 / 无法理解 12。 */
    public static DifficultyScaleProfile fallbackFor(Tier tier) {
        return switch (tier) {
            case EASY -> new DifficultyScaleProfile(1.0F, 1.0F);
            case NORMAL -> new DifficultyScaleProfile(1.5F, 1.5F);
            case HARD -> new DifficultyScaleProfile(4.0F, 4.0F);
            case INCOMPREHENSIBLE -> new DifficultyScaleProfile(12.0F, 12.0F);
        };
    }

    /** 普通档档案，用作默认值。 */
    public static DifficultyScaleProfile fallback() {
        return fallbackFor(Tier.NORMAL);
    }

    // ==================== 内部 ====================

    /** 安全读取配置实例；任何一步拿不到都返回 {@code null} 而不是抛错。 */
    private static SplendidingConfig config() {
        try {
            if (ConfigHelper.configHolder == null) return null;
            return ConfigHelper.configHolder.get();
        } catch (Throwable t) {
            // 配置读取失败只该降级，不该把实体进场事件带崩
            return null;
        }
    }

    /**
     * 把配置里的倍率钳到安全区间。
     * <p>{@code NaN} 也一并挡掉 —— Jankson 反序列化出一个 NaN 时，
     * 乘出来的血量是 NaN，原版 {@code setHealth} 会直接抛异常。
     */
    private static float sanitize(float value) {
        if (Float.isNaN(value)) return 1.0F;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
