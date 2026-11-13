package org.bytechen.rottenruins.client.mask;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 把模型 JSON 里的 {@code "mask_layers"} 数组解析成 {@link MaskLayerSpec}；单层写错只丢弃该层并点名，
 * 不牵连整件物品（模型 JSON 是美术直接改的，一处笔误不该让整件物品变成紫黑方块）。
 * 字段：{@code effect}（默认 {@code "glow"}）、{@code mask}（必填，如
 * {@code "rotten_ruins:item/void_sword_mask"}）、{@code color}（默认白，支持
 * {@code "#AARRGGBB"} / {@code "0xAARRGGBB"} / {@code "AARRGGBB"} / 十进制）；
 * {@code intensity}/{@code width}/{@code speed} 默认 1.0/2.0/1.0，{@code opacity}/{@code order}/{@code phase}
 * 默认 1.0/100.0/0.0，{@code gui}/{@code held}/{@code world} 三个上下文开关默认 true。
 */
public final class MaskLayerJson {

    /** 模型 JSON 里承载层数组的键名。 */
    public static final String KEY = "mask_layers";

    /** @return 不可变列表；缺键、类型不对或全部层非法时返回空列表 */
    public static List<MaskLayerSpec> parse(JsonObject parent) {
        if (parent == null || !parent.has(KEY)) return List.of();

        JsonElement element = parent.get(KEY);
        if (!element.isJsonArray()) {
            HallMod.LOGGER.warn("[MaskLayer] 模型 JSON 的 \"{}\" 不是数组，已忽略", KEY);
            return List.of();
        }

        JsonArray array = element.getAsJsonArray();
        List<MaskLayerSpec> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonObject()) {
                HallMod.LOGGER.warn("[MaskLayer] {}[{}] 不是对象，已忽略", KEY, i);
                continue;
            }
            MaskLayerSpec spec = parseOne(entry.getAsJsonObject(), i);
            if (spec != null) out.add(spec);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static MaskLayerSpec parseOne(JsonObject obj, int index) {
        String effect = optString(obj, "effect", MaskLayerSpec.EFFECT_GLOW);
        String maskText = optString(obj, "mask", null);

        if (maskText == null || maskText.isBlank()) {
            HallMod.LOGGER.warn("[MaskLayer] {}[{}] 缺少 \"mask\"，该层已忽略", KEY, index);
            return null;
        }
        ResourceLocation mask = ResourceLocation.tryParse(maskText);
        if (mask == null) {
            HallMod.LOGGER.warn("[MaskLayer] {}[{}] 的 mask \"{}\" 不是合法资源位置，该层已忽略",
                    KEY, index, maskText);
            return null;
        }

        return MaskLayerSpec.builder(effect)
                .mask(mask)
                .color(optColor(obj, "color", MaskLayerSpec.DEFAULT_COLOR))
                .intensity(optFloat(obj, "intensity", 1.0f))
                .width(optFloat(obj, "width", 2.0f))
                .speed(optFloat(obj, "speed", 1.0f))
                .opacity(optFloat(obj, "opacity", 1.0f))
                .order(optFloat(obj, "order", MaskLayerSpec.DEFAULT_ORDER))
                .phase(optFloat(obj, "phase", 0.0f))
                .showInGui(optBool(obj, "gui", true))
                .showWhenHeld(optBool(obj, "held", true))
                .showInWorld(optBool(obj, "world", true))
                .build();
    }

    // 取值小工具：字段类型不对一律退回默认值并记日志，不抛异常

    private static String optString(JsonObject obj, String key, String fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        return e.getAsString();
    }

    private static float optFloat(JsonObject obj, String key, float fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        return e.getAsFloat();
    }

    private static boolean optBool(JsonObject obj, String key, boolean fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) return fallback;
        return e.getAsBoolean();
    }

    /** 解析颜色。推荐写成 {@code "#AARRGGBB"} 字符串：ARGB 高位常超出 int 范围，
     *  写成 JSON 数字在某些解析器下会直接报错。 */
    private static int optColor(JsonObject obj, String key, int fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;

        if (e.getAsJsonPrimitive().isNumber()) {
            // 用 asLong 再截断：0xFFFF66E0 这类值超出 int，asInt() 会炸。
            try {
                return (int) e.getAsLong();
            } catch (NumberFormatException ex) {
                HallMod.LOGGER.warn("[MaskLayer] color 数字无法解析（{}），用默认色", e);
                return fallback;
            }
        }

        String raw = e.getAsString().trim();
        String hex = raw;
        if (hex.startsWith("#")) hex = hex.substring(1);
        else if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);

        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ex) {
            HallMod.LOGGER.warn("[MaskLayer] color \"{}\" 无法解析（期望 #AARRGGBB），用默认色", raw);
            return fallback;
        }
    }

    private MaskLayerJson() {}
}
