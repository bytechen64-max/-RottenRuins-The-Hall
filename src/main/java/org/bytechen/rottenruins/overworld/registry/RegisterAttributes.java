package org.bytechen.rottenruins.overworld.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.jetbrains.annotations.NotNull;

/** 本模组的魔法属性。加成类（上限/回速/槽位/冷却）走 {@code MagicStatProvider}，两个倍率走事件监听器。 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RegisterAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, HallMod.MODID);

    /** 法力上限加成（点）。默认 0 → 上限 = {@code MagicStats.BASE_MAX_MANA} + 它。 */
    public static final RegistryObject<Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute.name.rotten_ruins.max_mana", 0.0d, 0.0d, 100000.0d)
                    .setSyncable(true));

    /** 每秒回蓝加成（点/秒）。默认 0 → 每秒 = 1 + 它。 */
    public static final RegistryObject<Attribute> MANA_REGENERATION =
            ATTRIBUTES.register("mana_regeneration",
                    () -> new RangedAttribute("attribute.name.rotten_ruins.mana_regeneration",
                            0.0d, 0.0d, 1000.0d).setSyncable(true));

    /** 一切法力回复量的<b>倍率</b>。默认 1.0。 */
    public static final RegistryObject<Attribute> MANA_RESTORE =
            ATTRIBUTES.register("mana_restore",
                    () -> new RangedAttribute("attribute.name.rotten_ruins.mana_restore",
                            1.0d, 0.0d, 100.0d).setSyncable(true));

    /** 法术伤害的<b>倍率</b>。默认 1.0。 */
    public static final RegistryObject<Attribute> SPELL_POWER =
            ATTRIBUTES.register("spell_power",
                    () -> new RangedAttribute("attribute.name.rotten_ruins.spell_power",
                            1.0d, 0.0d, 100.0d).setSyncable(true));

    /** 魔法槽位数加成。默认 0 → 槽位 = 10 + 它（最终上限 {@code MagicStats.MAX_SLOT_COUNT}）。 */
    public static final RegistryObject<Attribute> MAGIC_SLOTS = ATTRIBUTES.register("magic_slots",
            () -> new RangedAttribute("attribute.name.rotten_ruins.magic_slots", 0.0d, 0.0d, 54.0d)
                    .setSyncable(true));

    /** <b>冷却缩减</b>比例，上限 0.5 —— 由 {@link RangedAttribute} 钳一次、{@code MagicAttributeProvider} 再钳一次。 */
    public static final RegistryObject<Attribute> COOLDOWN_REDUCTION =
            ATTRIBUTES.register("cooldown_reduction",
                    () -> new RangedAttribute("attribute.name.rotten_ruins.cooldown_reduction",
                            0.0d, 0.0d, MagicStats.MAX_COOLDOWN_REDUCTION).setSyncable(true));

    // 各流派法强：默认 0.0，与通用 spell_power 相加（通用 1.0 + 火系 0.5 = 1.5）。

    public static final RegistryObject<Attribute> SPELL_POWER_WATER = spellPowerOf("water");

    public static final RegistryObject<Attribute> SPELL_POWER_FIRE = spellPowerOf("fire");

    public static final RegistryObject<Attribute> SPELL_POWER_DEATH = spellPowerOf("death");

    public static final RegistryObject<Attribute> SPELL_POWER_NATURE = spellPowerOf("nature");

    public static final RegistryObject<Attribute> SPELL_POWER_LIGHT = spellPowerOf("light");

    public static final RegistryObject<Attribute> SPELL_POWER_VOID = spellPowerOf("void");

    public static final RegistryObject<Attribute> SPELL_POWER_MYSTERIOUS = spellPowerOf("mysterious");

    private static RegistryObject<Attribute> spellPowerOf(String school) {
        return ATTRIBUTES.register("spell_power_" + school,
                () -> new RangedAttribute("attribute.name.rotten_ruins.spell_power_" + school,
                        0.0d, 0.0d, 100.0d).setSyncable(true));
    }

    /** 新增流派必须在此补 case：漏了会静默只吃通用法强，故 switch 不写 default 让编译器盯着。 */
    @NotNull
    public static RegistryObject<Attribute> spellPowerOf(@NotNull MagicType type) {
        return switch (type) {
            case WATER -> SPELL_POWER_WATER;
            case FIRE -> SPELL_POWER_FIRE;
            case DEATH -> SPELL_POWER_DEATH;
            case NATURE -> SPELL_POWER_NATURE;
            case LIGHT -> SPELL_POWER_LIGHT;
            case VOID -> SPELL_POWER_VOID;
            case MYSTERIOUS -> SPELL_POWER_MYSTERIOUS;
        };
    }

    private RegisterAttributes() {
    }

    /**
     * 把属性挂到玩家身上。<b>不能省</b>：Forge 注册 {@code Attribute} 不会自动给实体建
     * {@code AttributeInstance}，漏了这一步 {@code player.getAttribute(...)} 永远返回 null。
     */
    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        addToPlayer(event, MAX_MANA.get());
        addToPlayer(event, MANA_REGENERATION.get());
        addToPlayer(event, MANA_RESTORE.get());
        addToPlayer(event, SPELL_POWER.get());
        addToPlayer(event, MAGIC_SLOTS.get());
        addToPlayer(event, COOLDOWN_REDUCTION.get());
        // 各流派的法强：遍历枚举，新增流派时这里自动跟上（属性本身还是要手动注册）
        for (MagicType type : MagicType.values()) {
            addToPlayer(event, spellPowerOf(type).get());
        }

        // 这行日志是刻意的：属性没挂上时完全静默，有这个能一眼区分
        HallMod.LOGGER.info("[rotten_ruins] 已把 {} 个魔法属性挂到玩家身上"
                        + "（max_mana / mana_regeneration / mana_restore / spell_power / magic_slots"
                        + " / cooldown_reduction / spell_power_<流派> x{}）",
                6 + MagicType.values().length, MagicType.values().length);
    }

    private static void addToPlayer(EntityAttributeModificationEvent event, Attribute attribute) {
        event.add((EntityType<? extends LivingEntity>) EntityType.PLAYER, attribute);
    }

    /**
     * 取实体属性当前值（含修饰器）。<b>必须容忍属性表为 null</b>：{@code AttachCapabilitiesEvent}
     * 在 {@code Entity} 构造器里派发，那时 {@code LivingEntity} 的 AttributeMap 还没赋值，
     * 原版 {@code getAttribute} 会抛 NPE → 整个能力附加失败 → 玩家被踢回登录界面。
     */
    public static double valueOf(LivingEntity entity, RegistryObject<Attribute> attribute,
                                 double fallback) {
        if (entity == null || attribute == null || !attribute.isPresent()) {
            return fallback;
        }
        // 实体还在构造中（AttachCapabilitiesEvent 期间）时属性表是 null，见方法注释。
        AttributeMap attributes = entity.getAttributes();
        if (attributes == null) {
            return fallback;
        }
        AttributeInstance instance = attributes.getInstance(attribute.get());
        return instance == null ? fallback : instance.getValue();
    }
}
