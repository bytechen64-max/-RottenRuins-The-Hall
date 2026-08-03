package org.bytechen.hall.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.utils.DomeriteStatsHelper;

import java.util.Map;

public class DomeritePickaxe extends PickaxeItem {

    private static final float BASE_DAMAGE = 1.0F; // vanilla pickaxe base before tier bonus

    public DomeritePickaxe(Properties properties) {
        super(Tiers.NETHERITE, (int) BASE_DAMAGE, -2.8f, properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return DomeriteStatsHelper.getScaledDurability(stack);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float baseSpeed = super.getDestroySpeed(stack, state);
        if (baseSpeed <= 0) return baseSpeed;
        return DomeriteStatsHelper.getScaledSpeed(stack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) return original;

        float scaledBonus = DomeriteStatsHelper.getScaledAttackBonus(stack);
        float total = BASE_DAMAGE + scaledBonus;

        HashMultimap<Attribute, AttributeModifier> dynamic = HashMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier mod = entry.getValue();
            if (attr == Attributes.ATTACK_DAMAGE) {
                dynamic.put(attr, new AttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_UUID, "Domerite damage", total,
                        AttributeModifier.Operation.ADDITION));
            } else if (attr == Attributes.ATTACK_SPEED) {
                dynamic.put(attr, new AttributeModifier(
                        Item.BASE_ATTACK_SPEED_UUID, "Domerite speed", -2.8f,
                        AttributeModifier.Operation.ADDITION));
            } else {
                dynamic.put(attr, mod);
            }
        }
        return dynamic;
    }
}
