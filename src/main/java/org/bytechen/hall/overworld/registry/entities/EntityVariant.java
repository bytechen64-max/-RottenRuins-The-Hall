package org.bytechen.hall.overworld.registry.entities;

import net.minecraft.resources.ResourceLocation;

public record EntityVariant(String id, ResourceLocation model, ResourceLocation texture, ResourceLocation animation) {
}
