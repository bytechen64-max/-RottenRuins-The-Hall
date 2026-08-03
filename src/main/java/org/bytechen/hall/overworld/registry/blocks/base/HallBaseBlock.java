package org.bytechen.hall.overworld.registry.blocks.base;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class HallBaseBlock extends Block {

    public HallBaseBlock() {
        super(createBlockProps());
    }

    public HallBaseBlock(Properties properties) {
        super(properties);
    }

    public static Properties createBlockProps() {
        return Properties.of();
    }
}
