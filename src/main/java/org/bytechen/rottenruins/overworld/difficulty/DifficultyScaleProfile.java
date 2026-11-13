package org.bytechen.rottenruins.overworld.difficulty;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import org.bytechen.infcore.api.difficulty.DifficultyType;
import org.jetbrains.annotations.Nullable;

/**
 * 难度 → 系数档案。
 * <p>难度 ID 只在这里翻译一次，玩法侧一律问 {@link #current()}，避免每加一项受难度影响的数值就散出
 * 四处 {@code if (id.equals(...))}、以及「忘了处理『无法理解』而静默回退」的 bug。
 * <p>取值来源：倍率不写死在代码里，正常运行走 {@link #fromConfig(Tier)} 从 {@link SplendidingConfig} 读，
 * 玩家可改；{@link #fallbackFor(Tier)} 只是配置读不到时的兜底。
 * <p>infcore 内置难度 ID（{@code infcore:hard} 之类）与未知 ID 由 {@link #tierOf} 归到最接近的一档。
 */
public record DifficultyScaleProfile(float healthScale, float damageScale) {

    /** 四档难度：简单 / 普通 / 困难 / 无法理解。 */
    public enum Tier {
        EASY,
        NORMAL,
        HARD,
        INCOMPREHENSIBLE;

        /** 写进实体持久化数据的稳定名字（小写名是存档数据的一部分，不随枚举常量改名而变）。 */
        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @Nullable
        public static Tier fromWireName(@Nullable String wireName) {
            if (wireName == null || wireName.isEmpty()) return null;
            for (Tier tier : values()) {
                if (tier.wireName().equals(wireName)) return tier;
            }
            return null;
        }
    }

    /** 倍率下限：0 会让生物一生成就死，负值无意义，故钳到 0.1。 */
    private static final float MIN_SCALE = 0.1F;

    /** 倍率上限：防手滑（填成 1e9 会让血量变 Infinity、之后打不死，且毫无排查线索）。 */
    private static final float MAX_SCALE = 1000.0F;

    private static volatile DifficultyScaleProfile current = fallbackFor(Tier.NORMAL);

    public static DifficultyScaleProfile current() {
        return current;
    }

    public static DifficultyScaleProfile forDifficulty(ResourceLocation difficulty) {
        return fromConfig(tierOf(difficulty));
    }

    public static DifficultyScaleProfile refresh(ResourceLocation difficulty) {
        DifficultyScaleProfile profile = forDifficulty(difficulty);
        current = profile;
        return profile;
    }

    /** 难度 ID → 档位（{@code null} 与未知 ID 都按普通档）。 */
    public static Tier tierOf(ResourceLocation difficulty) {
        if (difficulty == null) return Tier.NORMAL;

        if (HallDifficulty.EASY.equals(difficulty)) return Tier.EASY;
        if (HallDifficulty.NORMAL.equals(difficulty)) return Tier.NORMAL;
        if (HallDifficulty.HARD.equals(difficulty)) return Tier.HARD;
        if (HallDifficulty.INCOMPREHENSIBLE.equals(difficulty)) return Tier.INCOMPREHENSIBLE;

        // infcore 内置难度（顺序见 DifficultyType.getDefaultOrder）
        if (DifficultyType.PEACEFUL.getId().equals(difficulty)) return Tier.EASY;
        if (DifficultyType.EASY.getId().equals(difficulty)) return Tier.EASY;
        if (DifficultyType.NORMAL.getId().equals(difficulty)) return Tier.NORMAL;
        if (DifficultyType.HARD.getId().equals(difficulty)) return Tier.HARD;

        // 未知 ID（子模组自定义 / 被手改的存档）也按普通档：不抛异常也不归零
        return Tier.NORMAL;
    }

    /** 从配置文件读取指定档位的倍率；配置尚未加载时回退到 {@link #fallbackFor(Tier)}。 */
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

    public static DifficultyScaleProfile fallback() {
        return fallbackFor(Tier.NORMAL);
    }

    /** 安全读取配置实例：任何一步拿不到都返回 {@code null}，让实体进场事件照常降级而不崩。 */
    private static SplendidingConfig config() {
        try {
            if (ConfigHelper.configHolder == null) return null;
            return ConfigHelper.configHolder.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把配置里的倍率钳到安全区间；{@code NaN} 也挡掉（血量乘出 NaN 会让 {@code setHealth} 抛异常）。 */
    private static float sanitize(float value) {
        if (Float.isNaN(value)) return 1.0F;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
