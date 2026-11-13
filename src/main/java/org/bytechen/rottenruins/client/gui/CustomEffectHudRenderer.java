package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自定义效果的界面绘制 —— 内容与原版效果栏一致（方框图标 + 剩余时间 + 层数），
 * 但因为暂时没有贴图，图标用效果自带的颜色画成纯色块。
 *
 * <p>用在两个地方（见 {@code CustomEffectHud}）：</p>
 * <ul>
 *   <li><b>游戏内 HUD</b>：屏幕左侧、竖直排列；</li>
 *   <li><b>背包界面</b>：GUI 面板左侧边缘，竖直排列（原版是贴在右侧）。</li>
 * </ul>
 */
public final class CustomEffectHudRenderer {

    /** 单个图标框的边长（含边框）。 */
    public static final int BOX = 20;

    /** 相邻两项的垂直间距。 */
    public static final int STEP = 22;

    private CustomEffectHudRenderer() {
    }

    /** 玩家身上所有自定义效果，按剩余时长从短到长排（快没了的排在上面，和原版一致）。 */
    @NotNull
    public static List<CustomEffectInstance> activeOf(@NotNull LocalPlayer player) {
        List<CustomEffectInstance> list = new ArrayList<>(CustomEffects.getActiveEffects(player));
        list.removeIf(instance -> !instance.getEffect().isVisible());
        list.sort(Comparator.comparingInt(CustomEffectInstance::getDuration));
        return list;
    }

    /**
     * 在 {@code (x, y)} 起竖直画一列。
     *
     * @param rightAlign 时间文字画在框的左侧还是右侧（HUD 用右侧、背包左侧边缘用右侧也行）
     * @return 画了几项
     */
    public static int drawColumn(@NotNull GuiGraphics graphics, @NotNull List<CustomEffectInstance> effects,
                                 int x, int y, boolean rightAlign) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null) {
            return 0;
        }
        int drawn = 0;
        for (CustomEffectInstance instance : effects) {
            drawIcon(graphics, instance, x, y + drawn * STEP, rightAlign);
            drawn++;
        }
        return drawn;
    }

    private static void drawIcon(@NotNull GuiGraphics graphics, @NotNull CustomEffectInstance instance,
                                 int x, int y, boolean rightAlign) {
        Minecraft minecraft = Minecraft.getInstance();
        int color = instance.getEffect().getColor();
        boolean harmful = instance.getEffect().isHarmful();

        // 边框用分类色（有害红、有益绿），内部用效果自身的颜色
        int border = harmful ? 0xFFB03A3A : 0xFF3A8F4A;
        graphics.fill(x, y, x + BOX, y + BOX, border);
        graphics.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0xFF12161F);
        graphics.fill(x + 2, y + 2, x + BOX - 2, y + BOX - 2, withAlpha(color, 0xFF));
        // 左上角一点高光，读起来像"图标"而不是一块色块
        graphics.fill(x + 3, y + 3, x + 7, y + 5, 0x55FFFFFF);

        // 剩余时间（原版格式 m:ss），剩余 ≤10 秒用红色
        String time = formatDuration(instance.getDuration());
        int timeColor = instance.getDuration() <= 200 ? 0xFFFF5555 : 0xFFF2F2F2;
        int textX = rightAlign ? x + BOX + 2 : x - 2 - minecraft.font.width(time);
        if (rightAlign) {
            graphics.drawString(minecraft.font, time, textX, y + 6, timeColor, true);
        } else {
            graphics.drawString(minecraft.font, time, textX, y + 6, timeColor, true);
        }

        // 层数（I / II / III …）画在框内右下
        int amplifier = instance.getAmplifier();
        if (amplifier > 0) {
            String roman = roman(amplifier + 1);
            graphics.drawString(minecraft.font, roman,
                    x + BOX - 1 - minecraft.font.width(roman), y + BOX - 9, 0xFFFFFFFF, true);
        }
    }

    /** 原版风格时长：无限显示 "**"，否则 m:ss。 */
    @NotNull
    public static String formatDuration(int ticks) {
        if (ticks < 0) {
            return "**";
        }
        int totalSeconds = Mth.ceil(ticks / 20.0f);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(value);
        };
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
