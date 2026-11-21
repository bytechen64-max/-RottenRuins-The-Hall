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
import net.minecraft.world.item.Items;
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

        spellRecipes();
    }

    /** 15 个法术配方（材料顺序无关，势能合成台是无序精确匹配）。 */
    private void spellRecipes() {
        // 火球术：烈焰弹 3 + 烈焰棒 2 + 岩浆膏 1 + 远古残骸 1 + 火焰板 1
        add("potential_crafting/fire_ball", 0)
                .requires(Items.FIRE_CHARGE).requires(Items.FIRE_CHARGE).requires(Items.FIRE_CHARGE)
                .requires(Items.BLAZE_ROD).requires(Items.BLAZE_ROD)
                .requires(Items.MAGMA_CREAM)
                .requires(Items.ANCIENT_DEBRIS)
                .requires(RegisterItem.FIRE_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.FIRE_BALL.get()));

        // 潮汐冲击：水桶 1 + 鹦鹉螺壳 1 + 海洋之心 1 + 海晶石 3 + 暗海晶石 1 + 水板 1
        add("potential_crafting/tidal_pulse", 0)
                .requires(Items.WATER_BUCKET)
                .requires(Items.NAUTILUS_SHELL)
                .requires(Items.HEART_OF_THE_SEA)
                .requires(Items.PRISMARINE).requires(Items.PRISMARINE).requires(Items.PRISMARINE)
                .requires(Items.DARK_PRISMARINE)
                .requires(RegisterItem.WATER_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.TIDAL_PULSE.get()));

        // 抑制场：末影珍珠 2 + 末影之眼 2 + 末地水晶 1 + 虚空精粹 1 + 虚空板 1
        add("potential_crafting/suppression_field", 0)
                .requires(Items.ENDER_PEARL).requires(Items.ENDER_PEARL)
                .requires(Items.ENDER_EYE).requires(Items.ENDER_EYE)
                .requires(Items.END_CRYSTAL)
                .requires(RegisterItem.VOID_ESSENCE.get())
                .requires(RegisterItem.VOID_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.SUPPRESSION_FIELD.get()));

        // 荆棘禁锢：藤蔓 1 + 任意木头 4 + 毒马铃薯 1 + 骨粉 2 + 自然板 1
        add("potential_crafting/briar_snare", 0)
                .requires(Items.VINE)
                .requires(ItemTags.LOGS).requires(ItemTags.LOGS)
                .requires(ItemTags.LOGS).requires(ItemTags.LOGS)
                .requires(Items.POISONOUS_POTATO)
                .requires(Items.BONE_MEAL).requires(Items.BONE_MEAL)
                .requires(RegisterItem.NATURE_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.BRIAR_SNARE.get()));

        // 净化之光：萤石 3 + 亡灵杀手书 1 + 恶魂之泪 2 + 铁块 1 + 光明板 1
        add("potential_crafting/purifying_light", 0)
                .requires(Items.GLOWSTONE).requires(Items.GLOWSTONE).requires(Items.GLOWSTONE)
                .requires(CraftIngredient.of(Items.ENCHANTED_BOOK, storedEnchantment("minecraft:smite")))
                .requires(Items.GHAST_TEAR).requires(Items.GHAST_TEAR)
                .requires(Items.IRON_BLOCK)
                .requires(RegisterItem.LIGHT_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.PURIFYING_LIGHT.get()));

        // 奥数闪步：末影珍珠 2 + 畸骸末影珍珠 1 + 虚空碎片 1 + 虚空精粹 1 + 紫颂果 1 + 虚空板 1
        add("potential_crafting/arcane_step", 0)
                .requires(Items.ENDER_PEARL).requires(Items.ENDER_PEARL)
                .requires(RegisterItem.INF_ENDER_PEAR.get())
                .requires(RegisterItem.VOID_SHARD.get())
                .requires(RegisterItem.VOID_ESSENCE.get())
                .requires(Items.CHORUS_FRUIT)
                .requires(RegisterItem.VOID_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.ARCANE_STEP.get()));

        // 灵魂吸引：幽匿尖啸体 1 + 幽匿催发体 1 + 附魔之瓶 1 + 凋零骷髅头 2 + 回响碎片 5 + 死亡板 1
        add("potential_crafting/soul_drink", 0)
                .requires(Items.SCULK_SHRIEKER)
                .requires(Items.SCULK_CATALYST)
                .requires(Items.EXPERIENCE_BOTTLE)
                .requires(Items.WITHER_SKELETON_SKULL).requires(Items.WITHER_SKELETON_SKULL)
                .requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD)
                .requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD)
                .requires(RegisterItem.DEATH_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.SOUL_DRINK.get()));

        // 奥数立方：下界之星 1 + 死亡之星 1 + 末地水晶 2 + 附魔金苹果 1 + 龙头 1 + 龙息 3 + 回响碎片 5 + 神秘板 1
        add("potential_crafting/olympiad_cube", 0)
                .requires(Items.NETHER_STAR)
                .requires(RegisterItem.DEATH_STAR.get())
                .requires(Items.END_CRYSTAL).requires(Items.END_CRYSTAL)
                .requires(Items.ENCHANTED_GOLDEN_APPLE)
                .requires(Items.DRAGON_HEAD)
                .requires(Items.DRAGON_BREATH).requires(Items.DRAGON_BREATH).requires(Items.DRAGON_BREATH)
                .requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD)
                .requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD)
                .requires(RegisterItem.MYSTERIOUS_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.OLYMPIAD_CUBE.get()));

        // 疾行：幻翼膜 2 + 避雷针 1 + 激流书 1 + 烟花火箭 3 + 光明板 1
        add("potential_crafting/sprint", 0)
                .requires(Items.PHANTOM_MEMBRANE).requires(Items.PHANTOM_MEMBRANE)
                .requires(Items.LIGHTNING_ROD)
                .requires(CraftIngredient.of(Items.ENCHANTED_BOOK, storedEnchantment("minecraft:riptide")))
                .requires(Items.FIREWORK_ROCKET).requires(Items.FIREWORK_ROCKET).requires(Items.FIREWORK_ROCKET)
                .requires(RegisterItem.LIGHT_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.SPRINT.get()));

        // 护盾术：盾牌 2 + 铁块 1 + 下界合金碎片 2 + 铁门 1 + 金苹果 1 + 自然板 1
        add("potential_crafting/shield_ward", 0)
                .requires(Items.SHIELD).requires(Items.SHIELD)
                .requires(Items.IRON_BLOCK)
                .requires(Items.NETHERITE_SCRAP).requires(Items.NETHERITE_SCRAP)
                .requires(Items.IRON_DOOR)
                .requires(Items.GOLDEN_APPLE)
                .requires(RegisterItem.NATURE_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.SHIELD_WARD.get()));

        // 藤甲术：任意木头 5 + 鳞甲 2 + 盾牌 1 + 蜂蜜瓶 1 + 自然板 1
        add("potential_crafting/bark_armor", 0)
                .requires(ItemTags.LOGS).requires(ItemTags.LOGS).requires(ItemTags.LOGS)
                .requires(ItemTags.LOGS).requires(ItemTags.LOGS)
                .requires(Items.SCUTE).requires(Items.SCUTE)
                .requires(Items.SHIELD)
                .requires(Items.HONEY_BOTTLE)
                .requires(RegisterItem.NATURE_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.BARK_ARMOR.get()));

        // 冻雨：细雪桶 2 + 水桶 1 + 潮涌核心 1 + 水瓶 1 + 蓝冰 2 + 水板 1
        add("potential_crafting/freezing_rain", 0)
                .requires(Items.POWDER_SNOW_BUCKET).requires(Items.POWDER_SNOW_BUCKET)
                .requires(Items.WATER_BUCKET)
                .requires(Items.CONDUIT)
                .requires(CraftIngredient.of(Items.POTION, waterBottle()))
                .requires(Items.BLUE_ICE).requires(Items.BLUE_ICE)
                .requires(RegisterItem.WATER_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.FREEZING_RAIN.get()));

        // 焰斩：岩浆桶 1 + 烈焰棒 1 + 下界之星 1 + 恶魂之泪 1 + 下界合金锭 1 + 铁剑 1 + 火焰板 1
        add("potential_crafting/flame_slash", 0)
                .requires(Items.LAVA_BUCKET)
                .requires(Items.BLAZE_ROD)
                .requires(Items.NETHER_STAR)
                .requires(Items.GHAST_TEAR)
                .requires(Items.NETHERITE_INGOT)
                .requires(Items.IRON_SWORD)
                .requires(RegisterItem.FIRE_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.FLAME_SLASH.get()));

        // 圣翼：幻翼膜 4 + 烟花火箭 2 + 发射器 1 + 粘液块 1 + 羽毛 3 + 萤石 2 + 光明板 1
        add("potential_crafting/holy_wings", 0)
                .requires(Items.PHANTOM_MEMBRANE).requires(Items.PHANTOM_MEMBRANE)
                .requires(Items.PHANTOM_MEMBRANE).requires(Items.PHANTOM_MEMBRANE)
                .requires(Items.FIREWORK_ROCKET).requires(Items.FIREWORK_ROCKET)
                .requires(Items.DISPENSER)
                .requires(Items.SLIME_BLOCK)
                .requires(Items.FEATHER).requires(Items.FEATHER).requires(Items.FEATHER)
                .requires(Items.GLOWSTONE).requires(Items.GLOWSTONE)
                .requires(RegisterItem.LIGHT_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.HOLY_WINGS.get()));

        // 黑洞术：虚空精粹 3 + 末地水晶 1 + 回响碎片 2 + 龙息 3 + 黑曜石 2 + 虚空板 1
        add("potential_crafting/black_hole", 0)
                .requires(RegisterItem.VOID_ESSENCE.get()).requires(RegisterItem.VOID_ESSENCE.get())
                .requires(RegisterItem.VOID_ESSENCE.get())
                .requires(Items.END_CRYSTAL)
                .requires(Items.ECHO_SHARD).requires(Items.ECHO_SHARD)
                .requires(Items.DRAGON_BREATH).requires(Items.DRAGON_BREATH).requires(Items.DRAGON_BREATH)
                .requires(Items.OBSIDIAN).requires(Items.OBSIDIAN)
                .requires(RegisterItem.VOID_MANA_PLATE.get())
                .save(new ItemStack(RegisterItem.BLACK_HOLE.get()));
    }

    /** 附魔书 NBT：只要包含指定附魔即算匹配（势能合成台按子集匹配）。 */
    private static CompoundTag storedEnchantment(String enchantmentId) {
        CompoundTag enchantment = new CompoundTag();
        enchantment.putString("id", enchantmentId);
        enchantment.putShort("lvl", (short) 1);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(enchantment);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", list);
        return tag;
    }

    /** 水瓶 NBT。 */
    private static CompoundTag waterBottle() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:water");
        return tag;
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
