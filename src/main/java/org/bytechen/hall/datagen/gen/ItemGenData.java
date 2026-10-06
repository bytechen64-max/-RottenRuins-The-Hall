package org.bytechen.hall.datagen.gen;

import org.bytechen.hall.HallMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.hall.overworld.registry.items.BaseGlowingGeoItem;
import org.bytechen.hall.overworld.registry.items.magic.bases.StaffBase;

import java.util.Set;

public class ItemGenData extends ItemModelProvider {
    public ItemGenData(PackOutput output, ExistingFileHelper efh) { super(output, HallMod.MODID, efh); }

    private static final Set<String> MANUAL_WHITELIST = Set.of(
            "domerite_sword", "domerite_pickaxe", "domerite_axe", "domerite_shovel", "domerite_hoe","domerite_longsword","crimson_vow","silent_daylight"
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

    /**
     * 判断一件物品该不该用 {@code minecraft:item/handheld} 模型。
     *
     * <p>工具/剑之外，{@link StaffBase 法杖} 也走 handheld —— 它们和剑一样是
     * 手持长条状物品，用 {@code item/generated} 会在手里显示成"贴在掌心的一块贴图"。
     * 新加法杖不需要动这里，只要它继承 {@link StaffBase} 就会自动被认出来。</p>
     */
    private boolean isHandheld(Item item) {
        return item instanceof SwordItem || item instanceof PickaxeItem || item instanceof AxeItem
                || item instanceof ShovelItem || item instanceof HoeItem || item instanceof StaffBase;
    }
    protected void handheldItem(String path) { withExistingParent(path, mcLoc("item/handheld")).texture("layer0", modLoc("item/" + path)); }
}
