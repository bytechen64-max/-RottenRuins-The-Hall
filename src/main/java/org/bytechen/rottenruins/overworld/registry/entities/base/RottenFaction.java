package org.bytechen.rottenruins.overworld.registry.entities.base;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;

import javax.annotation.Nullable;

/**
 * 合并后的两大感染阵营。
 *
 * <p>王庭（HALL）与腐化（CORRUPTION）共用一套生物基类，但**不是从属关系**：
 * 两派各有自己的感染类型、感染/进化规则与感染结果，因此这里集中管理三组 id：</p>
 * <ul>
 *   <li>{@link #infectionType()} —— 阵营标识，{@code IInfectedEntity.getInfectionType()} 的返回值，
 *       {@code rotten_ruins:hall} / {@code rotten_ruins:corruption}；</li>
 *   <li>{@link #infectionRuleType()} —— 击杀感染规则类型（infcore_evolution 的 {@code type}），
 *       {@code rotten_ruins:hall_inf} / {@code rotten_ruins:corruption_inf}；</li>
 *   <li>{@link #evolutionRuleType()} —— 自身进阶规则类型，
 *       {@code rotten_ruins:hall_evo} / {@code rotten_ruins:corruption_evo}。</li>
 * </ul>
 *
 * <p>infcore 的索敌默认"感染者不互殴"（{@code targetOtherInfected=false}），所以两个阵营
 * 当前默认互不攻击；以后若要做王庭 vs 腐化内战，再开 {@code targetOtherInfected} + filter 即可。</p>
 */
public enum RottenFaction {
    /** 王庭系（原 Hall 内容）。 */
    HALL("hall"),
    /** 腐化系（原 corruption 内容）。 */
    CORRUPTION("corruption");

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

    /** 是否本模组两派任意之一的感染类型。 */
    public static boolean isOurs(@Nullable ResourceLocation type) {
        return HALL.matches(type) || CORRUPTION.matches(type);
    }
}
