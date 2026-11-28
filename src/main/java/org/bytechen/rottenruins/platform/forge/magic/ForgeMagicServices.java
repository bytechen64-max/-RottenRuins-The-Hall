package org.bytechen.rottenruins.platform.forge.magic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.WandEnchantments;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.magic.MagicCastEvent;
import org.bytechen.rottenruins.api.magic.ManaRestoreEvent;
import org.bytechen.rottenruins.gameplay.magic.MagicRuntime;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventory;
import org.bytechen.rottenruins.gameplay.magic.state.ManaAccount;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;

public final class ForgeMagicServices implements MagicRuntime.Services {
    @Override public boolean hasSchoolAccessory(Player player, org.bytechen.rottenruins.core.magic.model.MagicTypes types) {
        return org.bytechen.rottenruins.content.magic.curios.ICuriosBase.isSchoolEquipped(player, types);
    }
    @Override public double generalPower(Player player) {
        return RegisterAttributes.valueOf(player, RegisterAttributes.SPELL_POWER, MagicMath.BASE_SPELL_POWER);
    }
    @Override public double schoolPower(Player player, MagicType type) {
        return RegisterAttributes.valueOf(player, RegisterAttributes.spellPowerOf(type), 0.0d);
    }
    @Override public int efficiencyLevel(ItemStack staff) {
        return EnchantmentHelper.getItemEnchantmentLevel(WandEnchantments.EFFICIENCY.get(), staff);
    }
    @Override public Item emptyWand() { return RegisterItem.WAND.get(); }
    @Override public SpellInventory inventory(Player player) {
        return player.getCapability(CapabilityRegistry.MAGIC_POOL_CAP).orElse(null);
    }
    @Override public ManaAccount mana(Player player) {
        return player.getCapability(CapabilityRegistry.MANA_CAP).orElse(null);
    }
    @Override public boolean beforeCast(MagicCastEvent event) { return MinecraftForge.EVENT_BUS.post(event); }
    @Override public void afterCast(MagicCastEvent.Post event) { MinecraftForge.EVENT_BUS.post(event); }
    @Override public boolean beforeRestore(ManaRestoreEvent event) { return MinecraftForge.EVENT_BUS.post(event); }
    @Override public void afterRestore(ManaRestoreEvent.Post event) { MinecraftForge.EVENT_BUS.post(event); }
}
