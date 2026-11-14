package org.bytechen.rottenruins.core.infection.blockspread;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个方块扩散 JSON 文件的顶层数据结构。
 *
 * <pre>
 * {
 *   "replace": false,
 *   "entries": [
 *     {
 *       "type": "hall:spread",
 *       "source": "minecraft:grass_block",
 *       "dropResources": false,
 *       "results": [ {"target": "hall:hall_grass", "weight": 100} ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param replace 为 {@code true} 时清除同名源的已有条目再写入
 * @param entries 本文件包含的方块扩散规则列表
 */
public final class BlockSpreadData {
    private boolean replace;
    private List<BlockSpreadEntry> entries;

    public BlockSpreadData() {
        this.entries = new ArrayList<>();
    }

    public BlockSpreadData(boolean replace, List<BlockSpreadEntry> entries) {
        this.replace = replace;
        this.entries = entries;
    }

    public boolean replace() { return replace; }
    public List<BlockSpreadEntry> entries() { return entries; }
}
