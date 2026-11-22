package org.bytechen.rottenruins.event.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.gameplay.infection.evolution.EvolutionManager;
import org.bytechen.rottenruins.overworld.manager.HallSpreadHelper;
import org.bytechen.rottenruins.overworld.registry.capability.threat.ThreatHelper;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

/**
 * 死亡转换：非王庭生物死在王庭类群系里时，就地触发转换。
 *
 * <ol>
 *   <li><b>地形转换</b>：尸体附近按 {@code rotten_ruins:spread} 规则翻成王庭方块，
 *       群系由 {@code BlockSpreadEvent.Post} 入队改写；</li>
 *   <li><b>尸体转换</b>：直接复用王庭的感染/进化流程
 *       {@link EvolutionManager#applyEvolution} 配 {@code rotten_ruins:hall_inf}：
 *       <ul>
 *         <li>有专属映射的生物走进化表（玩家 → 畸骸玩家、骷髅 → 畸骸骷髅、
 *             腐化玩家/骷髅 → 对应畸骸……）；</li>
 *         <li>没有映射的生物走 {@code UlceratedConversionRules} 的碰撞体积兜底
 *             （宽×高 &lt; 2 → 纠察、2~8 → 追蹤者、&gt; 8 → 巨岩，生命上限 &lt; 5 的弱小生物不转化）。</li>
 *       </ul>
 *       这条路径自带位置/朝向/NBT 复制、粒子与 {@code EntityEvolveEvent}。</li>
 * </ol>
 *
 * <p>排除项：王庭生物自己死亡不触发（否则自繁殖）；玩家不转化尸体（保留地形转换）；
 * 盔甲架这类 {@code LivingEntity} 道具完全排除。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HallDeathConversionHandler {

    /** 地形转换：水平半径。 */
    private static final int RADIUS = 4;
    /** 地形转换：尝试次数（上下 ±2 格）。 */
    private static final int ATTEMPTS = 40;

    private HallDeathConversionHandler() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        if (ThreatHelper.isHallCreature(victim)) return;
        // 盔甲架也是 LivingEntity，但它是道具不是"外来生物"。
        if (victim instanceof ArmorStand) return;

        BlockPos pos = victim.blockPosition();
        if (!HallSpreadHelper.isHallBiome(level, pos)) return;

        int converted = HallSpreadHelper.spreadArea(level, pos, RADIUS, ATTEMPTS);
        boolean corpse = convertCorpse(level, victim);
        if (converted == 0 && !corpse) return;

        level.sendParticles(ParticleTypes.SOUL,
                victim.getX(), victim.getY() + 0.4d, victim.getZ(),
                12, 0.4d, 0.3d, 0.4d, 0.02d);
    }

    /**
     * 尸体转换：走王庭自己的感染/进化流程，映射表与碰撞体积兜底都在那里，
     * 这里不再维护第二份映射。
     */
    private static boolean convertCorpse(ServerLevel level, LivingEntity victim) {
        if (victim instanceof Player) return false;
        return EvolutionManager.applyEvolution(level, victim, RottenFaction.HALL.infectionRuleType());
    }
}
