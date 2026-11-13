package org.bytechen.rottenruins.overworld.registry.items.magic.bases;

/** 主动法术的使用方式，决定整条释放时间轴（由 {@code MagicHandle} 驱动）：IMMEDIATELY 按下即生效、无蓄力/维持；CHARGE 蓄力后松手释放、蓄满全额提前放按比例减弱；KEEP 按住持续执行并按触发速度回调。被动法术不走这条流程。 */
public enum UsingType {

    IMMEDIATELY,

    CHARGE,

    KEEP
}
