package org.bytechen.hall.overworld.registry.items.magic;

import net.minecraft.world.item.Item;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.hall.overworld.registry.items.magic.bases.StaffBase;
import org.jetbrains.annotations.NotNull;

/**
 * 通用法杖 —— <b>本轮只做注册</b>，除了声明{@linkplain MagicType 流派}之外没有任何自己的逻辑。
 *
 * <p>释放流程（查槽位 → 校验冷却 → 校验法力 → 抛事件 → 扣蓝 → 进冷却 → 调 {@code cast}）
 * 全部在 {@link org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle} 里，
 * 由 {@link StaffBase} 转发；法术的三种释放模式也<b>不由法杖决定</b>，
 * 而是由魔法槽里那件法术自己声明。所以这个类不需要覆写任何东西。</p>
 *
 * <h3>为什么是一个类 + 构造参数，而不是每根杖一个子类</h3>
 * <p>目前所有杖的行为完全相同（都是"转发"，而且 {@link StaffBase#accepts} 默认不限制流派），
 * 差别只有 {@code MagicType} 一个枚举值。等某根杖需要自己的东西时
 * —— 例如只接受火系法术、施法时消耗耐久、或换一套持杖姿态 ——
 * 再把它拆成独立的子类覆写对应的方法即可，拆分成本很低。</p>
 *
 * <h3>模型</h3>
 * <p>走 {@code minecraft:item/handheld}（和剑、工具同款），由
 * {@code datagen/gen/ItemGenData} 自动生成 —— 见那里的 {@code isHandheld}。</p>
 */
public class WandItem extends StaffBase {

    /**
     * @param properties 物品属性；法杖建议 {@code stacksTo(1)}
     * @param magicType  这根杖的流派
     */
    public WandItem(@NotNull Item.Properties properties, @NotNull MagicType magicType) {
        super(properties, magicType);
    }
}
