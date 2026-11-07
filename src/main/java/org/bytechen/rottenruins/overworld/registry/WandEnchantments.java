package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.StaffBase;

public final class WandEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, HallMod.MODID);
    public static final RegistryObject<Enchantment> EFFICIENCY = ENCHANTMENTS.register("mana_efficiency",
            () -> new Enchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
                @Override public int getMaxLevel() { return 5; }
                @Override public int getMinCost(int level) { return 8 + 10 * (level - 1); }
                @Override public int getMaxCost(int level) { return getMinCost(level) + 25; }
                @Override public boolean canEnchant(ItemStack stack) {
                    return stack.getItem() instanceof StaffBase;
                }
            });

    private WandEnchantments() { }
}
