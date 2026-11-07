package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.inventory.SourceMachineMenu;

public final class SourceMenus {
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,HallMod.MODID);
    public static final RegistryObject<MenuType<SourceMachineMenu>> SOURCE_MACHINE=MENUS.register("source_machine",()->IForgeMenuType.create(SourceMachineMenu::new));
    private SourceMenus() {}
}
