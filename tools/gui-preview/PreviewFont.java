import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 默认字体的<b>位图部分</b>（{@code ascii.png} + {@code include/default.json}）。
 *
 * <p>为什么不用 AWT 字体：GUI 预览的判断标准之一就是"和游戏里长得像"，而 MC 的
 * 文字是 8×8 像素图、行高 9px、字符前进宽度由像素右边界算出来。AWT 再像也差一截，
 * 面板宽度、换行位置全都不对。</p>
 *
 * <p>资源来源：脚本从 Gradle 缓存里的 {@code client.jar} 抽出
 * {@code assets/minecraft/textures/font/ascii.png}、{@code font/include/default.json}、
 * {@code font/include/space.json} 到 {@code build/gui-preview/mc-assets/}
 * （不把原版资源提交进仓库）。找不到时 {@link #available()} 为 false，文字会退化成
 * 占位方块而不是让整张图失败。</p>
 *
 * <p><b>已知局限</b>：只覆盖位图字体（拉丁/符号）。中日韩走 Unifont 的 unihex provider，
 * 这里不支持 —— 因此场景里的文字请用英文（项目 {@code en_us.json} 里有全部英文文案），
 * 遇到不支持的码点会画一个小空心方块提醒。</p>
 */
final class PreviewFont {

    /** 与原版 {@code Font.lineHeight} 一致。 */
    static final int LINE_HEIGHT = 9;

    /** 一个字形：在字体图上的格子位置、像素宽（=前进宽度）。 */
    record Glyph(int col, int row, int width, int advance) {}

    private final PreviewTexture texture;
    private final int[] pixels;
    private final int imageWidth;
    private final int imageHeight;
    private final int cellWidth;
    private final int cellHeight;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final int spaceAdvance;

    private PreviewFont(PreviewTexture texture, int[] pixels, int imageWidth, int imageHeight,
                        int cellWidth, int cellHeight, int spaceAdvance) {
        this.texture = texture;
        this.pixels = pixels;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.spaceAdvance = spaceAdvance;
    }

    /** 加载失败时的空字体：宽度按 4px/字符估，画不出字但有布局。 */
    static PreviewFont unavailable() {
        return new PreviewFont(null, new int[0], 0, 0, 0, 0, 4);
    }

    static PreviewFont load(Path mcAssetsRoot) {
        Path ascii = mcAssetsRoot.resolve("assets/minecraft/textures/font/ascii.png");
        Path defaultJson = mcAssetsRoot.resolve("assets/minecraft/font/include/default.json");
        Path spaceJson = mcAssetsRoot.resolve("assets/minecraft/font/include/space.json");
        if (!Files.isRegularFile(ascii) || !Files.isRegularFile(defaultJson)) {
            System.out.println("[gui-preview] 没有 MC 字体资源，文字将退化为占位方块（" + mcAssetsRoot + "）");
            return unavailable();
        }
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(ascii.toFile());
            if (image == null) {
                throw new IOException("ascii.png 读不出来");
            }
            int imageW = image.getWidth();
            int imageH = image.getHeight();
            int[] argb = image.getRGB(0, 0, imageW, imageH, null, 0, imageW);

            Map<String, Object> def = MiniJson.load(defaultJson);
            List<Object> providers = MiniJson.getArr(def, "providers");
            Map<String, Object> asciiProvider = null;
            if (providers != null) {
                for (Object o : providers) {
                    Map<String, Object> provider = MiniJson.obj(o);
                    String type = MiniJson.getStr(provider, "type", "");
                    String file = MiniJson.getStr(provider, "file", "");
                    if ("bitmap".equals(type) && file.endsWith("ascii.png")) {
                        asciiProvider = provider;
                        break;
                    }
                }
            }
            if (asciiProvider == null) {
                throw new IOException("default.json 里找不到 ascii.png 的 bitmap provider");
            }

            List<Object> rows = MiniJson.getArr(asciiProvider, "chars");
            if (rows == null || rows.isEmpty()) {
                throw new IOException("ascii provider 没有 chars");
            }
            int rowCount = rows.size();
            int cellW = imageW / 16;          // 原版固定 16 列
            int cellH = imageH / rowCount;

            int space = 4;
            if (Files.isRegularFile(spaceJson)) {
                Map<String, Object> spaceDef = MiniJson.load(spaceJson);
                List<Object> spaceProviders = MiniJson.getArr(spaceDef, "providers");
                if (spaceProviders != null) {
                    for (Object o : spaceProviders) {
                        Map<String, Object> provider = MiniJson.obj(o);
                        if (!"space".equals(MiniJson.getStr(provider, "type", ""))) {
                            continue;
                        }
                        Map<String, Object> advances = MiniJson.getObj(provider, "advances");
                        if (advances != null && advances.containsKey(" ")) {
                            space = MiniJson.i(advances.get(" "));
                        }
                    }
                }
            }

            PreviewFont font = new PreviewFont(PreviewTexture.load(ascii), argb,
                    imageW, imageH, cellW, cellH, space);
            font.readGlyphs(rows, cellW, cellH);
            return font;
        } catch (Exception e) {
            System.out.println("[gui-preview] 字体加载失败，文字退化为占位方块: " + e);
            return unavailable();
        }
    }

    private void readGlyphs(List<Object> rows, int cellW, int cellH) {
        for (int row = 0; row < rows.size(); row++) {
            String line = MiniJson.str(rows.get(row));
            int col = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == 0) {
                    col++;
                    continue;
                }
                int advance = measureAdvance(col, row, cellW, cellH) + 1;   // +1：原版字形的右侧留 1px 字距
                glyphs.put(cp, new Glyph(col, row, advance - 1, advance));
                col++;
            }
        }
    }

    /** 与 MC {@code BitmapProvider.getActualGlyphWidth} 同一算法：最右一个不透明像素列 + 1。 */
    private int measureAdvance(int col, int row, int cellW, int cellH) {
        for (int x = cellW - 1; x >= 0; x--) {
            int imageX = col * cellW + x;
            for (int y = 0; y < cellH; y++) {
                int imageY = row * cellH + y;
                if (((pixels[imageY * imageWidth + imageX] >>> 24) & 0xFF) != 0) {
                    return x + 1;
                }
            }
        }
        return 1;
    }

    boolean available() {
        return texture != null;
    }

    PreviewTexture texture() {
        return texture;
    }

    int[] pixels() {
        return pixels;
    }

    int imageWidth() {
        return imageWidth;
    }

    int imageHeight() {
        return imageHeight;
    }

    int cellWidth() {
        return cellWidth;
    }

    int cellHeight() {
        return cellHeight;
    }

    /** 像素 alpha（0..255）。 */
    int alphaAt(int imageX, int imageY) {
        if (imageX < 0 || imageY < 0 || imageX >= imageWidth || imageY >= imageHeight) {
            return 0;
        }
        return (pixels[imageY * imageWidth + imageX] >>> 24) & 0xFF;
    }

    Glyph glyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    int advance(int codepoint) {
        if (codepoint == ' ') {
            return spaceAdvance;
        }
        Glyph glyph = glyphs.get(codepoint);
        return glyph == null ? 0 : glyph.advance();
    }

    int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int advance = advance(cp);
            width += advance == 0 ? 6 : advance;
        }
        return width;
    }

    /** 写文字时用的纹理单元（canvas 内部使用）。 */
    void bind(int unit) {
        if (texture != null) {
            texture.bind(unit);
        }
    }
}
