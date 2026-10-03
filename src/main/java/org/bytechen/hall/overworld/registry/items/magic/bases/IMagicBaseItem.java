package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 魔法物品的行为接口。
 *
 * <p><b>注意</b>：从 main 分支摘过来时这里写的是
 * {@code import java.util.logging.Level}（不是 Minecraft 的 Level），
 * 于是 {@code onUseing(Player, Level)} 用的是日志的枚举，
 * 实现类没法正确覆写 —— 已修正为 {@code net.minecraft.world.level.Level}。</p>
 */
public interface IMagicBaseItem {
       boolean onUseing(Player player, Level level);
       boolean isUsing();
       boolean canUse();
       void onPlayHandleTick(Player player);
}
