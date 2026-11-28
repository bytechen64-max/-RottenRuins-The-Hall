package org.bytechen.rottenruins.overworld.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.jetbrains.annotations.NotNull;

/** Append one shard; table, player-kill and fixed probability conditions are data driven. */
public final class VoidShardLootModifier extends LootModifier {
    public static final Codec<VoidShardLootModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, VoidShardLootModifier::new));

    public VoidShardLootModifier(LootItemCondition[] conditions) { super(conditions); }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(new ItemStack(RegisterItem.VOID_SHARD.get()));
        return generatedLoot;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
