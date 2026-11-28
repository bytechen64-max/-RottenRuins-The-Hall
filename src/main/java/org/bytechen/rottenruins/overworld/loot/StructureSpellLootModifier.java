package org.bytechen.rottenruins.overworld.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Appends one spell; the item, target table and chance are configured by data packs. */
public final class StructureSpellLootModifier extends LootModifier {
    public static final Codec<StructureSpellLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).and(BuiltInRegistries.ITEM.byNameCodec()
                    .fieldOf("item").forGetter(modifier -> modifier.item))
                    .apply(instance, StructureSpellLootModifier::new));

    private final Item item;

    public StructureSpellLootModifier(LootItemCondition[] conditions, Item item) {
        super(conditions);
        this.item = item;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                         LootContext context) {
        generatedLoot.add(new ItemStack(item));
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
