package org.bytechen.rottenruins.core.infection.evolution;

/**
 * 单个进化目标实体及其权重。
 *
 * @param target 目标实体的注册名，如 {@code "infcore:inf_cow"}
 * @param weight 该目标的权重（值越大被选中的概率越高）
 */
public final class EvolutionTarget {
    private String target;
    private int weight;

    public EvolutionTarget() {}

    public EvolutionTarget(String target, int weight) {
        this.target = target;
        this.weight = weight;
    }

    public String target() { return target; }
    public int weight() { return weight; }
}
