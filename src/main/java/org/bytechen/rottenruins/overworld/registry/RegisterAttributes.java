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

/**
 * 本模组的<b>属性</b>。
 *
 * <p>这些属性就是"用属性修饰器改魔法数值"的落点。装上之后可以直接用原版命令验证：</p>
 * <pre>
 *   /attribute &#64;s rotten_ruins:spell_power modifier add test 0.5 multiply_total
 *   /attribute &#64;s rotten_ruins:max_mana     modifier add test 500 add
 *   /attribute &#64;s rotten_ruins:mana_regeneration modifier add test 4 add
 *   /attribute &#64;s rotten_ruins:mana_restore modifier add test 0.5 multiply_total
 *   /attribute &#64;s rotten_ruins:magic_slots  modifier add test 4 add
 *   /attribute &#64;s rotten_ruins:cooldown_reduction modifier add test 0.25 add
 *   /attribute &#64;s rotten_ruins:spell_power_fire   modifier add test 0.5 add
 * </pre>
 *
 * <h3>分成"加成"与"倍率"两类</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>默认</th><th>类型</th><th>作用点</th></tr>
 *   <tr><td>{@code max_mana}</td><td>0</td><td>加成</td>
 *       <td>{@code MagicStats.maxMana} → 法力上限 = 1000 + 它</td></tr>
 *   <tr><td>{@code mana_regeneration}</td><td>0</td><td>加成（点/秒）</td>
 *       <td>{@code MagicStats.manaRegenPerSecond} → 每秒 = 1 + 它</td></tr>
 *   <tr><td>{@code magic_slots}</td><td>0</td><td>加成</td>
 *       <td>{@code MagicStats.slotCount} → 槽位 = 10 + 它（上限 64）</td></tr>
 *   <tr><td>{@code cooldown_reduction}</td><td>0</td><td>加成（比例，<b>上限 0.5</b>）</td>
 *       <td>{@code MagicAttributeProvider#modifyCooldownTicks} →
 *           冷却 = 基值 × (1 − 它)，对全部法术生效</td></tr>
 *   <tr><td>{@code spell_power}</td><td>1.0</td><td><b>倍率</b>（通用）</td>
 *       <td>{@code MagicStats.spellPower} → 法术伤害 / 半径 ×（它 + 流派法强）</td></tr>
 *   <tr><td>{@code spell_power_&lt;流派&gt;}</td><td>0</td><td>加成（每个流派一个）</td>
 *       <td>同上，<b>与通用法强相加</b>。流派取 {@link MagicType}，共
 *           {@code WATER / FIRE / DEATH / NATURE / LIGHT / VOID / MYSTERIOUS} 七个</td></tr>
 *   <tr><td>{@code mana_restore}</td><td>1.0</td><td><b>倍率</b></td>
 *       <td>{@code ManaRestoreEvent} 监听器 → 一切法力回复 ×它</td></tr>
 * </table>
 *
 * <p><b>法术强度为什么要分"通用 + 流派"两个</b>：通用那份是"这个人是法师"，
 * 流派那份是"这个人专精火系"。两者<b>相加</b>而不是相乘 —— 相乘会让双修玩家的收益
 * 变成平方增长，而相加是线性的，数值好控。举例：通用 1.0、火系 +0.5、虚空 +0，
 * 那么火球术的有效法强是 1.5，同一时刻放虚空法术还是 1.0。</p>
 *
 * <p><b>为什么加成类的走 {@code MagicStatProvider}、两个倍率走事件监听器？</b><br>
 * 因为前者是"每次求值都现算"的量（法力上限、回速、槽位、冷却），天然适合 provider 链；
 * 而后者是"一次具体行为"的量（这一下打多少、这一次回多少），
 * 落在事件上才能拿到上下文（打的是谁、回复的来源是什么），也才能被别的监听器叠加/取消。
 * 法术强度两种都沾：它既能现算（火球半径），又要在伤害事件上用，所以
 * {@code MagicStats.spellPower} 是唯一入口，两个场合都从那里取。</p>
 *
 * <p><b>注意单位</b>：{@code RangedAttribute} 是 double，{@code /attribute} 与修饰器
 * 也都用 double。默认值 1.0 的是<b>倍率</b>（加 0.5 的 {@code multiply_total} 就是 ×1.5），
 * 默认值 0.0 的是<b>加成</b>（{@code add 0.5} 就是 +0.5）。</p>
 */
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

    /**
     * <b>冷却缩减</b>（比例）。默认 {@code 0.0} = 不减。
     *
     * <p>属性的上限直接设成 {@code 0.5} —— 需求就是"最多叠加 50%"，
     * 交给 {@link RangedAttribute} 自己钳最省事：无论多少条 {@code add} 修饰器叠上来，
     * 读到的值都不会超过 0.5。{@code MagicAttributeProvider} 那边还会再钳一次，
     * 防止有人绕过属性直接塞值。</p>
     *
     * <p>作用点：{@code MagicStats.cooldownTicks} —— 即
     * {@code cooldown = 基值 × (1 − 本值)}，对<b>所有</b>法术生效
     * （冷却键仍是物品注册名，同名物品照样共享缩短后的冷却）。</p>
     */
    public static final RegistryObject<Attribute> COOLDOWN_REDUCTION =
            ATTRIBUTES.register("cooldown_reduction",
                    () -> new RangedAttribute("attribute.name.rotten_ruins.cooldown_reduction",
                            0.0d, 0.0d, MagicStats.MAX_COOLDOWN_REDUCTION).setSyncable(true));

    // ══════════════════════════════════════════════════════════════
    // 各流派的法术强度
    // ══════════════════════════════════════════════════════════════
    // 一个流派一个属性，默认 0.0，都是"加成"而不是倍率 —— 计算方式是
    //   有效法强 = 通用法强(spell_power) + 该流派的法强
    // 也就是和通用法强<b>相加</b>：通用 1.0 + 火系 0.5 = 1.5 倍。
    // 见 MagicStats.spellPower(Player, ItemStack)。

    /** {@link MagicType#WATER} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_WATER = spellPowerOf("water");

    /** {@link MagicType#FIRE} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_FIRE = spellPowerOf("fire");

    /** {@link MagicType#DEATH} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_DEATH = spellPowerOf("death");

    /** {@link MagicType#NATURE} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_NATURE = spellPowerOf("nature");

    /** {@link MagicType#LIGHT} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_LIGHT = spellPowerOf("light");

    /** {@link MagicType#VOID} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_VOID = spellPowerOf("void");

    /** {@link MagicType#MYSTERIOUS} 流派的法术强度加成。 */
    public static final RegistryObject<Attribute> SPELL_POWER_MYSTERIOUS = spellPowerOf("mysterious");

    /** 注册一个流派法强属性：名字统一 {@code spell_power_<流派小写>}。 */
    private static RegistryObject<Attribute> spellPowerOf(String school) {
        return ATTRIBUTES.register("spell_power_" + school,
                () -> new RangedAttribute("attribute.name.rotten_ruins.spell_power_" + school,
                        0.0d, 0.0d, 100.0d).setSyncable(true));
    }

    /**
     * 取某个流派对应的法强属性。
     *
     * <p>新增流派时<b>必须</b>在这里补一个 case，否则那个流派的法术只会吃通用法强
     * —— 而且是静默的，不会有任何报错，所以 {@code switch} 不写 {@code default}
     * 让编译器替我们盯着（枚举新增值时会编译失败）。</p>
     */
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
     * 把属性挂到玩家身上。
     *
     * <p><b>这一步不能省</b>：Forge 注册一个 {@code Attribute} 只是让它进注册表，
     * 并不会自动给任何实体建出 {@code AttributeInstance}。必须在
     * {@code EntityAttributeModificationEvent} 里显式声明"哪些实体类型要有这个属性"，
     * 否则 {@code player.getAttribute(...)} 永远返回 {@code null}，
     * {@code /attribute} 也会说没这个属性。</p>
     *
     * <p>目前只给玩家加 —— 这套法力/法术槽系统就是玩家专属的。
     * 以后若想让某些生物也能施法，在这里补一行 {@code event.add(EntityType.X, ...)} 即可。</p>
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

        // 打一行日志是刻意的：这一步**失败时是静默的** ——
        // 事件没触发的话 player.getAttribute(...) 会一直返回 null，
        // /attribute 会说"没有这个属性"，但加载过程一点错都不报。
        // 有这一行就能一眼区分"属性挂上了"和"根本没挂"。
        HallMod.LOGGER.info("[rotten_ruins] 已把 {} 个魔法属性挂到玩家身上"
                        + "（max_mana / mana_regeneration / mana_restore / spell_power / magic_slots"
                        + " / cooldown_reduction / spell_power_<流派> x{}）",
                6 + MagicType.values().length, MagicType.values().length);
    }

    private static void addToPlayer(EntityAttributeModificationEvent event, Attribute attribute) {
        event.add((EntityType<? extends LivingEntity>) EntityType.PLAYER, attribute);
    }

    /**
     * 取某个实体上该属性的当前值（含所有修饰器）。
     *
     * <p><b>本方法可能在"实体还没构造完"的时候被调用</b>，所以必须自己扛住这种情况：</p>
     * <pre>
     *   Entity.&lt;init&gt;                     ← 属性表还没建
     *     → CapabilityProvider.gatherCapabilities
     *       → ForgeEventFactory.gatherCapabilities
     *         → AttachCapabilitiesEvent        ← 本模组在这里 new TwistedPoint(player)
     *           → MagicStats.maxMana           ← 记一次"上限 1000"
     *             → MagicAttributeProvider
     *               → valueOf                  ← 就是这里
     * </pre>
     * <p>{@code LivingEntity} 的 {@code AttributeMap} 是 {@code super(...)}（也就是
     * {@code Entity} 的构造函数）<b>返回之后</b>才赋值的，而 {@code AttachCapabilitiesEvent}
     * 正是在 {@code Entity} 的构造函数里派发的 —— 那一刻 {@code getAttributes()} 返回
     * {@code null}。原版 {@code LivingEntity#getAttribute} 直接拿它调
     * {@code AttributeMap.getInstance}，于是抛 {@code NullPointerException}；
     * 这个异常从能力附加的监听器里冒出去，Forge 会把整次 {@code AttachCapabilitiesEvent}
     * 判为失败，玩家的魔法池/法力池全都挂不上，最后表现为"无法把玩家放进世界"被踢回登录界面。</p>
     *
     * <p>所以属性表还没建好时返回 {@code fallback}。这不是"吞掉错误"：
     * 那个时刻本来就不存在任何修饰器（玩家刚 new 出来，装备/药水都还没上），
     * 三个加成属性的 {@code fallback}（0）与两个倍率属性的 {@code fallback}（1.0）
     * 正是它们此刻的真实值；真正的数值会在读的时候由 {@code MagicStats} 现算，
     * 或者由存档 NBT 覆盖回来。</p>
     *
     * @return 属性值；实体没有这个属性（例如生物没挂）或属性表尚未初始化时返回 {@code fallback}
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
