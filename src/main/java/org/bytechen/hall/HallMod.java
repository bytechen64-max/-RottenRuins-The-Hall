package org.bytechen.hall;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.bytechen.hall.config.ConfigHelper;
import org.bytechen.hall.overworld.difficulty.HallDifficulty;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.overworld.registry.*;
import org.bytechen.hall.overworld.registry.spawning.SplendidingSpawnPlacements;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(HallMod.MODID)
public class HallMod {

    public static final String MODID = "hall";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 群系修改器序列化器注册（数据包级别群系修改） */
    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, HallMod.MODID);

    @SuppressWarnings("removal")
    public HallMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 确保 BlockSetType / WoodType 在方块注册前初始化
        HallBlockSetTypes.init();

        // 方块 / 物品 / 效果 / 音效 / 实体 / 粒子 / 方块实体 注册
        RegisterTab.CREATIVE_MODE_TABS.register(modEventBus);
        RegisterBlock.BLOCKS.register(modEventBus);
        RegisterItem.ITEMS.register(modEventBus);
        RegisterEffect.EFFECTS.register(modEventBus);
        SoundEventRegistry.SOUND_EVENTS.register(modEventBus);
        EntityTypeRegistry.ENTITY_TYPES.register(modEventBus);
        RegisterParticles.PARTICLE_TYPES.register(modEventBus);
        RegisterBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // 群系修改器序列化器
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);

        // Cosmic starfield rendering（参考 Live 模组）
        if (FMLEnvironment.dist.isClient()) {
            org.bytechen.hall.client.cosmic.CosmicClient.init(modEventBus);
        }

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SplendidingSpawnPlacements::register);

        NetworkHelper.register();
        ConfigHelper.registry();

        // 注册 Hall 自定义难度
        HallDifficulty.registerAll();

        LOGGER.info("Hall common setup complete");
    }
}
