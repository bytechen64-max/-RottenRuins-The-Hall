package org.bytechen.rottenruins.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.crafting.CraftIngredient;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipe;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.utils.ModUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 集成：把奥秘仪台的配方按环形布局展示，并把工作方块挂成配方来源。
 *
 * <p>配方列表从服务端配方管理器读，重载／数据包改动后会跟着刷新。</p>
 */
@JeiPlugin
public final class RottenRuinsJeiPlugin implements IModPlugin {

    public static final RecipeType<PotentialCraftingJeiRecipe> POTENTIAL_CRAFTING =
            RecipeType.create(HallMod.MODID, "potential_crafting", PotentialCraftingJeiRecipe.class);

    @Override
    @NotNull
    public ResourceLocation getPluginUid() {
        return ModUtils.modLoc("jei_plugin");
    }

    @Override
    public void registerCategories(@NotNull IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PotentialCraftingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<PotentialCraftingJeiRecipe> recipes = new ArrayList<>();
        for (PotentialCraftingRecipe recipe
                : server.getRecipeManager().getAllRecipesFor(PotentialCraftingRecipes.TYPE.get())) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (CraftIngredient ingredient : recipe.ingredients()) {
                ItemStack display = ingredient.display();
                if (!display.isEmpty()) {
                    inputs.add(List.of(display));
                }
            }
            ItemStack output = recipe.getResultItem(server.registryAccess()).copy();
            if (!output.isEmpty()) {
                recipes.add(new PotentialCraftingJeiRecipe(recipe.getId(), inputs, output, recipe.timeTicks()));
            }
        }
        registration.addRecipes(POTENTIAL_CRAFTING, recipes);
    }

    @Override
    public void registerRecipeTransferHandlers(@NotNull IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new PotentialCraftingTransferHandler(), POTENTIAL_CRAFTING);
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(RegisterBlock.POTENTIAL_CRAFTING_TABLE.get()), POTENTIAL_CRAFTING);
    }
}
