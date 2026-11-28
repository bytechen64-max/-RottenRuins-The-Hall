package org.bytechen.rottenruins.overworld.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.config.data.SplendidingConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方序列化器（JSON / 网络）。
 *
 * <p>产物两种写法都收：字符串 {@code "output": "modid:item"}（数量 1，
 * 与 {@code exampleRepice.txt} 一致）或对象 {@code {"item":…,"count":…,"nbt":…}}。</p>
 *
 * <p>{@code outputtime} 单位 tick；省略或 ≤0 时用配置项
 * {@code potentialCraftingBaseTimeTicks}（默认 200 = 10 秒）。</p>
 */
public final class PotentialCraftingRecipeSerializer
        implements RecipeSerializer<PotentialCraftingRecipe> {

    @Override
    @NotNull
    public PotentialCraftingRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
        if (array.isEmpty()) {
            throw new JsonParseException("配方必须至少有一条材料：" + id);
        }
        if (array.size() > PotentialCraftingRecipe.MAX_INGREDIENTS) {
            throw new JsonParseException("材料条数超过 "
                    + PotentialCraftingRecipe.MAX_INGREDIENTS + "：" + id);
        }
        List<CraftIngredient> ingredients = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("材料要求必须是对象：" + element);
            }
            ingredients.add(CraftIngredient.fromJson(element.getAsJsonObject()));
        }

        ItemStack output = readOutput(json, id);
        int time = json.has("outputtime") ? GsonHelper.getAsInt(json, "outputtime", 0) : 0;
        if (time <= 0) {
            time = defaultTimeTicks();
        }
        return new PotentialCraftingRecipe(id, ingredients, output, time);
    }

    @Override
    public @Nullable PotentialCraftingRecipe fromNetwork(@NotNull ResourceLocation id,
                                                         @NotNull FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<CraftIngredient> ingredients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ingredients.add(readIngredient(buffer));
        }
        ItemStack output = buffer.readItem();
        return new PotentialCraftingRecipe(id, ingredients, output, buffer.readVarInt());
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull PotentialCraftingRecipe recipe) {
        List<CraftIngredient> ingredients = recipe.ingredients();
        buffer.writeVarInt(ingredients.size());
        for (CraftIngredient ingredient : ingredients) {
            writeIngredient(buffer, ingredient);
        }
        buffer.writeItem(recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY));
        buffer.writeVarInt(recipe.timeTicks());
    }

    // ── 产物 ──

    @NotNull
    private static ItemStack readOutput(@NotNull JsonObject json, @NotNull ResourceLocation recipeId) {
        JsonElement element = json.get("output");
        if (element == null) {
            throw new JsonParseException("配方缺少 output：" + recipeId);
        }
        if (element.isJsonPrimitive()) {
            return new ItemStack(requireItem(element.getAsString(), recipeId));
        }
        if (!element.isJsonObject()) {
            throw new JsonParseException("output 必须是字符串或对象：" + element);
        }
        JsonObject out = element.getAsJsonObject();
        Item item = requireItem(GsonHelper.getAsString(out, "item"), recipeId);
        ItemStack stack = new ItemStack(item, Math.max(1, GsonHelper.getAsInt(out, "count", 1)));
        if (out.has("nbt")) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(out.get("nbt").getAsString()));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new JsonParseException("产物 NBT 解析失败（配方 " + recipeId + "）", e);
            }
        }
        return stack;
    }

    @NotNull
    private static Item requireItem(@NotNull String rawId, @NotNull ResourceLocation recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            throw new JsonParseException("未知物品 " + rawId + "（配方 " + recipeId + "）");
        }
        return item;
    }

    // ── 材料 ──

    private static void writeIngredient(@NotNull FriendlyByteBuf buffer,
                                        @NotNull CraftIngredient ingredient) {
        if (ingredient.item() != null) {
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(ingredient.item()));
        } else {
            buffer.writeBoolean(false);
            buffer.writeResourceLocation(ingredient.tag().location());
        }
        CompoundTag nbt = ingredient.nbt();
        boolean hasTag = nbt != null && !nbt.isEmpty();
        buffer.writeBoolean(hasTag);
        if (hasTag) {
            buffer.writeNbt(nbt);
        }
    }

    @NotNull
    private static CraftIngredient readIngredient(@NotNull FriendlyByteBuf buffer) {
        boolean byItem = buffer.readBoolean();
        ResourceLocation key = buffer.readResourceLocation();
        CompoundTag nbt = buffer.readBoolean() ? buffer.readNbt() : null;
        if (!byItem) {
            return new CraftIngredient(null, TagKey.create(Registries.ITEM, key), nbt);
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null) {
            throw new IllegalStateException("同步到的配方引用了不存在的物品：" + key);
        }
        return new CraftIngredient(item, null, nbt);
    }

    /** 默认时长：读配置，读不到退回 200 tick。 */
    public static int defaultTimeTicks() {
        try {
            SplendidingConfig config = org.bytechen.rottenruins.config.ConfigHelper.configHolder.get();
            if (config != null) {
                return Math.max(1, config.potentialCraftingBaseTimeTicks);
            }
        } catch (Throwable ignored) {
            // 配置未加载（数据包早期）→ 用默认值
        }
        return 200;
    }
}
