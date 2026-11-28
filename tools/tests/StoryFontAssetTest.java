import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.stb.STBTruetype.*;

/** Loads and rasterizes sample glyphs with the STB TrueType engine used by Minecraft 1.20.1. */
public final class StoryFontAssetTest {
    public static void main(String[] args) throws Exception {
        String sample = "你在陌生的废墟中醒来。远方，那颗坠落的陨石仍在呼吸。而你，已经听见了它的心跳。Aa123";
        for (String font : new String[]{"wenkai", "xiaowei", "mashan"}) {
            System.out.println("Loading font " + font);
            Path file = Path.of(args[0], font + ".ttf");
            byte[] bytes = Files.readAllBytes(file);
            ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).flip();
            try (STBTTFontinfo info = STBTTFontinfo.malloc(); MemoryStack stack = MemoryStack.stackPush()) {
                if (!stbtt_InitFont(info, data)) throw new AssertionError("Cannot load " + font);
                String definition = Files.readString(Path.of(args[0], font + ".json"));
                var oversampleField = java.util.regex.Pattern.compile("\"oversample\"\\s*:\\s*([0-9.]+)").matcher(definition);
                var sizeField = java.util.regex.Pattern.compile("\"size\"\\s*:\\s*([0-9.]+)").matcher(definition);
                if (!oversampleField.find() || !sizeField.find()) throw new AssertionError("Missing font sampling settings");
                float oversample = Float.parseFloat(oversampleField.group(1));
                float logicalSize = Float.parseFloat(sizeField.group(1));
                if (oversample < 8) throw new AssertionError("Font sampling is too low for magnified world text");
                float scale = stbtt_ScaleForPixelHeight(info, logicalSize * oversample);
                int largestGlyph = 0;
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var x = stack.mallocInt(1);
                var y = stack.mallocInt(1);
                for (int codePoint : sample.codePoints().distinct().toArray()) {
                    if (stbtt_FindGlyphIndex(info, codePoint) == 0) {
                        throw new AssertionError(font + " is missing sample glyph U+" + Integer.toHexString(codePoint));
                    }
                    ByteBuffer bitmap = stbtt_GetCodepointBitmap(info, scale, scale, codePoint, width, height, x, y);
                    if (bitmap == null) throw new AssertionError("Cannot rasterize glyph in " + font);
                    try {
                        largestGlyph = Math.max(largestGlyph, Math.max(width.get(0), height.get(0)));
                        if (width.get(0) > 256 || height.get(0) > 256) throw new AssertionError("Glyph exceeds the Minecraft font atlas");
                        boolean ink = false;
                        for (int pixel = bitmap.position(); pixel < bitmap.limit(); pixel++) {
                            ink |= bitmap.get(pixel) != 0;
                        }
                        if (!ink) throw new AssertionError("Empty glyph in " + font);
                    } finally { stbtt_FreeBitmap(bitmap); }
                }
                if (largestGlyph < 40) throw new AssertionError("Glyphs still have low-resolution source bitmaps");
                System.out.println("PASS: " + font + " rasterizes all sample glyphs at " + oversample
                        + "x sampling; largest bitmap dimension=" + largestGlyph);
            } finally { MemoryUtil.memFree(data); }
        }
    }
}
