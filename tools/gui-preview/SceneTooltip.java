import java.nio.file.Path;
import java.util.Map;

/**
 * 绯红誓约（Crimson Vow）物品 tooltip 的<b>实机级</b>离屏预览。
 *
 * <h3>这个场景画的是哪条链路</h3>
 * <pre>
 *   底板：TooltipStyleHook（RenderTooltipEvent.Color）→ TooltipShaders.drawThermal
 *         → rendertype_tooltip_thermal（内容区外扩 {@link #PADDING} 的 quad）
 *   边框：TooltipRenderUtil 的 1px 内框（贴在内侧、上亮下暗、左右为纵向渐变）
 *   文字：原版 ClientTextTooltip —— 名字 / 誓词 / 三条说明行，每行一对逐字渐变
 *   图像：ClientBlockBarTooltip —— 名字之后 index 1 的减伤条（7 次 fill + 2 次 drawString）
 * </pre>
 *
 * <p>只照抄游戏 Java 的<b>几何 / 颜色 / uniform 数值</b>，不照抄 RenderSystem 调用：
 * 这里是同一个 FBO 上一次画完所有层，不需要 {@code gui.flush()} / 状态还原。</p>
 *
 * <h3>布局来源（逐条对过源码）</h3>
 * <ul>
 *   <li>内容区 {@code (x, y, w, h)}：{@code w = max(各组件宽度)}，
 *       {@code h = Σ getHeight}（组件数 &gt; 1 时没有 -2 —— 见
 *       {@code GuiGraphics.renderTooltipInternal}）。</li>
 *   <li>组件顺序：名字（index 0）→ 图像组件（Forge 固定插在 index 1）→ 名字后 2px →
 *       誓词 / 格挡 / 起誓 / 誓约四行（每行 {@code getHeight() == 10}，行间无额外间隙）。</li>
 *   <li>底板 quad = 内容区外扩 4；1px 边框在底板内侧 1px（原版那圈「外扩 1 的底色线」
 *       在热力效果生效时 alpha 被设成 0，因此视觉上最外沿是底板、边框退进来 1px）。
 *       边框左右两条是<b>纵向渐变</b>（borderTop → borderBottom），不是整条暗色。</li>
 * </ul>
 *
 * <h3>已知近似（与实机的差异，都是预览工具边界造成的）</h3>
 * <ul>
 *   <li><b>文字</b>：位图字体没有中文、没有 unifont，也没有斜体。所以用 en_us 文案；
 *       语言文件里的 {@code ·}（U+00B7 走 unifont）换成 {@code -}；誓词的斜体丢失。</li>
 *   <li><b>名字前缀</b>：实机显示名是 {@code "★ Crimson Vow"}（★ 走 unifont），
 *       预览只画 {@code "Crimson Vow"}，逐字渐变按字符索引 0 起算（少 2 个码点的相位）。</li>
 *   <li><b>渐变方式</b>：实机 tooltip 的文字是 <b>FlowingNameColors.flowing</b>
 *       （按时间来回流动的三角波）；本场景按任务要求画成"沿字符索引 from→to 的静态渐变"，
 *       配色数值一致、只是不随时间流动（时间只驱动底板热流）。</li>
 *   <li><b>未画</b>：原版属性块（{@code +7 Entity Reach} 等，来自 ItemStack 属性，不接 MC 类）
 *       和 F3+H 调试行（只在高级 tooltip 出现）。这两块不涉及本 tooltip 的自绘内容。</li>
 *   <li><b>位置</b>：实机按鼠标 +12/-12 定位；预览居中，方便看构图。</li>
 *   <li><b>不做换行</b>：按原版「宽度 = 最宽行」；en_us 最宽的「起誓」行（标签 + 正文）554px，
 *       加上外扩 8 的底板共 562px，仍小于默认 640 画布，不会被裁切。</li>
 * </ul>
 *
 * <h3>产物</h3>
 * <ul>
 *   <li>{@code tooltip_ingame.png}：world backdrop + 居中 tooltip（实机观感）</li>
 *   <li>{@code tooltip_ingame_t3.png}：同一构图、time+3s（看热流在动）</li>
 *   <li>{@code tooltip_flat.png} / {@code tooltip_flat_t3.png}：纯黑底，只看图案</li>
 *   <li>{@code tooltip_plain.png}：{@code uIntensity = 0} 的对照帧（没有热力效果时的底色/边框）</li>
 * </ul>
 */
