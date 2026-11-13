package org.bytechen.rottenruins.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.bytechen.rottenruins.utils.ModUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * 与 VitalProbe（改血引擎）的可选对接：先用 {@link ModList#isLoaded} 查 modid，再反射取 {@code org.bytechen.bccore.api.BCHealth}，
 * 整条链路包在 try/catch 里 —— 没装、版本不匹配、调用抛异常都只是"退回原版伤害 / 不改血"，绝不会把技能逻辑带崩。
 * <p>技能伤害统一走 {@link #damage}：{@code setHealth(getHealth() - 5)} 会留下 {@code 1.0E-7} 这种"看着是 0"的残值让人永远不死，
 * 所以约定"有效血量 - 伤害"<b>不做 0 下限钳制</b>，新血量 {@code <= 0} 时再补一次终结击。</p>
 */
public final class BCCoreCompat {

    /** 改血引擎的 modid：新版叫 {@code bccore}（见 {@code mods.toml}），只认一个名字会让整个改血 API 静默失效。 */
    public static final String MOD_ID = "bccore";

    public static final String LEGACY_MOD_ID = "vitalprobe";

    private static final String API_CLASS = "org.bytechen.bccore.api.BCHealth";

    private static final Class<?> API;

    private static final Method TRIGGER_VOID_SWORD;
    private static final Method GET_HEALTH;
    /** {@code BCHealth.set(LivingEntity, float)} —— 不钳制，允许 ≤0。 */
    private static final Method SET_HEALTH;
    private static final Method ADD_HEALTH;

    static {
        Class<?> api = null;
        Method trigger = null;
        Method get = null;
        Method set = null;
        Method add = null;

        if (ModList.get().isLoaded(MOD_ID) || ModList.get().isLoaded(LEGACY_MOD_ID)) {
            try {
                api = Class.forName(API_CLASS);
                // 优先按签名精确查找；名字给一组候选：不同版本的 VitalProbe 用过 get/getHealth、set/setHealth 两种写法。
                trigger = find(api, new String[]{"triggerVoidSword"}, Player.class, ItemStack.class);
                get = find(api, new String[]{"get", "getHealth", "healthOf"}, LivingEntity.class);
                set = find(api, new String[]{"set", "setHealth", "writeHealth"}, LivingEntity.class, float.class);
                add = find(api, new String[]{"add", "addHealth", "changeHealth"}, LivingEntity.class, float.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                ModUtils.LOGGER.warn("[BCCoreCompat] vitalprobe 已加载但 API 解析失败，"
                        + "改血相关功能将不可用：{}", e.toString());
                api = null;
            }
        }

        API = api;
        TRIGGER_VOID_SWORD = trigger;
        GET_HEALTH = get;
        SET_HEALTH = set;
        ADD_HEALTH = add;

        // 启动期把"改血到底能用哪一半"打出来：只 set 不 get 的路径（虚空剑）照样能工作，而"读血量再减"的路径会静默退回原版 hurt。
        if (api != null) {
            if (get != null && set != null) {
                ModUtils.LOGGER.info("[BCCoreCompat] 改血 API 就绪：读={} 写={} 增减={}",
                        get.getName(), set.getName(), add == null ? "无" : add.getName());
            } else {
                ModUtils.LOGGER.warn("[BCCoreCompat] vitalprobe 的改血 API 不完整（读={} 写={}），"
                                + "改血会退回原版 hurt（吃护甲、吃无敌帧）。",
                        get == null ? "缺失" : get.getName(),
                        set == null ? "缺失" : set.getName());
            }
        }
    }

    /** 按"候选方法名 + 精确签名"找方法；签名对不上再退一步只按"名字 + 形参个数"找（形参类型变 float→double 也不会直接失效）。 */
    private static Method find(Class<?> api, String[] names, Class<?>... params)
            throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return api.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (String name : names) {
            for (Method m : api.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params.length) {
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(java.util.Arrays.toString(names) + "/" + params.length);
    }

    private BCCoreCompat() {}

    public static boolean isAvailable() {
        return API != null && TRIGGER_VOID_SWORD != null;
    }

    public static boolean isHealthApiAvailable() {
        return API != null && GET_HEALTH != null && SET_HEALTH != null;
    }

    /** 让 VitalProbe 执行一次虚空剑范围改血。必须在服务端调用，否则返回 false。 */
    public static boolean triggerVoidSword(Player player, ItemStack stack) {
        if (!isAvailable() || player == null || stack == null) return false;
        try {
            Object result = TRIGGER_VOID_SWORD.invoke(null, player, stack);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 调用 VitalProbe.triggerVoidSword 失败，"
                    + "本次右键不改血：{}", exception.toString());
            return false;
        }
    }

    public static float getHealth(@Nullable LivingEntity entity) {
        if (!isHealthApiAvailable() || entity == null) return Float.NaN;
        try {
            Object result = GET_HEALTH.invoke(null, entity);
            // 返回类型可能是 float / double / int，统一按 Number 收；只认 Float 会让 double 实现静默变成 NaN。
            return result instanceof Number n ? n.floatValue() : Float.NaN;
        } catch (ReflectiveOperationException | LinkageError e) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 读取血量失败：{}", e.toString());
            return Float.NaN;
        }
    }

    /** 把有效血量<b>精确</b>置为 {@code value}，<b>不做 0 下限钳制</b>：技能要"保证杀死"就必须允许写 0 以下。 */
    public static boolean setHealth(@Nullable LivingEntity entity, float value) {
        if (!isHealthApiAvailable() || entity == null) return false;
        try {
            return Boolean.TRUE.equals(SET_HEALTH.invoke(null, entity, value));
        } catch (ReflectiveOperationException | LinkageError e) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 写入血量失败：{}", e.toString());
            return false;
        }
    }

    public static boolean addHealth(@Nullable LivingEntity entity, float delta) {
        if (API == null || ADD_HEALTH == null || entity == null) return false;
        try {
            return Boolean.TRUE.equals(ADD_HEALTH.invoke(null, entity, delta));
        } catch (ReflectiveOperationException | LinkageError e) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 增减血量失败：{}", e.toString());
            return false;
        }
    }

    /**
     * <b>技能伤害统一入口</b>：优先走 VitalProbe 改血（{@code get} → {@code -amount} 允许 ≤0 → {@code set}，
     * 绕开无敌帧 / 护甲 / 抗性 / 属性上限，新血量 {@code <= 0} 时补一次终结击），VitalProbe 不可用时回退原版 {@code hurt}。
     * <p>必须在<b>服务端线程</b>调用；{@code source} 仅用于回退路径与终结击，可为 null（退化为 magic）。</p>
     */
    public static boolean damage(@Nullable LivingEntity target, float amount, @Nullable DamageSource source) {
        if (target == null || amount <= 0f || !target.isAlive()) return false;

        // 创造 / 旁观豁免：改血直接写血量存储，绕过了原版 Player.hurt 的 isInvulnerableTo 创造免疫 ——
        // 装了 VitalProbe 之后"创造玩家测试 boss"会被自己的技能打死，必须在这里自己挡一道。
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        if (isHealthApiAvailable()) {
            float before = getHealth(target);
            if (!Float.isNaN(before)) {
                float after = before - amount;          // 故意不钳制：允许 ≤0
                if (setHealth(target, after)) {
                    if (after <= 0f) {
                        finishOff(target, source);
                    }
                    return true;
                }
            }
        }

        return hurtFallback(target, amount, source);
    }

    /**
     * 无效化目标的"抗改血"姿态，让接下来的写血量一定落得下去：清 {@code invulnerableTime}（原版无敌帧，
     * 不清的话后续 {@code hurt} 会被 {@code LivingEntity.hurt} 的 {@code invulnerableTime > 10} 分支吃掉）
     * 与 {@code hurtTime}（受击红闪）。
     * <p>刻意不碰 {@code setInvulnerable(true)}：那是实体级永久无敌（凋灵出场、世界边界），属于"本来就该免疫"的语义。
     * 必须在服务端调用。</p>
     */
    public static void clearHurtGuard(@Nullable LivingEntity target) {
        if (target == null) return;
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 清除无敌帧失败：{}", t.toString());
        }
    }

    /**
     * 把有效血量<b>精确</b>置为 {@code -1}（虚空剑「蹲下左键」的斩杀值）：写 0 附近可能留下 {@code 1.0E-7}
     * 这种"看着是 0、其实大于 0"的残值，写负数后 {@code <= 0} 是确定的，调用方补一次终结击就能收掉。
     * <p><b>不做创造豁免</b>：创造/旁观判定交给调用方（见 {@code VoidSwordGuard}），这里保证"说要改就一定改"。</p>
     */
    public static boolean setHealthToMinusOne(@Nullable LivingEntity target) {
        return setHealth(target, -1.0f);
    }

    /** 按最大生命值百分比改血。百分比取自<b>原版</b> {@link LivingEntity#getMaxHealth()}（改血引擎只改血量，最大生命值不参与改写）。 */
    public static boolean damagePercentOfMax(@Nullable LivingEntity target, float percent,
                                             @Nullable DamageSource source) {
        if (target == null || percent <= 0f) return false;
        float max = target.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0f) return false;
        return damage(target, max * (percent / 100f), source);
    }

    private static boolean hurtFallback(LivingEntity target, float amount, @Nullable DamageSource source) {
        try {
            DamageSource src = source != null ? source : target.damageSources().magic();
            return target.hurt(src, amount);
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 原版兜底伤害失败：{}", t.toString());
            return false;
        }
    }

    /** 终结击：血量已被写到 ≤0，再走一遍真实死亡流程（掉落物、死亡事件、Boss 血条才照常发生）：先 hurt(Float.MAX_VALUE)，不死再 kill()。 */
    private static void finishOff(LivingEntity target, @Nullable DamageSource source) {
        if (!target.isAlive()) return;
        try {
            DamageSource src = source != null ? source : target.damageSources().genericKill();
            target.hurt(src, Float.MAX_VALUE);
        } catch (Throwable ignored) {
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
            }
        }
    }
}
