package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 腐化系（原 corruption 工程）内容的统一注册入口。
 *
 * <p>合并后的简化移植只包含：方块、物品/刷怪蛋、生物、效果。
 * 节点系统、方块扩散/群系替换、同化/演化、自定义刷怪、技能/投射物、粒子/音效等机制层
 * 尚未迁入，由后续迭代按模块补回。</p>
 */
public final class CorruptionContent {

    private CorruptionContent() {
    }

    public static void register(IEventBus modEventBus) {
        CorruptionBlocks.BLOCKS.register(modEventBus);
        CorruptionItems.ITEMS.register(modEventBus);
        CorruptionEntityTypes.ENTITY_TYPES.register(modEventBus);
        CorruptionEffects.EFFECTS.register(modEventBus);
    }
}
