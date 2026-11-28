package org.bytechen.rottenruins.client.tooltip;

import org.bytechen.rottenruins.HallMod;

import java.lang.reflect.Field;

/**
 * 现代化 UI（ModernUI）兼容：它在 {@code RenderTooltipEvent.Pre} 里用自己的排版接管 tooltip 并取消原版绘制，
 * 于是 {@code RenderTooltipEvent.Color} 不会再触发，我们的底板着色器就没有机会画。
 * <p>
 * 它的两个 Pre 处理器都以 {@code TooltipRenderer.sTooltip} 为开关，所以在我们自己画 tooltip 的这段时间里
 * 把这个开关按下去、帧末再恢复，就能让法术物品回到原版管线（着色器 + 开合动画都照常）。
 * 全程反射，没装 ModernUI 时什么也不做。
 */
public final class ModernUiTooltipCompat {

    private static final String FLAG_CLASS = "icyllis.modernui.mc.TooltipRenderer";
    private static final String FLAG_FIELD = "sTooltip";

    private static Field flagField;
    private static boolean resolved;
    private static boolean silenced;
    private static boolean previousValue;

    private static boolean warned;

    private ModernUiTooltipCompat() {
    }

    /** 让 ModernUI 这一帧不要接管 tooltip；返回是否真的改过开关。 */
    public static void silence() {
        Field field = flag();
        if (field == null || silenced) {
            return;
        }
        try {
            previousValue = field.getBoolean(null);
            if (previousValue) {
                field.setBoolean(null, false);
                silenced = true;
            }
        } catch (Throwable error) {
            warnOnce(error);
        }
    }

    /** 帧末恢复开关。 */
    public static void restore() {
        if (!silenced) {
            return;
        }
        silenced = false;
        try {
            flagField.setBoolean(null, previousValue);
        } catch (Throwable error) {
            warnOnce(error);
        }
    }

    private static Field flag() {
        if (resolved) {
            return flagField;
        }
        resolved = true;
        try {
            flagField = Class.forName(FLAG_CLASS).getField(FLAG_FIELD);
            if (!flagField.getType().equals(boolean.class)) {
                flagField = null;
            }
        } catch (ClassNotFoundException absent) {
            flagField = null;
        } catch (Throwable error) {
            flagField = null;
            warnOnce(error);
        }
        return flagField;
    }

    private static void warnOnce(Throwable error) {
        if (warned) {
            return;
        }
        warned = true;
        HallMod.LOGGER.debug("[tooltip] ModernUI 兼容开关不可用：{}", error.toString());
    }
}
