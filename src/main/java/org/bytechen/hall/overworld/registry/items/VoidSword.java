package org.bytechen.hall.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.compat.BCCoreCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;

import java.util.Map;


/**
 * VoidSword — cosmic starfield + 黑白流动描边。
 *
 * <h3>Visual layers</h3>
 * <ol>
 *   <li>物品本体纹理（原版渲染）</li>
 *   <li>宇宙星空层（走模型 JSON 里的 {@code "loader": "hall:cosmic"}）</li>
 *   <li>黑白流动交替描边（本类实现 {@link ICustomOutline}）</li>
 * </ol>
 *
 * <p>描边是屏幕空间的「剪影遮罩 + 环形膨胀」：只画在剪影外侧，
 * 所以它跟贴在物品表面的星空层永远不会重叠（详见 docs/item-shader-outline.md）。</p>
 *
 * <h3>Right-click: radius health drain</h3>
 * <p>手持右键会把半径内的生物血量往下打。这一下<b>不在本模组里实现</b>，
 * 而是交给可选的 {@code vitalprobe} —— 它用 ASM 数据流逆向找到真实的血量存储位置，
 * 所以对"覆写 getHealth() 返回自定义血量"的抗改血模组同样有效。
 * 对接走 {@link BCCoreCompat} 的反射，VitalProbe 没装时右键只是没这一下，其余一切照常。
 *
 * <h3>Sneak + left-click: forced execution</h3>
 * <p>蹲下左键点中生物＝把它的生命值直接写成 {@code -1}（照样走 VitalProbe 改血，
 * 因此无敌帧、护甲、抗性、抗改血实现一律无视）。实现在 {@link VoidSwordGuard}，
 * 由 {@code AttackEntityEvent} 驱动 —— 事件路径与触发姿态的完整说明都在那个类里。
 * 这里只负责把物品的<b>攻击距离</b>拉到 100 格，让"选中"这一步在客户端就成立。</p>
 *
 * <h3>Attack range: 100 blocks</h3>
 * <p>原版玩家的攻击距离是 {@code forge:entity_reach}（默认 3 格，创造 +3）。
 * 这里给主手装备的这把剑挂一个 {@code +100} 的属性修饰符，于是：</p>
 * <ul>
 *   <li>客户端准星射线用同样的属性做判定，100 格内的生物都能被"选中"；</li>
 *   <li>服务端 {@code ServerGamePacketListenerImpl} 的距离校验也读这个属性，
 *       所以 100 格的攻击不会被判成作弊包丢掉。</li>
 * </ul>
 * <p>注意它同时抬高了"与实体交互"（右键点生物）的距离 —— 这是同一个属性，
 * 原版没有把它们分开。方块交互走的是 {@code forge:block_reach}，<b>不受影响</b>。</p>
 */
public class VoidSword extends SwordItem implements ICustomOutline {

    /**
     * 攻击距离加成（格）。
     *
     * <p>{@code forge:entity_reach} 默认 3.0、上限 1024，所以 100 是一个合法的普通值，
     * 不需要任何越界兜底。</p>
     */
    public static final double ATTACK_REACH_BONUS = 100.0D;

    /**
     * 攻击距离修饰符的 UUID。
     *
     * <p>用固定的字面量而不是随机生成：同一个物品的同一个属性修饰符在不同存档、
     * 不同端之间必须同名同 id，否则会被原版当成两个修饰符叠加（来回切换会越加越多）。</p>
     */
    private static final java.util.UUID REACH_MODIFIER_UUID =
            java.util.UUID.fromString("8f1c4b2a-7d63-4e59-9a0f-3c6b51d2e7a4");

    public VoidSword() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    /**
     * 主手拿在手里时，把玩家攻击距离抬到 {@link #ATTACK_REACH_BONUS} 格。
     *
     * <p>只改主手（{@code MAINHAND}）：这把剑是主手武器，放副手不该给玩家 100 格攻击距离。
     * 其它槽位原样返回 {@code super} 的结果，不插手。</p>
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) return original;

        HashMultimap<Attribute, AttributeModifier> dynamic = HashMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            dynamic.put(entry.getKey(), entry.getValue());
        }
        dynamic.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                REACH_MODIFIER_UUID, "Void sword reach", ATTACK_REACH_BONUS,
                AttributeModifier.Operation.ADDITION));
        return dynamic;
    }

    // ──────────────────────────────────────────────────────────────
    //  描边：黑白流动交替
    // ──────────────────────────────────────────────────────────────
    //
    // 颜色是在剪影遮罩 pass 里逐像素算出来的：outlineShaderKey() 返回 "gradient"
    // 会切到 ColorMode 1 —— 在主色与副色之间按 sin 反复插值、并随时间滚动。
    // 于是描边呈现一圈黑白交替、持续流动的带子（本模型横跨约 5 个来回）。

    @Override
    public int outlineColor() {
        return 0xFF000000;                               // 黑
    }

    @Override
    public int outlineSecondaryColor() {
        return 0xFFFFFFFF;                               // 白
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";                               // = 双色流动
    }


    /**
     * 必须用 {@code TRANSLUCENT}，不能用默认的 {@code ADDITIVE}。
     *
     * <p>加法混合下黑色等于"加 0"——黑的那半截会整个消失，只剩白色在闪，
     * 黑白交替根本不成立。换回 alpha 混合，黑色才真的压得下去。</p>
     */
    @Override
    public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    /** 描边向外扩展的宽度（屏幕像素）。可以在配置里用 outlineWidthScale 整体缩放。 */
    @Override
    public float outlinePixelWidth() {
        return 4.5f;
    }

    /** 默认实现只覆盖世界上下文；这把剑在物品栏图标里也要描一圈。 */
    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.HEAD;
    }

    @Override public Component getName(ItemStack stack) { return Component.translatable(this.getDescriptionId(stack)); }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }

    /**
     * 右键触发范围改血。
     *
     * <p>只处理"对着空气右键"这一种情况——对着方块/生物右键分别走
     * {@code useOn} / {@code interactLivingEntity}，VitalProbe 自己监听了那两条事件路径
     * （它的 {@code void_sword.selfTrigger} 默认开着）。
     *
     * <p>两边同时触发也不会重复生效：VitalProbe 那边用物品冷却去重，
     * 而冷却是在它自己的一次 {@code trigger} 里记账的。
     *
     * <p>客户端也要返回 success，否则客户端预测和服务端结果不一致（手臂不挥）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (BCCoreCompat.isAvailable()) {
            if (!level.isClientSide()) {
                BCCoreCompat.triggerVoidSword(player, stack);
            }
            return InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    /**
     * 原版命中回调。
     *
     * <p>{@code AttackEntityEvent} 已经负责蹲下左键的斩杀（见 {@link VoidSwordGuard}），
     * 这里再兜一道同样的判断：某些模组会自己调用 {@code hurtEnemy}（绕过事件），
     * 或者事件链路被别的模组改了，兜住之后行为不会莫名消失。
     * 同 tick 重复触发由 {@link VoidSwordGuard} 内部去重。</p>
     */
    @Override
    public boolean hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            VoidSwordGuard.executeInstantKill(player, target);
        }
        target.hurt(attacker.damageSources().mobAttack(attacker), 500);
        SwordAuraEntity.spawn(attacker.level(), target.getBoundingBox().getCenter(),
                1.5f, 40, 2.0f);
        return super.hurtEnemy(itemStack, target, attacker);
    }
}
