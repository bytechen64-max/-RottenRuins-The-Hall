package org.bytechen.rottenruins.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.bytechen.rottenruins.network.c2s.PotentialCraftFillPacket;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.SourceMenus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * JEI 的「一键填充」按钮：把配方的材料从背包搬进环形槽。
 *
 * <p>点击时只把配方 id 发给服务端，服务端按 id 重查配方、自己比对材料再搬运 ——
 * 界面这边的 {@code recipeSlotsView} 只用于 JEI 自己画高亮，不参与判定。</p>
 */
public final class PotentialCraftingTransferHandler
        implements IRecipeTransferHandler<PotentialCraftingMenu, PotentialCraftingJeiRecipe> {

    @Override
    @NotNull
    public Class<? extends PotentialCraftingMenu> getContainerClass() {
        return PotentialCraftingMenu.class;
    }

    @Override
    @NotNull
    public Optional<MenuType<PotentialCraftingMenu>> getMenuType() {
        return Optional.of(SourceMenus.POTENTIAL_CRAFTING.get());
    }

    @Override
    @NotNull
    public RecipeType<PotentialCraftingJeiRecipe> getRecipeType() {
        return RottenRuinsJeiPlugin.POTENTIAL_CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(@NotNull PotentialCraftingMenu container,
                                               @NotNull PotentialCraftingJeiRecipe recipe,
                                               @NotNull IRecipeSlotsView recipeSlots,
                                               @NotNull Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (doTransfer && recipe.id() != null) {
            PotentialCraftFillPacket.send(recipe.id());
        }
        // 返回 null = 没有错误；材料够不够由服务端判断并回一条动作栏提示
        return null;
    }
}
