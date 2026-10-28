package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 模组的创造模式标签。
 *
 * <p>物品表按 {@link SectionedCreativeModeTab 分区}声明：每个分区在网格里以一行
 * 流动色带 + 居中彩色标题开头，分区之间自动补空到整行。</p>
 *
 * <p>合并后内容分 6 类：刷怪蛋 / 材料 / 魔法 / 武器与工具 / 方块 / 其它。
 * 每类里先列王庭（原 Hall）内容，再列腐化（原 corruption）内容。分区内部用物品 id
 * 引用（经 {@link #item} 在注册表里查找），后续加内容只需要在对应列表里补一行 id，
 * 不需要回到注册类拿 {@code RegistryObject} 字段。</p>
 *
 * <p>最后一个 {@code misc} 分区是兜底：遍历本模组命名空间、按 {@link Item} 去重
 * （先声明的分区赢），保证以后新加的物品即使忘了登记也至少能在创造栏看到。</p>
 */
public class RegisterTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HallMod.MODID);

    private static Component section(String key) {
        return Component.translatable("itemGroup." + HallMod.MODID + ".main.section." + key);
    }

    /** 按 id 从注册表取物品并放入当前分区；找不到时打错误日志并跳过（不崩游戏）。 */
    private static void item(CreativeModeTab.Output output, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(HallMod.MODID, id));
        if (item == null) {
            HallMod.LOGGER.error("[CreativeTab] 物品不存在，已跳过: {}", id);
            return;
        }
        output.accept(item);
    }

    private static void items(CreativeModeTab.Output output, String... ids) {
        for (String id : ids) {
            item(output, id);
        }
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            HallMod.MODID + "_tab",
            () -> SectionedCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HallMod.MODID + ".main"))
                    .icon(() -> new ItemStack(RegisterItem.EXAMPLE_ITEM.get()))

                    // ─────────────────────────── 刷怪蛋（33） ───────────────────────────
                    .section(section("creatures"), (params, output) -> {
                        // 王庭系（原 Hall，畸骸种 hall_*）
                        items(output,
                                "hall_player_spawn_egg",
                                "hall_enderman_spawn_egg",
                                "hall_skeleton_spawn_egg",
                                "scout_spawn_egg",
                                "pursuer_spawn_egg",
                                "monolith_spawn_egg",
                                "bonecrusher_spawn_egg",
                                "heavy_bomb_spawn_egg",
                                "collapsar_spawn_egg",
                                "winding_column_spawn_egg");
                        // 腐化系（原 corruption，同化种 corruption_*）
                        items(output,
                                "corruption_zombie_spawn_egg",
                                "corruption_skeleton_spawn_egg",
                                "corruption_player_spawn_egg",
                                "corruption_villager_spawn_egg",
                                "corruption_cow_spawn_egg",
                                "corruption_sheep_spawn_egg",
                                "corruption_pig_spawn_egg",
                                "corruption_chicken_spawn_egg",
                                "corruption_wolf_spawn_egg",
                                "corruption_bat_spawn_egg",
                                "corruption_bone_ruins_spawn_egg");
                        items(output,
                                "degenerate_pig_spawn_egg",
                                "degenerate_zombie_spawn_egg",
                                "degenerate_villager_spawn_egg",
                                "degenerate_cow_spawn_egg",
                                "groveler_spawn_egg",
                                "attacker_spawn_egg",
                                "bearer_spawn_egg",
                                "long_arm_beast_spawn_egg",
                                "shocker_spawn_egg",
                                "humus_column_spawn_egg",
                                "summoning_pillar_spawn_egg",
                                "original_bug_spawn_egg");
                    })

                    // ─────────────────────────── 材料（23） ───────────────────────────
                    .section(section("materials"), (params, output) -> {
                        // 王庭材料
                        items(output,
                                "hall_tendon",
                                "hall_bone_fragments",
                                "bonecrusher_core",
                                "domite_ore",
                                "domite_crystal",
                                "domerite_ore",
                                "domerite_ingot",
                                "domerite_stick",
                                "heat_anomaly_extract",
                                "cold_anomaly_extract",
                                "acid_anomaly_extract");
                        // 腐化材料
                        items(output,
                                "infection_bone",
                                "hard_fragments",
                                "chitin_polymer",
                                "host_carapace",
                                "host_tooth",
                                "host_meat",
                                "live_meat",
                                "degenerate_heart",
                                "long_arm_beast_head",
                                "long_arm_beast_tooth",
                                "acid_sac",
                                "acid_ball");
                    })

                    // ─────────────────────────── 魔法（10） ───────────────────────────
                    .section(section("magic"), (params, output) -> {
                        // 法力板（六系）
                        items(output,
                                "light_mana_plate",
                                "fire_mana_plate",
                                "water_mana_plate",
                                "nature_mana_plate",
                                "void_mana_plate",
                                "death_mana_plate");
                        // 施法媒介 / 催化剂
                        items(output,
                                "magic_cata",
                                "void_rune",
                                "wand",
                                "wand_nature");
                    })

                    // ─────────────────────── 武器与工具（16） ───────────────────────
                    .section(section("gear"), (params, output) -> {
                        // 王庭武器与工具
                        items(output,
                                "domerite_sword",
                                "domerite_longsword",
                                "domerite_axe",
                                "domerite_pickaxe",
                                "domerite_shovel",
                                "domerite_hoe",
                                "void_sword",
                                "crimson_vow",
                                "silent_daylight");
                        // 腐化工具与注射器
                        items(output,
                                "crude_sword",
                                "crude_axe",
                                "crude_pickaxe",
                                "crude_shovel",
                                "crude_hoe",
                                "syringe",
                                "syringe_bloody_concentrate");
                    })

                    // ─────────────────────── 方块（54） ───────────────────────
                    .section(section("blocks"), (params, output) -> {
                        // 王庭：地表 / 石系
                        items(output,
                                "hall_grass_block",
                                "hall_dirt",
                                "hall_stone",
                                "hall_stone_stairs",
                                "hall_stone_slab",
                                "hall_stone_wall",
                                "hall_stone_button",
                                "hall_stone_pressure_plate",
                                "hall_sandstone",
                                "hall_ash_sand",
                                "hall_ash_cut_sandstone",
                                "hall_ash_smooth_sandstone",
                                "hall_ash_collapsed_chiseled_sandstone",
                                "hall_ash_cactus",
                                "hall_ash_dead_bush");
                        // 王庭：木系 / 植被 / 矿石
                        items(output,
                                "hall_log",
                                "hall_leaves",
                                "hall_pillar",
                                "hall_planks",
                                "hall_stairs",
                                "hall_slab",
                                "hall_fence",
                                "hall_fence_gate",
                                "hall_door",
                                "hall_trapdoor",
                                "hall_button",
                                "hall_pressure_plate",
                                "hall_vine",
                                "hall_grass",
                                "hall_flower",
                                "domite_mineral",
                                "domerite_mineral",
                                "flesh_rift");
                        // 腐化：地表 / 组织
                        items(output,
                                "spread_dirt",
                                "spread_stone",
                                "infection_cobblestone_block",
                                "spread_infection_leaves",
                                "spread_infection_wood",
                                "corruption_grass",
                                "corruption_trachea",
                                "connect_block",
                                "decidual_tissue_block",
                                "fleshbed",
                                "variable_height_crop",
                                "spiracle_block");
                        // 腐化：遗骸（腐化 / 同化 / 重构 × 小/中/大）
                        items(output,
                                "degenerate_remains_small",
                                "degenerate_remains_medium",
                                "degenerate_remains_large",
                                "host_remains_small",
                                "host_remains_medium",
                                "host_remains_large",
                                "refactored_remains_small",
                                "refactored_remains_medium",
                                "refactored_remains_large");
                    })

                    // ─────────────────────── 其它（兜底） ───────────────────────
                    .section(section("misc"), (params, output) -> {
                        // 先放已经明确归入"其它"的物品
                        items(output, "hall_ender_pear");
                        // 再兜底收本命名空间里尚未归类的物品（example_item 是占位图标，不展示）
                        Set<Item> claimed = new LinkedHashSet<>();
                        ForgeRegistries.ITEMS.getValues().stream()
                                .filter(item -> item.builtInRegistryHolder().key().location()
                                        .getNamespace().equals(HallMod.MODID))
                                .filter(item -> item != RegisterItem.EXAMPLE_ITEM.get())
                                .forEach(claimed::add);
                        claimed.forEach(output::accept);
                    })

                    .build()
    );
}
