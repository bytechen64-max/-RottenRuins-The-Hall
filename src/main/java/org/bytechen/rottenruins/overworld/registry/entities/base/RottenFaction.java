package org.bytechen.rottenruins.overworld.registry.entities.base;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;

import javax.annotation.Nullable;

/**
 * 生物阵营：王庭（HALL）/ 腐化（CORRUPTION）/ 死亡（DEATH）。
 *
 * <p>三派共用一套生物基类（{@code AbstractRottenEntity}），但**不是从属关系**：
 * 各有自己的阵营标识与规则，因此这里集中管理三组 id：</p>
 * <ul>
 *   <li>{@link #infectionType()} —— 阵营标识，{@code IInfectedEntity.getInfectionType()} 的返回值，
 *       {@code rotten_ruins:hall} / {@code rotten_ruins:corruption} / {@code rotten_ruins:death}；</li>
 *   <li>{@link #infectionRuleType()} —— 击杀感染规则类型（infcore_evolution 的 {@code type}），
 *       {@code rotten_ruins:hall_inf} / {@code rotten_ruins:corruption_inf} /
 *       {@code rotten_ruins:death_inf}；</li>
 *   <li>{@link #evolutionRuleType()} —— 自身进阶规则类型，
 *       {@code rotten_ruins:hall_evo} / {@code rotten_ruins:corruption_evo} /
 *       {@code rotten_ruins:death_evo}。</li>
 * </ul>
 *
 * <h3>死亡（DEATH）为什么是独立阵营</h3>
 * <p>死亡系不是王庭 / 腐化的分支：它不参与感染扩散，也没有任何
 * {@code death_inf} / {@code death_evo} 数据规则 —— 击杀结算时
 * {@code EvolutionManager.applyEvolution} 查不到规则会直接返回 {@code false}，
 * 因此死亡系<b>不会把击杀目标感染成任何形态</b>，只是单纯地猎杀。</p>
 * <p>索敌层面它则是一个正常阵营：死亡系与王庭 / 腐化互为"不同感染类型"，
 * 双方开启跨阵营索敌（{@code targetOtherInfected=true}）后天然敌对；
 * 同类死亡系之间仍然互不攻击。</p>
 *
 * <p>infcore 的索敌默认"感染者不互殴"（{@code targetOtherInfected=false}），
 * 各生物按需开启跨阵营索敌：目前王庭 / 腐化 / 死亡都已开启。</p>
 */
public enum RottenFaction {
    /** 王庭系（原 Hall 内容）。 */
    HALL("hall"),
    /** 腐化系（原 corruption 内容）。 */
    CORRUPTION("corruption"),
    /**
     * 死亡系（独立阵营，不属于王庭 / 腐化）。
     * <p>没有感染 / 进化规则：击杀不会转化目标，也不会喷感染血。</p>
     */
    DEATH("death");

    private final String id;
    private final ResourceLocation infectionType;
    private final ResourceLocation infectionRuleType;
    private final ResourceLocation evolutionRuleType;

    RottenFaction(String id) {
        this.id = id;
        this.infectionType = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, id);
        this.infectionRuleType = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, id + "_inf");
        this.evolutionRuleType = ResourceLocation.fromNamespaceAndPath(HallMod.MODID, id + "_evo");
    }

    public String id() {
        return id;
    }

    /** 阵营标识（{@code IInfectedEntity.getInfectionType()} 的返回值）。 */
    public ResourceLocation infectionType() {
        return infectionType;
    }

    /** 击杀感染的演化规则 type。 */
    public ResourceLocation infectionRuleType() {
        return infectionRuleType;
    }

    /** 自身进阶（进化）规则 type。 */
    public ResourceLocation evolutionRuleType() {
        return evolutionRuleType;
    }

    /** 给定感染类型是否属于该阵营。 */
    public boolean matches(@Nullable ResourceLocation type) {
        return type != null
                && HallMod.MODID.equals(type.getNamespace())
                && id.equals(type.getPath());
    }

    public static boolean isHall(@Nullable ResourceLocation type) {
        return HALL.matches(type);
    }

    public static boolean isCorruption(@Nullable ResourceLocation type) {
        return CORRUPTION.matches(type);
    }

    public static boolean isDeath(@Nullable ResourceLocation type) {
        return DEATH.matches(type);
    }

    /** 是否本模组我方阵营（王庭 / 腐化 / 死亡）的感染类型。 */
    public static boolean isOurs(@Nullable ResourceLocation type) {
        return HALL.matches(type) || CORRUPTION.matches(type) || DEATH.matches(type);
    }
}