public final class SceneTooltip {

    private SceneTooltip() {}

    // ══════════════════════════════════════════════════════════════════
    //  实机常量 —— 全部照抄源码；这里改一个数就不再是"实机级"了
    // ══════════════════════════════════════════════════════════════════

    /** {@code TooltipStyleHook.PADDING}：效果层 = 内容区外扩 4（原版底色外扩 3 + 1px 描边）。 */
    private static final int PADDING = 4;

    /** {@code CrimsonVow.TOOLTIP_BG_TOP}：深紫红。热力底板的冷底 {@code uBaseColor} 就是它。 */
    private static final int TOOLTIP_BG_TOP = 0xF01E0D28;

    /**
     * {@code CrimsonVow.TOOLTIP_BG_BOTTOM}：更深的紫黑。
     *
     * <p>只有「着色器没加载成功 → 退回纯色底板」时才画（{@code TooltipStyleHook} 的兜底路径）；
     * 本预览画的是着色器成功的那条路，所以它只在注释里留档。</p>
     */
    @SuppressWarnings("unused")
    private static final int TOOLTIP_BG_BOTTOM = 0xF00E0716;

    /** {@code CrimsonVow.TOOLTIP_BORDER_TOP} = {@code accent(ACCENT_FROM, 0x80)}。 */
    private static final int TOOLTIP_BORDER_TOP = 0x80FF4FB8;

    /** {@code CrimsonVow.TOOLTIP_BORDER_BOTTOM} = {@code accent(ACCENT_TO, 0x80)}。 */
    private static final int TOOLTIP_BORDER_BOTTOM = 0x809B4DFF;

    // ── CrimsonVow 色板（名字 / 标签 / 条子 / 每行正文各用自己的） ──

    /** 亮粉：名字渐变起色、面板上边框、条子的亮端、减伤数值。 */
    private static final int ACCENT_FROM = 0xFFFF4FB8;

    /** 紫：名字渐变止色、面板下/侧边框、条子的暗端。 */
    private static final int ACCENT_TO = 0xFF9B4DFF;

    /** 雾粉紫：条子标题 + 当前刻度线。 */
    private static final int ACCENT_HIGHLIGHT = 0xFFE8A6FF;

    /** 深紫黑：条子未填充的轨道（必须不透明，否则会被底板渐变吃掉）。 */
    private static final int ACCENT_SHADOW = 0xFF2A1B3D;

    /** 誓词（斜体）的渐变：浅紫 → 亮粉。 */
    private static final int LORE_FROM = 0xFFC9A7FF;
    private static final int LORE_TO = 0xFFFF4FB8;

    /** 「格挡」正文的渐变：灰紫 → 雾粉紫（比标签暗一档）。 */
    private static final int BLOCK_TEXT_FROM = 0xFF9A86C8;
    private static final int BLOCK_TEXT_TO = 0xFFE8A6FF;

    /** 「起誓」正文的渐变：暗红 → 亮粉（最暖的一行）。 */
    private static final int SIGIL_TEXT_FROM = 0xFFC2527A;
    private static final int SIGIL_TEXT_TO = 0xFFFF9AD4;

    /** 「誓约」正文的渐变：冰蓝紫 → 雾紫（有意偏冷）。 */
    private static final int VOW_TEXT_FROM = 0xFF8FD6FF;
    private static final int VOW_TEXT_TO = 0xFFB388FF;

    // ── 热力底板：TooltipShaders.drawThermal 的 uniform ──

    /** {@code TooltipShaders.THERMAL_SPEED}。 */
    private static final float THERMAL_SPEED = 0.16F;

    /** {@code TooltipShaders.THERMAL_SCALE}：热羽特征尺寸（GUI 像素）。 */
    private static final float THERMAL_SCALE = 96.0F;

    /** {@code TooltipShaderSpec.thermal(...)} 的默认强度（{@code CrimsonVow.tooltipShader()} 用它）。 */
    private static final float THERMAL_INTENSITY = 0.9F;

