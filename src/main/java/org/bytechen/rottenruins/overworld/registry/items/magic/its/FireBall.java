package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.FireBallEntity;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

/** <b>火球术</b>：立刻释放，朝准星方向打出一颗火球，命中后爆炸（受 {@code mobGriefing} 影响破坏方块 + 范围伤害，另结算一次直接伤害）。半径 = {@link #baseExplosionRadius()} × 法术强度（通用 spell_power + 火系 spell_power_fire），钳在 {@value #MIN_EXPLOSION_RADIUS} ~ {@value #MAX_EXPLOSION_RADIUS} 格；上限用于防止叠加到把区块炸穿，想要更大火球请覆写 {@link #baseExplosionRadius()}。 */
public class FireBall extends CMagicBaseItem {

    public static final double BASE_EXPLOSION_RADIUS = 2.0d;

    public static final double MIN_EXPLOSION_RADIUS = 0.5d;

    public static final double MAX_EXPLOSION_RADIUS = 12.0d;

    public FireBall() {
        super(new Properties().stacksTo(1), MagicType.FIRE, UsingType.IMMEDIATELY,
                40,     // 启动耗蓝
                40,     // 冷却 tick（同名物品共享）
                0,      // 蓄力 tick —— 非 CHARGE，基类会归零
                0,      // 每秒维持耗蓝 —— 非 KEEP，基类会归零
                4.0f);  // 直接命中的伤害基值
    }

    public double baseExplosionRadius() {
        return BASE_EXPLOSION_RADIUS;
    }
    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        double spellPower = MagicStats.spellPower(player, stack);
        double radius = Mth.clamp(baseExplosionRadius() * spellPower,
                MIN_EXPLOSION_RADIUS, MAX_EXPLOSION_RADIUS);

        FireBallEntity.spawn(player, stack, radius,player.isShiftKeyDown());
        return true;
    }
}
