package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;
import org.bytechen.infcore.core.evolution.EvolutionEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 感染/进化规则数据生成（infcore_evolution）。
 *
 * <p>两大阵营各用独立的 type，互不覆盖：</p>
 * <ul>
 *   <li>王庭：{@code rotten_ruins:hall_inf}（击杀感染）、{@code rotten_ruins:hall_evo}（巨碑进阶）</li>
 *   <li>腐化：{@code rotten_ruins:corruption_inf}（同化感染，迁自 corruption 的
 *       {@code AssimilationMapManager.loadDefaults()}）、{@code rotten_ruins:corruption_evo}
 *       （迁自 {@code BearerManager} 的孕育者进阶）</li>
 * </ul>
 */
public class EvolutionDataProvider extends org.bytechen.infcore.core.datagen.EvolutionDataProvider {

    public EvolutionDataProvider(PackOutput packOutput) {
        super(packOutput, HallMod.MODID, "default");
    }

    @Override
    public List<EvolutionEntry> buildEntries() {
        List<EvolutionEntry> entries = new ArrayList<>();

        // 注意：infcore 的 entry() 会自动加 modId 命名空间，这里传 path（局部 type 名）即可。
        String hallInf = RottenFaction.HALL.infectionRuleType().getPath();
        String hallEvo = RottenFaction.HALL.evolutionRuleType().getPath();
        String corruptionInf = RottenFaction.CORRUPTION.infectionRuleType().getPath();
        String corruptionEvo = RottenFaction.CORRUPTION.evolutionRuleType().getPath();

        // ==================== 王庭：击杀感染 ====================
        entries.add(entry(hallInf, EntityType.PLAYER, false, false,
                List.of(target(EntityTypeRegistry.INF_PLAYER.get(), 100))));
        entries.add(entry(hallInf, EntityType.SKELETON, false, false,
                List.of(target(EntityTypeRegistry.INF_SKELETON.get(), 100))));

        // 王庭击杀腐化生物后的转换规则（corruption victim -> hall 形态）：
        // 有对应王庭形态的走显式规则；其余腐化生物由 UlceratedConversionRules 的
        // 体积兜底（小 -> 斥候 / 中 -> 追迹者 / 大 -> 巨碑）自动转换。
        entries.add(entry(hallInf, CorruptionEntityTypes.CORRUPTION_PLAYER.get(), false, false,
                List.of(target(EntityTypeRegistry.INF_PLAYER.get(), 100))));
        entries.add(entry(hallInf, CorruptionEntityTypes.CORRUPTION_SKELETON.get(), false, false,
                List.of(target(EntityTypeRegistry.INF_SKELETON.get(), 100))));

        // ==================== 王庭：巨碑进阶 ====================
        // 注意：同一 source 的两条规则会互相覆盖，必须写成一条 entry + 两个 target。
        entries.add(entry(hallEvo, EntityTypeRegistry.MONOLITH.get(), false, false,
                List.of(target(EntityTypeRegistry.BONECRUSHER.get(), 50),
                        target(EntityTypeRegistry.HEAVY_BOMB.get(), 50))));

        // ==================== 腐化：同化感染（corruption AssimilationMapManager.loadDefaults） ====================
        entries.add(entry(corruptionInf, EntityType.BAT, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_BAT.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.PIG, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_PIG.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.SHEEP, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_SHEEP.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.COW, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_COW.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.WOLF, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_WOLF.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.CHICKEN, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_CHICKEN.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.SKELETON, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_SKELETON.get(), 100))));
        entries.add(entry(corruptionInf, EntityType.ZOMBIE, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(), 100))));
        // 原工程对村民还有"非幼年 + 精英名称 + 持久化"的回调，infcore 数据格式暂不支持条件/回调，
        // 这里先保留基础转化，条件与命名后续在实体/事件层补。
        entries.add(entry(corruptionInf, EntityType.VILLAGER, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_VILLAGER.get(), 100))));
        // 玩家同化（原 tryAssimilatePlayer）：生成同名同化玩家
        entries.add(entry(corruptionInf, EntityType.PLAYER, false, false,
                List.of(target(CorruptionEntityTypes.CORRUPTION_PLAYER.get(), 100))));

        // ==================== 腐化：感染生物击杀进阶（3 kills -> degenerate） ====================
        // 同化种击杀 3 个生物后进化为对应的腐化种（宿主家族 -> 腐化家族）。
        // 没有对应腐化种的（骷髅/狼/蝙蝠/鸡/玩家/骨堆）不配置规则，applyEvolution 自然不触发。
        entries.add(entry(corruptionEvo, CorruptionEntityTypes.CORRUPTION_ZOMBIE.get(), false, false,
                List.of(target(CorruptionEntityTypes.DEGENERATE_ZOMBIE.get(), 100))));
        entries.add(entry(corruptionEvo, CorruptionEntityTypes.CORRUPTION_COW.get(), false, false,
                List.of(target(CorruptionEntityTypes.DEGENERATE_COW.get(), 100))));
        entries.add(entry(corruptionEvo, CorruptionEntityTypes.CORRUPTION_PIG.get(), false, false,
                List.of(target(CorruptionEntityTypes.DEGENERATE_PIG.get(), 100))));
        entries.add(entry(corruptionEvo, CorruptionEntityTypes.CORRUPTION_VILLAGER.get(), false, false,
                List.of(target(CorruptionEntityTypes.DEGENERATE_VILLAGER.get(), 100))));

        // ==================== 腐化：孕育者进阶（corruption BearerManager） ====================
        entries.add(entry(corruptionEvo, CorruptionEntityTypes.BEARER.get(), false, false,
                List.of(target(CorruptionEntityTypes.LONG_ARM_BEAST.get(), 50),
                        target(CorruptionEntityTypes.SHOCKER.get(), 50))));

        return entries;
    }
}
