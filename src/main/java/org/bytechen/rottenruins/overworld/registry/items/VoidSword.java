package org.bytechen.rottenruins.overworld.registry.items;

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
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.api.ICustomOutline;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;

import java.util.Map;

/**
 * VoidSword — cosmic starfield + 黑白流动描边。
 * <p>三层视觉：物品本体纹理、模型 JSON 走 {@code rotten_ruins:cosmic} 的星空层、本类实现的
 * {@link ICustomOutline} 流动描边（屏幕空间「剪影遮罩 + 环形膨胀」，只画在剪影外侧）。
 * <p>??????????????????????{@link org.bytechen.rottenruins.utils.entity.DirectHealth}
 * ?????????????????????
 * <p>攻击距离 100 格：给主手装备的这把剑加 {@code forge:entity_reach} 修饰符，客户端准星射线与服务端
 * 距离校验读的是同一属性（不是作弊包）；方块交互走 {@code forge:block_reach}，不受影响。
 */
public class VoidSword extends SwordItem implements ICustomOutline, IBlockingWeapon {

    public VoidSword() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    /** 攻击距离加成（格）：{@code forge:entity_reach} 默认 3.0、上限 1024，100 是合法普通值。 */
    public static final double ATTACK_REACH_BONUS = 100.0D;

    /** 修饰符 UUID 必须是固定字面量：不同存档/端上同名同 id 才不会被原版当成两个修饰符叠加。 */
    private static final java.util.UUID REACH_MODIFIER_UUID =
            java.util.UUID.fromString("8f1c4b2a-7d63-4e59-9a0f-3c6b51d2e7a4");
    /** 只改主手（{@code MAINHAND}）：放副手不该给玩家 100 格攻击距离，其它槽位原样返回 {@code super}。 */
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

    // 描边：{@code outlineShaderKey() = "gradient"} 切到 ColorMode 1，主副色之间按 sin 反复插值并随时间滚动。

    @Override
    public int outlineColor() {
        return 0xFF000000;
    }

    @Override
    public int outlineSecondaryColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";
    }

    /** 必须 {@code TRANSLUCENT} 不能 {@code ADDITIVE}：加法下黑色等于加 0，黑的那半截会整个消失。 */
    @Override
    public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    /** 描边向外扩展的宽度（屏幕像素），可被配置里的 {@code outlineWidthScale} 整体缩放。 */
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

    /** 右键范围改血：只处理「对着空气右键」（方块/生物走 {@code useOn} / {@code interactLivingEntity}，
     *  客户端也要返回 success，否则手臂不挥。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        return IBlockingWeapon.beginBlock(level, player, hand);
    }

    /** 原版命中回调：{@code AttackEntityEvent} 已负责蹲下左键斩杀（见 {@link VoidSwordGuard}），这里兜一道防止绕过事件的模组让行为消失。 */
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
