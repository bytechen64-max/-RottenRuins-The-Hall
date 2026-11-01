package org.bytechen.rottenruins.overworld.registry;

import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.blockentity.DeathCoreBlockEntity;
import org.bytechen.rottenruins.overworld.registry.blockentity.SourceMachineBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class RegisterBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HallMod.MODID);

    public static final RegistryObject<BlockEntityType<SourceMachineBlockEntity>> SOURCE_FILTER=BLOCK_ENTITIES.register("source_filter",()->
            BlockEntityType.Builder.of((pos,state)->new SourceMachineBlockEntity(pos,state,false),RegisterBlock.SOURCE_FILTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<SourceMachineBlockEntity>> SOURCE_COLLECTOR=BLOCK_ENTITIES.register("source_collector",()->
            BlockEntityType.Builder.of((pos,state)->new SourceMachineBlockEntity(pos,state,true),RegisterBlock.SOURCE_COLLECTOR.get()).build(null));

    public static <T extends BlockEntityType<?>> RegistryObject<T> registerBlockEntity(String name, Supplier<T> supplier) {
        return BLOCK_ENTITIES.register(name, supplier);
    }

    public static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> registerBlockEntityForBlocks(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Block[] blocks) {
        return BLOCK_ENTITIES.register(name, () ->
                BlockEntityType.Builder.of(factory, blocks).build(null));
    }

    /**
     * 死亡核心 —— 方块版 mask 效果层（{@code death_core}）的宿主。
     *
     * <p>用直接注册而不是 {@link #registerBlockEntityForBlocks}：后者要求调用时
     * 就拿到 {@code Block[]}，而那会在静态初始化阶段提前 {@code .get()} 方块注册表。
     * 这里的 lambda 在注册事件触发时才求值，顺序才是安全的。</p>
     */
    public static final RegistryObject<BlockEntityType<DeathCoreBlockEntity>> DEATH_CORE =
            BLOCK_ENTITIES.register("death_core", () -> BlockEntityType.Builder
                    .of(DeathCoreBlockEntity::new, RegisterBlock.DEATH_CORE.get())
                    .build(null));
}
