package org.bytechen.rottenruins.client.gui.markdown;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableCell.Alignment;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** A measured document in viewport-relative GUI pixels. Rebuilt only when its bounds change. */
record MarkdownLayout(int height, List<MarkdownLayout.Element> elements, Map<String, Integer> anchors) {
    private static final int INK = 0xE1E5EB;
    private static final int MUTED = 0x9AA9BB;
    private static final int ACCENT = 0xF0CE8A;
    private static final int BORDER = 0xFF394557;
    private static final int PANEL = 0xFF1A2330;
    private static final Style BODY = Style.EMPTY.withColor(INK);
    private static final Style CODE = Style.EMPTY.withColor(0xC5D8F4)
            .withFont(ResourceLocation.fromNamespaceAndPath("minecraft", "uniform"));
    private static final Pattern ANCHOR_PUNCTUATION = Pattern.compile("[^\\p{L}\\p{M}\\p{N}_ -]");

    static MarkdownLayout measure(Font font, Node root, MarkdownResources resources, int width, int viewportHeight) {
        Builder builder = new Builder(font, resources, viewportHeight);
        int height = builder.blocks(root, new Column(0, width), 0, 7);
        return new MarkdownLayout(height, List.copyOf(builder.elements), Map.copyOf(builder.anchors));
    }

    interface Element {
        int y();

        int height();

        void draw(GuiGraphics graphics, Font font, int offsetX, int offsetY);

        default Style styleAt(Font font, double x, double y) {
            return Style.EMPTY;
        }
    }

