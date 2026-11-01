package org.bytechen.rottenruins.overworld.registry.spawning;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;

/**
 * 自然生成放置规则。
 *
 * <p>腐化生物需要在腐化群系（{@code rotten_ruins:corruption_wasteland}）里自然生成，
 * 这里给全部腐化生物注册 {@code ON_GROUND + MOTION_BLOCKING_NO_LEAVES} 的放置规则；
 * 生成表本身写在 {@code CorruptionBiomeData} 的 biome spawners 里。</p>
 */
public class SplendidingSpawnPlacements {

    public static void register() {
        registerCorruption(CorruptionEntityTypes.CORRUPTION_ZOMBIE);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_COW);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_PIG);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_SHEEP);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_CHICKEN);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_SKELETON);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_WOLF);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_BAT);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_VILLAGER);
        registerCorruption(CorruptionEntityTypes.CORRUPTION_BONE_RUINS);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_ZOMBIE);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_COW);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_PIG);
        registerCorruption(CorruptionEntityTypes.DEGENERATE_VILLAGER);
        registerCorruption(CorruptionEntityTypes.GROVELER);
        registerCorruption(CorruptionEntityTypes.ATTACKER);
        registerCorruption(CorruptionEntityTypes.BEARER);
        registerCorruption(CorruptionEntityTypes.LONG_ARM_BEAST);
        registerCorruption(CorruptionEntityTypes.SHOCKER);
        registerCorruption(CorruptionEntityTypes.HUMUS_COLUMN);
        registerCorruption(CorruptionEntityTypes.SUMMONING_PILLAR);
    }

    private static <T extends Monster> void registerCorruption(RegistryObject<EntityType<T>> type) {
        SpawnPlacements.register(type.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
    }
}
