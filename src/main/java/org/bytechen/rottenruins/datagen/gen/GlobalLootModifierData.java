package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.loot.SourceChargeSandLootModifier;

public final class GlobalLootModifierData extends GlobalLootModifierProvider {
    public static final float SOURCE_CHARGE_SAND_DROP_CHANCE = 0.40F;

    public GlobalLootModifierData(PackOutput output) {
        super(output, HallMod.MODID);
    }

    @Override
    protected void start() {
        add("source_charge_sand_from_gravel", new SourceChargeSandLootModifier(new LootItemCondition[] {
                LootTableIdCondition.builder(Blocks.GRAVEL.getLootTable()).build(),
                LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                        EntityPredicate.Builder.entity().of(EntityType.PLAYER)).build(),
                MatchTool.toolMatches(ItemPredicate.Builder.item().hasEnchantment(
                        new EnchantmentPredicate(Enchantments.SILK_TOUCH, MinMaxBounds.Ints.atLeast(1))))
                        .invert().build(),
                LootItemRandomChanceCondition.randomChance(SOURCE_CHARGE_SAND_DROP_CHANCE).build()
        }));
    }
}
