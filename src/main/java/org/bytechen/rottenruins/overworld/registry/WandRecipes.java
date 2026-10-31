package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.items.magic.WandCraftingRecipe;

public final class WandRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, HallMod.MODID);
    public static final RegistryObject<RecipeSerializer<WandCraftingRecipe>> WAND_CRAFTING =
            SERIALIZERS.register("wand_crafting", () -> new SimpleCraftingRecipeSerializer<>(WandCraftingRecipe::new));

    private WandRecipes() { }
}
