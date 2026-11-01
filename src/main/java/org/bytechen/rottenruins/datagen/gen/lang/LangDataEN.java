package org.bytechen.rottenruins.datagen.gen.lang;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.datagen.gen.AdvancementProviderData;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionItems;
import org.bytechen.rottenruins.utils.TranslateUtils;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class LangDataEN extends LanguageProvider {
    public LangDataEN(PackOutput output, String locale) {
        super(output, HallMod.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        // Creative tab label / panel title: flowing gradient (§6 fallback keeps it gold when effects are off).
        add("itemGroup." + HallMod.MODID + ".main",
                "§6§[rrs:grad:#6FBF4A:#8B1E2D:speed=0.6]Rotten Ruins§[/]");
        // Section header rows inside the creative tab (see SectionedCreativeModeTab / RegisterTab)
        add("itemGroup." + HallMod.MODID + ".main.section.creatures", "Spawn Eggs");
        add("itemGroup." + HallMod.MODID + ".main.section.materials", "Materials");
        add("itemGroup." + HallMod.MODID + ".main.section.magic", "Magic");
        add("itemGroup." + HallMod.MODID + ".main.section.gear", "Weapons & Tools");
        add("itemGroup." + HallMod.MODID + ".main.section.blocks", "Blocks");
        add("itemGroup." + HallMod.MODID + ".main.section.misc", "Miscellaneous");
        add(RegisterItem.EXAMPLE_ITEM.get(), "Example Item");
        add(RegisterItem.VOID_SWORD.get(), "Void Sword");
        add(RegisterItem.CRIMSON_VOW.get(), "Crimson Vow");
        add(RegisterItem.SILENT_DAYLIGHT.get(), "Silent Daylight");
        add(RegisterItem.HALL_BONE_FRAGMENTS.get(), "Hall Bone Fragments");
        add(RegisterItem.HALL_TENDON.get(), "Hall Tendon");
        // 补充缺失条目：畸骸末影珍珠
        add(RegisterItem.INF_ENDER_PEAR.get(), "§[rrs:glitch:amp=2.0]Infected Ender Pearl§[/]");
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
        add(EntityTypeRegistry.HEARTHSTONE.get(), "Hearthstone");
        add(EntityTypeRegistry.DEATH_HUNTER.get(), "Death Hunter");
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
        // The "Incomprehensible" tier name is drawn as a single Component by
        // DifficultySelectScreen, so effect markers are safe here (and degrade to plain text).
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, "§[rrs:glitch:amp=1.4]Incomprehensible§[/]");
        add(TranslateUtils.DIFFICULTY_EASY_TOOLTIP, "For new players — weaker anomalous creatures, abundant resources");
        add(TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP, "Standard experience — balanced anomaly threats and resource distribution");
        // Difficulty tooltip lines are drawn as a single Component (DifficultySelectScreen), so they may
        // carry markers. Do NOT add markers to lore lines that go through FlowingNameColors / TooltipLines:
        // their per-character rebuild would split the markers across sibling components and show them raw.
        add(TranslateUtils.DIFFICULTY_HARD_TOOLTIP, "§[rrs:shake:amp=1.0]Higher damage, faster spread — for those who seek a real challenge§[/]");
        add(TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP, "§[rrs:glitch:amp=2.0]All laws collapse, all order dissolves. Are you sure you can comprehend this?§[/]");
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
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_SLASH, "Slash");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_SLASH,
                "Any left-click hit adds %s long blue-white slashes · each deals %s frost damage + %s%% of max health to everything within %s blocks");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_LABEL_WARD, "Frost");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_WARD,
                "Raising the blade opens a %s-block frost field · heals you, freezes creatures and sweeps projectiles away");
        add(TranslateUtils.SILENT_DAYLIGHT_TOOLTIP_DEBUG,
                "Debug · block resolves x%s%% · frontal only %s · use duration %s ticks");

        // 血肉庭园维度 (hall:heall)
        // 地形复用现有王庭方块，因此这里只有通道方块的译名
        add(RegisterBlock.FLESH_RIFT.get(), "Flesh Rift");
        // Death core: the first block that renders a mask shader layer via a BER
        add(RegisterBlock.DEATH_CORE.get(), "Death Core");
        add("dimension." + HallMod.MODID + ".heall", "Carrion Marrow");
        add("biome." + HallMod.MODID + ".flesh_marrow", "Carrion Marrow");
        add(RegisterBlock.FLESH_RIFT.get().getDescriptionId() + ".no_dimension", "The other end of the rift has not yet formed...");

        // ---- Magic attributes (see RegisterAttributes) ----
        // ---- Magic: school names + item tooltips (see MagicType / MagicTooltips) ----
        add("magic_type." + HallMod.MODID + ".water", "Water");
        add("magic_type." + HallMod.MODID + ".fire", "Fire");
        add("magic_type." + HallMod.MODID + ".death", "Death");
        add("magic_type." + HallMod.MODID + ".nature", "Nature");
        add("magic_type." + HallMod.MODID + ".light", "Light");
        add("magic_type." + HallMod.MODID + ".void", "Void");
        add("magic_type." + HallMod.MODID + ".mysterious", "Mysterious");
        add("tooltip." + HallMod.MODID + ".magic.type", "Type: %s");
        add("tooltip." + HallMod.MODID + ".magic.cooldown", "Cooldown: %s s");
        add("tooltip." + HallMod.MODID + ".magic.cooldown.none", "Cooldown: none");
        add("tooltip." + HallMod.MODID + ".magic.mana", "Cost: %s mana");
        add("tooltip." + HallMod.MODID + ".magic.mana.none", "Cost: none");
        add("tooltip." + HallMod.MODID + ".magic.keep_cost", "Upkeep: %s mana/s");

        // Keys must match the strings hard-coded in the RangedAttribute constructors exactly.
        add("attribute.name." + HallMod.MODID + ".max_mana", "Max Mana");        add("attribute.name." + HallMod.MODID + ".mana_regeneration", "Mana Regeneration");
        add("attribute.name." + HallMod.MODID + ".mana_restore", "Mana Restored");
        add("attribute.name." + HallMod.MODID + ".spell_power", "Spell Power");
        add("attribute.name." + HallMod.MODID + ".magic_slots", "Magic Slots");
        add("attribute.name." + HallMod.MODID + ".cooldown_reduction", "Cooldown Reduction");
        // Per-school spell power: ADDS to the general spell power (default 0).
        // Keys must match RegisterAttributes.spellPowerOf(String) exactly.
        add("attribute.name." + HallMod.MODID + ".spell_power_water", "Water Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_fire", "Fire Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_death", "Death Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_nature", "Nature Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_light", "Light Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_void", "Void Spell Power");
        add("attribute.name." + HallMod.MODID + ".spell_power_mysterious", "Mysterious Spell Power");

        // ---- Magic system · items ----
        // Mana plates: one per school.
        add(RegisterItem.DEATH_MANA_PLATE.get(), "Death Mana Plate");
        add(RegisterItem.FIRE_MANA_PLATE.get(), "Fire Mana Plate");
        add(RegisterItem.LIGHT_MANA_PLATE.get(), "Light Mana Plate");
        add(RegisterItem.NATURE_MANA_PLATE.get(), "Nature Mana Plate");
        add(RegisterItem.VOID_MANA_PLATE.get(), "Void Mana Plate");
        add(RegisterItem.WATER_MANA_PLATE.get(), "Water Mana Plate");
        add(RegisterItem.MYSTERIOUS_MANA_PLATE.get(), "Mysterious Mana Plate");
        add(RegisterItem.MAGIC_CATA.get(), "Magic Catalyst");
        add(RegisterItem.VOID_RUNE.get(), "Void Rune");
        add(RegisterItem.WAND.get(), "Empty Wand");
        add(RegisterItem.WAND_NATURE.get(), "Nature Wand");
        add(RegisterItem.WAND_FIRE.get(), "Fire Wand");
        add(RegisterItem.WAND_DEATH.get(), "Death Wand");
        add(RegisterItem.WAND_LIGHT.get(), "Light Wand");
        add(RegisterItem.WAND_VOID.get(), "Void Wand");
        add(RegisterItem.WAND_WATER.get(), "Water Wand");
        add(RegisterItem.WAND_MYSTERIOUS.get(), "Mysterious Wand");
        add(RegisterItem.WAND_COMMON.get(), "Common Spell Wand");
        add("enchantment.rotten_ruins.mana_efficiency", "Mana Efficiency");
        add("tooltip.rotten_ruins.wand_mana", "Wand Mana: %s / %s");
        // Spells (upstream 1.20.1forge "magic update")
        add(RegisterItem.FIRE_BALL.get(), "Fireball");
        add(RegisterItem.SPEAR_OF_JUDGMENT.get(), "Spear of Judgment");
        add(RegisterItem.SUPPRESSION_FIELD.get(), "Suppression Field");
        add(RegisterItem.TIDAL_PULSE.get(), "Tidal Pulse");
        add(RegisterItem.BRIAR_SNARE.get(), "Briar Snare");
        add(RegisterItem.PURIFYING_LIGHT.get(), "Purifying Light");
        add(RegisterItem.ARCANE_STEP.get(), "Arcane Step");
        add(RegisterItem.SPRINT.get(), "Sprint");
        add("item.rotten_ruins.tidal_pulse.desc", "Pushes creatures back; extinguishes you and grants fire and water protection");
        add("item.rotten_ruins.briar_snare.desc", "Entangles creatures near your crosshair with weakness and poison");
        add("item.rotten_ruins.purifying_light.desc", "Heals, cleanses and shields allies; harms hostiles, especially undead");
        add("item.rotten_ruins.arcane_step.desc", "Blink to solid ground near your crosshair and gain a short speed boost");
        add("item.rotten_ruins.sprint.desc", "Dashes along your view direction; momentum equals a 5 x spell power block vertical lift (max 30) multiplied by 3, with sparks; half cooldown on a special use");
        // Death Star: nether star + wither roses
        add(RegisterItem.DEATH_STAR.get(), "Death Star");

        // ---- Spell constellation GUI / key bind ----
        add("key.categories." + HallMod.MODID, "Rotten Ruins");
        add("key." + HallMod.MODID + ".open_magic", "Open Spell Constellation");
        add("rotten_ruins.gui.magic.title", "Spell Constellation");
        add("rotten_ruins.gui.magic.inventory", "Spells in Inventory");
        add("rotten_ruins.gui.magic.empty", "No spells");
        add("rotten_ruins.gui.magic.hint", "Drag a spell into a slot · Click a slot to select");
        add("rotten_ruins.gui.magic.slots", "Slots %s / %s");
        add("rotten_ruins.gui.magic.kind.active", "Active spell");
        add("rotten_ruins.gui.magic.kind.passive", "Passive spell");
        add("rotten_ruins.gui.magic.mana", "Mana %s");
        add("rotten_ruins.gui.magic.cooldown", "Cooldown %ss");
        add("rotten_ruins.gui.magic.charge", "Charge %ss");
        // ---- Spell wheel HUD / loadout ----
        add("key." + HallMod.MODID + ".cast_wheel", "Open/Close Spell Wheel");
        add("key." + HallMod.MODID + ".open_loadout", "Spell Loadout");
        add("rotten_ruins.gui.wheel.title", "Spell Wheel");
        add("rotten_ruins.gui.wheel.loadout.title", "Spell Loadout");
        add("rotten_ruins.wheel.hint.hold_staff", "Hold a staff to cast from the wheel");
        add("rotten_ruins.wheel.hint.no_spells",
                "No active spells on wheel I - press V to configure");
        add("rotten_ruins.wheel.begin.sustaining", "SUSTAINING - RMB / R to end");
        add("rotten_ruins.wheel.result.success", "Cast");
        add("rotten_ruins.wheel.result.invalid_staff", "Hold a staff");
        add("rotten_ruins.wheel.result.no_spell", "Empty socket");
        add("rotten_ruins.wheel.result.passive_spell", "Passive spells cannot be cast");
        add("rotten_ruins.wheel.result.on_cooldown", "On cooldown");
        add("rotten_ruins.wheel.result.not_enough_mana", "Not enough mana");
        add("rotten_ruins.wheel.result.not_charged", "Not charged");
        add("rotten_ruins.wheel.result.no_target", "No target");
        add("rotten_ruins.wheel.result.cancelled", "Cancelled");
        add("rotten_ruins.wheel.result.rejected", "Rejected");
        add("rotten_ruins.wheel.result.invalid_slot", "Invalid socket");
        add("rotten_ruins.wheel.result.not_active", "No wheel session");
        // 轮盘 HUD / 装载界面（datagen 生成 en_us/zh_cn；代码里全部走 WheelI18n.tr）
        add("rotten_ruins.wheel.title", "ARCANE WHEEL");
        add("rotten_ruins.wheel.subtitle", "%s: OPEN/CLOSE  |  CLICK A SIGIL TO CAST");
        add("rotten_ruins.wheel.active_sigils", "ACTIVE SIGILS  %s / %s");
        add("rotten_ruins.wheel.mana", "MANA  %s / %s");
        add("rotten_ruins.wheel.release_to_cast", "CLICK A SIGIL TO CAST");
        add("rotten_ruins.wheel.rmb_cancel", "RMB  CLOSE");
        add("rotten_ruins.wheel.page", "WHEEL  %s");
        add("rotten_ruins.wheel.ready", "READY");
        add("rotten_ruins.wheel.maintain", "SUSTAINING %s s");
        add("rotten_ruins.wheel.maintain_end", "RMB / R TO END");
        add("rotten_ruins.wheel.center.wheel", "WHEEL");
        add("rotten_ruins.wheel.center.aim", "AIM A SIGIL");
        add("rotten_ruins.wheel.center.no_active", "NO ACTIVE SIGILS");
        add("rotten_ruins.wheel.center.sockets", "8 SOCKETS");
        add("rotten_ruins.wheel.center.release_hint", "click to cast");
        add("rotten_ruins.wheel.center.bind_hint", "bind active spells in the loadout");
        add("rotten_ruins.wheel.center.drag_hint", "drag spells onto sockets");
        add("rotten_ruins.wheel.center.cooldown", "COOLDOWN %s");
        add("rotten_ruins.wheel.center.not_enough_mana", "NOT ENOUGH MANA");
        add("rotten_ruins.wheel.center.charging", "CHARGING %s s");
        add("rotten_ruins.wheel.center.ready", "READY  |  %s MANA");
        add("rotten_ruins.wheel.center.selected", "SELECTED");
        add("rotten_ruins.wheel.school.water", "WATER");
        add("rotten_ruins.wheel.school.fire", "FIRE");
        add("rotten_ruins.wheel.school.death", "DEATH");
        add("rotten_ruins.wheel.school.nature", "NATURE");
        add("rotten_ruins.wheel.school.light", "LIGHT");
        add("rotten_ruins.wheel.school.void", "VOID");
        add("rotten_ruins.wheel.school.mysterious", "MYSTERIOUS");
        add("rotten_ruins.wheel.using.instant", "INSTANT");
        add("rotten_ruins.wheel.using.charge", "CHARGE");
        add("rotten_ruins.wheel.using.channel", "CHANNEL");
        add("rotten_ruins.wheel.loadout.subtitle", "WHEEL CONFIGURATION  -  PAGE %s");
        add("rotten_ruins.wheel.loadout.close", "ESC  CLOSE");
        add("rotten_ruins.wheel.loadout.preview", "WHEEL PREVIEW");
        add("rotten_ruins.wheel.loadout.preview_summary",
                "WHEEL %s  |  %s / %s SOCKETS");
        add("rotten_ruins.wheel.loadout.drag_hint", "Drag a spell onto a socket");
        add("rotten_ruins.wheel.loadout.unbind_hint", "Right-click a socket to unbind");
        add("rotten_ruins.wheel.loadout.save_hint", "Changes apply when you press SAVE");
        add("rotten_ruins.wheel.loadout.codex", "SPELL CODEX");
        add("rotten_ruins.wheel.loadout.count_active", "%s ACTIVE");
        add("rotten_ruins.wheel.loadout.count_passive", "%s PASSIVE");
        add("rotten_ruins.wheel.loadout.tab.active", "ACTIVE");
        add("rotten_ruins.wheel.loadout.tab.passive", "PASSIVE");
        add("rotten_ruins.wheel.loadout.no_active", "NO ACTIVE SPELLS");
        add("rotten_ruins.wheel.loadout.no_passive", "NO PASSIVE SPELLS");
        add("rotten_ruins.wheel.loadout.passive_hint", "Passives appear here once learned");
        add("rotten_ruins.wheel.loadout.equip_hint", "Equip spells in the magic constellation");
        add("rotten_ruins.wheel.loadout.footer_readonly", "READ ONLY");
        add("rotten_ruins.wheel.loadout.footer_active", "ACTIVE SPELLS");
        add("rotten_ruins.wheel.loadout.total", "%s TOTAL");
        add("rotten_ruins.wheel.loadout.scroll", "%s-%s / %s  -  SCROLL");
        add("rotten_ruins.wheel.loadout.slot_badge", "SLOT %s");
        add("rotten_ruins.wheel.loadout.passive_always", "PASSIVE  -  ALWAYS ON");
        add("rotten_ruins.wheel.loadout.card_sub", "%s   %s MANA   %s");
        add("rotten_ruins.wheel.loadout.details", "DETAILS");
        add("rotten_ruins.wheel.loadout.type", "TYPE");
        add("rotten_ruins.wheel.loadout.mana", "MANA");
        add("rotten_ruins.wheel.loadout.cooldown", "COOLDOWN");
        add("rotten_ruins.wheel.loadout.charge", "CHARGE");
        add("rotten_ruins.wheel.loadout.sustain", "SUSTAIN");
        add("rotten_ruins.wheel.loadout.assigned", "ASSIGNED");
        add("rotten_ruins.wheel.loadout.always_on", "ALWAYS ON");
        add("rotten_ruins.wheel.loadout.not_assigned", "NOT ASSIGNED");
        add("rotten_ruins.wheel.loadout.assigned_at", "WHEEL %s - SLOT %s");
        add("rotten_ruins.wheel.loadout.description", "DESCRIPTION");
        add("rotten_ruins.wheel.loadout.passive_warn1", "PASSIVE SPELLS CANNOT");
        add("rotten_ruins.wheel.loadout.passive_warn2", "BE PLACED ON THE WHEEL");
        add("rotten_ruins.wheel.loadout.passive_lore",
                "Passive spells are always active while equipped and can never be placed on the wheel.");
        add("rotten_ruins.wheel.loadout.desc.instant",
                "Releases the instant it is cast: no wind-up, the effect lands immediately.");
        add("rotten_ruins.wheel.loadout.desc.charge",
                "Hold to charge, then release. A longer charge means a stronger cast; releasing too early can fail.");
        add("rotten_ruins.wheel.loadout.desc.keep",
                "Starts on dwell and keeps running while held, draining mana over time. Release to end it.");
        add("rotten_ruins.wheel.loadout.socket", "SOCKET %s");
        add("rotten_ruins.wheel.loadout.empty_socket", "EMPTY SOCKET");
        add("rotten_ruins.wheel.loadout.drag_here", "Drag a spell here");
        add("rotten_ruins.wheel.loadout.no_selection", "NO SPELL SELECTED");
        add("rotten_ruins.wheel.loadout.click_hint", "Click a spell or a socket");
        add("rotten_ruins.wheel.loadout.wheel_page", "WHEEL PAGE");
        add("rotten_ruins.wheel.loadout.button.clear", "CLEAR SLOT");
        add("rotten_ruins.wheel.loadout.button.autofill", "AUTO FILL");
        add("rotten_ruins.wheel.loadout.button.save", "SAVE");
        add("rotten_ruins.wheel.loadout.hint",
                "Drag a spell onto a socket   |   RMB unbinds   |   SAVE applies to WHEEL %s");
        add("rotten_ruins.wheel.loadout.hint_short",
                "Drag to bind   |   RMB unbind   |   SAVE applies to this page");
        add("rotten_ruins.wheel.loadout.hint_tiny", "Drag to bind   |   SAVE applies");
        add("rotten_ruins.wheel.loadout.per_second", "%s / s");
        add("rotten_ruins.wheel.loadout.bind_to_slot", "BIND TO SLOT %s");
        add("rotten_ruins.wheel.loadout.release_on_socket", "RELEASE ON A SOCKET");
        // ---- Section-code text effects · preview screen / command ----
        add("rotten_ruins.gui.textfx.title", "§[rrs:grad:#6FBF4A:#8B1E2D]Section-Code Text Effects§[/]");
        add("rotten_ruins.gui.textfx.hint", "Each row animates live; press ESC to close; type /rrsfx for more");
        add("rotten_ruins.command.rrsfx.enabled", "§aText effects: ON");
        add("rotten_ruins.command.rrsfx.disabled", "§cText effects: OFF");

        // ---- Sound subtitles (see SoundData) ----
        // Body-explosion sound: same subtitle text as corruption's rip_body, hall namespace.
        add("subtitles." + HallMod.MODID + ".rip_body", "Body rips apart");

        // ====================================================================
        // ---- Simplified corruption content (migrated from the corruption mod) ----
        // Blocks / items / entities / effects are referenced through their registry
        // objects (CorruptionBlocks / CorruptionItems / CorruptionEntityTypes /
        // CorruptionEffects); keys are derived from registry ids, so a rename is a
        // compile error instead of a silently-missing translation.
        // ====================================================================

        // ---- A. Blocks: block.rotten_ruins.<id> (21) ----
        add(CorruptionBlocks.SPREAD_DIRT.get(), "Corrupted Dirt");
        add(CorruptionBlocks.SPREAD_STONE.get(), "Corrupted Stone");
        add(CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get(), "Corrupted Cobblestone");
        add(CorruptionBlocks.SPREAD_INFECTION_LEAVES.get(), "Corrupted Leaves");
        // corruption registers this block as spread_infection_wood but names it in neither lang file
        add(CorruptionBlocks.SPREAD_INFECTION_WOOD.get(), "Corrupted Log");
        add(CorruptionBlocks.CORRUPTION_GRASS.get(), "Corrupted Short Grass");
        add(CorruptionBlocks.CORRUPTION_TRACHEA.get(), "Respiratory Tract");
        add(CorruptionBlocks.CONNECT_BLOCK.get(), "Connective Tissue Block");
        add(CorruptionBlocks.DECIDUAL_TISSUE_BLOCK.get(), "Decidual Tissue Block");
        // corruption has no English name for fleshbed either
        add(CorruptionBlocks.FLESHBED.get(), "Fleshbed");
        add(CorruptionBlocks.DEGENERATE_REMAINS_SMALL.get(), "Small Degenerate Remains");
        add(CorruptionBlocks.DEGENERATE_REMAINS_MEDIUM.get(), "Medium Degenerate Remains");
        add(CorruptionBlocks.DEGENERATE_REMAINS_LARGE.get(), "Large Degenerate Remains");
        add(CorruptionBlocks.HOST_REMAINS_SMALL.get(), "Small Assimilated Remains");
        add(CorruptionBlocks.HOST_REMAINS_MEDIUM.get(), "Medium Assimilated Remains");
        add(CorruptionBlocks.HOST_REMAINS_LARGE.get(), "Large Assimilated Remains");
        add(CorruptionBlocks.REFACTORED_REMAINS_SMALL.get(), "Small Refactored Remains");
        add(CorruptionBlocks.REFACTORED_REMAINS_MEDIUM.get(), "Medium Refactored Remains");
        add(CorruptionBlocks.REFACTORED_REMAINS_LARGE.get(), "Large Refactored Remains");
        add(CorruptionBlocks.VARIABLE_HEIGHT_CROP.get(), "Corrupted Meridian");
        add(CorruptionBlocks.SPIRACLE_BLOCK.get(), "Living Spiracle");

        // ---- B. Items: item.rotten_ruins.<id> (42 = 14 materials + 5 tools + 23 spawn eggs) ----
        // Materials
        add(CorruptionItems.INFECTION_BONE.get(), "Infected Bone");
        add(CorruptionItems.HARD_FRAGMENTS.get(), "Hardened Fragments");
        // corruption has no English name for chitin_polymer either
        add(CorruptionItems.CHITIN_POLYMER.get(), "Chitin Polymer");
        add(CorruptionItems.LIVE_MEAT.get(), "Living Flesh");
        add(CorruptionItems.HOST_MEAT.get(), "Assimilated Flesh");
        add(CorruptionItems.HOST_CARAPACE.get(), "Assimilated Carapace");
        add(CorruptionItems.HOST_TOOTH.get(), "Assimilated Tooth");
        add(CorruptionItems.DEGENERATE_HEART.get(), "Degenerate Heart");
        add(CorruptionItems.LONG_ARM_BEAST_HEAD.get(), "Longibrachiate Head");
        add(CorruptionItems.LONG_ARM_BEAST_TOOTH.get(), "Longibrachiate Tooth");
        add(CorruptionItems.ACID_SAC.get(), "Acid Sac");
        add(CorruptionItems.ACID_BALL.get(), "Acid Ball");
        add(CorruptionItems.SYRINGE.get(), "Syringe");
        add(CorruptionItems.SYRINGE_BLOODY_CONCENTRATE.get(), "Concentrated Blood Syringe");
        // Tools
        add(CorruptionItems.CRUDE_AXE.get(), "Crude Axe");
        add(CorruptionItems.CRUDE_HOE.get(), "Crude Hoe");
        add(CorruptionItems.CRUDE_PICKAXE.get(), "Crude Pickaxe");
        add(CorruptionItems.CRUDE_SHOVEL.get(), "Crude Shovel");
        add(CorruptionItems.CRUDE_SWORD.get(), "Crude Sword");
        // Spawn eggs (key follows the entity id, value is "<entity name> Spawn Egg")
        add(CorruptionItems.CORRUPTION_ZOMBIE_SPAWN_EGG.get(), "Assimilated Zombie Spawn Egg");
        add(CorruptionItems.CORRUPTION_PLAYER_SPAWN_EGG.get(), "Assimilated Player Spawn Egg");
        add(CorruptionItems.CORRUPTION_COW_SPAWN_EGG.get(), "Assimilated Cow Spawn Egg");
        add(CorruptionItems.CORRUPTION_SHEEP_SPAWN_EGG.get(), "Assimilated Sheep Spawn Egg");
        add(CorruptionItems.CORRUPTION_PIG_SPAWN_EGG.get(), "Assimilated Pig Spawn Egg");
        add(CorruptionItems.CORRUPTION_BAT_SPAWN_EGG.get(), "Assimilated Bat Spawn Egg");
        add(CorruptionItems.CORRUPTION_SKELETON_SPAWN_EGG.get(), "Assimilated Skeleton Spawn Egg");
        add(CorruptionItems.CORRUPTION_BONE_RUINS_SPAWN_EGG.get(), "Skeletal Remains Spawn Egg");
        add(CorruptionItems.CORRUPTION_VILLAGER_SPAWN_EGG.get(), "Assimilated Villager Spawn Egg");
        add(CorruptionItems.CORRUPTION_WOLF_SPAWN_EGG.get(), "Assimilated Wolf Spawn Egg");
        add(CorruptionItems.CORRUPTION_CHICKEN_SPAWN_EGG.get(), "Assimilated Chicken Spawn Egg");
        add(CorruptionItems.DEGENERATE_PIG_SPAWN_EGG.get(), "Degenerate Pig Spawn Egg");
        add(CorruptionItems.DEGENERATE_ZOMBIE_SPAWN_EGG.get(), "Degenerate Zombie Spawn Egg");
        add(CorruptionItems.DEGENERATE_VILLAGER_SPAWN_EGG.get(), "Degenerate Villager Spawn Egg");
        add(CorruptionItems.DEGENERATE_COW_SPAWN_EGG.get(), "Degenerate Cow Spawn Egg");
        add(CorruptionItems.GROVELER_SPAWN_EGG.get(), "Crawlfex Spawn Egg");
        add(CorruptionItems.ATTACKER_SPAWN_EGG.get(), "Attacker Spawn Egg");
        add(CorruptionItems.BEARER_SPAWN_EGG.get(), "Gestaltomb Spawn Egg");
        add(CorruptionItems.LONG_ARM_BEAST_SPAWN_EGG.get(), "Longibrachiate Spawn Egg");
        add(CorruptionItems.SHOCKER_SPAWN_EGG.get(), "Shocker Spawn Egg");
        add(CorruptionItems.HUMUS_COLUMN_SPAWN_EGG.get(), "Stage I Humus Column Spawn Egg");
        add(CorruptionItems.SUMMONING_PILLAR_SPAWN_EGG.get(), "Stage I Summoning Pillar Spawn Egg");
        add(CorruptionItems.ORIGINAL_BUG_SPAWN_EGG.get(), "Primordial Worm Spawn Egg");

        // ---- C. Entities: entity.rotten_ruins.<id> (23) ----
        add(CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(), "Assimilated Zombie");
        add(CorruptionEntityTypes.CORRUPTION_PLAYER.get(), "Assimilated Player");
        add(CorruptionEntityTypes.CORRUPTION_COW.get(), "Assimilated Cow");
        add(CorruptionEntityTypes.CORRUPTION_SHEEP.get(), "Assimilated Sheep");
        // corruption has no entity name for the pig/skeleton, only their spawn eggs
        add(CorruptionEntityTypes.CORRUPTION_PIG.get(), "Assimilated Pig");
        add(CorruptionEntityTypes.CORRUPTION_BAT.get(), "Assimilated Bat");
        add(CorruptionEntityTypes.CORRUPTION_SKELETON.get(), "Assimilated Skeleton");
        add(CorruptionEntityTypes.CORRUPTION_BONE_RUINS.get(), "Skeletal Remains");
        add(CorruptionEntityTypes.CORRUPTION_VILLAGER.get(), "Assimilated Villager");
        add(CorruptionEntityTypes.CORRUPTION_WOLF.get(), "Assimilated Wolf");
        add(CorruptionEntityTypes.CORRUPTION_CHICKEN.get(), "Assimilated Chicken");
        add(CorruptionEntityTypes.DEGENERATE_PIG.get(), "Degenerate Pig");
        add(CorruptionEntityTypes.DEGENERATE_ZOMBIE.get(), "Degenerate Zombie");
        add(CorruptionEntityTypes.DEGENERATE_VILLAGER.get(), "Degenerate Villager");
        add(CorruptionEntityTypes.DEGENERATE_COW.get(), "Degenerate Cow");
        add(CorruptionEntityTypes.GROVELER.get(), "Crawlfex");
        add(CorruptionEntityTypes.ATTACKER.get(), "Attacker");
        add(CorruptionEntityTypes.BEARER.get(), "Gestaltomb");
        add(CorruptionEntityTypes.LONG_ARM_BEAST.get(), "Longibrachiate");
        add(CorruptionEntityTypes.SHOCKER.get(), "Shocker");
        add(CorruptionEntityTypes.HUMUS_COLUMN.get(), "Stage I Humus Column");
        add(CorruptionEntityTypes.SUMMONING_PILLAR.get(), "Stage I Summoning Pillar");
        add(CorruptionEntityTypes.ORIGINAL_BUG.get(), "Primordial Worm");

        // ---- D. Effects: effect.rotten_ruins.<id> (6) ----
        add(CorruptionEffects.CORRUPTION.get(), "Corruption");
        add(CorruptionEffects.LYSIS.get(), "Lysis");
        add(CorruptionEffects.MIASMA.get(), "Despair");
        add(CorruptionEffects.SCLEROSIS.get(), "Sclerosis");
        add(CorruptionEffects.BLEEDING.get(), "Bleeding");
        add(CorruptionEffects.FEAR.get(), "Fear");

        // ---- E. Advancements: advancements.rotten_ruins.<path> ----
        // Keys come from AdvancementProviderData so the display JSON and lang stay in sync.
        // Advancement titles/descriptions are drawn as single Components (advancement GUI / toast),
        // so effect markers are safe here.
        add(AdvancementProviderData.titleKey(AdvancementProviderData.ROOT_PATH),
                "§[rrs:grad:#6FBF4A:#8B1E2D:speed=0.5]Rotten Ruins§[/]");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.ROOT_PATH),
                "As a god, experience yourself as a human");
        // Death branch: triggered when the player obtains the Death Star.
        add(AdvancementProviderData.titleKey(AdvancementProviderData.DEATH_PATH), "Death");
        add(AdvancementProviderData.descriptionKey(AdvancementProviderData.DEATH_PATH),
                "§[rrs:glitch:amp=1.5]Obtain the Death Star§[/]");
    }
}
