package org.bytechen.hall.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.api.IBlockingWeapon;

/**
 * 所有 {@link IBlockingWeapon} 共用的格挡结算：<b>减伤 + 正面判定 + 回调派发</b>。
 *
 * <h3>为什么单独一个类</h3>
 * <p>与本模组 {@code DomeriteLongswordBehavior} 同样的理由：事件层只做订阅转发，
 * 具体规则集中在行为类里。这一条规则服务所有实现 {@link IBlockingWeapon} 的武器，
 * 所以它不该绑在任何一把具体的剑上（早先叫 {@code CrimsonVowBlock}，现已通用化）。</p>
 *
 * <h3>伤害减免为什么走 {@code setAmount} 而不是 {@code invulnerableTime}</h3>
 * <p>后者会让第一下伤害把后续若干 tick 全部免疫掉，既不可预期、又会被着火/中毒
 * 这类东西意外触发；改倍率是干净且可叠加的。</p>
 *
 * <h3>正面判定</h3>
 * <p>用「伤害来源方向 vs 玩家视线」的点积：来源在身前才减伤。阈值取 {@code 0}
 * （正好 90° 为界）—— 取正值会让侧面攻击完全无法格挡（手感很怪），
 * 取负值则会把背后的攻击也算进来（失去意义）。</p>
 *
 * <p>无来源（摔落、虚空、{@code /damage} 之类）<b>不</b>算格挡 ——
 * 1.8 里摔落伤害也是挡不住的。</p>
 */
public final class WeaponBlock {

    private WeaponBlock() {}

    /** 是否只允许正面格挡（由武器的 {@link IBlockingWeapon#blockOnlyFrontal()} 决定）。 */
    private static boolean frontalOnly(IBlockingWeapon weapon) {
        return weapon.blockOnlyFrontal();
    }

    /**
     * 事件入口：正在格挡的玩家受到的（正面）伤害打折，并派发 {@code onBlockedHit}。
     *
     * <p>只对<b>玩家</b>生效 —— 这条逻辑是给玩家用的防反手段，
     * 让任意生物都能格挡会连带影响刷怪与 AI 行为的平衡。</p>
     *
     * @param entity 受伤者
     * @param source 伤害来源
     * @param amount 原始伤害
     * @return 减免后的伤害；未格挡时原样返回
     */
    public static float mitigate(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0f) return amount;
        if (!(entity instanceof Player)) return amount;

        IBlockingWeapon weapon = IBlockingWeapon.blockingWeapon(entity);
        if (weapon == null) return amount;
        if (frontalOnly(weapon) && !isFrontal(entity, source)) return amount;

        float reduced = amount * weapon.blockDamageMultiplier();

        // 挡下之后派发回调（内部还会处理提示 / 粒子 / 兜底音效）
        IBlockingWeapon.notifyBlockedHit(entity, source, amount, reduced);

        return reduced;
    }

    /**
     * 伤害是否来自玩家的前方。
     *
     * <p>来源位置缺失（摔落/虚空/无来源伤害）时返回 false —— 这类伤害挡不住。</p>
     */
    public static boolean isFrontal(LivingEntity entity, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) return false;

        Vec3 from;
        Vec3 src = source.getSourcePosition();
        from = (src != null) ? src : attacker.position();

        Vec3 toSource = from.subtract(entity.position());
        Vec3 flat = new Vec3(toSource.x, 0.0, toSource.z);
        if (flat.lengthSqr() < 1.0e-6) {
            // 来源就在脚下/重叠：无法判定方向，按"挡不住"处理，避免贴脸时白送减伤
            return false;
        }
        Vec3 look = entity.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0e-6) return false;

        return flat.normalize().dot(flatLook.normalize()) > 0.0;
    }
}
