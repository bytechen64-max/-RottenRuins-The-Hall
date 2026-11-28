package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipe;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * 势能合成台配方的注册与查找。
 *
 * <p>配方 JSON 放在 {@code data/<命名空间>/recipes/} 下，用
 * {@code "type": "rotten_ruins:potential_crafting"} 选中本类型。</p>
 */
public final class PotentialCraftingRecipes {

    public static final String PATH = "potential_crafting";

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, HallMod.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, HallMod.MODID);

    public static final RegistryObject<RecipeType<PotentialCraftingRecipe>> TYPE =
            RECIPE_TYPES.register(PATH, () -> RecipeType.simple(modLoc()));

    public static final RegistryObject<RecipeSerializer<PotentialCraftingRecipe>> SERIALIZER =
            SERIALIZERS.register(PATH, PotentialCraftingRecipeSerializer::new);

    private PotentialCraftingRecipes() {
    }

    @NotNull
    public static ResourceLocation modLoc() {
        return ModUtils.modLoc(PATH);
    }

    /** 找第一条匹配的配方（注册顺序优先）；服务端调用。 */
    @Nullable
    public static PotentialCraftingRecipe findMatch(@NotNull Level level, @NotNull Container container) {
        for (PotentialCraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(TYPE.get())) {
            if (recipe.matches(container, level)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    public static PotentialCraftingRecipe byId(@NotNull Level level, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id)
                .filter(recipe -> recipe instanceof PotentialCraftingRecipe)
                .map(recipe -> (PotentialCraftingRecipe) recipe)
                .orElse(null);
    }

    @NotNull
    public static RegistryAccess registries() {
        return RegistryAccess.EMPTY;
    }
}
