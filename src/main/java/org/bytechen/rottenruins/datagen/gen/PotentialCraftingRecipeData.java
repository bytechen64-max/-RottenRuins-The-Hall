package org.bytechen.rottenruins.datagen.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.crafting.CraftIngredient;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * 势能合成台配方的 datagen，写出 {@code data/rotten_ruins/recipes/potential_crafting/*.json}。
 *
 * <p>格式与 {@code exampleRepice.txt} 一致；产物数量为 1 且无 NBT 时写成字符串，
 * 否则写成 {@code {"item":…,"count":…,"nbt":…}}。</p>
 *
 * <p>没有继承 {@code RecipeProvider}：它已经是 final 的 "Recipes" 名字，再加一个会撞
 * "Duplicate provider"。</p>
 */
public final class PotentialCraftingRecipeData implements DataProvider {

    private final PackOutput.PathProvider pathProvider;
    private final List<Entry> recipes = new ArrayList<>();

    public PotentialCraftingRecipeData(PackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipes");
    }

    @Override
    public CompletableFuture<?> run(@NotNull CachedOutput cache) {
        buildRecipes();
        List<CompletableFuture<?>> writes = new ArrayList<>(recipes.size());
        for (Entry recipe : recipes) {
            JsonObject json = new JsonObject();
            json.addProperty("type", PotentialCraftingRecipes.modLoc().toString());

            JsonArray array = new JsonArray();
            for (CraftIngredient ingredient : recipe.ingredients) {
                array.add(ingredient.toJson());
            }
            json.add("ingredients", array);

            ItemStack output = recipe.output;
            if (output.getCount() == 1 && output.getTag() == null) {
                json.addProperty("output", String.valueOf(BuiltInRegistries.ITEM.getKey(output.getItem())));
            } else {
                JsonObject out = new JsonObject();
                out.addProperty("item", String.valueOf(BuiltInRegistries.ITEM.getKey(output.getItem())));
                if (output.getCount() != 1) {
                    out.addProperty("count", output.getCount());
                }
                if (output.getTag() != null) {
                    out.addProperty("nbt", output.getTag().toString());
                }
                json.add("output", out);
            }
            if (recipe.outputTime > 0) {
                json.addProperty("outputtime", recipe.outputTime);
            }
            writes.add(DataProvider.saveStable(cache, json, pathProvider.json(recipe.id)));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    @NotNull
    public String getName() {
        return "Potential Crafting Recipes: " + HallMod.MODID;
    }

    private void buildRecipes() {
        // 4 玻璃瓶 + 1 源荷砂 → 储魔瓶（outputtime 省略 = 用配置里的基准时间）
        add("potential_crafting/mana_bottle", 0)
                .requires(net.minecraft.world.item.Items.GLASS_BOTTLE)
                .requires(net.minecraft.world.item.Items.GLASS_BOTTLE)
                .requires(net.minecraft.world.item.Items.GLASS_BOTTLE)
                .requires(net.minecraft.world.item.Items.GLASS_BOTTLE)
                .requires(RegisterItem.SOURCE_CHARGE_SAND.get())
                .save(new ItemStack(RegisterItem.MANA_BOTTLE.get()));

        // 任意 2 木板 → 2 王庭木板（标签配方）
        add("potential_crafting/hall_planks_from_any_planks", 0)
                .requires(ItemTags.PLANKS)
                .requires(ItemTags.PLANKS)
                .save(new ItemStack(Item.byBlock(RegisterBlock.HALL_PLANKS.get()), 2));

        // 带 Charged 标记的储魔瓶 + 2 晶体 → 3 晶体（NBT 子集匹配）
        CompoundTag marked = new CompoundTag();
        marked.putBoolean("Charged", true);
        add("potential_crafting/domite_crystal_from_charged_bottle", 300)
                .requires(CraftIngredient.of(RegisterItem.MANA_BOTTLE.get(), marked))
                .requires(RegisterItem.DOMITE_CRYSTAL.get())
                .requires(RegisterItem.DOMITE_CRYSTAL.get())
                .save(new ItemStack(RegisterItem.DOMITE_CRYSTAL.get(), 3));
    }

    private Builder add(String path, int outputTime) {
        return new Builder(path, outputTime);
    }

    private record Entry(ResourceLocation id, List<CraftIngredient> ingredients,
                         ItemStack output, int outputTime) {
    }

    /** 配方构造器；每调用一次 requires 就是一条材料（对应一个格子）。 */
    private final class Builder {
        private final String path;
        private final int outputTime;
        private final List<CraftIngredient> ingredients = new ArrayList<>();

        private Builder(String path, int outputTime) {
            this.path = path;
            this.outputTime = outputTime;
        }

        @NotNull
        private Builder requires(@NotNull CraftIngredient ingredient) {
            ingredients.add(ingredient);
            return this;
        }

        @NotNull
        private Builder requires(@NotNull ItemLike item) {
            return requires(CraftIngredient.of(item.asItem()));
        }

        @NotNull
        private Builder requires(@NotNull TagKey<Item> tag) {
            return requires(CraftIngredient.of(tag));
        }

        private void save(@NotNull ItemStack output) {
            recipes.add(new Entry(ModUtils.modLoc(path),
                    List.copyOf(ingredients), output, outputTime));
        }
    }
}
