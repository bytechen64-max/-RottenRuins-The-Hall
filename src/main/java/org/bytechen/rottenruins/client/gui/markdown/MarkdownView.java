package org.bytechen.rottenruins.client.gui.markdown;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/** A reusable resource-pack Markdown viewport for client screens. */
public final class MarkdownView extends AbstractWidget {
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.builder().maxCells(8_192).build(),
                    StrikethroughExtension.create(), TaskListItemsExtension.create()))
            .build();
    private static final int SCROLLBAR_SPACE = 12;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLL_STEP = 26;
    private static final int DRAG_THRESHOLD = 3;

    private record ContentPress(double x, double y, Style style) {
        boolean moved(double mouseX, double mouseY) {
            return Math.abs(mouseX - x) >= DRAG_THRESHOLD || Math.abs(mouseY - y) >= DRAG_THRESHOLD;
        }
    }

    private final Font font;
    private final Node document;
    private final MarkdownResources resources;
    private final Predicate<Style> linkHandler;
    private MarkdownLayout layout;
    private double scroll;
    private double thumbGrab = -1;
    private ContentPress contentPress;
    private boolean contentDragging;

    private MarkdownView(Font font, Node document, MarkdownResources resources, Predicate<Style> linkHandler) {
        super(0, 0, 0, 0, Component.translatable("rotten_ruins.gui.markdown.title"));
        this.font = font;
        this.document = document;
        this.resources = resources;
        this.linkHandler = linkHandler;
    }

    /** The identifier has no language prefix or .md suffix. Set the viewport with {@link #setBounds}. */
    public static MarkdownView load(Minecraft minecraft, ResourceLocation id, Predicate<Style> linkHandler)
            throws IOException {
        var resources = new MarkdownResources(minecraft.getResourceManager(), id,
                minecraft.getLanguageManager().getSelected());
        String source = resources.read();
        try {
            return new MarkdownView(minecraft.font, PARSER.parse(source), resources, linkHandler);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Cannot parse Markdown document " + id, failure);
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        clearPointerCapture();
        setPosition(x, y);
        width = Math.max(SCROLLBAR_SPACE + 1, width);
        height = Math.max(1, height);
        if (layout != null && width == getWidth() && height == getHeight()) {
            return;
        }
        int oldMax = maxScroll();
        double progress = oldMax == 0 ? 0 : scroll / oldMax;
        setWidth(width);
        setHeight(height);
        layout = MarkdownLayout.measure(font, document, resources, width - SCROLLBAR_SPACE, height);
        scrollTo(progress * maxScroll());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null) {
            return;
        }
        int top = (int) scroll;
        int bottom = top + getHeight();
        graphics.enableScissor(getX(), getY(), contentRight(), getY() + getHeight());
        try {
            for (MarkdownLayout.Element element : layout.elements()) {
                if (element.y() < bottom && element.y() + element.height() > top) {
                    element.draw(graphics, font, getX(), getY() - top);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (maxScroll() > 0) {
            int x = scrollbarX();
            int thumbTop = thumbTop();
            graphics.fill(x, getY(), x + SCROLLBAR_WIDTH, getY() + getHeight(), 0xFF273343);
            int color = thumbGrab >= 0 || (isHovered() && mouseX >= contentRight()) ? 0xFFE4BE80 : 0xFFAA8A5E;
            graphics.fill(x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight(), color);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() == 0) {
            return false;
        }
        scrollTo(scroll - delta * SCROLL_STEP);
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        clearPointerCapture();
        if (layout == null) {
            return;
        }
        if (mouseX >= contentRight() && maxScroll() > 0) {
            int top = thumbTop();
            if (mouseY >= top && mouseY < top + thumbHeight()) {
                thumbGrab = mouseY - top;
            } else {
                thumbGrab = thumbHeight() / 2.0;
                dragThumb(mouseY);
            }
        } else if (mouseX < contentRight()) {
            contentPress = new ContentPress(mouseX, mouseY, linkAt(mouseX, mouseY));
        }
    }

    private Style linkAt(double mouseX, double mouseY) {
        double x = mouseX - getX();
        double y = mouseY - getY() + (int) scroll;
        for (MarkdownLayout.Element element : layout.elements()) {
            Style style = element.styleAt(font, x, y);
            if (style.getClickEvent() != null) {
                return style;
            }
        }
        return Style.EMPTY;
    }

    private void followLink(Style style, ClickEvent click) {
        if (click.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            int anchor = click.getValue().isEmpty() ? 0 : layout.anchors().getOrDefault(click.getValue(), -1);
            if (anchor >= 0) {
                scrollTo(anchor);
            }
        } else {
            linkHandler.test(style);
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (thumbGrab >= 0) {
            dragThumb(mouseY);
        } else if (contentPress != null) {
            if (contentDragging) {
                scrollTo(scroll - dragY);
            } else if (contentPress.moved(mouseX, mouseY)) {
                contentDragging = true;
                scrollTo(scroll - (mouseY - contentPress.y()));
            }
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        ContentPress press = contentDragging ? null : contentPress;
        clearPointerCapture();
        if (press == null || !isFocused() || press.moved(mouseX, mouseY)
                || !isMouseOver(mouseX, mouseY) || mouseX >= contentRight()) {
            return;
        }
        ClickEvent click = press.style().getClickEvent();
        if (click != null && click.equals(linkAt(mouseX, mouseY).getClickEvent())) {
            followLink(press.style(), click);
        }
    }

    /** Lets a host deliver the left-button release even after the pointer leaves the viewport. */
    public boolean hasPointerCapture() {
        return thumbGrab >= 0 || contentPress != null;
    }

    private void clearPointerCapture() {
        thumbGrab = -1;
        contentPress = null;
        contentDragging = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || maxScroll() == 0) {
            return false;
        }
        int page = Math.max(1, getHeight() - 12);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> scrollTo(scroll - SCROLL_STEP);
            case GLFW.GLFW_KEY_DOWN -> scrollTo(scroll + SCROLL_STEP);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(scroll - page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(scroll + page);
            case GLFW.GLFW_KEY_HOME -> scrollTo(0);
            case GLFW.GLFW_KEY_END -> scrollTo(maxScroll());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private void scrollTo(double position) {
        scroll = Mth.clamp(position, 0, maxScroll());
    }

    private int maxScroll() {
        return layout == null ? 0 : Math.max(0, layout.height() - getHeight());
    }

    private int scrollbarX() {
        return getX() + getWidth() - SCROLLBAR_WIDTH;
    }

    private int contentRight() {
        return getX() + getWidth() - SCROLLBAR_SPACE;
    }

    private int thumbHeight() {
        int proportional = (int) ((double) getHeight() * getHeight() / layout.height());
        return Math.min(Math.max(1, getHeight() - 1), Math.max(12, proportional));
    }

    private int thumbTop() {
        double progress = scroll / maxScroll();
        return getY() + (int) Math.round(progress * (getHeight() - thumbHeight()));
    }

    private void dragThumb(double mouseY) {
        int travel = Math.max(1, getHeight() - thumbHeight());
        scrollTo((mouseY - getY() - thumbGrab) / travel * maxScroll());
    }
}
