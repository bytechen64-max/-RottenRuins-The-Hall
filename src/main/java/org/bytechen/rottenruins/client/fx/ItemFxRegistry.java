package org.bytechen.rottenruins.client.fx;

import org.bytechen.rottenruins.HallMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品效果的注册表：{@code id → 效果实现}。
 *
 * <p>形状直接沿用项目里已经验证过的 {@code MaskEffectRegistry}（那套是给"物品表面"
 * 那半边用的）：重复注册覆盖 + 警告，未注册的 id 不崩、只跳过并在日志里点名一次。
 * 两边将来会合成一套，S1 先让物品渲染这半边有同样的插拔能力。</p>
 *
 * <p><b>注册时机</b>：内置效果在类静态初始化时注册（见
 * {@link org.bytechen.rottenruins.client.fx.effects.OutlineFxEffect}）；第三方效果可以在任意时刻
 * 注册，只要赶在有人引用它之前。</p>
 */
public final class ItemFxRegistry {

    private static final Map<String, ItemFxEffect> EFFECTS = new LinkedHashMap<>();

    private ItemFxRegistry() { }

    /** 注册一个效果。重复 id 覆盖前一个并留一条警告 —— 覆盖几乎总是笔误。 */
    public static void register(@Nullable ItemFxEffect effect) {
        if (effect == null || effect.id() == null || effect.id().isBlank()) {
            HallMod.LOGGER.warn("[ItemFx] 忽略了一个没有 id 的效果实现: {}", effect);
            return;
        }
        ItemFxEffect previous = EFFECTS.put(effect.id(), effect);
        if (previous != null) {
            HallMod.LOGGER.warn("[ItemFx] 效果 id '{}' 被重复注册，后者生效: {} 覆盖了 {}",
                    effect.id(), effect.getClass().getName(), previous.getClass().getName());
        }
    }

    @Nullable
    public static ItemFxEffect get(@Nullable String id) {
        return id == null ? null : EFFECTS.get(id);
    }

    /** 按 {@code order} 升序列出（升序，与 mask_layers 的语义一致）。 */
    public static List<ItemFxEffect> ordered() {
        List<ItemFxEffect> list = new ArrayList<>(EFFECTS.values());
        list.sort((a, b) -> Float.compare(a.order(), b.order()));
        return Collections.unmodifiableList(list);
    }

    public static boolean isEmpty() {
        return EFFECTS.isEmpty();
    }

    /** 诊断用：列出已注册的效果 id。 */
    public static String describe() {
        return EFFECTS.isEmpty() ? "(空)" : String.join(", ", EFFECTS.keySet());
    }
}
