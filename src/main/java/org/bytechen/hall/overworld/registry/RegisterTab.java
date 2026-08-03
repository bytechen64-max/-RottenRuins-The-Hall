package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashSet;
import java.util.Set;


public class RegisterTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HallMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            HallMod.MODID + "_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HallMod.MODID + ".main"))
                    .icon(() -> new ItemStack(RegisterItem.EXAMPLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        Set<Item> items = new LinkedHashSet<>();

                        // Add spawn eggs here
                        items.add(RegisterItem.INF_PLAYER_SPAWN_EGG.get());
                        items.add(RegisterItem.INF_ENDERMAN_SPAWN_EGG.get());
                        items.add(RegisterItem.BONECRUSHER_SPAWN_EGG.get());
                        items.add(RegisterItem.HALL_TENDON.get());
                        items.add(RegisterItem.HALL_BONE_FRAGMENTS.get());
                        items.add(RegisterItem.DOMITE_ORE.get());
                        items.add(RegisterItem.DOMITE_CRYSTAL.get());
                        items.add(RegisterItem.DOMERITE_ORE.get());
                        items.add(RegisterItem.DOMERITE_INGOT.get());
                        items.add(RegisterItem.HEAT_ANOMALY_EXTRACT.get());
                        items.add(RegisterItem.COLD_ANOMALY_EXTRACT.get());
                        items.add(RegisterItem.ACID_ANOMALY_EXTRACT.get());
                        items.add(RegisterItem.DOMERITE_SWORD.get());
                        items.add(RegisterItem.DOMERITE_AXE.get());
                        items.add(RegisterItem.DOMERITE_PICKAXE.get());
                        items.add(RegisterItem.DOMERITE_SHOVEL.get());
                        items.add(RegisterItem.DOMERITE_HOE.get());
                        items.add(Item.byBlock(RegisterBlock.HALL_GRASS_BLOCK.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_DIRT.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE.get()));
                        // 王庭石系列
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE_STAIRS.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE_SLAB.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE_WALL.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE_BUTTON.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_LOG.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_LEAVES.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_PILLAR.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_PLANKS.get()));
                        // 王庭木系列
                        items.add(Item.byBlock(RegisterBlock.HALL_STAIRS.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_SLAB.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_FENCE.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_FENCE_GATE.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_DOOR.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_TRAPDOOR.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_BUTTON.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_PRESSURE_PLATE.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_VINE.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_GRASS.get()));
                        items.add(Item.byBlock(RegisterBlock.HALL_FLOWER.get()));
                        items.add(Item.byBlock(RegisterBlock.DOMITE_MINERAL.get()));
                        items.add(Item.byBlock(RegisterBlock.DOMERITE_MINERAL.get()));

                        items.add(RegisterItem.VOID_SWORD.get());


                        ForgeRegistries.ITEMS.getValues().stream()
                                .filter(item -> item.builtInRegistryHolder().key().location().getNamespace().equals(HallMod.MODID))
                                .forEach(items::add);

                        items.forEach(output::accept);
                    })
                    .build()
    );
}
