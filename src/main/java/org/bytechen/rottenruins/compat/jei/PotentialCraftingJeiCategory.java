package org.bytechen.rottenruins.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 奥秘仪台的 JEI 分类：15 条材料按环形排布、圆心放产物，和方块界面一致。
 */
public final class PotentialCraftingJeiCategory implements IRecipeCategory<PotentialCraftingJeiRecipe> {

    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;
    private static final int CENTER = WIDTH / 2;
    private static final int RADIUS = 44;
    private static final int SLOT = 18;

    private static final IDrawable BACKGROUND = new IDrawable() {
        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public void draw(@NotNull GuiGraphics graphics, int xOffset, int yOffset) {
            graphics.fill(xOffset, yOffset, xOffset + WIDTH, yOffset + HEIGHT, 0xF0161B26);
            graphics.fill(xOffset + 1, yOffset + 1, xOffset + WIDTH - 1, yOffset + HEIGHT - 1, 0xFF101724);
        }
    };

    private final IDrawable icon;

    public PotentialCraftingJeiCategory(@NotNull IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(RegisterBlock.POTENTIAL_CRAFTING_TABLE.get()));
    }

    @Override
    @NotNull
    public RecipeType<PotentialCraftingJeiRecipe> getRecipeType() {
        return RottenRuinsJeiPlugin.POTENTIAL_CRAFTING;
    }

    @Override
    @NotNull
    public Component getTitle() {
        return Component.translatable("block.rotten_ruins.potential_crafting_table");
    }

    @Override
    @NotNull
    public IDrawable getBackground() {
        return BACKGROUND;
    }

    @Override
    @NotNull
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull PotentialCraftingJeiRecipe recipe,
                          @NotNull IFocusGroup focuses) {
        List<List<ItemStack>> inputs = recipe.inputs();
        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            double angle = Math.toRadians(index * (360.0d / PotentialCraftingTableBlockEntity.RING_SLOTS) - 90.0d);
            int x = CENTER + (int) Math.round(Math.cos(angle) * RADIUS) - 1;
            int y = CENTER + (int) Math.round(Math.sin(angle) * RADIUS) - 1;
            var slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y);
            if (index < inputs.size()) {
                slot.addItemStacks(inputs.get(index));
            }
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER - SLOT / 2 + 1, CENTER - SLOT / 2 + 1)
                .addItemStack(recipe.output());
    }

    @Override
    public void draw(@NotNull PotentialCraftingJeiRecipe recipe, @NotNull IRecipeSlotsView slotsView,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        // 环形连线：只画一圈淡色，表示这些槽没有先后顺序
        int steps = 90;
        for (int step = 0; step < steps; step++) {
            double angle = Math.toRadians(step * 360.0d / steps);
            int x = CENTER + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = CENTER + (int) Math.round(Math.sin(angle) * RADIUS);
            graphics.fill(x, y, x + 1, y + 1, 0xFF2C4358);
        }
        if (recipe.timeTicks() > 0) {
            Component time = Component.translatable("gui.rotten_ruins.potential_crafting.time",
                    String.format(java.util.Locale.ROOT, "%.1f", recipe.timeTicks() / 20.0f));
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, time,
                    CENTER, HEIGHT - 12, 0x8FA6BF);
        }
    }
}
