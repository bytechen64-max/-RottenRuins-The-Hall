package org.bytechen.rottenruins.client.story;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import org.bytechen.rottenruins.client.rend.glint.ShaderPackDetector;
import org.bytechen.rottenruins.config.ConfigHelper;
import org.bytechen.rottenruins.gameplay.story.StoryText;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Private world-space text. Translation follows the player; rotation never drags the anchor. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryTextClient {
    private static final float SCALE = 0.0125f;
    private static final int WRAP_WIDTH = 200;
    private static final int PAGE_LINES = 3;
    private static final ResourceLocation STORY_FONT = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "story");
    private static final int MAX_PAGES = 128;
    private static final ArrayDeque<Page> QUEUE = new ArrayDeque<>();
    private static final MultiBufferSource.BufferSource TEXT_BUFFER = MultiBufferSource.immediate(
            new com.mojang.blaze3d.vertex.BufferBuilder(4096));
    private static final MultiBufferSource STORY_BUFFER = type -> TEXT_BUFFER.getBuffer(StoryTextShader.renderType(type));
    private static ClientLevel owner;
    private static Page active;
    private static Vec3 offset = Vec3.ZERO;
    private static StoryPlayback playback;
    private static List<Glyph> glyphs = List.of();
    private static Matrix4f projection;
    private static StoryProjection.Result projected;
    private static float bearing;
    private static float anchorYaw;
    private static boolean readable;
    private static float halfWidth, halfHeight, renderScale = SCALE;
    private static RenderFrame deferredFrame;
    private static Language layoutLanguage;
    private record Options(Boolean shadow, Float entrySpeed, Float exitSpeed, int priority, String session) {}

    private record Paragraph(Component text, int duration, float angle, double distance, double height,
                             boolean newParagraph, boolean clocked, long startTime, boolean autoplay, Options options) {}

    private record Page(List<FormattedCharSequence> lines, int duration, float angle,
                        double distance, double height, boolean newParagraph, boolean clocked, long startTime,
                        Paragraph paragraph, int part, boolean autoplay) {}

    private record Glyph(FormattedCharSequence text, FormattedCharSequence shadow, float x, float y,
                         float width, int line, int character, int globalIndex) {}
    private record DrawGlyph(Glyph glyph, Matrix4f matrix, int light, int alpha) {}
    private record RenderFrame(Page page, ClientLevel level, Matrix4f projection,
                               Matrix4f modelView, List<DrawGlyph> glyphs) {}

    private StoryTextClient() {}

    public static void register() {
        PacketActionRegistry.register(StoryText.ACTION, (data, context) -> {
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT
                    && data.getValue() instanceof CompoundTag tag) receive(tag);
        });
    }

    private static void receive(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (tag.getBoolean("clear")) {
            if (!tag.contains("session")) clear();
            else {
                String canceled = tag.getString("session");
                QUEUE.removeIf(page -> canceled.equals(page.paragraph().options().session()));
                if (active != null && canceled.equals(active.paragraph().options().session())) advance();
            }
            return;
        }
        if (mc.player == null || mc.level == null || !mc.level.dimension().location().toString()
                .equals(tag.getString("dimension"))) return;
        refreshLanguage();
        if (tag.contains("entries")) { receiveDefinition(tag); return; }
        float angle = tag.getFloat("angle");
        double distance = tag.getDouble("distance"), height = tag.getDouble("height");
        if (!Float.isFinite(angle)
                || !Double.isFinite(distance) || !Double.isFinite(height)) return;
        boolean clocked = tag.getBoolean("clocked");
        List<String> paragraphs = new ArrayList<>();
        if (clocked) {
            var encoded = tag.getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
            if (encoded.isEmpty() || encoded.size() > 32) return;
            for (int i = 0; i < encoded.size(); i++) paragraphs.add(encoded.getString(i));
        } else paragraphs.add(tag.getString("text"));
        ArrayDeque<Page> incoming = new ArrayDeque<>();
        int duration = Math.max(20, Math.min(12000, tag.getInt("ticks")));
        for (int paragraph = 0; paragraph < paragraphs.size(); paragraph++) {
            String json = paragraphs.get(paragraph);
            if (json.length() > StoryText.MAX_TEXT_LENGTH) return;
            Component text;
            try { text = Component.Serializer.fromJson(json); }
            catch (RuntimeException invalidText) { return; }
            if (text == null || text.getString().isBlank()) continue;
            incoming.addAll(paginate(new Paragraph(text, duration, angle, Math.max(1, Math.min(8, distance)),
                    Math.max(-2, Math.min(2, height)), !clocked || paragraph == 0, clocked,
                    tag.getLong("startTime") + (long) paragraph * duration, tag.getBoolean("autoplay"),
                    new Options(null, null, null, 0, tag.getString("session")))));
        }
        if (owner != mc.level || !tag.getBoolean("append") || clocked) clear();
        owner = mc.level;
        layoutLanguage = Language.getInstance();
        while (!incoming.isEmpty() && QUEUE.size() < MAX_PAGES) QUEUE.add(incoming.poll());
        if (active == null) advance();
    }

    private static void receiveDefinition(CompoundTag tag) {
        var entries = tag.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > 32) return;
        ArrayDeque<Page> incoming = new ArrayDeque<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String json = entry.getString("text");
            if (json.length() > StoryText.MAX_TEXT_LENGTH) return;
            Component text;
            try { text = Component.Serializer.fromJson(json); }
            catch (RuntimeException invalid) { return; }
            if (text == null) return;
            float angle = entry.getFloat("angle");
            double distance = entry.getDouble("distance"), height = entry.getDouble("height");
            if (!Float.isFinite(angle) || !Double.isFinite(distance) || !Double.isFinite(height)) return;
            Float entrySpeed = entry.contains("entry_speed") ? entry.getFloat("entry_speed") : null;
            Float exitSpeed = entry.contains("exit_speed") ? entry.getFloat("exit_speed") : null;
            if ((entrySpeed != null && (!Float.isFinite(entrySpeed) || entrySpeed < 0.1f || entrySpeed > 100))
                    || (exitSpeed != null && (!Float.isFinite(exitSpeed) || exitSpeed < 0.1f || exitSpeed > 100))) return;
            var options = new Options(entry.contains("shadow") ? entry.getBoolean("shadow") : null,
                    entrySpeed, exitSpeed, tag.getInt("priority"), tag.getString("session"));
            incoming.addAll(paginate(new Paragraph(text, Math.max(20, Math.min(12000, entry.getInt("ticks"))),
                    angle, Math.max(1, Math.min(8, distance)), Math.max(-2, Math.min(2, height)),
                    i == 0 || entry.getBoolean("recapture_anchor"), entry.getBoolean("clocked"),
                    entry.getLong("startTime"), entry.getBoolean("autoplay"), options)));
            if (incoming.size() > MAX_PAGES) return;
        }
        if (incoming.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        String policy = tag.getString("conflict");
        boolean queue = policy.equals("queue");
        if (owner == mc.level && active != null && !queue && (policy.equals("ignore")
                || tag.getInt("priority") < active.paragraph().options().priority())) return;
        if (queue && owner == mc.level && incoming.size() + QUEUE.size() > MAX_PAGES) return;
        if (!queue || owner != mc.level) clear();
        owner = mc.level;
        layoutLanguage = Language.getInstance();
        QUEUE.addAll(incoming);
        if (active == null) advance();
    }

    private static void advance() {
        advance(false);
    }

    private static List<Page> paginate(Paragraph paragraph) {
        List<FormattedCharSequence> lines = Minecraft.getInstance().font.split(paragraph.text().copy().withStyle(style ->
                style.getFont().equals(Style.DEFAULT_FONT) ? style.withFont(STORY_FONT) : style), WRAP_WIDTH);
        int parts = Math.max(1, (lines.size() + PAGE_LINES - 1) / PAGE_LINES);
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < lines.size() && pages.size() < MAX_PAGES; i += PAGE_LINES) {
            int part = i / PAGE_LINES;
            int begin = part * paragraph.duration() / parts, end = (part + 1) * paragraph.duration() / parts;
            pages.add(new Page(List.copyOf(lines.subList(i, Math.min(i + PAGE_LINES, lines.size()))),
                    paragraph.clocked() ? Math.max(1, end - begin) : paragraph.duration(), paragraph.angle(),
                    paragraph.distance(), paragraph.height(), i == 0 && paragraph.newParagraph(), paragraph.clocked(),
                    paragraph.startTime() + begin, paragraph, part, paragraph.autoplay()));
        }
        return pages;
    }

    private static void refreshLanguage() {
        Language current = Language.getInstance();
        if (active == null || layoutLanguage == current) return;
        layoutLanguage = current;
        // FormattedCharSequence stores resolved characters. Rebuild from the untranslated Component,
        // once per paragraph, so a language change also updates wrapping and pending pages.
        List<Page> previous = new ArrayList<>();
        previous.add(active);
        previous.addAll(QUEUE);
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Paragraph, Boolean>());
        QUEUE.clear();
        for (Page page : previous) {
            if (!seen.add(page.paragraph())) continue;
            List<Page> translated = paginate(page.paragraph());
            int firstPart = page.clocked() ? 0 : Math.min(page.part(), Math.max(0, translated.size() - 1));
            for (int i = firstPart; i < translated.size() && QUEUE.size() < MAX_PAGES; i++) QUEUE.add(translated.get(i));
        }
        // Regular pages restart typing in the new language; clocked pages seek to the existing deadline.
        advance(true);
    }

    private static void advance(boolean keepAnchor) {
        boolean captureAnchor = active == null;
        Minecraft mc = Minecraft.getInstance();
        do { active = QUEUE.poll(); }
        while (active != null && active.clocked() && mc.level.getGameTime() >= active.startTime() + active.duration());
        playback = null;
        glyphs = List.of();
        readable = false;
        projected = null;
        if (active == null) return;
        if (!keepAnchor && (active.newParagraph() || captureAnchor)) anchorYaw = mc.player.getYRot();
        double yaw = Math.toRadians(anchorYaw + active.angle());
        bearing = (float) yaw;
        offset = new Vec3(-Math.sin(yaw) * active.distance(), active.height(), Math.cos(yaw) * active.distance());
        halfWidth = (float) active.lines().stream().mapToInt(mc.font::width).max().orElse(0) * SCALE / 2 + 0.04f;
        halfHeight = active.lines().size() * 12 * SCALE / 2;
        String[] textLines = active.lines().stream().map(StoryTextClient::lineText).toArray(String[]::new);
        var config = ConfigHelper.configHolder == null ? null : ConfigHelper.configHolder.get();
        float entrySpeed = config == null ? StoryPlayback.DEFAULT_ENTRY_SPEED : config.storyTextEntrySpeed;
        float exitSpeed = config == null ? StoryPlayback.DEFAULT_EXIT_SPEED : config.storyTextExitSpeed;
        if (active.paragraph().options().entrySpeed() != null) entrySpeed = active.paragraph().options().entrySpeed();
        if (active.paragraph().options().exitSpeed() != null) exitSpeed = active.paragraph().options().exitSpeed();
        playback = active.clocked() ? StoryPlayback.fixed(textLines, active.duration(), entrySpeed, exitSpeed)
                : new StoryPlayback(textLines, active.duration(), entrySpeed, exitSpeed);
        if (active.clocked()) playback.seek(mc.level.getGameTime() - active.startTime());
        List<Glyph> layout = new ArrayList<>();
        for (int line = 0; line < active.lines().size(); line++) {
            FormattedCharSequence sequence = active.lines().get(line);
            float[] x = {-mc.font.getSplitter().stringWidth(sequence) / 2};
            int[] character = {0};
            int lineIndex = line;
            float y = -active.lines().size() * 6 + line * 12;
            sequence.accept((index, style, codePoint) -> {
                FormattedCharSequence single = FormattedCharSequence.codepoint(codePoint, style);
                float width = mc.font.getSplitter().stringWidth(single);
                layout.add(new Glyph(single, FormattedCharSequence.codepoint(codePoint, style.withColor(0x101720)),
                        x[0], y, width, lineIndex, character[0]++, layout.size()));
                x[0] += width;
                return true;
            });
        }
        glyphs = List.copyOf(layout);
    }

    private static void clear() {
        layoutLanguage = null;
        deferredFrame = null;
        active = null;
        owner = null;
        QUEUE.clear();
        projection = null;
        projected = null;
        readable = false;
        playback = null;
        glyphs = List.of();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != owner || mc.player == null || !mc.player.isAlive()) { clear(); return; }
        if (active == null) return;
        refreshLanguage();
        if (active == null) return;
        if (active.clocked()) {
            playback.seek(mc.level.getGameTime() - active.startTime());
            if (playback.finished()) advance();
            return;
        }
        if (mc.isPaused() || !screenAllowsWorldText()) return;
        if (readable || active.autoplay()) {
            playback.tick();
            if (playback.finished()) advance();
        }
    }

    private static String lineText(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }

    private static boolean canRender() {
        Minecraft mc = Minecraft.getInstance();
        return active != null && mc.player != null && mc.level == owner && mc.player.isAlive()
                && screenAllowsWorldText();
    }

    private static boolean screenAllowsWorldText() {
        var screen = Minecraft.getInstance().screen;
        return screen == null || screen instanceof ChatScreen;
    }

    private static Vec3 anchor(float partialTick) {
        return Minecraft.getInstance().player.getEyePosition(partialTick).add(offset);
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        // AFTER_LEVEL supplies GameRenderer's projection stack in Forge 1.20.1.
        // AFTER_ENTITIES supplies the actual world view stack, including camera roll.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        // Shadow cameras must neither draw story text nor replace the main-view snapshot.
        if (ShaderPackDetector.isShadowPass()) return;
        deferredFrame = null;
        if (!canRender()) return;
        Minecraft mc = Minecraft.getInstance();
        if (active.clocked()) {
            playback.seek(mc.level.getGameTime() - active.startTime());
            if (playback.finished()) return;
        }
        projection = new Matrix4f(event.getProjectionMatrix());
        Vec3 delta = anchor(event.getPartialTick()).subtract(event.getCamera().getPosition());
        // Keep a complete page readable at narrow aspect ratios and low FOV as well.
        double distance = Math.max(0.1, delta.length());
        renderScale = (float) (SCALE * Math.min(1, Math.min(
                distance * 0.7 / (Math.max(0.01, halfWidth) * Math.abs(projection.m00())),
                distance * 0.7 / (Math.max(0.01, halfHeight) * Math.abs(projection.m11())))));
        Matrix4f model = StoryTextTransform.panel(event.getPoseStack().last().pose(),
                new Vector3f((float) delta.x, (float) delta.y, (float) delta.z), bearing, renderScale);
        projected = StoryTextTransform.project(projection, model, halfWidth / SCALE, halfHeight / SCALE);
        readable = projected.readable();
        // Use our own buffer so flushing text never flushes another world renderer's geometry.
        float partial = (readable || active.clocked() || active.autoplay()) && !mc.isPaused() ? event.getPartialTick() : 0;
        List<DrawGlyph> frame = new ArrayList<>(glyphs.size());
        for (Glyph glyph : glyphs) {
            float age = playback.characterAge(glyph.line(), glyph.character(), partial);
            if (age < 0) continue;
            var motion = StoryGlyphMotion.sample(age, playback.exitAge(glyph.globalIndex(), partial), glyph.globalIndex());
            if (motion.dissolve() >= 1) continue;
            int alpha = StoryTextShader.available() ? 255 : Math.round(255 * (1 - motion.dissolve()));
            if (alpha == 0) continue;
            int light = StoryTextShader.available() ? StoryGlyphMotion.packedLight(motion.dissolve()) : LightTexture.FULL_BRIGHT;
            frame.add(new DrawGlyph(glyph, StoryTextTransform.glyph(model, glyph.x(), glyph.y(), glyph.width(), motion), light, alpha));
        }
        if (ShaderPackDetector.shouldUseShaderPackPipeline()) {
            // Save this frame's matrices before hand rendering changes the projection.
            // Custom text must bypass the pack's GBuffer and draw after composition.
            deferredFrame = new RenderFrame(active, owner, new Matrix4f(projection),
                    new Matrix4f(RenderSystem.getModelViewMatrix()), List.copyOf(frame));
        } else drawFrame(frame);
    }

    /** Replayed by the project's existing GameRenderer.renderLevel TAIL compatibility hook. */
    public static void renderDeferred() {
        RenderFrame frame = deferredFrame;
        deferredFrame = null;
        if (frame == null || !canRender() || frame.page() != active || frame.level() != owner
                || ShaderPackDetector.isShadowPass()) return;
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            LateOutlineRenderState.prepareMainTargetPass();
            modelView.last().pose().set(frame.modelView());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(frame.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            drawFrame(frame.glyphs());
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            LateOutlineRenderState.finishMainTargetPass();
        }
    }

    private static void drawFrame(List<DrawGlyph> frame) {
        Minecraft mc = Minecraft.getInstance();
        var config = ConfigHelper.configHolder == null ? null : ConfigHelper.configHolder.get();
        boolean shadowEnabled = config == null || config.storyTextShadow;
        if (active.paragraph().options().shadow() != null) shadowEnabled = active.paragraph().options().shadow();
        final boolean drawShadow = shadowEnabled;
        // Per-glyph dissolve is encoded in vertices, independently of atlas batching.
        StoryTextShader.setDissolve(0);
        // Complete all shadows before any foreground, even across different font atlas batches.
        // The first pass also bakes newly revealed glyphs before the foreground pass uses them.
        StoryTextLayers.render(shadowPass -> {
            if (shadowPass && !drawShadow) return;
            for (DrawGlyph draw : frame) {
                if (shadowPass) {
                    int shadowColor = Math.round(draw.alpha() * 0.4f) << 24 | 0x101720;
                    mc.font.drawInBatch(draw.glyph().shadow(), 0.25f, 0.35f, shadowColor, false,
                            draw.matrix(), STORY_BUFFER, Font.DisplayMode.SEE_THROUGH, 0, draw.light());
                } else {
                    mc.font.drawInBatch(draw.glyph().text(), 0, 0, draw.alpha() << 24 | 0xF5EFDF, false,
                            draw.matrix(), STORY_BUFFER, Font.DisplayMode.SEE_THROUGH, 0, draw.light());
                }
            }
        }, TEXT_BUFFER::endBatch);
    }

    @SubscribeEvent
    public static void renderPointer(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().options.hideGui || !canRender() || projected == null) return;
        var result = projected;
        if (result.readable()) return;
        Minecraft mc = Minecraft.getInstance();
        double dx = result.directionX(), dy = result.directionY();
        double rx = Math.max(1, mc.getWindow().getGuiScaledWidth() / 2.0 - 24);
        double ry = Math.max(1, mc.getWindow().getGuiScaledHeight() / 2.0 - 24);
        double edge = 1 / Math.max(Math.abs(dx) / rx, Math.abs(dy) / ry);
        var gui = event.getGuiGraphics();
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(mc.getWindow().getGuiScaledWidth() / 2.0 + dx * edge,
                    mc.getWindow().getGuiScaledHeight() / 2.0 + dy * edge, 0);
            pose.mulPose(Axis.ZP.rotation((float) (Math.atan2(dy, dx) + Math.PI / 2)));
            for (int row = 0; row < 13; row++) {
                int width = (row + 1) / 2;
                gui.fill(-width - 1, row - 7, width + 2, row - 6, 0xBB091018);
                gui.fill(-width, row - 8, width + 1, row - 7, 0xFFE8D9AF);
            }
        } finally { pose.popPose(); }
    }
}
