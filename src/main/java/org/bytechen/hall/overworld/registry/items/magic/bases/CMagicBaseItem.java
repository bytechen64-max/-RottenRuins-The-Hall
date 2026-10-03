package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 魔法物品基类：冷却 / 持续时间两个计时器 + {@link IMagicBaseItem} 的默认实现。
 *
 * <p><b>从 main 摘入时修掉的三处</b>（原样拿过来是编译不过的）：</p>
 * <ol>
 *   <li>{@code import java.util.logging.Level} → {@code net.minecraft.world.level.Level}，
 *       否则 {@code onUseing} 的参数类型对不上接口；</li>
 *   <li>补上 {@code onPlayHandleTick} 的默认空实现 —— 接口声明了它，
 *       而抽象类没实现，任何具体子类都会被强制要求实现；</li>
 *   <li>{@code p_41383_} 改成有意义的名字。</li>
 * </ol>
 */
public abstract class CMagicBaseItem extends Item implements IMagicBaseItem {
    public int cooldown;
    public int usingtime;

    public CMagicBaseItem(Properties properties) {
        super(properties);
    }

    public CMagicBaseItem(Properties properties, int usingtime, int cooldown) {
        super(properties);
        this.cooldown = cooldown;
        this.usingtime = usingtime;
    }

    @Override
    public boolean onUseing(Player player, Level level) {
        return false;
    }

    @Override
    public boolean isUsing() {
        return usingtime != 0;
    }

    @Override
    public boolean canUse() {
        return cooldown <= 0;
    }

    /** 默认什么都不做；具体魔法物品按需覆写。 */
    @Override
    public void onPlayHandleTick(Player player) {
    }
}
