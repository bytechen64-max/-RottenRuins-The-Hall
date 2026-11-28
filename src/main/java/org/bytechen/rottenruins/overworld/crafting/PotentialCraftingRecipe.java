package org.bytechen.rottenruins.overworld.crafting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 势能合成台的配方：无序材料表 + 产物 + 耗时（tick）。
 *
 * <p>环形槽每格只能放 1 个物品，所以材料条数 ≤ {@value #MAX_INGREDIENTS}，
 * 且一格刚好对应一条要求。</p>
 */
public final class PotentialCraftingRecipe implements Recipe<Container> {

    /** 材料条数上限 = 环形槽数（每格 1 个物品）。 */
    public static final int MAX_INGREDIENTS = PotentialCraftingTableBlockEntity.RING_SLOTS;

    private final ResourceLocation id;
    private final List<CraftIngredient> ingredients;
    private final ItemStack output;
    private final int timeTicks;

    public PotentialCraftingRecipe(@NotNull ResourceLocation id,
                                   @NotNull List<CraftIngredient> ingredients,
                                   @NotNull ItemStack output, int timeTicks) {
        this.id = id;
        this.ingredients = List.copyOf(ingredients);
        this.output = output;
        this.timeTicks = Math.max(1, timeTicks);
    }

    @NotNull
    public List<CraftIngredient> ingredients() {
        return ingredients;
    }

    public int timeTicks() {
        return timeTicks;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return matchConsumption(container) != null;
    }

    /**
     * 匹配成功时返回每个槽位要消耗的数量（每格 1 个，所以是 0/1），否则 {@code null}。
     *
     * <p>用回溯而不是贪心：标签要求与具体物品要求可能重叠，贪心会先占掉
     * 只属于另一条要求的槽位。格子只有 16 个，搜索空间极小。</p>
     */
    @Nullable
    public int[] matchConsumption(@NotNull Container container) {
        int size = container.getContainerSize();
        int[] remaining = new int[size];
        for (int slot = 0; slot < size; slot++) {
            remaining[slot] = container.getItem(slot).getCount();
        }
        int[] used = new int[size];
        if (!assign(0, remaining, used, container)) {
            return null;
        }
        for (int left : remaining) {
            if (left > 0) {
                return null;   // 有多余物品不算匹配
            }
        }
        return used;
    }

    private boolean assign(int index, int[] remaining, int[] used, Container container) {
        if (index >= ingredients.size()) {
            return true;
        }
        CraftIngredient requirement = ingredients.get(index);
        for (int slot = 0; slot < remaining.length; slot++) {
            if (remaining[slot] <= 0 || !requirement.test(container.getItem(slot))) {
                continue;
            }
            remaining[slot]--;
            used[slot]++;
            if (assign(index + 1, remaining, used, container)) {
                return true;
            }
            remaining[slot]++;
            used[slot]--;
        }
        return false;
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    @NotNull
    public ItemStack getResultItem(@NotNull RegistryAccess registries) {
        return output;
    }

    /** 不进原版配方书：匹配规则与工作台不同。 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    @NotNull
    public RecipeSerializer<?> getSerializer() {
        return PotentialCraftingRecipes.SERIALIZER.get();
    }

    @Override
    @NotNull
    public RecipeType<?> getType() {
        return PotentialCraftingRecipes.TYPE.get();
    }

    @Override
    @NotNull
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @NotNull
    public ItemStack getToastSymbol() {
        return new ItemStack(RegisterBlock.POTENTIAL_CRAFTING_TABLE.get());
    }

    @Override
    public String toString() {
        return "PotentialCrafting[" + id + " -> " + output + ", " + timeTicks + "t]";
    }
}
