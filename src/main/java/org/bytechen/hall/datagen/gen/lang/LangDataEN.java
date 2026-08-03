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
        add(RegisterItem.HALL_BONE_FRAGMENTS.get(), "Hall Bone Fragments");
        add(RegisterItem.HALL_TENDON.get(), "Hall Tendon");
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
        add(RegisterItem.BONECRUSHER_SPAWN_EGG.get(), "Hall Shell Spawn Egg");
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
        add(EntityTypeRegistry.INF_PLAYER.get(), "Infected Player");
        add(EntityTypeRegistry.INF_ENDERMAN.get(), "Infected Enderman");
        add(EntityTypeRegistry.BONECRUSHER.get(), "Hall Shell");
        add(EntityTypeRegistry.SHOCKWAVE.get(), "Shockwave");
        add(EntityTypeRegistry.METEORITE.get(), "Meteorite");
        add(RegisterEffect.ACID_ANOMALY_ADAPTATION.get(), "Acid Anomaly Adaptation");
        add(RegisterEffect.COLD_ANOMALY_ADAPTATION.get(), "Cold Anomaly Adaptation");
        add(RegisterEffect.HEAT_ANOMALY_ADAPTATION.get(), "Heat Anomaly Adaptation");
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
    }
}