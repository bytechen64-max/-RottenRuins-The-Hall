package org.bytechen.rottenruins.platform.forge.story;

import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.core.story.StoryRule;
import org.bytechen.rottenruins.gameplay.story.*;
import java.util.*;

/** JSON schema validation lives entirely at the Forge resource boundary. */
public final class StoryJsonLoader {
    private StoryJsonLoader() {}
    public static StoryDefinition story(ResourceLocation id, JsonObject json) {
        fields(json, "enabled", "font", "timing", "pause_when_unseen", "conflict", "priority", "placement",
                "shadow", "entry_speed", "exit_speed", "hold_ticks", "duration_ticks", "recapture_anchor", "pages");
        if (!bool(json, "enabled", true)) return null;
        String timingName = string(json, "timing", bool(json, "pause_when_unseen", true) ? "readable" : "autoplay");
        StoryDefinition.Timing timing = enumeration(StoryDefinition.Timing.class, timingName, "timing");
        StoryDefinition.Conflict conflict = enumeration(StoryDefinition.Conflict.class, string(json, "conflict", "replace"), "conflict");
        int priority = integer(json, "priority", 0, -1000000, 1000000);
        JsonArray pages = array(json, "pages");
        require(!pages.isEmpty() && pages.size() <= 32, "pages: expected 1..32 pages");
        List<StoryDefinition.Page> result = new ArrayList<>();
        int index = 0;
        for (JsonElement value : pages) {
            try {
                JsonObject page = object(value, "page");
                fields(page, "text", "font", "placement", "shadow", "entry_speed", "exit_speed", "hold_ticks", "duration_ticks", "recapture_anchor");
                require(page.has("text"), "text: required Component");
                Component text = Component.Serializer.fromJson(page.get("text"));
                require(text != null && !text.getString().isBlank(), "text: expected nonempty Component");
                String encoded = Component.Serializer.toJson(text);
                require(encoded.length() <= StoryText.MAX_TEXT_LENGTH, "text: exceeds 4096 characters of Component JSON");
                JsonObject combined = json.deepCopy();
                page.entrySet().forEach(entry -> combined.add(entry.getKey(), entry.getValue()));
                ResourceLocation font = combined.has("font") ? font(string(combined, "font", "")) : null;
                if (font != null) require(Component.Serializer.toJson(text.copy().withStyle(style -> style.withFont(font))).length()
                        <= StoryText.MAX_TEXT_LENGTH, "text: Component with font exceeds 4096 characters");
                JsonObject placement = json.has("placement") ? object(json.get("placement"), "placement").deepCopy() : new JsonObject();
                if (page.has("placement")) object(page.get("placement"), "placement").entrySet()
                        .forEach(entry -> placement.add(entry.getKey(), entry.getValue()));
                fields(placement, "angle", "distance", "height");
                var position = new StoryDefinition.Placement((float) number(placement, "angle", 0, -180, 180),
                        number(placement, "distance", 1.6, 1, 8), number(placement, "height", 0, -2, 2));
                Boolean shadow = combined.has("shadow") ? bool(combined, "shadow", true) : null;
                Float entrySpeed = combined.has("entry_speed") ? (float) number(combined, "entry_speed", 10, 0.1, 100) : null;
                Float exitSpeed = combined.has("exit_speed") ? (float) number(combined, "exit_speed", 16.666667, 0.1, 100) : null;
                int duration = integer(combined, "duration_ticks", 0, 0, 12000);
                if (timing == StoryDefinition.Timing.CLOCKED) require(duration >= 20, "duration_ticks: clocked pages require 20..12000 ticks");
                else require(duration == 0, "duration_ticks: only valid for clocked stories; use hold_ticks");
                result.add(new StoryDefinition.Page(encoded, font, integer(combined, "hold_ticks", 120, 20, 12000),
                        duration, position, shadow, entrySpeed, exitSpeed, bool(combined, "recapture_anchor", index == 0)));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("pages[" + index + "]: " + error.getMessage(), error);
            }
            index++;
        }
        require(timing != StoryDefinition.Timing.CLOCKED || conflict != StoryDefinition.Conflict.QUEUE,
                "conflict: clocked stories cannot queue behind unrelated text");
        return new StoryDefinition(id, timing, conflict, priority, result);
    }

