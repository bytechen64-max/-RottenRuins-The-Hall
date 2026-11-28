package org.bytechen.rottenruins.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 奥秘仪台在 JEI 里的配方视图：材料顺序无关，所以只留一个材料组合 + 产物。
 *
 * @param id       配方 id（一键填充时回传给服务端，由它按 id 重新查配方）
 * @param inputs   每条材料对应的候选物品（标签会展开成多个）
 * @param output   产物
 * @param timeTicks 基础耗时（tick，势能满时减半）
 */
public record PotentialCraftingJeiRecipe(@Nullable ResourceLocation id, @NotNull List<List<ItemStack>> inputs,
                                         @NotNull ItemStack output, int timeTicks) {
}