    private record Line(int x, int y, int width, int height, float scale, FormattedCharSequence text)
            implements Element {
        @Override
        public void draw(GuiGraphics graphics, Font font, int offsetX, int offsetY) {
            graphics.pose().pushPose();
            graphics.pose().translate(offsetX + x, offsetY + y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, INK, false);
            graphics.pose().popPose();
        }

        @Override
        public Style styleAt(Font font, double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
                return Style.EMPTY;
            }
            Style style = font.getSplitter().componentStyleAtWidth(text, (int) ((mouseX - x) / scale));
            return style == null ? Style.EMPTY : style;
        }
    }

    private record Rect(int x, int y, int width, int height, int color) implements Element {
        @Override
        public void draw(GuiGraphics graphics, Font font, int offsetX, int offsetY) {
            graphics.fill(offsetX + x, offsetY + y, offsetX + x + width, offsetY + y + height, color);
        }
    }

    private record Picture(int x, int y, int width, int height, ResourceLocation texture, Style style)
            implements Element {
        @Override
        public void draw(GuiGraphics graphics, Font font, int offsetX, int offsetY) {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                graphics.blit(texture, offsetX + x, offsetY + y, 1,
                        0.0F, 0.0F, width, height, width, height);
            } finally {
                RenderSystem.disableBlend();
            }
        }

        @Override
        public Style styleAt(Font font, double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
                    ? style : Style.EMPTY;
        }
    }

    private record Column(int x, int width) {
        Column inset(int left, int right) {
            return new Column(x + left, Math.max(1, width - left - right));
        }
    }

    private static final class Builder {
        private final Font font;
        private final MarkdownResources resources;
        private final int viewportHeight;
        private final List<Element> elements = new ArrayList<>();
        private final Map<String, Integer> anchors = new HashMap<>();
        private final Map<String, Integer> nextAnchorSuffix = new HashMap<>();

        private Builder(Font font, MarkdownResources resources, int viewportHeight) {
            this.font = font;
            this.resources = resources;
            this.viewportHeight = viewportHeight;
        }

        private int blocks(Node parent, Column column, int y, int gap) {
            boolean hasContent = false;
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                int start = hasContent ? y + gap : y;
                int end = block(child, column, start);
                if (end > start) {
                    y = end;
                    hasContent = true;
                }
            }
            return y;
        }

        private int block(Node node, Column column, int y) {
            if (node instanceof Heading heading) {
                addAnchor(heading, y);
                float scale = switch (heading.getLevel()) {
                    case 1 -> 1.8F;
                    case 2 -> 1.5F;
                    case 3 -> 1.25F;
                    default -> 1.0F;
                };
                return inline(node, column, y, BODY.withColor(ACCENT).withBold(true), scale, Alignment.LEFT);
            }
            if (node instanceof Paragraph) {
                return inline(node, column, y, BODY, 1, Alignment.LEFT);
            }
            if (node instanceof BlockQuote) {
                int indent = Math.min(9, Math.max(0, column.width() - 24));
                int end = blocks(node, column.inset(indent, 0), y + 2, 5) + 2;
                if (indent >= 3) {
                    elements.add(new Rect(column.x(), y, 2, end - y, 0xFFB79160));
                }
                return end;
            }
            if (node instanceof ListBlock list) {
                return list(list, column, y);
            }
            if (node instanceof ThematicBreak) {
                elements.add(new Rect(column.x(), y + 3, column.width(), 1, BORDER));
                return y + 7;
            }
            if (node instanceof FencedCodeBlock code) {
                return codeBlock(code.getLiteral(), column, y);
            }
            if (node instanceof IndentedCodeBlock code) {
                return codeBlock(code.getLiteral(), column, y);
            }
            if (node instanceof HtmlBlock html) {
                return codeBlock(html.getLiteral(), column, y);
            }
            if (node instanceof TableBlock table) {
                return table(table, column, y);
            }
            return y;
        }

        private void addAnchor(Heading heading, int y) {
            String headingText = plainText(heading).strip().toLowerCase(Locale.ROOT);
            String slug = ANCHOR_PUNCTUATION.matcher(headingText).replaceAll("").replace(' ', '-');
            if (slug.isEmpty() || anchors.putIfAbsent(slug, y) == null) {
                return;
            }
            int suffix = nextAnchorSuffix.getOrDefault(slug, 1);
            while (anchors.putIfAbsent(slug + "-" + suffix, y) != null) {
                suffix++;
            }
            nextAnchorSuffix.put(slug, suffix + 1);
        }

        private int list(ListBlock list, Column column, int y) {
            int number = list instanceof OrderedList ordered ? ordered.getMarkerStartNumber() : 1;
            int gap = list.isTight() ? 2 : 6;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                String marker = list instanceof OrderedList ? number++ + "." : "•";
                if (item.getFirstChild() instanceof TaskListItemMarker task) {
                    marker = task.isChecked() ? "[x]" : "[ ]";
                }
                int indent = font.width(marker) + 5;
                int markerEnd = text(FormattedText.of(marker, BODY.withColor(ACCENT)), column, y, 1, Alignment.LEFT);
                int contentEnd = column.width() >= indent + 24
                        ? blocks(item, column.inset(indent, 0), y, gap)
                        : blocks(item, column, markerEnd, gap);
                y = Math.max(contentEnd, markerEnd);
                if (item.getNext() != null) {
                    y += gap;
                }
            }
            return y;
        }

        private int codeBlock(String source, Column column, int y) {
            int background = elements.size();
            int end = y + 4;
            String code = source.endsWith("\n") ? source.substring(0, source.length() - 1) : source;
            for (String line : code.split("\n", -1)) {
                end = text(FormattedText.of(line.replace("\t", "    "), CODE),
                        column.inset(5, 5), end, 1, Alignment.LEFT);
            }
            end += 3;
            elements.add(background, new Rect(column.x(), y, column.width(), end - y, PANEL));
            return end;
        }

        private int table(TableBlock table, Column column, int y) {
            List<List<TableCell>> rows = new ArrayList<>();
            for (Node group = table.getFirstChild(); group != null; group = group.getNext()) {
                for (Node row = group.getFirstChild(); row != null; row = row.getNext()) {
                    List<TableCell> cells = new ArrayList<>();
                    for (Node cell = row.getFirstChild(); cell instanceof TableCell entry; cell = cell.getNext()) {
                        cells.add(entry);
                    }
                    rows.add(cells);
                }
            }
            if (rows.isEmpty() || rows.get(0).isEmpty()) {
                return y;
            }
            List<TableCell> headers = rows.get(0);
            boolean stacked = column.width() / headers.size() < 28;
            int firstRow = stacked && rows.size() > 1 ? 1 : 0;
            for (int row = firstRow; row < rows.size(); row++) {
                y = tableRow(rows.get(row), headers, column, y, row, stacked);
            }
            return y;
        }

        private int tableRow(List<TableCell> cells, List<TableCell> headers, Column column,
                             int y, int row, boolean stacked) {
            int background = elements.size();
            int end = y + 4;
            for (int i = 0; i < cells.size(); i++) {
                int left = stacked ? 0 : i * column.width() / headers.size();
                int right = stacked ? column.width() : (i + 1) * column.width() / headers.size();
                Column cellColumn = new Column(column.x() + left, right - left).inset(4, 4);
                TableCell cell = cells.get(i);
                if (stacked && row > 0) {
                    end = inline(headers.get(i), cellColumn, end, BODY.withColor(ACCENT).withBold(true),
                            1, Alignment.LEFT);
                }
                int cellY = stacked ? end : y + 4;
                Style style = cell.isHeader() ? BODY.withColor(ACCENT).withBold(true) : BODY;
                Alignment alignment = stacked || cell.getAlignment() == null ? Alignment.LEFT : cell.getAlignment();
                end = Math.max(end, inline(cell, cellColumn, cellY, style, 1, alignment) + 4);
            }
            int color = row == 0 ? 0xFF273343 : row % 2 == 0 ? PANEL : 0xFF151E29;
            elements.add(background, new Rect(column.x(), y, column.width(), end - y, color));
            elements.add(new Rect(column.x(), end - 1, column.width(), 1, BORDER));
            return end;
        }

        private int inline(Node parent, Column column, int y, Style style, float scale, Alignment alignment) {
            InlineFlow flow = new InlineFlow(column, y, scale, alignment);
            flow.visitChildren(parent, style);
            flow.flush();
            return flow.y == y ? y + lineHeight(scale) : flow.y;
        }

        private int text(FormattedText text, Column column, int y, float scale, Alignment alignment) {
            int lineHeight = lineHeight(scale);
            int width = Math.max(1, (int) (column.width() / scale));
            List<FormattedCharSequence> lines = font.split(text, width);
            for (FormattedCharSequence line : lines) {
                int lineWidth = (int) Math.ceil(font.width(line) * scale);
                int free = Math.max(0, column.width() - lineWidth);
                int margin = switch (alignment) {
                    case RIGHT -> free;
                    case CENTER -> free / 2;
                    case LEFT -> 0;
                };
                elements.add(new Line(column.x() + margin, y, lineWidth, lineHeight - 2, scale, line));
                y += lineHeight;
            }
            return lines.isEmpty() ? y + lineHeight : y;
        }

        private int lineHeight(float scale) {
            return (int) Math.ceil(font.lineHeight * scale) + 2;
        }

        private final class InlineFlow {
            private final Column column;
            private final float scale;
            private final Alignment alignment;
            private final List<FormattedText> pending = new ArrayList<>();
            private int y;

            private InlineFlow(Column column, int y, float scale, Alignment alignment) {
                this.column = column;
                this.y = y;
                this.scale = scale;
                this.alignment = alignment;
            }

            private void visitChildren(Node parent, Style style) {
                for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                    if (child instanceof Text text) {
                        append(text.getLiteral(), style);
                    } else if (child instanceof Code code) {
                        append(code.getLiteral(), style.withColor(CODE.getColor()).withFont(CODE.getFont()));
                    } else if (child instanceof HtmlInline html) {
                        append(html.getLiteral(), style);
                    } else if (child instanceof StrongEmphasis) {
                        visitChildren(child, style.withBold(true));
                    } else if (child instanceof Emphasis) {
                        visitChildren(child, style.withItalic(true));
                    } else if (child instanceof Strikethrough) {
                        visitChildren(child, style.withStrikethrough(true));
                    } else if (child instanceof Link link) {
                        visitChildren(child, linkStyle(link.getDestination(), style));
                    } else if (child instanceof Image image) {
                        picture(image, style);
                    } else if (child instanceof SoftLineBreak) {
                        append(" ", style);
                    } else if (child instanceof HardLineBreak) {
                        if (pending.isEmpty()) {
                            y += lineHeight(scale);
                        } else {
                            flush();
                        }
                    }
                }
            }

            private void append(String value, Style style) {
                pending.add(FormattedText.of(value, style));
            }

            private void flush() {
                if (!pending.isEmpty()) {
                    y = text(FormattedText.composite(pending), column, y, scale, alignment);
                    pending.clear();
                }
            }

            private void picture(Image image, Style style) {
                var loaded = resources.image(image.getDestination());
                if (loaded.isEmpty()) {
                    append(plainText(image), style.withColor(MUTED).withItalic(true));
                    return;
                }
                flush();
                MarkdownResources.Image asset = loaded.get();
                float fit = Math.min(1, Math.min((float) column.width() / asset.width(),
                        (float) Math.max(1, viewportHeight - 4) / asset.height()));
                int width = Math.max(1, (int) (asset.width() * fit));
                int height = Math.max(1, (int) (asset.height() * fit));
                elements.add(new Picture(column.x(), y + 2, width, height, asset.texture(), style));
                y += height + 4;
            }
        }
    }

    private static Style linkStyle(String destination, Style style) {
        try {
            URI uri = new URI(destination);
            ClickEvent click;
            if (destination.startsWith("#")) {
                click = new ClickEvent(ClickEvent.Action.CHANGE_PAGE, uri.getFragment());
            } else if (uri.getHost() != null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                click = new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString());
            } else {
                return style;
            }
            return style.withColor(0x8CC9FF).withUnderlined(true).withClickEvent(click);
        } catch (URISyntaxException ignored) {
            return style;
        }
    }

    private static String plainText(Node parent) {
        StringBuilder text = new StringBuilder();
        appendPlainText(parent, text);
        return text.toString();
    }

    private static void appendPlainText(Node parent, StringBuilder text) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text literal) {
                text.append(literal.getLiteral());
            } else if (child instanceof Code code) {
                text.append(code.getLiteral());
            } else if (child instanceof HardLineBreak || child instanceof SoftLineBreak) {
                text.append(' ');
            } else {
                appendPlainText(child, text);
            }
        }
    }
}
