package org.bytechen.hall.client.mask;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * mask 效果实现的注册表：{@code "effect" id → 效果实现}。
 *
 * <p>这是「注入系统」的第二个入口（第一个是物品侧的 {@link MaskLayerRegistry}）：
 * 一边声明<b>有哪些效果可用</b>，另一边声明<b>哪个物品用哪个效果</b>。</p>
 *
 * <h3>注册时机</h3>
 * <p>内置效果在 {@code MaskLayerClient.init} 里注册；第三方效果可以在任意时刻
 * 注册，但要在有物品引用它<b>之前</b>。没注册的 id 不会崩，只是那一层被跳过
 * 并在日志里点名一次（见 {@link MaskLayerResolver}）。</p>
 */
public final class MaskEffectRegistry {

    private static final Map<String, MaskEffectPass> EFFECTS = new LinkedHashMap<>();

    /**
     * 注册一个效果实现。重复注册同一个 id 会覆盖前一个，
     * 并留下一条警告 —— 覆盖几乎总是笔误。
     */
    public static void register(MaskEffectPass pass) {
        if (pass == null || pass.id() == null || pass.id().isBlank()) {
            org.bytechen.hall.HallMod.LOGGER.warn("[MaskLayer] 忽略了一个没有 id 的效果实现: {}", pass);
            return;
        }
        MaskEffectPass previous = EFFECTS.put(pass.id(), pass);
        if (previous != null) {
            org.bytechen.hall.HallMod.LOGGER.warn(
                    "[MaskLayer] 效果 id '{}' 被重复注册，后者生效: {} 覆盖了 {}",
                    pass.id(), pass.getClass().getName(), previous.getClass().getName());
        }
    }

    @Nullable
    public static MaskEffectPass get(String id) {
        return id == null ? null : EFFECTS.get(id);
    }

    public static boolean isRegistered(String id) {
        return id != null && EFFECTS.containsKey(id);
    }

    public static boolean isEmpty() {
        return EFFECTS.isEmpty();
    }

    private MaskEffectRegistry() {}
}
