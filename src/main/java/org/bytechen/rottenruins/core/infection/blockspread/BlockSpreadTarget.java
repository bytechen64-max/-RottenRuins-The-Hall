package org.bytechen.rottenruins.core.infection.blockspread;

/**
 * 单个方块扩散目标及其权重。
 *
 * @param target 目标方块的注册名，如 {@code "minecraft:dirt"}
 * @param weight 该目标的权重（值越大被选中的概率越高）
 */
public final class BlockSpreadTarget {
    private String target;
    private int weight;

    public BlockSpreadTarget() {}

    public BlockSpreadTarget(String target, int weight) {
        this.target = target;
        this.weight = weight;
    }

    public String target() { return target; }
    public int weight() { return weight; }
}
