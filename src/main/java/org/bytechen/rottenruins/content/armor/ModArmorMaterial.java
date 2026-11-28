package org.bytechen.rottenruins.content.armor;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 本模组的盔甲材质。
 *
 * <p>护甲/韧性/耐久照抄原版：粗制活体 = 下界合金，如梦 = 钻石。名字同时是贴图路径
 * （{@code textures/models/armor/<name>_layer_1.png}），暂无贴图所以用占位空图。</p>
 */
public record ModArmorMaterial(String name, int durabilityMultiplier, int[] defense,
                               float toughness, float knockbackResistance, int enchantmentValue,
                               SoundEvent equipSound, Supplier<Ingredient> repairIngredient)
        implements ArmorMaterial {

    /** 下界合金数值。 */
    public static final ModArmorMaterial CRUDE_LIVING = new ModArmorMaterial(
            "crude_living", 37, new int[]{3, 6, 8, 3}, 3.0F, 0.1F, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.NETHERITE_INGOT));

    /** 钻石数值。 */
    public static final ModArmorMaterial DREAMLIKE = new ModArmorMaterial(
            "dreamlike", 33, new int[]{3, 6, 8, 3}, 2.0F, 0.0F, 10,
            SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.of(Items.DIAMOND));

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int perSlot = switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
        return perSlot * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> defense[0];
            case LEGGINGS -> defense[1];
            case CHESTPLATE -> defense[2];
            case HELMET -> defense[3];
        };
    }

    @Override
    public String getName() {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }
}
