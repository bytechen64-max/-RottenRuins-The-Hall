package org.bytechen.rottenruins.api;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个效果层的参数块（不可变）。
 *
 * <p>为什么不是又一张"所有效果共用的固定字段表"：{@code mask_layers} 那套
 * （color / intensity / width / speed / opacity / order / phase）塞不下新效果要的东西 ——
 * 析晶要晶面尺度与种子、腐蚀要扩散速率、火焰要上升速度与湍流强度。
 * 每加一个效果就去改那张共用 record，是"配置结构没抽象对"的典型症状。</p>
 *
 * <p>取值一律带默认值：效果自己解释自己的键，缺键不算错。</p>
 */
public final class ItemFxParams {

    public static final ItemFxParams EMPTY = new ItemFxParams(Map.of());

    private final Map<String, Float> values;

    private ItemFxParams(Map<String, Float> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ItemFxParams of(String key, float value) {
        return builder().set(key, value).build();
    }

    public float getFloat(String key, float fallback) {
        Float v = values.get(key);
        return v == null ? fallback : v;
    }

    public int getInt(String key, int fallback) {
        Float v = values.get(key);
        return v == null ? fallback : v.intValue();
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    @Nullable
    public Float peek(String key) {
        return values.get(key);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, Float> asMap() {
        return values;
    }

    public static final class Builder {
        private final Map<String, Float> values = new LinkedHashMap<>();

        public Builder set(String key, float value) {
            values.put(key, value);
            return this;
        }

        public ItemFxParams build() {
            return values.isEmpty() ? EMPTY : new ItemFxParams(Map.copyOf(values));
        }
    }
}
