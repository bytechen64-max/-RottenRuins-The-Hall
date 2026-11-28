package org.bytechen.rottenruins.api.combat.slash;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server callbacks run on the server thread. Aim is locked when the swing starts. */
public record SlashContext(ServerPlayer player, InteractionHand hand, ItemStack stack, SlashPose pose,
                           int comboIndex, SlashSpec spec, Vec3 forward, Vec3 cutDirection) {}
