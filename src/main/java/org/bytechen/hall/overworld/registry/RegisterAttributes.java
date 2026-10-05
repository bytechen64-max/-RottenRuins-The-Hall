package org.bytechen.hall.overworld.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.hall.HallMod;

/**
 * 本模组的<b>属性</b>。
 *
 * <p>这五个属性就是"用属性修饰器改魔法数值"的落点。装上之后可以直接用原版命令验证：</p>
 * <pre>
 *   /attribute &#64;s hall:spell_power modifier add hall:test 0.5 multiply_total
 *   /attribute &#64;s hall:max_mana     modifier add hall:test 500 add
 *   /attribute &#64;s hall:mana_regeneration modifier add hall:test 4 add
 *   /attribute &#64;s hall:mana_restore modifier add hall:test 0.5 multiply_total
 *   /attribute &#64;s hall:magic_slots  modifier add hall:test 4 add
 * </pre>
 *
 * <h3>分成"加成"与"倍率"两类</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>默认</th><th>类型</th><th>作用点</th></tr>
 *   <tr><td>{@code hall:max_mana}</td><td>0</td><td>加成</td>
 *       <td>{@code MagicStats.maxMana} → 法力上限 = 1000 + 它</td></tr>
 *   <tr><td>{@code hall:mana_regeneration}</td><td>0</td><td>加成（点/秒）</td>
 *       <td>{@code MagicStats.manaRegenPerSecond} → 每秒 = 1 + 它</td></tr>
 *   <tr><td>{@code hall:magic_slots}</td><td>0</td><td>加成</td>
 *       <td>{@code MagicStats.slotCount} → 槽位 = 10 + 它（上限 64）</td></tr>
 *   <tr><td>{@code hall:spell_power}</td><td>1.0</td><td><b>倍率</b></td>
 *       <td>{@code MagicDamageEvent} 监听器 → 法术伤害 ×它</td></tr>
 *   <tr><td>{@code hall:mana_restore}</td><td>1.0</td><td><b>倍率</b></td>
 *       <td>{@code ManaRestoreEvent} 监听器 → 一切法力回复 ×它</td></tr>
 * </table>
 *
 * <p><b>为什么前三个走 {@code MagicStatProvider}、后两个走事件监听器？</b><br>
 * 因为前三个是"每次求值都现算"的量（法力上限、回速、槽位数），天然适合 provider 链；
 * 而后两个是"一次具体行为"的量（这一下打多少、这一次回多少），
 * 落在事件上才能拿到上下文（打的是谁、回复的来源是什么），也才能被别的监听器叠加/取消。
 * 你要求的也正是这两个事件。</p>
 *
 * <p><b>注意单位</b>：{@code RangedAttribute} 是 double，但 {@code /attribute} 与
 * 修饰器都用 double；两个"倍率"属性的默认值是 1.0，所以给修饰器加 0.5
 * （{@code multiply_total}）就是 ×1.5。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RegisterAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, HallMod.MODID);

    /** 法力上限加成（点）。默认 0 → 上限 = {@code MagicStats.BASE_MAX_MANA} + 它。 */
    public static final RegistryObject<Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute.name.hall.max_mana", 0.0d, 0.0d, 100000.0d)
                    .setSyncable(true));

    /** 每秒回蓝加成（点/秒）。默认 0 → 每秒 = 1 + 它。 */
    public static final RegistryObject<Attribute> MANA_REGENERATION =
            ATTRIBUTES.register("mana_regeneration",
                    () -> new RangedAttribute("attribute.name.hall.mana_regeneration",
                            0.0d, 0.0d, 1000.0d).setSyncable(true));

    /** 一切法力回复量的<b>倍率</b>。默认 1.0。 */
    public static final RegistryObject<Attribute> MANA_RESTORE =
            ATTRIBUTES.register("mana_restore",
                    () -> new RangedAttribute("attribute.name.hall.mana_restore",
                            1.0d, 0.0d, 100.0d).setSyncable(true));

    /** 法术伤害的<b>倍率</b>。默认 1.0。 */
    public static final RegistryObject<Attribute> SPELL_POWER =
            ATTRIBUTES.register("spell_power",
                    () -> new RangedAttribute("attribute.name.hall.spell_power",
                            1.0d, 0.0d, 100.0d).setSyncable(true));

    /** 魔法槽位数加成。默认 0 → 槽位 = 10 + 它（最终上限 {@code MagicStats.MAX_SLOT_COUNT}）。 */
    public static final RegistryObject<Attribute> MAGIC_SLOTS = ATTRIBUTES.register("magic_slots",
            () -> new RangedAttribute("attribute.name.hall.magic_slots", 0.0d, 0.0d, 54.0d)
                    .setSyncable(true));

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

        // 打一行日志是刻意的：这一步**失败时是静默的** ——
        // 事件没触发的话 player.getAttribute(...) 会一直返回 null，
        // /attribute 会说"没有这个属性"，但加载过程一点错都不报。
        // 有这一行就能一眼区分"属性挂上了"和"根本没挂"。
        HallMod.LOGGER.info("[hall] 已把 5 个魔法属性挂到玩家身上"
                + "（hall:max_mana / mana_regeneration / mana_restore / spell_power / magic_slots）");
    }

    private static void addToPlayer(EntityAttributeModificationEvent event, Attribute attribute) {
        event.add((EntityType<? extends LivingEntity>) EntityType.PLAYER, attribute);
    }

    /**
     * 取某个实体上该属性的当前值（含所有修饰器）。
     *
     * @return 属性值；实体没有这个属性（例如生物没挂）时返回 {@code fallback}
     */
    public static double valueOf(LivingEntity entity, RegistryObject<Attribute> attribute,
                                 double fallback) {
        if (entity == null || attribute == null || !attribute.isPresent()) {
            return fallback;
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
                entity.getAttribute(attribute.get());
        return instance == null ? fallback : instance.getValue();
    }
}
