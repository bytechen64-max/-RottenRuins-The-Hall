package org.bytechen.rottenruins.core.infection.evolution;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个进化 JSON 文件的顶层数据结构。
 *
 * <pre>
 * {
 *   "replace": false,
 *   "entries": [
 *     {
 *       "type": "infection",
 *       "source": "minecraft:cow",
 *       "keepEquipment": false,
 *       "keepNbt": true,
 *       "results": [ {"target": "infcore:inf_cow", "weight": 100} ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param replace 为 {@code true} 时清除同名源的已有条目再写入
 * @param entries 本文件包含的进化规则列表
 */
public final class EvolutionData {
    private boolean replace;
    private List<EvolutionEntry> entries;

    public EvolutionData() {
        this.entries = new ArrayList<>();
    }

    public EvolutionData(boolean replace, List<EvolutionEntry> entries) {
        this.replace = replace;
        this.entries = entries;
    }

    public boolean replace() { return replace; }
    public List<EvolutionEntry> entries() { return entries; }
}
