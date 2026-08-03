package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class RegisterBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HallMod.MODID);

    public static <T extends BlockEntityType<?>> RegistryObject<T> registerBlockEntity(String name, Supplier<T> supplier) {
        return BLOCK_ENTITIES.register(name, supplier);
    }

    public static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> registerBlockEntityForBlocks(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Block[] blocks) {
        return BLOCK_ENTITIES.register(name, () ->
                BlockEntityType.Builder.of(factory, blocks).build(null));
    }

    // Your block entities here
}
