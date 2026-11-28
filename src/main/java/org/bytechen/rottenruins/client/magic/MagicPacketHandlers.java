package org.bytechen.rottenruins.client.magic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.core.magic.state.ManaState;
import org.bytechen.rottenruins.gameplay.magic.state.ManaAccount;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventory;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventorySnapshot;
import org.bytechen.rottenruins.gameplay.magic.casting.MagicHandle;

/** Client-only application of server snapshots; callers enqueue on the client thread. */
public final class MagicPacketHandlers {
    private MagicPacketHandlers() {}
    public static void applyMana(int entityId, ManaState.View state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        ManaAccount mana = MagicHandle.getMana(player);
        if (mana != null) {
            mana.applyServerState(state);
        }
    }

    public static void applyPool(int entityId, SpellInventorySnapshot state, int usableSlots) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        SpellInventory pool = MagicHandle.getPool(player);
        if (pool != null) {
            pool.applyServerSnapshot(state, usableSlots);
        }
    }
}
