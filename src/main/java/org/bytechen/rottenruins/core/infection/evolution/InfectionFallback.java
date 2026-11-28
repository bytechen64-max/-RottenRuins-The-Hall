package org.bytechen.rottenruins.core.infection.evolution;


import java.util.function.DoublePredicate;

/**
 * 感染兜底形态（一个档位）。
 * <p>
 * 当某种感染（{@code type}）作用于一个<b>没有对应感染形态</b>的生物
 * —— 即进化表里查不到该生物类型的规则 ——
 * 且它的碰撞体积（{@code 宽 × 高}）满足 {@link #volumeCondition()} 时，
 * {@link EvolutionManager} 会把它替换成 {@code count} 个 {@code target}。
 * <p>
 * 同一种感染可以注册多个档位，按注册顺序依次判定，<b>第一个满足条件的档位生效</b>；
 * 所有档位都不满足时生物保持原样。因此可以按碰撞体积分档：
 * <pre>{@code
 * // 体积 < 2   → 1 只小怪
 * // 体积 2 ~ 8 → 2 只小怪
 * // 体积 > 8   → 1 只大怪
 * EvolutionManager.registerFallback(type, SMALL_MOB, 1, v -> v < 2.0D);
 * EvolutionManager.registerFallback(type, SMALL_MOB, 2, v -> v >= 2.0D && v <= 8.0D);
 * EvolutionManager.registerFallback(type, BIG_MOB,   1, v -> v > 8.0D);
 * }</pre>
 * <p>
 * 兜底形态没有 JSON 数据文件，由下游模组在初始化时注册，
 * 也不会被 {@link EvolutionManager#forceReload()} 清除。
 *
 * @param target          兜底转化的目标实体类型
 * @param count           一次转化生成的个数（小于 1 时按 1 处理）
 * @param volumeCondition 碰撞体积（{@code 宽 × 高}）需要满足的条件，{@code null} 表示不限制
 */
public record InfectionFallback<T>(T target, int count, DoublePredicate volumeCondition) {

    public InfectionFallback {
        if (count < 1) count = 1;
    }

    /**
     * 指定碰撞体积是否落在该档位的条件内。
     *
     * @param volume 碰撞体积（{@code 宽 × 高}）
     * @return 是否匹配
     */
    public boolean matches(double volume) {
        return this.volumeCondition == null || this.volumeCondition.test(volume);
    }
}
