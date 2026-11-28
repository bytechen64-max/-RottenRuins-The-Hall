package org.bytechen.rottenruins.overworld.crafting;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 一条材料要求，支持物品 / 标签 / 物品+NBT。NBT 采用子集匹配。
 *
 * <pre>{@code
 * { "item": "modid:item" }
 * { "tag" : "modid:tag"  }
 * { "item": "modid:item", "nbt": "{K:1b}" }
 * }</pre>
 */
public record CraftIngredient(@Nullable Item item, @Nullable TagKey<Item> tag, @Nullable CompoundTag nbt) {

    public CraftIngredient {
        if ((item == null) == (tag == null)) {
            throw new IllegalArgumentException("必须且只能指定 item 或 tag 之一");
        }
    }

    public static CraftIngredient of(@NotNull Item item) {
        return new CraftIngredient(item, null, null);
    }

    public static CraftIngredient of(@NotNull TagKey<Item> tag) {
        return new CraftIngredient(null, tag, null);
    }

    public static CraftIngredient of(@NotNull Item item, @Nullable CompoundTag nbt) {
        return new CraftIngredient(item, null, nbt);
    }

    public boolean test(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (item != null) {
            if (!stack.is(item)) {
                return false;
            }
        } else if (!stack.is(Objects.requireNonNull(tag))) {
            return false;
        }
        if (nbt == null || nbt.isEmpty()) {
            return true;
        }
        CompoundTag actual = stack.getTag();
        return actual != null && contains(actual, nbt);
    }

    /** 仅用于展示（标签取注册表里第一个符合的物品）。 */
    @NotNull
    public ItemStack display() {
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            if (nbt != null && !nbt.isEmpty()) {
                stack.setTag(nbt.copy());
            }
            return stack;
        }
        if (tag != null) {
            for (Item candidate : ForgeRegistries.ITEMS.getValues()) {
                ItemStack stack = new ItemStack(candidate);
                if (stack.is(tag)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @NotNull
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (item != null) {
            json.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
        } else {
            json.addProperty("tag", Objects.requireNonNull(tag).location().toString());
        }
        if (nbt != null && !nbt.isEmpty()) {
            json.addProperty("nbt", nbt.toString());
        }
        return json;
    }

    @NotNull
    public static CraftIngredient fromJson(@NotNull JsonObject json) {
        CompoundTag nbt = null;
        if (json.has("nbt")) {
            try {
                nbt = net.minecraft.nbt.TagParser.parseTag(json.get("nbt").getAsString());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new com.google.gson.JsonParseException("NBT 解析失败：" + json.get("nbt"), e);
            }
        }
        if (json.has("tag")) {
            ResourceLocation id = new ResourceLocation(json.get("tag").getAsString());
            return new CraftIngredient(null, TagKey.create(Registries.ITEM, id), nbt);
        }
        if (json.has("item")) {
            ResourceLocation id = new ResourceLocation(json.get("item").getAsString());
            Item found = ForgeRegistries.ITEMS.getValue(id);
            if (found == null) {
                throw new com.google.gson.JsonParseException("未知物品：" + id);
            }
            return new CraftIngredient(found, null, nbt);
        }
        throw new com.google.gson.JsonParseException("材料要求必须包含 item 或 tag");
    }

    private static boolean contains(@NotNull CompoundTag actual, @NotNull CompoundTag wanted) {
        for (String key : wanted.getAllKeys()) {
            Tag want = wanted.get(key);
            Tag have = actual.get(key);
            if (have == null || want == null || !want.equals(have)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        String base = item != null
                ? String.valueOf(ForgeRegistries.ITEMS.getKey(item))
                : "#" + Objects.requireNonNull(tag).location();
        return nbt == null || nbt.isEmpty() ? base : base + nbt;
    }
}
