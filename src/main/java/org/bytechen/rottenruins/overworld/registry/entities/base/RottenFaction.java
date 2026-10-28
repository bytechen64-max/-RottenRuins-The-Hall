package org.bytechen.rottenruins.overworld.registry.entities.base;

/**
 * 合并后的两大生物阵营。
 *
 * <p>王庭（HALL）与腐化（CORRUPTION）共用一个统一基类 {@link AbstractRottenEntity}，
 * 阵营只作为数据/表现/统计标记；敌我判定仍由 infcore 的感染类型 + {@code ITargetFilter} 决定。</p>
 */
public enum RottenFaction {
    /** 王庭系（原 Hall 内容）。 */
    HALL,
    /** 腐化系（原 corruption 内容）。 */
    CORRUPTION
}