    public static StoryRule rule(ResourceLocation id, JsonObject json) {
        fields(json, "enabled", "event", "priority", "delay_ticks", "cooldown_ticks", "once_key", "repeat_group", "avoid_repeat", "conditions", "choices");
        if (!bool(json, "enabled", true)) return null;
        String event = string(json, "event", "");
        require(!event.isBlank() && event.length() <= 128, "event: required nonempty name, at most 128 characters");
        JsonObject when = json.has("conditions") ? object(json.get("conditions"), "conditions") : new JsonObject();
        fields(when, "min_deaths", "max_deaths", "min_streak", "max_streak", "max_death_interval_ticks", "cause", "dimension", "required_flags", "forbidden_flags");
        int minDeaths = integer(when, "min_deaths", 0, 0, Integer.MAX_VALUE);
        int maxDeaths = integer(when, "max_deaths", Integer.MAX_VALUE, minDeaths, Integer.MAX_VALUE);
        int minStreak = integer(when, "min_streak", 0, 0, Integer.MAX_VALUE);
        int maxStreak = integer(when, "max_streak", Integer.MAX_VALUE, minStreak, Integer.MAX_VALUE);
        String dimension = string(when, "dimension", "");
        if (!dimension.isEmpty()) dimension = id(dimension).toString();
        var conditions = new StoryRule.Conditions(minDeaths, maxDeaths, minStreak, maxStreak,
                integer(when, "max_death_interval_ticks", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                string(when, "cause", ""), dimension, strings(when, "required_flags"), strings(when, "forbidden_flags"));
        List<StoryRule.Choice> choices = new ArrayList<>();
        long total = 0;
        for (JsonElement value : array(json, "choices")) {
            JsonObject choice = object(value, "choices[]");
            fields(choice, "story", "weight");
            String story = id(string(choice, "story", "")).toString();
            int weight = integer(choice, "weight", 1, 0, Integer.MAX_VALUE);
            total += weight;
            choices.add(new StoryRule.Choice(story, weight));
        }
        require(choices.size() <= 256 && total > 0 && total <= Integer.MAX_VALUE, "choices: require 1..256 entries and positive weight sum within integer range");
        String once = string(json, "once_key", "");
        if (!once.isEmpty()) id(once);
        String group = string(json, "repeat_group", id.toString());
        id(group);
        return new StoryRule(id.toString(), event, integer(json, "priority", 0, -1000000, 1000000),
                integer(json, "delay_ticks", 0, 0, 12000), integer(json, "cooldown_ticks", 0, 0, Integer.MAX_VALUE),
                once, group, bool(json, "avoid_repeat", true), conditions, choices);
    }

    private static ResourceLocation font(String value) {
        var preset = StoryFonts.find(value);
        return preset == null ? id(value) : preset.resource();
    }
    static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        require(!value.isBlank() && result != null, "invalid resource ID: " + value);
        return result;
    }
    private static Set<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        Set<String> result = new HashSet<>();
        for (var value : array(json, key)) result.add(id(value.getAsString()).toString());
        return Set.copyOf(result);
    }
    private static <T extends Enum<T>> T enumeration(Class<T> type, String value, String field) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(field + ": unknown value " + value); }
    }
    static JsonObject object(JsonElement value, String field) {
        require(value != null && value.isJsonObject(), field + ": expected object");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject json, String key) {
        require(json.has(key) && json.get(key).isJsonArray(), key + ": expected array");
        return json.getAsJsonArray(key);
    }
    private static void fields(JsonObject json, String... names) {
        Set<String> allowed = Set.of(names);
        for (String key : json.keySet()) require(allowed.contains(key), "unknown field: " + key);
    }
    private static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        require(json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isString(), key + ": expected string");
        return json.get(key).getAsString();
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        require(json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isBoolean(), key + ": expected boolean");
        return json.get(key).getAsBoolean();
    }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) {
        double value = number(json, key, fallback, min, max);
        require(value == Math.rint(value), key + ": expected integer");
        return (int) value;
    }
    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        if (!json.has(key)) return fallback;
        require(json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber(), key + ": expected number");
        double value = json.get(key).getAsDouble();
        require(Double.isFinite(value) && value >= min && value <= max, key + ": expected " + min + ".." + max);
        return value;
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
