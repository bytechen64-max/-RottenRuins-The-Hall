import com.google.gson.JsonParser;
import net.minecraft.client.StringSplitter;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Checks real Component serialization, language cache invalidation and localized wrapping. */
public final class StoryLanguageTest {
    public static void main(String[] args) throws Exception {
        Language original = Language.getInstance();
        try {
            Language chinese = load("zh_cn"), english = load("en_us");
            StringSplitter splitter = new StringSplitter((codePoint, style) -> 6f);
            for (String key : new String[]{"opening", "fleshbed.1", "fleshbed.2", "fleshbed.3", "fleshbed.4",
                    "respawn.first.0", "respawn.normal.0", "respawn.normal.1", "respawn.normal.2", "respawn.normal.3",
                    "respawn.rapid.0", "respawn.rapid.1", "respawn.fall.0", "respawn.fall.1",
                    "respawn.drown.0", "respawn.drown.1", "respawn.mob.0", "respawn.mob.1"}) {
                String translation = "rotten_ruins.story." + key;
                Language.inject(chinese);
                Component text = Component.translatable(translation).withStyle(style -> style.withFont(
                        ResourceLocation.fromNamespaceAndPath("rotten_ruins", "wenkai")));
                // The same JSON path used by the story network payload must retain the key and font.
                String json = Component.Serializer.toJson(text);
                if (!json.contains("\"translate\"")) throw new AssertionError("Flattened translation: " + key);
                Component received = Component.Serializer.fromJson(json);
                String before = received.getString();
                var oldLines = splitter.splitLines(received, 200, Style.EMPTY);
                Language.inject(english);
                String after = received.getString();
                if (before.equals(after) || !after.equals(english.getOrDefault(translation)))
                    throw new AssertionError("Language did not refresh: " + key);
                if (after.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))
                    throw new AssertionError("Chinese in English story: " + key);
                if (!received.getStyle().getFont().getPath().equals("wenkai"))
                    throw new AssertionError("Font lost on language switch");
                String rewrapped = splitter.splitLines(received, 200, Style.EMPTY).stream()
                        .map(FormattedText::getString).reduce("", String::concat).replace(" ", "");
                if (!rewrapped.equals(after.replace("\n", "").replace(" ", "")))
                    throw new AssertionError("Localized wrapping dropped text: " + key);
                if (oldLines.isEmpty()) throw new AssertionError("Missing Chinese layout");
                Language.inject(chinese);
                if (!received.getString().equals(before)) throw new AssertionError("Switch back failed");
            }
            System.out.println("PASS: 18 story translations, retained network keys/fonts, CN -> EN -> CN and localized wrapping");
        } finally {
            Language.inject(original);
        }
    }

    private static Language load(String locale) throws Exception {
        Map<String, String> entries = new HashMap<>();
        var json = JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/rotten_ruins/lang/" + locale + ".json"))).getAsJsonObject();
        json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
        return new Language() {
            public String getOrDefault(String key, String fallback) { return entries.getOrDefault(key, fallback); }
            public boolean has(String key) { return entries.containsKey(key); }
            public boolean isDefaultRightToLeft() { return false; }
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
            }
        };
    }
}
