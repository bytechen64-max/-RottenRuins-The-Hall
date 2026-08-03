package org.bytechen.hall.client.rend.glint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * 手持物品 FBO 轮廓+泛光渲染的快捷配置 API。
 *
 * <pre>{@code
 * // 给一个或多个物品开启自动采样颜色 + 泛光
 * HeldItemGlintHelper.enableForItems(
 *     RegisterItem.RED_DWARF_BOW.get(),
 *     RegisterItem.POISONED_SABER.get()
 * );
 *
 * // 自定义色彩模式
 * HeldItemOutlineSettings.setColorMode(HeldItemOutlineSettings.ColorMode.RAINBOW_FLOW);
 * HeldItemGlintHelper.enableForItems(RegisterItem.VICE_TEMPLATE.get());
 * }</pre>
 */
public final class HeldItemGlintHelper {

    private HeldItemGlintHelper() {}

    /**
     * 快捷方法：为指定物品开启手持轮廓+泛光渲染。
     * 使用当前全局设置（颜色模式、泛光参数等），默认 RuleMode 为 WHITELIST。
     */
    public static void enableForItems(Item... items) {
        if (items.length == 0) return;

        HeldItemRuleManager.setRuleMode(HeldItemRuleManager.RuleMode.WHITELIST);

        List<String> entries = new ArrayList<>(HeldItemRuleManager.getWhitelistEntries());
        for (Item item : items) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!entries.contains(id)) {
                entries.add(id);
            }
        }
        HeldItemRuleManager.setWhitelistEntries(entries);
    }

    /**
     * 为指定物品追加到白名单（保留已有条目）。
     */
    public static void addToWhitelist(Item... items) {
        enableForItems(items);
    }

    /**
     * 从白名单中移除指定物品。
     */
    public static void removeFromWhitelist(Item... items) {
        List<String> entries = new ArrayList<>(HeldItemRuleManager.getWhitelistEntries());
        for (Item item : items) {
            entries.remove(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        HeldItemRuleManager.setWhitelistEntries(entries);
    }

    /**
     * 对所有手持物品启用效果。
     */
    public static void enableForAll() {
        HeldItemRuleManager.setRuleMode(HeldItemRuleManager.RuleMode.ALL);
    }

    /**
     * 禁用所有手持物品的效果。
     */
    public static void disableAll() {
        HeldItemRuleManager.setRuleMode(HeldItemRuleManager.RuleMode.BLACKLIST);
        HeldItemRuleManager.setBlacklistEntries(List.of("*"));
    }

    /**
     * 快捷方法：设置自动采样 + 泛光的推荐默认值。
     */
    public static void applyDefaultPreset() {
        HeldItemOutlineSettings.setColorMode(HeldItemOutlineSettings.ColorMode.AUTO_SAMPLE_SCROLL);
        HeldItemOutlineSettings.setBloomEnabled(true);
        HeldItemOutlineSettings.setBloomStrength(0.55f);
        HeldItemOutlineSettings.setBloomRadius(1.0f);
        HeldItemOutlineSettings.setWidth(2.25f);
        HeldItemOutlineSettings.setSoftness(1.35f);
        HeldItemOutlineSettings.setOpacity(0.95f);
    }
}
