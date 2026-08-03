package org.bytechen.hall.overworld.difficulty;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.core.difficulty.DifficultyRegistry;

/**
 * RottenRuinsSplendiding 四档难度定义。
 * <p>
 * 难度从低到高：
 * <ol>
 *   <li>{@link #EASY 简单}</li>
 *   <li>{@link #NORMAL 普通}</li>
 *   <li>{@link #HARD 困难}</li>
 *   <li>{@link #INCOMPREHENSIBLE 无法理解}</li>
 * </ol>
 * <p>
 * 注册后可通过以下方式使用：
 * <ul>
 *   <li>{@code /infcore difficulty list} — 查看所有已注册难度</li>
 *   <li>{@code /infcore difficulty set hall:easy} — 切换难度</li>
 *   <li>{@code DifficultyHelper.getDifficulty(level)} — 服务端查询</li>
 *   <li>{@code DifficultyHelper.getClientDifficulty()} — 客户端查询（自动同步）</li>
 * </ul>
 */
public final class HallDifficulty {

    /** 简单 */
    public static final ResourceLocation EASY =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "easy");

    /** 普通（默认） */
    public static final ResourceLocation NORMAL =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "normal");

    /** 困难 */
    public static final ResourceLocation HARD =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hard");

    /** 无法理解 */
    public static final ResourceLocation INCOMPREHENSIBLE =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "incomprehensible");

    private HallDifficulty() {}

    /**
     * 向 infcore 的 DifficultyRegistry 注册所有四档难度，
     * 使其出现在 {@code /infcore difficulty list} 和 tab 补全中。
     * <p>
     * 在 {@link HallMod#commonSetup} 中调用。
     */
    public static void registerAll() {
        DifficultyRegistry.register(EASY);
        DifficultyRegistry.register(NORMAL);
        DifficultyRegistry.register(HARD);
        DifficultyRegistry.register(INCOMPREHENSIBLE);
        HallMod.LOGGER.info("Hall difficulties registered: easy, normal, hard, incomprehensible");
    }
}
