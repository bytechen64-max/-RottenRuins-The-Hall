package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.entity.player.Player;

import java.util.logging.Level;

public interface IMagicBaseItem {
       boolean onUseing(Player player, Level level);
       boolean isUsing();
       boolean canUse();
       void onPlayHandleTick(Player player);
}