    // ── 减伤条：ClientBlockBarTooltip 的布局常量 ──

    private static final int MIN_WIDTH = 132;
    private static final int MARGIN = 2;
    private static final int INNER_PAD = 2;
    private static final int TEXT_INSET = 4;
    private static final int LABEL_H = 10;
    private static final int LABEL_GAP = 2;
    private static final int BAR_H = 5;
    private static final int LABEL_VALUE_GAP = 24;
    private static final int INNER_FRAME_ALPHA = 0x78;

    /** {@code ClientBlockBarTooltip.HEIGHT}：2*MARGIN + 1 + INNER_PAD + LABEL_H + LABEL_GAP + BAR_H + INNER_PAD + 1 = 27。 */
    private static final int BAR_HEIGHT = MARGIN * 2 + 1 + INNER_PAD + LABEL_H + LABEL_GAP
            + BAR_H + INNER_PAD + 1;

    /**
     * {@code IBlockingWeapon.blockDamageMultiplier()} 的默认值。
     *
     * <p>{@code CrimsonVow} 没有覆写它（它只覆写了粒子/冷却这些），所以 {@code getTooltipImage()}
     * 里构造 {@code BlockBarTooltip} 传的就是这个 0.25 —— 条子填充比例 = 1 - 0.25 = 75%。</p>
     */
    private static final float BLOCK_MULTIPLIER = 0.25F;

    /** {@code ClientTextTooltip.getHeight()}：原版 tooltip 一行文字占 10px（字面 9px）。 */
    private static final int TEXT_LINE_HEIGHT = 10;

    /** {@code TooltipLines.GAP}：标签与正文之间是两个空格。 */
    private static final String GAP = "  ";

    // ══════════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        Map<String, Object> lang = loadLang();
        Panel panel = new Panel(c, lang);

        // ① 纯黑底：只看图案 / 配色。两次不同时间的同一构图像素级对照，能看出热羽在往上走。
        c.clear(0xFF000000);
        panel.draw(c, time, THERMAL_INTENSITY);
        c.save("tooltip_flat");

        c.clear(0xFF000000);
        panel.draw(c, time + 3.0F, THERMAL_INTENSITY);
        c.save("tooltip_flat_t3");

        // ② 对照帧：uIntensity = 0（只留冷底 + 边缘压暗，热流/热核全关）。
        c.clear(0xFF000000);
        panel.draw(c, time, 0.0F);
        c.save("tooltip_plain");

        // ③ 世界背景上的实机观感。最后画的那一帧会被工具再存成 <scene>.png。
        c.backdrop();
        panel.draw(c, time + 3.0F, THERMAL_INTENSITY);
        c.save("tooltip_ingame_t3");

