package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.logging.Level;

public abstract class CMagicBaseItem extends Item implements IMagicBaseItem {
    public int cooldown;
    public int usingtime;
    public CMagicBaseItem(Properties p_41383_) {
        super(p_41383_);
    }
    public CMagicBaseItem(Properties properties , int usingtime , int cooldown){
        super(properties);
        this.cooldown = cooldown;
        this.usingtime = usingtime;
    }

    @Override
    public boolean onUseing(Player player, Level level) {
        return false;
    }
    @Override
    public boolean isUsing(){
        return usingtime != 0;
    }
    @Override
    public boolean canUse(){
        return cooldown==0;
    }
}
