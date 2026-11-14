package org.bytechen.rottenruins.gameplay.magic;

import java.util.Objects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.api.magic.MagicCastEvent;
import org.bytechen.rottenruins.api.magic.ManaRestoreEvent;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventory;
import org.bytechen.rottenruins.gameplay.magic.state.ManaAccount;

/** Loader services installed before players are created. No silent fallback to missing capabilities/events. */
public final class MagicRuntime {
    public interface Services {
        boolean hasSchoolAccessory(Player player, org.bytechen.rottenruins.core.magic.model.MagicTypes types);
        double generalPower(Player player);
        double schoolPower(Player player, MagicType type);
        int efficiencyLevel(ItemStack staff);
        Item emptyWand();
        SpellInventory inventory(Player player);
        ManaAccount mana(Player player);
        boolean beforeCast(MagicCastEvent event);
        void afterCast(MagicCastEvent.Post event);
        boolean beforeRestore(ManaRestoreEvent event);
        void afterRestore(ManaRestoreEvent.Post event);
    }
    private static volatile Services services;
    private MagicRuntime() {}
    public static synchronized void install(Services value) {
        Objects.requireNonNull(value, "services");
        if (services != null) throw new IllegalStateException("Magic runtime already installed");
        services = value;
    }
    public static Services services() {
        Services value = services;
        if (value == null) throw new IllegalStateException("Magic runtime not installed");
        return value;
    }
}
