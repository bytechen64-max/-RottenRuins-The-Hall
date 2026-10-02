package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.infcore.core.evolution.EvolutionManager;

/**
 * 溃烂（{@code hall:inf}）的兜底转化规则。
 * <p>
 * 被感染、但在进化表里<b>没有专属感染形态</b>的生物，按碰撞体积（宽 × 高）分三档转化：
 * <table border="1">
 *   <tr><th>碰撞体积（宽 × 高）</th><th>转化结果</th></tr>
 *   <tr><td>&lt; 2</td><td>1 只 {@link ScoutEntity 溃烂纠察}</td></tr>
 *   <tr><td>2 ~ 8</td><td>1 只 {@link PursuerEntity 溃烂追蹤者}</td></tr>
 *   <tr><td>&gt; 8</td><td>1 只 {@link MonolithEntity 溃烂巨岩}</td></tr>
 * </table>
 * <p>
 * 有专属形态的生物（玩家 → 畸骸玩家、骷髅 → 畸骸骷髅……）不受此规则影响，
 * 判定与转化都由 infcore 的感染流程执行。
 * <p>
 * 另外，<b>生命上限低于 {@code EvolutionManager.FALLBACK_MIN_MAX_HEALTH}（5）的弱小生物不转化</b>
 * —— 小鸡、兔子、鱼这类被击杀后直接正常死亡，不会变成溃烂生物。
 * <p>
 * 注释与实现：档位在 {@link #register()} 里注册，判定顺序即注册顺序。
 */
public final class UlceratedConversionRules {

    /** 小型的体积上限（不含）：低于该值转化为 1 只斥候 */
    public static final double SMALL_VOLUME = 2.0D;
    /** 中型的体积上限（含）：该区间转化为 1 只追蹤者；超过该值转化为巨碑 */
    public static final double MEDIUM_VOLUME = 8.0D;

    private UlceratedConversionRules() {}

    /**
     * 把三个档位注册到 infcore 的感染流程（在 {@code FMLCommonSetupEvent} 中调用）。
     * <p>
     * 档位按注册顺序判定，第一个满足体积条件的生效。
     */
    public static void register() {
        ResourceLocation infectionType = HallMod.INFECTION_TYPE;
        EntityType<?> scout = EntityTypeRegistry.SCOUT.get();
        EntityType<?> pursuer = EntityTypeRegistry.PURSUER.get();
        EntityType<?> monolith = EntityTypeRegistry.MONOLITH.get();

        // 宽 × 高 < 2 → 1 只斥候
        EvolutionManager.registerFallback(infectionType, scout, 1,
                volume -> volume < SMALL_VOLUME);
        // 宽 × 高 2 ~ 8 → 1 只追蹤者
        EvolutionManager.registerFallback(infectionType, pursuer, 1,
                volume -> volume >= SMALL_VOLUME && volume <= MEDIUM_VOLUME);
        // 宽 × 高 > 8 → 1 只巨碑
        EvolutionManager.registerFallback(infectionType, monolith, 1,
                volume -> volume > MEDIUM_VOLUME);

        HallMod.LOGGER.info("Ulcerated conversion rules registered (scout < {}, pursuer <= {}, monolith > {})",
                SMALL_VOLUME, MEDIUM_VOLUME, MEDIUM_VOLUME);
    }
}
