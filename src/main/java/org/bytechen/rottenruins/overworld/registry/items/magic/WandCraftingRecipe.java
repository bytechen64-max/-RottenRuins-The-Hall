package org.bytechen.rottenruins.overworld.registry.items.magic;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.WandRecipes;

import java.util.ArrayList;
import java.util.List;

/** NBT preserving wand assembly and plate recharge recipes. */
public class WandCraftingRecipe extends CustomRecipe {
    private record Pair(Item wand, Item plate) { }

    public WandCraftingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static List<Pair> pairs() {
        return List.of(
                new Pair(RegisterItem.WAND_FIRE.get(), RegisterItem.FIRE_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_DEATH.get(), RegisterItem.DEATH_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_LIGHT.get(), RegisterItem.LIGHT_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_NATURE.get(), RegisterItem.NATURE_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_VOID.get(), RegisterItem.VOID_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_WATER.get(), RegisterItem.WATER_MANA_PLATE.get()),
                new Pair(RegisterItem.WAND_MYSTERIOUS.get(), RegisterItem.MYSTERIOUS_MANA_PLATE.get()));
    }

    private static List<ItemStack> contents(CraftingContainer container) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) result.add(container.getItem(i));
        }
        return result;
    }

    private ItemStack result(CraftingContainer container) {
        List<ItemStack> items = contents(container);
        String path = getId().getPath();
        boolean recharge = path.endsWith("_recharge");
        if (path.equals("wand_common") && items.size() == 8) {
            ItemStack blank = find(items, RegisterItem.WAND.get());
            if (blank.isEmpty()) return ItemStack.EMPTY;
            for (Pair pair : pairs()) if (find(items, pair.plate()).isEmpty()) return ItemStack.EMPTY;
            return upgraded(blank, RegisterItem.WAND_COMMON.get());
        }
        if (path.equals("wand_common_recharge") && items.size() == 2) {
            ItemStack wand = find(items, RegisterItem.WAND_COMMON.get());
            if (wand.isEmpty() || !isAnyPlate(items)) return ItemStack.EMPTY;
            return recharged(wand);
        }
        if (items.size() != 2) return ItemStack.EMPTY;
        for (Pair pair : pairs()) {
            if (!path.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pair.wand()).getPath()
                    + (recharge ? "_recharge" : ""))) continue;
            ItemStack source = find(items, recharge ? pair.wand() : RegisterItem.WAND.get());
            if (source.isEmpty() || find(items, pair.plate()).isEmpty()) return ItemStack.EMPTY;
            return recharge ? recharged(source) : upgraded(source, pair.wand());
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack find(List<ItemStack> stacks, Item item) {
        for (ItemStack stack : stacks) if (stack.is(item)) return stack;
        return ItemStack.EMPTY;
    }

    private static boolean isAnyPlate(List<ItemStack> stacks) {
        for (Pair pair : pairs()) if (!find(stacks, pair.plate()).isEmpty()) return true;
        return false;
    }

    private static ItemStack upgraded(ItemStack source, Item output) {
        ItemStack result = new ItemStack(output);
        if (source.hasTag()) result.setTag(source.getTag().copy());
        result.setDamageValue(source.getDamageValue());
        ((WandItem) output).setMana(result, ((WandItem) output).capacity());
        return result;
    }

    private static ItemStack recharged(ItemStack source) {
        WandItem wand = (WandItem) source.getItem();
        if (wand.mana(source) >= wand.capacity()) return ItemStack.EMPTY;
        ItemStack result = source.copy();
        result.setCount(1);
        wand.setMana(result, wand.mana(source) + WandItem.PLATE_RECHARGE);
        return result;
    }

    @Override public boolean matches(CraftingContainer container, Level level) {
        return !result(container).isEmpty();
    }

    @Override public ItemStack assemble(CraftingContainer container, RegistryAccess registry) {
        return result(container);
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }

    @Override public RecipeSerializer<?> getSerializer() { return WandRecipes.WAND_CRAFTING.get(); }
}
