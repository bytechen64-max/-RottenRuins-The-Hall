package org.bytechen.rottenruins.network.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.overworld.crafting.CraftIngredient;
import org.bytechen.rottenruins.overworld.crafting.PotentialCraftingRecipe;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端 → 服务端：JEI「一键填充」。
 *
 * <p>只发配方 id，材料判定与搬运全在服务端做 —— 客户端那份背包只是镜像，
 * 由它决定放什么等于让客户端伪造物品。</p>
 */
public final class PotentialCraftFillPacket {

    public static final String ACTION = "rotten_ruins:potential_craft_fill";

    /** 玩家背包主区 + 快捷栏。 */
    private static final int INVENTORY_SIZE = 36;

    private PotentialCraftFillPacket() {
    }

    public static void send(ResourceLocation recipeId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("r", recipeId.toString());
        NetworkHelper.sendToServer(new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    public static void register() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (data.getValue() instanceof CompoundTag tag) {
                handle(ctx.getSender(), tag);
            }
        });
    }

    private static void handle(ServerPlayer player, CompoundTag tag) {
        if (player == null || player.isSpectator() || !tag.contains("r")) {
            return;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(tag.getString("r"));
        } catch (RuntimeException malformed) {
            return;
        }
        if (!(player.containerMenu instanceof PotentialCraftingMenu menu) || menu.isCrafting()) {
            return;
        }
        if (!(player.level().getBlockEntity(menu.pos()) instanceof PotentialCraftingTableBlockEntity station)) {
            return;
        }
        Recipe<?> found = player.level().getRecipeManager().byKey(id).orElse(null);
        if (found instanceof PotentialCraftingRecipe recipe) {
            fill(player, station, recipe);
        }
    }

    private static void fill(ServerPlayer player, PotentialCraftingTableBlockEntity station,
                             PotentialCraftingRecipe recipe) {
        // 环上已有的先抵扣掉，允许「放了一半再用 JEI 补齐」
        List<CraftIngredient> missing = new ArrayList<>(recipe.ingredients());
        int freeSlots = 0;
        for (int slot = 0; slot < PotentialCraftingTableBlockEntity.RING_SLOTS; slot++) {
            ItemStack present = station.getItem(slot);
            if (present.isEmpty()) {
                freeSlots++;
                continue;
            }
            for (int index = 0; index < missing.size(); index++) {
                if (missing.get(index).test(present)) {
                    missing.remove(index);
                    break;
                }
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (freeSlots < missing.size()) {
            player.displayClientMessage(Component.translatable(
                    "gui.rotten_ruins.potential_crafting.missing"), true);
            return;
        }

        // 先整份规划（同一格可以被同种材料重复取用），缺料就不搬
        int[] sources = new int[missing.size()];
        Map<Integer, Integer> taken = new HashMap<>();
        for (int index = 0; index < missing.size(); index++) {
            int source = findSource(player, missing.get(index), taken);
            if (source < 0) {
                player.displayClientMessage(Component.translatable(
                        "gui.rotten_ruins.potential_crafting.missing"), true);
                return;
            }
            sources[index] = source;
        }

        int cursor = 0;
        for (int source : sources) {
            while (cursor < PotentialCraftingTableBlockEntity.RING_SLOTS && !station.getItem(cursor).isEmpty()) {
                cursor++;
            }
            if (cursor >= PotentialCraftingTableBlockEntity.RING_SLOTS) {
                break;
            }
            ItemStack stack = player.getInventory().getItem(source);
            if (stack.isEmpty()) {
                continue;
            }
            station.setItem(cursor++, stack.split(1));
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static int findSource(ServerPlayer player, CraftIngredient ingredient, Map<Integer, Integer> taken) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int already = taken.getOrDefault(slot, 0);
            if (stack.getCount() > already) {
                taken.put(slot, already + 1);
                return slot;
            }
        }
        return -1;
    }
}
