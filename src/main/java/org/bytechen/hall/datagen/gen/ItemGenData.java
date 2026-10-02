package org.bytechen.hall.datagen.gen;

import org.bytechen.hall.HallMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.hall.overworld.registry.items.BaseGlowingGeoItem;

import java.util.Set;

public class ItemGenData extends ItemModelProvider {
    public ItemGenData(PackOutput output, ExistingFileHelper efh) { super(output, HallMod.MODID, efh); }

    private static final Set<String> MANUAL_WHITELIST = Set.of(
            "domerite_sword", "domerite_pickaxe", "domerite_axe", "domerite_shovel", "domerite_hoe","domerite_longsword","crimson_vow"
    );

    @Override
    protected void registerModels() {
        ForgeRegistries.ITEMS.getValues().forEach(item -> {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc != null && loc.getNamespace().equals(HallMod.MODID)) {
                if(item instanceof BaseGlowingGeoItem)return;
                String path = loc.getPath();
                if (item instanceof BlockItem || MANUAL_WHITELIST.contains(path)) return;
                if (isHandheld(item)) handheldItem(path);
                else this.basicItem(item);
            }
        });
    }

    private boolean isHandheld(Item item) { return item instanceof SwordItem || item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem; }
    protected void handheldItem(String path) { withExistingParent(path, mcLoc("item/handheld")).texture("layer0", modLoc("item/" + path)); }
}
