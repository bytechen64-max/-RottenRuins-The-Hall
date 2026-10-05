package org.bytechen.hall.datagen.gen.lang;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.RegisterEffect;
import org.bytechen.hall.overworld.registry.RegisterItem;
import org.bytechen.hall.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataEN extends LanguageProvider {
    public LangDataEN(PackOutput output, String locale) {
        super(output, HallMod.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + HallMod.MODID + ".main", "Rotten Ruins: Splendiding");
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
// 物品：补缺失条目
        add(RegisterItem.BONECRUSHER_CORE.get(), "Bonecrusher Core");
        add(RegisterItem.DOMERITE_STICK.get(), "Domerite Stick");
        add(RegisterItem.DOMERITE_LONGSWORD.get(), "Skyfall Verdict");

        add(RegisterItem.SCOUT_SPAWN_EGG.get(), "Ulcerated Scout Spawn Egg");
        add(RegisterItem.PURSUER_SPAWN_EGG.get(), "Ulcerated Pursuer Spawn Egg");
        add(RegisterItem.MONOLITH_SPAWN_EGG.get(), "Ulcerated Monolith Spawn Egg");

// 实体：补缺失条目
        add(EntityTypeRegistry.COLLAPSAR.get(), "Collapsar");
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
        add("item.hall.domerite_longsword.cooldown", "Verdict not ready (%s s)");
        // Skyfall Verdict: pitch too low for the beam (this used to be completely silent)
        add("item.hall.domerite_longsword.no_aim", "Not aimed — look up to bring down the verdict");
        // Skyfall Verdict: the skill hit nothing (a whiff only records a short cooldown)
        add("item.hall.domerite_longsword.missed", "Whiff — cooldown refunded");

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
    }
}