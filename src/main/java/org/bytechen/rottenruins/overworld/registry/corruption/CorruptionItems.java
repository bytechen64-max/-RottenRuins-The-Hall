package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;

import java.util.function.Supplier;

/**
 * 腐化系物品注册表 —— 简化版移植。
 *
 * <p>材料 / 粗制工具 / 注射器 / 刷怪蛋；技能类物品（红矮星弓、文件系统、注毒军刀、七宗罪、
 * 芯片、调试器）按既定决策不移植。注射器暂为无功能物品，转化逻辑留待机制层补齐。</p>
 */
@SuppressWarnings("removal")
public class CorruptionItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HallMod.MODID);

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> egg(String name, Supplier<? extends EntityType<? extends Mob>> type) {
        return ITEMS.register(name + "_spawn_egg",
                () -> new ForgeSpawnEggItem(type, -1, -1, new Item.Properties()));
    }

    // ==================== 材料 ====================
    public static final RegistryObject<Item> INFECTION_BONE = simple("infection_bone");
    public static final RegistryObject<Item> HARD_FRAGMENTS = simple("hard_fragments");
    public static final RegistryObject<Item> CHITIN_POLYMER = simple("chitin_polymer");
    public static final RegistryObject<Item> LIVE_MEAT = simple("live_meat");
    public static final RegistryObject<Item> HOST_MEAT = simple("host_meat");
    public static final RegistryObject<Item> HOST_CARAPACE = simple("host_carapace");
    public static final RegistryObject<Item> HOST_TOOTH = simple("host_tooth");
    public static final RegistryObject<Item> DEGENERATE_HEART = simple("degenerate_heart");
    public static final RegistryObject<Item> LONG_ARM_BEAST_HEAD = simple("long_arm_beast_head");
    public static final RegistryObject<Item> LONG_ARM_BEAST_TOOTH = simple("long_arm_beast_tooth");
    public static final RegistryObject<Item> ACID_SAC = simple("acid_sac");
    public static final RegistryObject<Item> ACID_BALL = simple("acid_ball");

    // ==================== 注射器（占位，机制层后续补转化逻辑） ====================
    public static final RegistryObject<Item> SYRINGE = ITEMS.register("syringe",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SYRINGE_BLOODY_CONCENTRATE = ITEMS.register("syringe_bloody_concentrate",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // ==================== 粗制工具（简化：石级 Tier，配方/耐久等以后再调） ====================
    private static final Tier CRUDE_TIER = new Tier() {
        @Override public int getUses() { return 131; }
        @Override public float getSpeed() { return 4.0F; }
        @Override public float getAttackDamageBonus() { return 0.5F; }
        @Override public int getLevel() { return 1; }
        @Override public int getEnchantmentValue() { return 5; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.ROTTEN_FLESH); }
    };

    public static final RegistryObject<Item> CRUDE_SWORD = ITEMS.register("crude_sword",
            () -> new SwordItem(CRUDE_TIER, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> CRUDE_AXE = ITEMS.register("crude_axe",
            () -> new AxeItem(CRUDE_TIER, 6.0F, -3.2F, new Item.Properties()));
    public static final RegistryObject<Item> CRUDE_PICKAXE = ITEMS.register("crude_pickaxe",
            () -> new PickaxeItem(CRUDE_TIER, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> CRUDE_SHOVEL = ITEMS.register("crude_shovel",
            () -> new ShovelItem(CRUDE_TIER, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> CRUDE_HOE = ITEMS.register("crude_hoe",
            () -> new HoeItem(CRUDE_TIER, -2, -1.0F, new Item.Properties()));

    // ==================== 刷怪蛋 ====================
    public static final RegistryObject<Item> CORRUPTION_ZOMBIE_SPAWN_EGG = egg("corruption_zombie", CorruptionEntityTypes.CORRUPTION_ZOMBIE);
    public static final RegistryObject<Item> CORRUPTION_PLAYER_SPAWN_EGG = egg("corruption_player", CorruptionEntityTypes.CORRUPTION_PLAYER);
    public static final RegistryObject<Item> CORRUPTION_COW_SPAWN_EGG = egg("corruption_cow", CorruptionEntityTypes.CORRUPTION_COW);
    public static final RegistryObject<Item> CORRUPTION_SHEEP_SPAWN_EGG = egg("corruption_sheep", CorruptionEntityTypes.CORRUPTION_SHEEP);
    public static final RegistryObject<Item> CORRUPTION_PIG_SPAWN_EGG = egg("corruption_pig", CorruptionEntityTypes.CORRUPTION_PIG);
    public static final RegistryObject<Item> CORRUPTION_BAT_SPAWN_EGG = egg("corruption_bat", CorruptionEntityTypes.CORRUPTION_BAT);
    public static final RegistryObject<Item> CORRUPTION_SKELETON_SPAWN_EGG = egg("corruption_skeleton", CorruptionEntityTypes.CORRUPTION_SKELETON);
    public static final RegistryObject<Item> CORRUPTION_BONE_RUINS_SPAWN_EGG = egg("corruption_bone_ruins", CorruptionEntityTypes.CORRUPTION_BONE_RUINS);
    public static final RegistryObject<Item> CORRUPTION_VILLAGER_SPAWN_EGG = egg("corruption_villager", CorruptionEntityTypes.CORRUPTION_VILLAGER);
    public static final RegistryObject<Item> CORRUPTION_WOLF_SPAWN_EGG = egg("corruption_wolf", CorruptionEntityTypes.CORRUPTION_WOLF);
    public static final RegistryObject<Item> CORRUPTION_CHICKEN_SPAWN_EGG = egg("corruption_chicken", CorruptionEntityTypes.CORRUPTION_CHICKEN);
    public static final RegistryObject<Item> DEGENERATE_PIG_SPAWN_EGG = egg("degenerate_pig", CorruptionEntityTypes.DEGENERATE_PIG);
    public static final RegistryObject<Item> DEGENERATE_ZOMBIE_SPAWN_EGG = egg("degenerate_zombie", CorruptionEntityTypes.DEGENERATE_ZOMBIE);
    public static final RegistryObject<Item> DEGENERATE_VILLAGER_SPAWN_EGG = egg("degenerate_villager", CorruptionEntityTypes.DEGENERATE_VILLAGER);
    public static final RegistryObject<Item> DEGENERATE_COW_SPAWN_EGG = egg("degenerate_cow", CorruptionEntityTypes.DEGENERATE_COW);
    public static final RegistryObject<Item> GROVELER_SPAWN_EGG = egg("groveler", CorruptionEntityTypes.GROVELER);
    public static final RegistryObject<Item> ATTACKER_SPAWN_EGG = egg("attacker", CorruptionEntityTypes.ATTACKER);
    public static final RegistryObject<Item> BEARER_SPAWN_EGG = egg("bearer", CorruptionEntityTypes.BEARER);
    public static final RegistryObject<Item> LONG_ARM_BEAST_SPAWN_EGG = egg("long_arm_beast", CorruptionEntityTypes.LONG_ARM_BEAST);
    public static final RegistryObject<Item> SHOCKER_SPAWN_EGG = egg("shocker", CorruptionEntityTypes.SHOCKER);
    public static final RegistryObject<Item> HUMUS_COLUMN_SPAWN_EGG = egg("humus_column", CorruptionEntityTypes.HUMUS_COLUMN);
    public static final RegistryObject<Item> SUMMONING_PILLAR_SPAWN_EGG = egg("summoning_pillar", CorruptionEntityTypes.SUMMONING_PILLAR);
    public static final RegistryObject<Item> ORIGINAL_BUG_SPAWN_EGG = egg("original_bug", CorruptionEntityTypes.ORIGINAL_BUG);
}
