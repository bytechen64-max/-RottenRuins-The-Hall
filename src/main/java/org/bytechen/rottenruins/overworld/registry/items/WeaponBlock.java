package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.IBlockingWeapon;

/** IBlockingWeapon 共用的格挡结算：减伤走倍率而非 invulnerableTime；正面判定取点积 > 0（90°），无来源伤害不算格挡。 */
public final class WeaponBlock {

    private WeaponBlock() {}

    private static boolean frontalOnly(IBlockingWeapon weapon) {
        return weapon.blockOnlyFrontal();
    }

    /** 事件入口：格挡中的玩家受到的（正面）伤害打折并派发 onBlockedHit；只对玩家生效以免影响刷怪/AI 平衡。 */
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
