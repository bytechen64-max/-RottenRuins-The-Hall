import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.bytechen.rottenruins.client.story.StoryTextLayers;

import java.nio.ByteOrder;

/** Uses Minecraft's actual quad sorter to reproduce the dark-left-half regression. */
public final class StoryTextOrderTest {
    public static void main(String[] args) throws Exception {
        boolean regressionReproduced = false;
        boolean sort = StoryTextLayers.SORT_QUADS;
        for (int visible = 1; visible <= 24; visible++) {
            // Multiple atlas batches and progressively revealed text, on both sides of the center.
            for (int start = 0; start < visible; start += 5) {
                int end = Math.min(visible, start + 5);
                regressionReproduced |= darkGlyphs(start, end, true) > 0;
                if (darkGlyphs(start, end, sort) != 0) {
                    throw new AssertionError("An offset shadow is sorted over foreground text");
                }
            }
        }
        if (!regressionReproduced) throw new AssertionError("Test must reproduce the original distance-sort failure");
        java.util.List<String> submissions = new java.util.ArrayList<>();
        StoryTextLayers.render(shadow -> {
            for (int atlas = 0; atlas < 4; atlas++) submissions.add((shadow ? "shadow" : "foreground") + atlas);
        }, () -> submissions.add("flush"));
        if (!submissions.equals(java.util.List.of("shadow0", "shadow1", "shadow2", "shadow3", "flush",
                "foreground0", "foreground1", "foreground2", "foreground3", "flush"))) {
            throw new AssertionError("All shadow atlases must finish before foreground submission");
        }
        System.out.println("PASS: legacy darkening reproduced with Minecraft quad sorter; 24 reveal steps and cross-atlas layer barriers");
    }

    private static int darkGlyphs(int start, int end, boolean sort) {
        BufferBuilder builder = new BufferBuilder(4096);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = start; i < end; i++) {
            float x = -1.4f + i * 0.12f;
            quad(builder, x + 0.003f, 0.004f, 0); // shadow
            quad(builder, x, 0, 255); // foreground
        }
        if (sort) builder.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
        var rendered = builder.end();
        try {
            int quads = (end - start) * 2;
            int[] lastLayer = new int[end - start];
            var indices = rendered.indexBuffer().order(ByteOrder.nativeOrder());
            for (int drawn = 0; drawn < quads; drawn++) {
                int quad = rendered.drawState().sequentialIndex() ? drawn
                        : Short.toUnsignedInt(indices.getShort(drawn * 6 * 2)) / 4;
                lastLayer[quad / 2] = quad % 2;
            }
            int dark = 0;
            for (int layer : lastLayer) if (layer == 0) dark++;
            return dark;
        } finally { rendered.release(); }
    }

    private static void quad(BufferBuilder builder, float x, float y, int color) {
        builder.vertex(x, y, -3).color(color, color, color, 255).endVertex();
        builder.vertex(x, y + 0.08f, -3).color(color, color, color, 255).endVertex();
        builder.vertex(x + 0.08f, y + 0.08f, -3).color(color, color, color, 255).endVertex();
        builder.vertex(x + 0.08f, y, -3).color(color, color, color, 255).endVertex();
    }
}
