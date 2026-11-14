package org.bytechen.rottenruins.client.rend.twitch;

import net.minecraft.world.item.ItemDisplayContext;

/** 标记接口：渲染时给物品模型加抽动特效；走 cosmic 模型加载器的物品已由 BakedModelCosmic 自动获得。 */
public interface ITwitchItem {

    default boolean twitchShouldRender(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.GUI;
    }

    /** 物品层面的关闭声明：崩坏层与抽动层以此为准，模型里的 "twitch": false 可能被渲染包装器覆盖。 */
    default boolean twitchDisabled() {
        return false;
    }
}
