package org.bytechen.rottenruins.core.infection.blockspread;

import java.util.List;

/**
 * 一个源方块到其所有可能扩散目标的映射。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code type} —— 扩散类型的字符串标识，如 {@code "hall:spread"}</li>
 *   <li>{@code source} —— 源方块的注册名，如 {@code "minecraft:grass_block"}</li>
 *   <li>{@code dropResources} —— 扩散时是否掉落源方块的资源</li>
 *   <li>{@code results} —— 可扩散目标及权重的列表</li>
 * </ul>
 *
 * @param type          扩散类型字符串
 * @param source        源方块注册名
 * @param dropResources 是否掉落资源
 * @param results       扩散目标及权重列表
 */
public final class BlockSpreadEntry {
    private String type;
    private String source;
    private boolean dropResources;
    private List<BlockSpreadTarget> results;

    public BlockSpreadEntry() {}

    public BlockSpreadEntry(String type, String source, boolean dropResources, List<BlockSpreadTarget> results) {
        this.type = type;
        this.source = source;
        this.dropResources = dropResources;
        this.results = List.copyOf(results);
    }

    public String type() { return type; }
    public String source() { return source; }
    public boolean dropResources() { return dropResources; }
    public List<BlockSpreadTarget> results() { return results; }
}
