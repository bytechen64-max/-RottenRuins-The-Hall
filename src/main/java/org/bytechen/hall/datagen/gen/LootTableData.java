package org.bytechen.hall.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.RegisterItem;

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

    // ────────── 实体战利品 ──────────
    public static class ModEntityLoot extends EntityLootSubProvider {
        protected ModEntityLoot() {
            super(FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        public void generate() {
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
        }

        @Override
        protected Stream<EntityType<?>> getKnownEntityTypes() {
            return Stream.of(
                    EntityTypeRegistry.INF_PLAYER.get(),
                    EntityTypeRegistry.BONECRUSHER.get()
            );
        }
    }

    // ────────── 方块战利品（保持不变） ──────────
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