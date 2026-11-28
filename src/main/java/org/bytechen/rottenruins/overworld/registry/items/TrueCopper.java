package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import org.bytechen.rottenruins.api.ICustomOutline;

public final class TrueCopper extends SwordItem implements ICustomOutline, org.bytechen.rottenruins.api.combat.slash.ISlashWeapon {
    @Override
    public org.bytechen.rottenruins.api.combat.slash.SlashSpec slashSpec(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
        return org.bytechen.rottenruins.content.combat.slash.copper.CopperSlash.SPEC;
    }
    public TrueCopper() {
        super(Tiers.IRON, 3, -2.4F, new Properties());
    }

    @Override
    public int outlineColor() {
        return 0xFFE89A5C; // Bright copper: RGB (232, 154, 92), fully opaque.
    }

    @Override
    public String outlineShaderKey() {
        return "default";
    }

    @Override
    public float outlinePixelWidth() {
        return 5.0F;
    }

    @Override
    public BlendMode outlineBlend() {
        return BlendMode.TRANSLUCENT;
    }

    @Override
    public boolean outlineEnabled(ItemDisplayContext context) {
        return context != ItemDisplayContext.HEAD;
    }
}
