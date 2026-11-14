package org.bytechen.rottenruins.overworld.registry.corruption;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 腐化系（原 corruption 工程）内容的统一注册入口。
 *
 * <p>合并后已包含：方块（含自主扩散与群系跟随）、物品/刷怪蛋、生物、效果、技能与投射物。
 * 仍未闭环的只剩：节点系统（已定不搬，腐化强度改用 infcore 演化值/难度/群系存在感表达）、
 * 演化回调里"非幼年 + 精英名 + 持久化"这类条件，以及腐化粒子与专属音效
 * （缺口清单见 {@code docs/merge/corruption-skill-assets.md}）。</p>
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