        c.backdrop();
        panel.draw(c, time, THERMAL_INTENSITY);
        c.save("tooltip_ingame");
    }

    // ══════════════════════════════════════════════════════════════════
    //  面板：先按实机规则算一次几何，之后每帧只重画
    // ══════════════════════════════════════════════════════════════════

    /**
     * 一块已经量好的 tooltip。几何只算一次（每帧的宽度/行高与文案无关），
     * 每帧只是按不同 {@code uTime}/{@code uIntensity} 重画一遍。
     */
    private static final class Panel {

        // ── 文案（来自 en_us.json；加载失败时用同值的兜底） ──
        private final String name;
        private final String lore;
        private final FeatureLine blockLine;
        private final FeatureLine sigilLine;
        private final FeatureLine traitLine;

        // ── 减伤条的数据（CrimsonVow.getTooltipImage() 构造的 BlockBarTooltip） ──
        private final String barLabel;
        private final String barValue;
        private final int barWidth;

        // ── 内容区（screen 绝对坐标 + 宽高，单位 = GUI 像素） ──
        private final int contentX;
        private final int contentY;
        private final int contentWidth;
        private final int contentHeight;

        Panel(PreviewCanvas c, Map<String, Object> lang) {
            this.name = previewSafe(langText(lang, "item.rotten_ruins.crimson_vow", "Crimson Vow"));
            this.lore = previewSafe(langText(lang,
                    "item.rotten_ruins.crimson_vow.tooltip.lore",
                    "\"By blood I vow: this blade shall not break.\""));

            // TooltipLines.feature(labelKey, bodyKey, level, ACCENT_FROM, ACCENT_TO, 正文一对色)
            this.blockLine = new FeatureLine(
                    safeLang(lang, "rotten_ruins.tooltip.label.block", "Block"),
                    ACCENT_FROM, ACCENT_TO,
                    safeLang(lang, "rotten_ruins.tooltip.block",
                            "Hold right-click to raise the blade - frontal attacks only"),
                    BLOCK_TEXT_FROM, BLOCK_TEXT_TO);
            this.sigilLine = new FeatureLine(
                    safeLang(lang, "item.rotten_ruins.crimson_vow.tooltip.label.sigil", "Oath"),
                    ACCENT_FROM, ACCENT_TO,
                    safeLang(lang, "item.rotten_ruins.crimson_vow.tooltip.sigil",
                            "Sneak + right-click to raise the blade - when fully charged, "
                                    + "unfurl a crimson sigil where you look (20s)"),
                    SIGIL_TEXT_FROM, SIGIL_TEXT_TO);
            this.traitLine = new FeatureLine(
                    safeLang(lang, "item.rotten_ruins.crimson_vow.tooltip.label.trait", "Vow"),
                    ACCENT_FROM, ACCENT_TO,
                    safeLang(lang, "rotten_ruins.tooltip.trait",
                            "Unbreakable - Fireproof - Returned after crafting"),
                    VOW_TEXT_FROM, VOW_TEXT_TO);

            // 减伤条：label/value 与宽度公式逐字照抄 ClientBlockBarTooltip。
            this.barLabel = safeLang(lang,
                    "rotten_ruins.tooltip.bar.label", "Block mitigation");
            String valueFormat = safeLang(lang, "rotten_ruins.tooltip.bar.value", "%s%%");
            int percent = Math.round((1.0F - BLOCK_MULTIPLIER) * 100.0F);
            String value;
            try {
                value = String.format(valueFormat, percent);
            } catch (RuntimeException e) {
                value = percent + "%";
            }
            this.barValue = value;
            this.barWidth = Math.max(MIN_WIDTH,
                    c.textWidth(barLabel) + LABEL_VALUE_GAP + c.textWidth(barValue)
                            + (TEXT_INSET + 1) * 2);

            // 组件宽度 = 各行文字宽度；原版宽度 = 最宽的那一行（这里也包含名字行与图像组件）。
            int width = Math.max(c.textWidth(name), barWidth);
            width = Math.max(width, c.textWidth(lore));
            width = Math.max(width, blockLine.width(c));
            width = Math.max(width, sigilLine.width(c));
            width = Math.max(width, traitLine.width(c));
            this.contentWidth = width;

            // 组件高度 = 名字 10 + 图像组件 27 + 4 行文字 * 10 = 77（+2px 的"名字后间隙"
            // 只体现在绘制走位上，不进 contentHeight —— 与 renderTooltipInternal 一致）。
            this.contentHeight = TEXT_LINE_HEIGHT + BAR_HEIGHT + 4 * TEXT_LINE_HEIGHT;

            this.contentX = Math.max(0, (c.width - contentWidth) / 2);
            this.contentY = Math.max(0, (c.height - contentHeight) / 2);

            System.out.println("[tooltip] content " + contentWidth + "x" + contentHeight
                    + " @ (" + contentX + "," + contentY + ")"
                    + " | bar " + barWidth + "x" + BAR_HEIGHT
                    + " | name " + c.textWidth(name) + "px"
                    + " | lines lore=" + c.textWidth(lore)
                    + " block=" + blockLine.width(c)
                    + " sigil=" + sigilLine.width(c)
                    + " trait=" + traitLine.width(c));
        }

        /** 画一帧：底板 → 1px 边框 → 文字 / 减伤条（顺序 = renderTooltipInternal 的层次）。 */
        void draw(PreviewCanvas c, float time, float intensity) {
            drawThermalFloor(c, time, intensity);
            drawTooltipBorder(c);

            // index 0：名字（行高 10，名字后额外 2px 才开始下一个组件）。
            drawGradientRun(c, name, contentX, contentY, ACCENT_FROM, ACCENT_TO, true);

            // index 1：减伤条（Forge 固定插在名字之后）。
            drawBlockBar(c, contentX, contentY + TEXT_LINE_HEIGHT + 2);

            // index 2..5：誓词 / 格挡 / 起誓 / 誓约，每行 10px、行间无额外间隙。
            int y = contentY + TEXT_LINE_HEIGHT + 2 + BAR_HEIGHT;
            drawGradientRun(c, lore, contentX, y, LORE_FROM, LORE_TO, true);
            y += TEXT_LINE_HEIGHT;
            blockLine.draw(c, contentX, y);
            y += TEXT_LINE_HEIGHT;
            sigilLine.draw(c, contentX, y);
            y += TEXT_LINE_HEIGHT;
            traitLine.draw(c, contentX, y);
        }

        // ──────────────────────────────────────────────────────────
        //  底板：rendertype_tooltip_thermal
        // ──────────────────────────────────────────────────────────

        /**
         * 在「原版底色本该占的那块矩形」上跑热力着色器。
         *
         * <p>矩形 = 内容区外扩 {@link #PADDING}；uniform 与 {@code TooltipShaders.drawThermal}
         * 逐条一致（uSpeed / uScale / uIntensity / uPanelSize / 三个颜色 ARGB→归一化 RGBA）。
         * 三层颜色全来自 {@code TooltipShaderSpec.thermal(TOOLTIP_BG_TOP, ACCENT_FROM, ACCENT_HIGHLIGHT)}。</p>
         */
        private void drawThermalFloor(PreviewCanvas c, float time, float intensity) {
            int x = contentX - PADDING;
            int y = contentY - PADDING;
            int w = contentWidth + PADDING * 2;
            int h = contentHeight + PADDING * 2;

            PreviewShader shader = c.shader("rendertype_tooltip_thermal");
            // 注意顺序：uniform 设完立刻画、中间不插别的绘制（实机
            // TooltipShaders.drawThermal 也是这个顺序 —— Uniform.set() 只是记值，
            // 上传发生在 apply/draw；中间换 program 就会把上一份值用到下一次绘制里）。
            shader.set("uTime", time);
            shader.set("uSpeed", THERMAL_SPEED);
            shader.set("uScale", THERMAL_SCALE);
            shader.set("uIntensity", intensity);
            shader.set("uPanelSize", (float) w, (float) h);
            shader.set("uBaseColor", normalizedRgba(TOOLTIP_BG_TOP));
            shader.set("uFlowColor", normalizedRgba(ACCENT_FROM));
            shader.set("uHotColor", normalizedRgba(ACCENT_HIGHLIGHT));

            c.shaderQuad(shader, x, y, w, h);
        }

        // ──────────────────────────────────────────────────────────
        //  1px 边框：TooltipRenderUtil.renderTooltipBackground 的内框
        // ──────────────────────────────────────────────────────────

        /**
         * 原版的 1px 边框。
         *
         * <p>几何照 {@code TooltipRenderUtil}：底色矩形从内容区外扩 3，外面再让背景色本身
         * 铺一圈 1px（外扩 4）—— 但热力效果生效时背景色 alpha 被设成 0，所以那圈最外面
         * 是<b>效果层自己</b>的 1px 边，真正可见的边框退在内容区外扩 3 的位置，
         * 也就是底板内侧 1px（差 1px 的话边缘会露出一条缝/一圈亮边）。</p>
         *
         * <p>颜色分布也与原版一致：上边 {@code borderTop}、下边 {@code borderBottom}、
         * 左右两条是 {@code borderTop → borderBottom} 的<b>纵向渐变</b>
         * （{@code renderVerticalLineGradient}），不是整条暗色。</p>
         */
        private void drawTooltipBorder(PreviewCanvas c) {
            int left = contentX - 3;
            int top = contentY - 3;
            int width = contentWidth + 6;
            int height = contentHeight + 6;

            c.fill(left, top, width, 1, TOOLTIP_BORDER_TOP);
            c.fill(left, top + height - 1, width, 1, TOOLTIP_BORDER_BOTTOM);
            c.fillGradientV(left, top + 1, 1, height - 2,
                    TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
            c.fillGradientV(left + width - 1, top + 1, 1, height - 2,
                    TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        }

        // ──────────────────────────────────────────────────────────
        //  减伤条：ClientBlockBarTooltip.renderImage 1:1
        // ──────────────────────────────────────────────────────────

        /**
         * 减伤条组件（7 次 fill + 2 次 drawString）。
         *
         * <p>绘制顺序与源码一致：1px 内框（上亮下暗）→ 标题左 / 数值右 → 轨道 → 已填充段
         * 纵向渐变（accentFrom → accentTo）→ 当前刻度亮线。文字<b>无阴影</b>
         * （源码 {@code drawString(..., false)}）；条子的两端与文字左右缘对齐。</p>
         */
        private void drawBlockBar(PreviewCanvas c, int x, int y) {
            final int width = barWidth;
            final int frameLeft = x + 1;
            final int frameTop = y + MARGIN;
            final int frameRight = x + width - 1;
            final int frameBottom = y + BAR_HEIGHT - MARGIN;

            // 1) 1px 内框：上边亮端、其余暗端，alpha 压到 0x78（底板边框才是主角）。
            final int frameFrom = withAlpha(ACCENT_FROM, INNER_FRAME_ALPHA);
            final int frameTo = withAlpha(ACCENT_TO, INNER_FRAME_ALPHA);
            c.fill(frameLeft, frameTop, frameRight - frameLeft, 1, frameFrom);
            c.fill(frameLeft, frameBottom - 1, frameRight - frameLeft, 1, frameTo);
            c.fill(frameLeft, frameTop + 1, 1, frameBottom - frameTop - 2, frameTo);
            c.fill(frameRight - 1, frameTop + 1, 1, frameBottom - frameTop - 2, frameTo);

            // 2) 标题行：左侧标题（高光色）、右侧右对齐数值（亮端），均无阴影。
            final int textTop = frameTop + 1 + INNER_PAD;
            final int contentLeft = frameLeft + 1 + TEXT_INSET;
            final int contentRight = frameRight - 1 - TEXT_INSET;
            c.text(barLabel, contentLeft, textTop, ACCENT_HIGHLIGHT, false);
            c.textRight(barValue, contentRight, textTop, ACCENT_FROM, false);

            // 3) 条子：轨道用不透明暗部色 → 已填充段用纵向渐变 → 当前刻度一条比条子上下各高 1px 的亮线。
            final int barTop = textTop + LABEL_H + LABEL_GAP;
            final int barBottom = barTop + BAR_H;
            c.fill(contentLeft, barTop, contentRight - contentLeft, BAR_H, ACCENT_SHADOW);

            final int filled = Math.round((contentRight - contentLeft) * (1.0F - BLOCK_MULTIPLIER));
            if (filled <= 0) {
                return;
            }
            c.fillGradientV(contentLeft, barTop, filled, BAR_H, ACCENT_FROM, ACCENT_TO);
            c.fill(contentLeft + filled - 1, barTop - 1, 1, BAR_H + 2, ACCENT_HIGHLIGHT);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  一行「标签 + 正文」：TooltipLines.feature 的绘制语义
    // ══════════════════════════════════════════════════════════════════

    /**
     * 一行由「标签 + 两个空格 + 正文」组成，两半<b>各自</b>一对逐字渐变
     * （标签用强调色板，正文用它自己那对颜色 —— 一行之内标签亮、正文暗）。
     *
     * <p>渐变按任务要求沿字符索引线性铺开（from → to）。实机这两半其实分别是
     * {@code FlowingNameColors.flowing} 的流动相位，配色相同、只是会随时间来回走。</p>
     */
    private record FeatureLine(String label, int labelFrom, int labelTo,
                               String body, int bodyFrom, int bodyTo) {

        /** 整行宽度 = 标签 + 两空格 + 正文（GAP 的两个空格也有宽度）。 */
        int width(PreviewCanvas c) {
            int width = c.textWidth(body);
            if (!label.isEmpty()) {
                width += c.textWidth(label) + c.textWidth(GAP);
            }
            return width;
        }

        void draw(PreviewCanvas c, int x, int y) {
            int penX = x;
            if (!label.isEmpty()) {
                drawGradientRun(c, label, penX, y, labelFrom, labelTo, true);
                penX += c.textWidth(label) + c.textWidth(GAP);
            }
            drawGradientRun(c, body, penX, y, bodyFrom, bodyTo, true);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  文字：MC 位图字体 + 逐字渐变
    // ══════════════════════════════════════════════════════════════════

    /**
     * 逐字画一行文字，第 i 个字取 {@code from → to} 线性渐变的第 {@code i/(n-1)} 个采样
     * （与 {@code FlowingNameColors.gradient} 的取值方式相同）。
     *
     * <p>逐字画而不是整串画：位图字体没有字距调整，按 {@code textWidth(ch)} 推进笔位
     * 与整串渲染完全等价；阴影由 canvas 的 {@code text(..., shadow=true)} 提供
     * （原版 tooltip 文字同样带投影 —— {@code ClientTextTooltip.renderText} 传的是 true）。</p>
     */
    private static void drawGradientRun(PreviewCanvas c, String text, int x, int y,
                                        int from, int to, boolean shadow) {
        int count = Math.max(1, text.codePointCount(0, text.length()) - 1);
        int penX = x;
        int index = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            i += Character.charCount(codePoint);
            int color = lerpRgb(from, to, index / (float) count);
            c.text(glyph, penX, y, color, shadow);
            penX += c.textWidth(glyph);
            index++;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  小工具
    // ══════════════════════════════════════════════════════════════════

    /** 两色 RGB 线性插值（只取 RGB，alpha 固定 0xFF —— 与原版 TextColor 一致）。 */
    private static int lerpRgb(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF;
        int ag = (from >> 8) & 0xFF;
        int ab = from & 0xFF;
        int br = (to >> 16) & 0xFF;
        int bg = (to >> 8) & 0xFF;
        int bb = to & 0xFF;
        int r = clamp(ar + (int) ((br - ar) * t));
        int g = clamp(ag + (int) ((bg - ag) * t));
        int b = clamp(ab + (int) ((bb - ab) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** 只换 alpha、保留 RGB —— 与 {@code ClientBlockBarTooltip.withAlpha} 同义。 */
    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** ARGB → 归一化 RGBA（{@code TooltipShaders.set4} 的算法：alpha 参与）。 */
    private static float[] normalizedRgba(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F};
    }

    /** 读 en_us.json（datagen 产物）；失败时返回空表，所有文案走兜底字符串。 */
    private static Map<String, Object> loadLang() {
        Path file = Path.of("src/generated/resources/assets/rotten_ruins/lang/en_us.json");
        try {
            return MiniJson.load(file);
        } catch (Exception e) {
            System.out.println("[tooltip] 读不到 en_us.json（" + file + "），文案用兜底值: " + e);
            return Map.of();
        }
    }

    private static String langText(Map<String, Object> lang, String key, String fallback) {
        return MiniJson.getStr(lang, key, fallback);
    }

    /** 读文案并做"预览字体可显示"的清洗（见 {@link #previewSafe}）。 */
    private static String safeLang(Map<String, Object> lang, String key, String fallback) {
        return previewSafe(langText(lang, key, fallback));
    }

    /**
     * 画面里的文字必须是字库覆盖得到的字符（{@code ascii.png} 只有 0x20–0x7E + 少量拉丁/制表符）。
     *
     * <p>en_us 文案里的 {@code ·}（U+00B7）在实机走 unifont、预览字体没有，
     * 留着会被画成占位方块，所以换成视觉最接近的 ASCII {@code -}。
     * 其余常用标点（弯引号、破折号）同理做保底替换。</p>
     */
    private static String previewSafe(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u00B7', '-')   // ·
                .replace('\u2013', '-')   // –
                .replace('\u2014', '-')   // —
                .replace('\u2018', '\'')  // ‘
                .replace('\u2019', '\'')  // ’
                .replace('\u201C', '"')   // “
                .replace('\u201D', '"');  // ”
    }
}
