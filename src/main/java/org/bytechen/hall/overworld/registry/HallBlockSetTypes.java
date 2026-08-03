package org.bytechen.hall.overworld.registry;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * HALL 木板对应的 {@link BlockSetType} 和 {@link WoodType} 注册。
 * 为门、活板门、按钮、压力板、栅栏门提供统一的木制行为（音效、开关速度等）。
 */
public class HallBlockSetTypes {

    public static final BlockSetType HALL = BlockSetType.register(
            new BlockSetType(
                    "hall",
                    true,  // canOpenByHand
                    SoundType.WOOD,
                    SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundEvents.WOODEN_DOOR_OPEN,
                    SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                    SoundEvents.WOODEN_TRAPDOOR_OPEN,
                    SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF,
                    SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.WOODEN_BUTTON_CLICK_OFF,
                    SoundEvents.WOODEN_BUTTON_CLICK_ON
            )
    );

    // ---- HALL_STONE（石制）BlockSetType / WoodType ----

    public static final BlockSetType HALL_STONE = BlockSetType.register(
            new BlockSetType(
                    "hall_stone",
                    true,  // canOpenByHand
                    SoundType.STONE,
                    SoundEvents.IRON_DOOR_CLOSE,
                    SoundEvents.IRON_DOOR_OPEN,
                    SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF,
                    SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundEvents.STONE_BUTTON_CLICK_ON
            )
    );

    public static final WoodType HALL_WOOD = WoodType.register(
            new WoodType(
                    "hall",
                    HALL,
                    SoundType.WOOD,
                    SoundType.HANGING_SIGN,
                    SoundEvents.FENCE_GATE_CLOSE,
                    SoundEvents.FENCE_GATE_OPEN
            )
    );

    /**
     * 触发静态初始化块，将 BlockSetType / WoodType 写入原版注册表。
     * 必须在方块注册前调用。
     */
    public static void init() {
        // 静态字段初始化触发注册，无需额外逻辑
    }
}
