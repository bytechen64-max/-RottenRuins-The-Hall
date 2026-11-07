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

/** 条件由 datagen 提供；满足时在原版掉落之外追加一份源荷砂。 */
public final class SourceChargeSandLootModifier extends LootModifier {
    public static final Codec<SourceChargeSandLootModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, SourceChargeSandLootModifier::new));

    public SourceChargeSandLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(new ItemStack(RegisterItem.SOURCE_CHARGE_SAND.get()));
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
