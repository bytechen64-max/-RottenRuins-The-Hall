package org.bytechen.rottenruins.core.infection.evolution;

import java.util.List;

/**
 * 一个源实体到其所有可能进化目标的映射。
 * <p>
 * 相比 SRP 的进化系统，增加了以下字段：
 * <ul>
 *   <li>{@code type} —— 感染类型的字符串标识，如 {@code "infection"}、{@code "evolution"}、{@code "seize"}</li>
 *   <li>{@code keepEquipment} —— 进化后是否保留源实体的装备</li>
 *   <li>{@code keepNbt} —— 进化后是否保留源实体的完整 NBT 数据</li>
 * </ul>
 *
 * @param type           感染/进化类型字符串
 * @param source         源实体的注册名，如 {@code "minecraft:cow"}
 * @param keepEquipment  是否保留装备
 * @param keepNbt        是否保留 NBT
 * @param results        可进化目标及权重的列表
 */
public final class EvolutionEntry {
    private String type;
    private String source;
    private boolean keepEquipment;
    private boolean keepNbt;
    private List<EvolutionTarget> results;

    public EvolutionEntry() {}

    public EvolutionEntry(String type, String source, boolean keepEquipment, boolean keepNbt, List<EvolutionTarget> results) {
        this.type = type;
        this.source = source;
        this.keepEquipment = keepEquipment;
        this.keepNbt = keepNbt;
        this.results = List.copyOf(results);
    }

    public String type() { return type; }
    public String source() { return source; }
    public boolean keepEquipment() { return keepEquipment; }
    public boolean keepNbt() { return keepNbt; }
    public List<EvolutionTarget> results() { return results; }
}
