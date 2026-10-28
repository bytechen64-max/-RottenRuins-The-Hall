package org.bytechen.rottenruins.datagen.gen.lang;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataEN extends LanguageProvider {
    public LangDataEN(PackOutput output, String locale) {
        super(output, HallMod.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + HallMod.MODID + ".main", "Rotten Ruins: Splendiding");
        // Section header rows inside the creative tab (see SectionedCreativeModeTab / RegisterTab)
        add("itemGroup." + HallMod.MODID + ".main.section.creatures", "Infected & Hall Creatures");
        add("itemGroup." + HallMod.MODID + ".main.section.materials", "Hall Materials");
        add("itemGroup." + HallMod.MODID + ".main.section.gear", "Weapons & Tools");
        add("itemGroup." + HallMod.MODID + ".main.section.blocks", "Hall Building Blocks");
        add("itemGroup." + HallMod.MODID + ".main.section.misc", "Miscellaneous");
        add(RegisterItem.EXAMPLE_ITEM.get(), "Example Item");
        add(RegisterItem.VOID_SWORD.get(), "Void Sword");
        add(RegisterItem.CRIMSON_VOW.get(), "Crimson Vow");
        add(RegisterItem.SILENT_DAYLIGHT.get(), "Silent Daylight");
        add(RegisterItem.HALL_BONE_FRAGMENTS.get(), "Hall Bone Fragments");
        add(RegisterItem.HALL_TENDON.get(), "Hall Tendon");
        // 补充缺失条目：畸骸末影珍珠
        add(RegisterItem.INF_ENDER_PEAR.get(), "Infected Ender Pearl");
        add(RegisterItem.DOMITE_ORE.get(), "Domite Ore");
        add(RegisterItem.DOMERITE_ORE.get(), "Domerite Ore");
        add(RegisterItem.DOMITE_CRYSTAL.get(), "Domite Crystal");
        add(RegisterItem.DOMERITE_INGOT.get(), "Domerite Ingot");
        add(RegisterItem.DOMERITE_SWORD.get(), "Domerite Sword");
        add(RegisterItem.DOMERITE_PICKAXE.get(), "Domerite Pickaxe");
        add(RegisterItem.DOMERITE_AXE.get(), "Domerite Axe");
        add(RegisterItem.DOMERITE_SHOVEL.get(), "Domerite Shovel");
        add(RegisterItem.DOMERITE_HOE.get(), "Domerite Hoe");
        add(RegisterItem.ACID_ANOMALY_EXTRACT.get(), "Acid Anomaly Extract");
        add(RegisterItem.COLD_ANOMALY_EXTRACT.get(), "Cold Anomaly Extract");
        add(RegisterItem.HEAT_ANOMALY_EXTRACT.get(), "Heat Anomaly Extract");
        add(RegisterItem.INF_PLAYER_SPAWN_EGG.get(), "Infected Player Spawn Egg");
        add(RegisterItem.INF_ENDERMAN_SPAWN_EGG.get(), "Infected Enderman Spawn Egg");
        add(RegisterItem.INF_SKELETON_SPAWN_EGG.get(), "Infected Skeleton Spawn Egg");
        add(RegisterItem.BONECRUSHER_SPAWN_EGG.get(), "Hall Shell Spawn Egg");
        // 补充缺失条目：王庭重型轰炸刷怪蛋
        add(RegisterItem.HEAVY_BOMB_SPAWN_EGG.get(), "Heavy Bomb Spawn Egg");
        add(RegisterItem.COLLAPSAR_SPAWN_EGG.get(), "Collapsar Spawn Egg");
        add(RegisterItem.WINDING_COLUMN_SPAWN_EGG.get(), "Winding Column Spawn Egg");
// 物品：补缺失条目
        add(RegisterItem.BONECRUSHER_CORE.get(), "Bonecrusher Core");
        add(RegisterItem.DOMERITE_STICK.get(), "Domerite Stick");
        add(RegisterItem.DOMERITE_LONGSWORD.get(), "Skyfall Verdict");

        add(RegisterItem.SCOUT_SPAWN_EGG.get(), "Ulcerated Scout Spawn Egg");
        add(RegisterItem.PURSUER_SPAWN_EGG.get(), "Ulcerated Pursuer Spawn Egg");
        add(RegisterItem.MONOLITH_SPAWN_EGG.get(), "Ulcerated Monolith Spawn Egg");

// 实体：补缺失条目
        add(EntityTypeRegistry.COLLAPSAR.get(), "Collapsar");
        // Defensive creature: the Winding Column (immobile · grab · damage transfer · burrow ambush)
        add(EntityTypeRegistry.WINDING_COLUMN.get(), "Winding Column");
        // 方块
        add(RegisterBlock.HALL_GRASS_BLOCK.get(), "Hall Grass Block");
        add(RegisterBlock.HALL_DIRT.get(), "Hall Dirt");
        add(RegisterBlock.HALL_STONE.get(), "Hall Stone");
        add(RegisterBlock.HALL_LOG.get(), "Hall Log");
        add(RegisterBlock.HALL_LEAVES.get(), "Hall Leaves");
        add(RegisterBlock.HALL_PILLAR.get(), "Hall Pillar");
        add(RegisterBlock.HALL_PLANKS.get(), "Hall Planks");
        add(RegisterBlock.HALL_STAIRS.get(), "Hall Stairs");
        add(RegisterBlock.HALL_SLAB.get(), "Hall Slab");
        add(RegisterBlock.HALL_FENCE.get(), "Hall Fence");
        add(RegisterBlock.HALL_FENCE_GATE.get(), "Hall Fence Gate");
        add(RegisterBlock.HALL_DOOR.get(), "Hall Wooden Door");
        add(RegisterBlock.HALL_TRAPDOOR.get(), "Hall Wooden Trapdoor");
        add(RegisterBlock.HALL_BUTTON.get(), "Hall Button");
        add(RegisterBlock.HALL_PRESSURE_PLATE.get(), "Hall Pressure Plate");
        add(RegisterBlock.HALL_STONE_STAIRS.get(), "Hall Stone Stairs");
        add(RegisterBlock.HALL_STONE_SLAB.get(), "Hall Stone Slab");
        add(RegisterBlock.HALL_STONE_WALL.get(), "Hall Stone Wall");
        add(RegisterBlock.HALL_STONE_BUTTON.get(), "Hall Stone Button");
        add(RegisterBlock.HALL_STONE_PRESSURE_PLATE.get(), "Hall Stone Pressure Plate");
        add(RegisterBlock.DOMITE_MINERAL.get(), "Domite Ore Block");
        add(RegisterBlock.DOMERITE_MINERAL.get(), "Domerite Ore Block");
        add(RegisterBlock.HALL_VINE.get(), "Hall Vine");
        add(RegisterBlock.HALL_FLOWER.get(), "Hall Flower");
        add(RegisterBlock.HALL_GRASS.get(), "Hall Grass");
        add(RegisterBlock.HALL_SANDSTONE.get(), "Hall Sandstone");
        add(RegisterBlock.HALL_ASH_SAND.get(), "Hall Ash Sand");
        add(RegisterBlock.HALL_ASH_CUT_SANDSTONE.get(), "Hall Ash Cut Sandstone");
        add(RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get(), "Hall Ash Smooth Sandstone");
        add(RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get(), "Hall Ash Collapsed Chiseled Sandstone");
        add(RegisterBlock.HALL_ASH_CACTUS.get(), "Hall Ash Cactus");
        add(RegisterBlock.HALL_ASH_DEAD_BUSH.get(), "Hall Ash Dead Bush");

        // 实体
        add(EntityTypeRegistry.INF_PLAYER.get(), "Infected Player");
        add(EntityTypeRegistry.INF_ENDERMAN.get(), "Infected Enderman");
        add(EntityTypeRegistry.INF_SKELETON.get(), "Infected Skeleton");
        add(EntityTypeRegistry.INF_SKELETON_ARROW.get(), "Infected Skeleton Arrow");
        add(EntityTypeRegistry.HEAVY_BOMB.get(), "Heavy Bomb");
        add(EntityTypeRegistry.HEAVY_BOMB_TNT.get(), "Heavy Bomb TNT");
        add(EntityTypeRegistry.BONECRUSHER.get(), "Hall Shell");
        add(EntityTypeRegistry.SHOCKWAVE.get(), "Shockwave");
        add(EntityTypeRegistry.METEORITE.get(), "Meteorite");
        // Skyfall Verdict: the field's descending blade (a pure-VFX skill entity,
        // but it still gets a readable name for debugging)
        add(EntityTypeRegistry.VERDICT_SWORD_DROP.get(), "Verdict Blade");
        add(EntityTypeRegistry.SCOUT.get(), "Ulcerated Scout");
        add(EntityTypeRegistry.PURSUER.get(), "Ulcerated Pursuer");
        add(EntityTypeRegistry.MONOLITH.get(), "Ulcerated Monolith");

        // 状态效果
        add(RegisterEffect.ACID_ANOMALY_ADAPTATION.get(), "Acid Anomaly Adaptation");
        add(RegisterEffect.COLD_ANOMALY_ADAPTATION.get(), "Cold Anomaly Adaptation");
        add(RegisterEffect.HEAT_ANOMALY_ADAPTATION.get(), "Heat Anomaly Adaptation");
        add(RegisterEffect.VERDICT.get(), "Verdict");
        // Skyfall Verdict: action-bar hint when right-clicking during cooldown
        add("item.rotten_ruins.domerite_longsword.cooldown", "Verdict not ready (%s s)");
        // Skyfall Verdict: pitch too low for the beam (this used to be completely silent)
        add("item.rotten_ruins.domerite_longsword.no_aim", "Not aimed — look up to bring down the verdict");
        // Skyfall Verdict: the skill hit nothing (a whiff only records a short cooldown)
        add("item.rotten_ruins.domerite_longsword.missed", "Whiff — cooldown refunded");

        // 难度选择 UI
        add(TranslateUtils.DIFFICULTY_EASY, "Easy");
        add(TranslateUtils.DIFFICULTY_NORMAL, "Normal");
        add(TranslateUtils.DIFFICULTY_HARD, "Hard");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, "Incomprehensible");
        add(TranslateUtils.DIFFICULTY_EASY_TOOLTIP, "For new players — weaker anomalous creatures, abundant resources");
        add(TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP, "Standard experience — balanced anomaly threats and resource distribution");
        add(TranslateUtils.DIFFICULTY_HARD_TOOLTIP, "Higher damage, faster spread — for those who seek a real challenge");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP, "All laws collapse, all order dissolves. Are you sure you can comprehend this?");
        add(TranslateUtils.GUI_DIFFICULTY_SELECT_TITLE, "Select Difficulty");
        add(TranslateUtils.GUI_DIFFICULTY_SELECT_HINT, "Choose the world difficulty (can be changed later via commands)");

        // Tooltip copy (placeholder, pending final wording)
        // Shared mechanic lines live once (several blocking weapons share them);
        // each item only owns its own flavour. A "label + body" line is split into
        // two keys because the per-character gradient flattens a line via getString()
        // (see client.rend.text.TooltipLines).
        // %% is vanilla TranslatableContents' escape (see its FORMAT_PATTERN block).
        add(TranslateUtils.TOOLTIP_LABEL_BLOCK, "Block");
        add(TranslateUtils.TOOLTIP_BLOCK, "Hold right-click to raise the blade · frontal attacks only");
        add(TranslateUtils.TOOLTIP_TRAIT, "Unbreakable · Fireproof · Returned after crafting");
        add(TranslateUtils.TOOLTIP_BAR_LABEL, "Block mitigation");
        add(TranslateUtils.TOOLTIP_BAR_VALUE, "%s%%");

        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LORE, "\"By blood I vow: this blade shall not break.\"");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_TRAIT, "Vow");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_LABEL_SIGIL, "Oath");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_SIGIL,
                "Sneak + right-click to raise the blade · when fully charged, unfurl a crimson sigil where you look (20s)");
        add(TranslateUtils.CRIMSON_VOW_COOLDOWN, "The oath is not yet ready (%s s)");
        add(TranslateUtils.CRIMSON_VOW_TOOLTIP_DEBUG,
                "Debug · block resolves x%s%% · frontal only %s · use duration %s ticks · entity reach +%s");

        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LORE, "\"Noon is silent; only cold water remains.\"");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TIDE, "Tide");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_TIDE, "The blade lies under a layer of flowing water");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_TRAIT, "Daylight");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                "Debug · block resolves x%s%% · frontal only %s · use duration %s ticks");

        // 血肉庭园维度 (hall:heall)
        // 地形复用现有王庭方块，因此这里只有通道方块的译名
        add(RegisterBlock.FLESH_RIFT.get(), "Flesh Rift");
        add("dimension." + HallMod.MODID + ".heall", "Carrion Marrow");
        add("biome." + HallMod.MODID + ".flesh_marrow", "Carrion Marrow");
        add("block." + HallMod.MODID + ".flesh_rift.no_dimension", "The other end of the rift has not yet formed...");

        // ---- Magic attributes (see RegisterAttributes) ----
        // Keys must match the strings hard-coded in the RangedAttribute constructors exactly.
        add("attribute.name." + HallMod.MODID + ".max_mana", "Max Mana");
        add("attribute.name." + HallMod.MODID + ".mana_regeneration", "Mana Regeneration");
        add("attribute.name." + HallMod.MODID + ".mana_restore", "Mana Restored");
        add("attribute.name." + HallMod.MODID + ".spell_power", "Spell Power");
        add("attribute.name." + HallMod.MODID + ".magic_slots", "Magic Slots");

        // ---- Magic system · items ----
        // Mana plates: one per school, plain materials for now (see RegisterItem —
        // the texture is "water" while MagicType has ACE in that slot; no mapping yet).
        add(RegisterItem.DEATH_MANA_PLATE.get(), "Death Mana Plate");
        add(RegisterItem.FIRE_MANA_PLATE.get(), "Fire Mana Plate");
        add(RegisterItem.LIGHT_MANA_PLATE.get(), "Light Mana Plate");
        add(RegisterItem.NATURE_MANA_PLATE.get(), "Nature Mana Plate");
        add(RegisterItem.VOID_MANA_PLATE.get(), "Void Mana Plate");
        add(RegisterItem.WATER_MANA_PLATE.get(), "Water Mana Plate");
        add(RegisterItem.MAGIC_CATA.get(), "Magic Catalyst");
        add(RegisterItem.VOID_RUNE.get(), "Void Rune");
        add(RegisterItem.WAND.get(), "Wand");
        add(RegisterItem.WAND_NATURE.get(), "Nature Wand");

        // ---- Sound subtitles (see SoundData) ----
        // Body-explosion sound: same subtitle text as corruption's rip_body, hall namespace.
        add("subtitles." + HallMod.MODID + ".rip_body", "Body rips apart");

        // ====================================================================
        // ---- Simplified corruption content (migrated from the corruption mod) ----
        // The block/item/entity/effect registrations are not in this project yet, so
        // the translation keys are registered directly as "<type>.rotten_ruins.<id>"
        // strings. Once the content is migrated the keys match the registry IDs and
        // runData will keep them.
        // ====================================================================

        // ---- A. Blocks: block.rotten_ruins.<id> (21) ----
        add("block." + HallMod.MODID + ".spread_dirt", "Corrupted Dirt");
        add("block." + HallMod.MODID + ".spread_stone", "Corrupted Stone");
        add("block." + HallMod.MODID + ".infection_cobblestone_block", "Corrupted Cobblestone");
        add("block." + HallMod.MODID + ".spread_infection_leaves", "Corrupted Leaves");
        // corruption registers this block as spread_infection_wood but names it in neither lang file
        add("block." + HallMod.MODID + ".spread_infection_wood", "Corrupted Log");
        add("block." + HallMod.MODID + ".corruption_grass", "Corrupted Short Grass");
        add("block." + HallMod.MODID + ".corruption_trachea", "Respiratory Tract");
        add("block." + HallMod.MODID + ".connect_block", "Connective Tissue Block");
        add("block." + HallMod.MODID + ".decidual_tissue_block", "Decidual Tissue Block");
        // corruption has no English name for fleshbed either
        add("block." + HallMod.MODID + ".fleshbed", "Fleshbed");
        add("block." + HallMod.MODID + ".degenerate_remains_small", "Small Degenerate Remains");
        add("block." + HallMod.MODID + ".degenerate_remains_medium", "Medium Degenerate Remains");
        add("block." + HallMod.MODID + ".degenerate_remains_large", "Large Degenerate Remains");
        add("block." + HallMod.MODID + ".host_remains_small", "Small Assimilated Remains");
        add("block." + HallMod.MODID + ".host_remains_medium", "Medium Assimilated Remains");
        add("block." + HallMod.MODID + ".host_remains_large", "Large Assimilated Remains");
        add("block." + HallMod.MODID + ".refactored_remains_small", "Small Refactored Remains");
        add("block." + HallMod.MODID + ".refactored_remains_medium", "Medium Refactored Remains");
        add("block." + HallMod.MODID + ".refactored_remains_large", "Large Refactored Remains");
        add("block." + HallMod.MODID + ".variable_height_crop", "Corrupted Meridian");
        add("block." + HallMod.MODID + ".spiracle_block", "Living Spiracle");

        // ---- B. Items: item.rotten_ruins.<id> (42 = 14 materials + 5 tools + 23 spawn eggs) ----
        // Materials
        add("item." + HallMod.MODID + ".infection_bone", "Infected Bone");
        add("item." + HallMod.MODID + ".hard_fragments", "Hardened Fragments");
        // corruption has no English name for chitin_polymer either
        add("item." + HallMod.MODID + ".chitin_polymer", "Chitin Polymer");
        add("item." + HallMod.MODID + ".live_meat", "Living Flesh");
        add("item." + HallMod.MODID + ".host_meat", "Assimilated Flesh");
        add("item." + HallMod.MODID + ".host_carapace", "Assimilated Carapace");
        add("item." + HallMod.MODID + ".host_tooth", "Assimilated Tooth");
        add("item." + HallMod.MODID + ".degenerate_heart", "Degenerate Heart");
        add("item." + HallMod.MODID + ".long_arm_beast_head", "Longibrachiate Head");
        add("item." + HallMod.MODID + ".long_arm_beast_tooth", "Longibrachiate Tooth");
        add("item." + HallMod.MODID + ".acid_sac", "Acid Sac");
        add("item." + HallMod.MODID + ".acid_ball", "Acid Ball");
        add("item." + HallMod.MODID + ".syringe", "Syringe");
        add("item." + HallMod.MODID + ".syringe_bloody_concentrate", "Concentrated Blood Syringe");
        // Tools
        add("item." + HallMod.MODID + ".crude_axe", "Crude Axe");
        add("item." + HallMod.MODID + ".crude_hoe", "Crude Hoe");
        add("item." + HallMod.MODID + ".crude_pickaxe", "Crude Pickaxe");
        add("item." + HallMod.MODID + ".crude_shovel", "Crude Shovel");
        add("item." + HallMod.MODID + ".crude_sword", "Crude Sword");
        // Spawn eggs (key follows the entity id, value is "<entity name> Spawn Egg")
        add("item." + HallMod.MODID + ".corruption_zombie_spawn_egg", "Assimilated Zombie Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_player_spawn_egg", "Assimilated Player Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_cow_spawn_egg", "Assimilated Cow Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_sheep_spawn_egg", "Assimilated Sheep Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_pig_spawn_egg", "Assimilated Pig Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_bat_spawn_egg", "Assimilated Bat Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_skeleton_spawn_egg", "Assimilated Skeleton Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_bone_ruins_spawn_egg", "Skeletal Remains Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_villager_spawn_egg", "Assimilated Villager Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_wolf_spawn_egg", "Assimilated Wolf Spawn Egg");
        add("item." + HallMod.MODID + ".corruption_chicken_spawn_egg", "Assimilated Chicken Spawn Egg");
        add("item." + HallMod.MODID + ".degenerate_pig_spawn_egg", "Degenerate Pig Spawn Egg");
        add("item." + HallMod.MODID + ".degenerate_zombie_spawn_egg", "Degenerate Zombie Spawn Egg");
        add("item." + HallMod.MODID + ".degenerate_villager_spawn_egg", "Degenerate Villager Spawn Egg");
        add("item." + HallMod.MODID + ".degenerate_cow_spawn_egg", "Degenerate Cow Spawn Egg");
        add("item." + HallMod.MODID + ".groveler_spawn_egg", "Crawlfex Spawn Egg");
        add("item." + HallMod.MODID + ".attacker_spawn_egg", "Attacker Spawn Egg");
        add("item." + HallMod.MODID + ".bearer_spawn_egg", "Gestaltomb Spawn Egg");
        add("item." + HallMod.MODID + ".long_arm_beast_spawn_egg", "Longibrachiate Spawn Egg");
        add("item." + HallMod.MODID + ".shocker_spawn_egg", "Shocker Spawn Egg");
        add("item." + HallMod.MODID + ".humus_column_spawn_egg", "Stage I Humus Column Spawn Egg");
        add("item." + HallMod.MODID + ".summoning_pillar_spawn_egg", "Stage I Summoning Pillar Spawn Egg");
        add("item." + HallMod.MODID + ".original_bug_spawn_egg", "Primordial Worm Spawn Egg");

        // ---- C. Entities: entity.rotten_ruins.<id> (23) ----
        add("entity." + HallMod.MODID + ".corruption_zombie", "Assimilated Zombie");
        add("entity." + HallMod.MODID + ".corruption_player", "Assimilated Player");
        add("entity." + HallMod.MODID + ".corruption_cow", "Assimilated Cow");
        add("entity." + HallMod.MODID + ".corruption_sheep", "Assimilated Sheep");
        // corruption has no entity name for the pig/skeleton, only their spawn eggs
        add("entity." + HallMod.MODID + ".corruption_pig", "Assimilated Pig");
        add("entity." + HallMod.MODID + ".corruption_bat", "Assimilated Bat");
        add("entity." + HallMod.MODID + ".corruption_skeleton", "Assimilated Skeleton");
        add("entity." + HallMod.MODID + ".corruption_bone_ruins", "Skeletal Remains");
        add("entity." + HallMod.MODID + ".corruption_villager", "Assimilated Villager");
        add("entity." + HallMod.MODID + ".corruption_wolf", "Assimilated Wolf");
        add("entity." + HallMod.MODID + ".corruption_chicken", "Assimilated Chicken");
        add("entity." + HallMod.MODID + ".degenerate_pig", "Degenerate Pig");
        add("entity." + HallMod.MODID + ".degenerate_zombie", "Degenerate Zombie");
        add("entity." + HallMod.MODID + ".degenerate_villager", "Degenerate Villager");
        add("entity." + HallMod.MODID + ".degenerate_cow", "Degenerate Cow");
        add("entity." + HallMod.MODID + ".groveler", "Crawlfex");
        add("entity." + HallMod.MODID + ".attacker", "Attacker");
        add("entity." + HallMod.MODID + ".bearer", "Gestaltomb");
        add("entity." + HallMod.MODID + ".long_arm_beast", "Longibrachiate");
        add("entity." + HallMod.MODID + ".shocker", "Shocker");
        add("entity." + HallMod.MODID + ".humus_column", "Stage I Humus Column");
        add("entity." + HallMod.MODID + ".summoning_pillar", "Stage I Summoning Pillar");
        add("entity." + HallMod.MODID + ".original_bug", "Primordial Worm");

        // ---- D. Effects: effect.rotten_ruins.<id> (6) ----
        add("effect." + HallMod.MODID + ".corruption", "Corruption");
        add("effect." + HallMod.MODID + ".lysis", "Lysis");
        add("effect." + HallMod.MODID + ".miasma", "Despair");
        add("effect." + HallMod.MODID + ".sclerosis", "Sclerosis");
        add("effect." + HallMod.MODID + ".bleeding", "Bleeding");
        add("effect." + HallMod.MODID + ".fear", "Fear");
    }
}