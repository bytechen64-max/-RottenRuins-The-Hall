package org.bytechen.hall.client.mask;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 把模型 JSON 里的 {@code "mask_layers"} 数组解析成 {@link MaskLayerSpec}。
 *
 * <pre>{@code
 * {
 *   "loader": "hall:cosmic",
 *   "cosmic": { "mask": "hall:item/void_sword_mask" },
 *   "mask_layers": [
 *     { "effect": "glow", "mask": "hall:item/void_sword_mask",
 *       "color": "#FF66E0FF", "width": 2.0, "intensity": 1.0, "order": 120 }
 *   ]
 * }
 * }</pre>
 *
 * <h3>解析失败的单层只被丢弃，不牵连整件物品</h3>
 * <p>一层写错（少了 mask、mask 名字不合法）只丢这一层并点名，其余层和该物品的
 * 本体、cosmic 层照常。模型 JSON 是美术会直接改的文件，一处笔误导致整件物品
 * 变成紫黑方块是难以接受的失败模式。</p>
 *
 * <h3>字段</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>默认</th></tr>
 *   <tr><td>{@code effect}</td><td>string</td><td>{@code "glow"}</td></tr>
 *   <tr><td>{@code mask}</td><td>string</td><td><b>必填</b>（如
 *       {@code "hall:item/void_sword_mask"}）</td></tr>
 *   <tr><td>{@code color}</td><td>int 或 string</td><td>白。支持
 *       {@code "#AARRGGBB"} / {@code "0xAARRGGBB"} / {@code "AARRGGBB"} / 十进制数</td></tr>
 *   <tr><td>{@code intensity} / {@code width} / {@code speed}</td><td>number</td>
 *       <td>1.0 / 2.0 / 1.0</td></tr>
 *   <tr><td>{@code opacity} / {@code order} / {@code phase}</td><td>number</td>
 *       <td>1.0 / 100.0 / 0.0</td></tr>
 *   <tr><td>{@code gui} / {@code held} / {@code world}</td><td>boolean</td>
 *       <td>true（三个上下文的开关）</td></tr>
 * </table>
 */
public final class MaskLayerJson {

    /** 模型 JSON 里承载层数组的键名。 */
    public static final String KEY = "mask_layers";

    /**
     * 解析一个模型 JSON 根对象里的层数组。
     *
     * @return 不可变列表；没有该键、类型不对、或全部层都不合法时返回空列表
     */
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

    // ── 取值小工具：任何一个字段类型不对都退回默认值，不抛 ──────────

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

    /**
     * 解析颜色。写成字符串形式（{@code "#AARRGGBB"}）是推荐用法：ARGB 的
     * 高位常常超出 int 表示范围，写成 JSON 数字在某些解析器下会直接报错。
     */
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
