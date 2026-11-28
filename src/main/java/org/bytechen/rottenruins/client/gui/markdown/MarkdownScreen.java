package org.bytechen.rottenruins.client.gui.markdown;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;

/** A standalone reader. Other GUIs can embed {@link MarkdownView} directly. */
public final class MarkdownScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PANEL_WIDTH = 540;
    private static final int MARGIN = 10;

    private final Screen parent;
    private final ResourceLocation document;
    private MarkdownView view;
    private Component error;

    public MarkdownScreen(Screen parent, ResourceLocation document) {
        super(Component.translatable("rotten_ruins.gui.markdown.title"));
        this.parent = parent;
        this.document = document;
    }

    public static void open(ResourceLocation document) {
        Minecraft.getInstance().setScreen(new MarkdownScreen(null, document));
    }

    @Override
    protected void init() {
        if (view == null && error == null) {
            try {
                view = MarkdownView.load(minecraft, document, this::handleComponentClicked);
            } catch (FileNotFoundException failure) {
                error = Component.translatable("rotten_ruins.gui.markdown.missing", document.toString());
            } catch (IOException failure) {
                LOGGER.warn("Could not load Markdown document {}", document, failure);
                error = Component.translatable("rotten_ruins.gui.markdown.unreadable", document.toString());
            }
        }
        if (view != null) {
            int panelWidth = panelWidth();
            view.setBounds((width - panelWidth) / 2 + MARGIN, 34,
                    panelWidth - MARGIN * 2, Math.max(1, height - 74));
            addRenderableWidget(view);
        }
        int buttonWidth = Math.min(100, width);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - buttonWidth) / 2, height - 27, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = panelWidth();
        int left = (width - panelWidth) / 2;
        graphics.fill(left, 8, left + panelWidth, height - 34, 0xF2121821);
        graphics.renderOutline(left, 8, panelWidth, Math.max(1, height - 42), 0xFF596476);
        graphics.drawCenteredString(font, title, width / 2, 17, 0xF0CE8A);
        graphics.hLine(left + MARGIN, left + panelWidth - MARGIN, 28, 0xFF394557);
        if (error != null) {
            int y = 36;
            for (FormattedCharSequence line : font.split(error, Math.max(1, panelWidth - 2 * MARGIN))) {
                graphics.drawString(font, line, left + MARGIN, y, 0xFFC6AD, false);
                y += font.lineHeight + 2;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && view != null && view.hasPointerCapture()) {
            // The default Screen release only reaches widgets still under the pointer.
            setDragging(false);
            return view.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (view != null && view.keyPressed(keyCode, scanCode, modifiers))
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelWidth() {
        return Math.max(1, Math.min(MAX_PANEL_WIDTH, width - 2 * MARGIN));
    }
}
