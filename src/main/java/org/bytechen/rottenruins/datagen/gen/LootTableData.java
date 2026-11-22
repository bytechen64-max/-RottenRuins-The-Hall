package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.LootingEnchantFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithLootingCondition;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionItems;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LootTableData extends LootTableProvider {
    public LootTableData(PackOutput output) {
        super(output, Set.of(), List.of(
                new SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK),
                new SubProviderEntry(ModEntityLoot::new, LootContextParamSets.ENTITY)
        ));
    }

    public static class ModEntityLoot extends EntityLootSubProvider {
        protected ModEntityLoot() {
            super(FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        public void generate() {
            this.add(EntityTypeRegistry.DEATH_HUNTER.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                            .when(net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition.killedByPlayer())
                            .when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(0.1F))
                            .add(LootItem.lootTableItem(RegisterItem.DEATH_DAGGER.get()))));

            // InfPlayer 掉落 HALL_TENDON 和 HALL_BONE_FRAGMENTS
            // 注意：此处移除了 ApplyBonusCount，掉落数量仅由 SetItemCountFunction 控制，
            // 不再受抢夺附魔影响。如需保留抢夺效果，请参考方案二通过 LivingDropsEvent 实现。
            this.add(EntityTypeRegistry.INF_PLAYER.get(),
                    LootTable.lootTable()
                            .withPool(LootPool.lootPool()
                                    .setRolls(ConstantValue.exactly(1))
                                    .add(LootItem.lootTableItem(RegisterItem.HALL_TENDON.get())
                                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0f, 2.0f)))
                                    )
                                    .add(LootItem.lootTableItem(RegisterItem.HALL_BONE_FRAGMENTS.get())
                                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0f, 2.0f)))
                                    )
                            )
            );

            // 城墙碎盾：城墙掉落 1–3 个，受抢夺影响
            this.add(EntityTypeRegistry.RAMPART.get(),
                    LootTable.lootTable().withPool(materialDrop(RegisterItem.RAMPART_SHIELD_SHARD.get(), 1, 3)));

            // Bonecrusher 掉落 HALL_BONE_FRAGMENTS 和 HALL_TENDON
            this.add(EntityTypeRegistry.BONECRUSHER.get(),
                    LootTable.lootTable()
                            .withPool(LootPool.lootPool()
                                    .setRolls(ConstantValue.exactly(1))
                                    .add(LootItem.lootTableItem(RegisterItem.BONECRUSHER_CORE.get())
                                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0f, 3.0f)))
                                    )
                                    .add(LootItem.lootTableItem(RegisterItem.HALL_BONE_FRAGMENTS.get())
                                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0f, 3.0f)))
                                    )
                                    .add(LootItem.lootTableItem(RegisterItem.HALL_TENDON.get())
                                            .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0f)))
                                    )
                            )
            );

            hostTypes().forEach(type -> this.add(type, hostDrops()));
            degenerateTypes().forEach(type -> this.add(type, degenerateDrops()));
            refactoredTypes().forEach(type -> this.add(type, refactoredDrops()));
            this.add(CorruptionEntityTypes.LONG_ARM_BEAST.get(), refactoredDrops()
                    .withPool(rareDrop(CorruptionItems.LONG_ARM_BEAST_HEAD.get()))
                    .withPool(materialDrop(CorruptionItems.LONG_ARM_BEAST_TOOTH.get(), 1, 2)));
        }

        // Each material has its own pool, so one material never replaces another.
        private static LootPool.Builder materialDrop(ItemLike item, int min, int max) {
            return LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(item)
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                            .apply(LootingEnchantFunction.lootingMultiplier(UniformGenerator.between(0, 1))));
        }

        private static LootPool.Builder rareDrop(ItemLike item) {
            return LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceWithLootingCondition.randomChanceAndLootingBoost(0.25F, 0.05F))
                    .add(LootItem.lootTableItem(item));
        }

        private static LootTable.Builder hostDrops() {
            return LootTable.lootTable()
                    .withPool(materialDrop(CorruptionItems.HOST_MEAT.get(), 1, 2))
                    .withPool(materialDrop(CorruptionItems.LIVE_MEAT.get(), 0, 1))
                    .withPool(materialDrop(CorruptionItems.HOST_CARAPACE.get(), 0, 1))
                    .withPool(materialDrop(CorruptionItems.INFECTION_BONE.get(), 0, 2));
        }

        private static LootTable.Builder degenerateDrops() {
            return hostDrops()
                    .withPool(rareDrop(CorruptionItems.DEGENERATE_HEART.get()))
                    .withPool(materialDrop(CorruptionItems.ACID_SAC.get(), 0, 1))
                    .withPool(materialDrop(CorruptionItems.ACID_BALL.get(), 0, 2));
        }

        private static LootTable.Builder refactoredDrops() {
            return degenerateDrops()
                    .withPool(materialDrop(CorruptionItems.CHITIN_POLYMER.get(), 0, 2))
                    .withPool(materialDrop(CorruptionItems.HARD_FRAGMENTS.get(), 1, 3));
        }

        private static Stream<EntityType<?>> hostTypes() {
            return Stream.of(CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(),
                    CorruptionEntityTypes.CORRUPTION_PLAYER.get(), CorruptionEntityTypes.CORRUPTION_COW.get(),
                    CorruptionEntityTypes.CORRUPTION_SHEEP.get(), CorruptionEntityTypes.CORRUPTION_PIG.get(),
                    CorruptionEntityTypes.CORRUPTION_BAT.get(), CorruptionEntityTypes.CORRUPTION_SKELETON.get(),
                    CorruptionEntityTypes.CORRUPTION_BONE_RUINS.get(), CorruptionEntityTypes.CORRUPTION_VILLAGER.get(),
                    CorruptionEntityTypes.CORRUPTION_WOLF.get(), CorruptionEntityTypes.CORRUPTION_CHICKEN.get());
        }

        private static Stream<EntityType<?>> degenerateTypes() {
            return Stream.of(CorruptionEntityTypes.DEGENERATE_PIG.get(), CorruptionEntityTypes.DEGENERATE_ZOMBIE.get(),
                    CorruptionEntityTypes.DEGENERATE_VILLAGER.get(), CorruptionEntityTypes.DEGENERATE_COW.get());
        }

        // LongArmBeast has an additional species-specific pool and is registered separately.
        private static Stream<EntityType<?>> refactoredTypes() {
            return Stream.of(CorruptionEntityTypes.ATTACKER.get(), CorruptionEntityTypes.BEARER.get(),
                    CorruptionEntityTypes.SHOCKER.get());
        }

        @Override
        protected Stream<EntityType<?>> getKnownEntityTypes() {
            return Stream.concat(Stream.of(
                    EntityTypeRegistry.INF_PLAYER.get(),
                    EntityTypeRegistry.DEATH_HUNTER.get(),
                    EntityTypeRegistry.BONECRUSHER.get(),
                    EntityTypeRegistry.RAMPART.get(),
                    CorruptionEntityTypes.LONG_ARM_BEAST.get()
            ), Stream.of(hostTypes(), degenerateTypes(), refactoredTypes()).flatMap(types -> types));
        }
    }

    public static class ModBlockLoot extends BlockLootSubProvider {
        protected ModBlockLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            ForgeRegistries.BLOCKS.getEntries().stream()
                    .filter(e -> e.getKey().location().getNamespace().equals(HallMod.MODID))
                    .forEach(e -> {
                        Block block = e.getValue();
                        if (block == RegisterBlock.DOMITE_MINERAL.get()) {
                            dropOther(RegisterBlock.DOMITE_MINERAL.get(), RegisterItem.DOMITE_ORE.get());
                        } else if (block == RegisterBlock.DOMERITE_MINERAL.get()) {
                            dropOther(RegisterBlock.DOMERITE_MINERAL.get(), RegisterItem.DOMERITE_ORE.get());
                        } else if (block == CorruptionBlocks.CORRUPTION_GRASS.get()) {
                            // two blocks tall: only the lower half drops
                            add(block, createSinglePropConditionTable(block,
                                    net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
                        } else if (block instanceof DoorBlock) {
                            // 门类方块必须使用 createDoorTable，否则两半都会掉落
                            add(block, createDoorTable((DoorBlock) block));
                        } else {
                            dropSelf(block);
                        }
                    });
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ForgeRegistries.BLOCKS.getEntries().stream()
                    .filter(e -> e.getKey().location().getNamespace().equals(HallMod.MODID))
                    .map(java.util.Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
    }
}
