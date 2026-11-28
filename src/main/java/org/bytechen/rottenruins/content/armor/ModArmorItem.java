package org.bytechen.rottenruins.content.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/** 带额外属性修饰器的盔甲：修饰器只在穿到对应槽位时生效。 */
public class ModArmorItem extends ArmorItem {

    /** 一条额外修饰器。 */
    public record Extra(Attribute attribute, double amount, AttributeModifier.Operation operation) {}

    private final List<Extra> extras;

    public ModArmorItem(ArmorMaterial material, ArmorItem.Type type, Properties properties, Extra... extras) {
        super(material, type, properties);
        this.extras = List.of(extras);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (slot != getType().getSlot() || extras.isEmpty()) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        for (Extra extra : extras) {
            // 名字决定 UUID，四件各穿一条不会互相覆盖。
            String name = "rotten_ruins.armor." + getMaterial().getName() + "." + getType().getName()
                    + "." + extra.attribute().getDescriptionId();
            builder.put(extra.attribute(), new AttributeModifier(name, extra.amount(), extra.operation()));
        }
        return builder.build();
    }

    /** 四件是否整套同材质。 */
    public static boolean wearingFullSet(LivingEntity entity, ArmorMaterial material) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getMaterial() != material) {
                return false;
            }
        }
        return true;
    }
}
