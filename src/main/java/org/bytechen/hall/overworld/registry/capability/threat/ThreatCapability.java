package org.bytechen.hall.overworld.registry.capability.threat;

import net.minecraft.nbt.CompoundTag;
import org.bytechen.hall.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.hall.overworld.registry.capability.base.IModCapability;

/**
 * 威胁点数能力 —— 记录一个生物（或玩家）对王庭势力的"招惹程度"。
 * <p>
 * 规则（由 {@code ThreatHelper} 与 {@code ForgeEventHelpers} 驱动）：
 * <ul>
 *   <li>初始 0；</li>
 *   <li>非王庭生物击杀王庭生物后，增加「被击杀者最大生命值 / 5」（向下取整）；</li>
 *   <li>王庭生物的索敌判定读取它：玩家威胁点数低于阈值时不会被主动索敌；</li>
 *   <li>玩家死亡时清零。</li>
 * </ul>
 * 数据通过 Forge 能力系统附加到实体上，随实体 NBT 持久化（字段名 {@code Threat}）。
 */
public class ThreatCapability extends AbstractCapability<ThreatCapability> implements IModCapability {

    /** 持久化字段名 */
    public static final String NBT_KEY = "Threat";

    private int threatPoints;

    public ThreatCapability() {
    }

    public ThreatCapability(int threatPoints) {
        this.threatPoints = Math.max(0, threatPoints);
    }

    /** 当前威胁点数。 */
    public int getThreatPoints() {
        return threatPoints;
    }

    /** 直接设置威胁点数（负数按 0 处理）。 */
    public void setThreatPoints(int value) {
        this.threatPoints = Math.max(0, value);
    }

    /** 增加威胁点数，返回增加后的值。 */
    public int addThreatPoints(int amount) {
        if (amount <= 0) return this.threatPoints;
        this.threatPoints = Math.max(0, this.threatPoints + amount);
        return this.threatPoints;
    }

    /** 清空威胁点数（玩家死亡时调用）。 */
    public void reset() {
        this.threatPoints = 0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (threatPoints != 0) {
            tag.putInt(NBT_KEY, threatPoints);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.threatPoints = Math.max(0, nbt.getInt(NBT_KEY));
    }
}
