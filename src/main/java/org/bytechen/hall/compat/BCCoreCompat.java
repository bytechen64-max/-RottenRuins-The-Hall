package org.bytechen.hall.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.bytechen.hall.utils.ModUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * 与 VitalProbe 的<b>可选</b>对接。
 *
 * <p>VitalProbe 提供逆向改血引擎（能穿透各种"覆写 getHealth 返回自定义血量"的抗改血实现），
 * 虚空剑右键改血、以及本模组所有"使徒技能"的伤害就是它做的。但本模组<b>不</b>把 VitalProbe
 * 作为编译期/必需依赖：
 *
 * <ul>
 *   <li>先用 {@link ModList#isLoaded} 检查 vitalprobe 是否加载；</li>
 *   <li>再反射拿 {@code org.bytechen.bccore.api.BCHealth} 上的静态方法；</li>
 *   <li>整个链路包在 try/catch 里，VitalProbe 没装、版本不匹配、调用抛异常，
 *       都只是"退回原版伤害/不改血"，绝不会把技能逻辑带崩。</li>
 * </ul>
 *
 * <p>放到这里的实际好处：VitalProbe 从"必装"变成"装了更好"。玩家单独玩本模组时，
 * 虚空剑照常是那把剑（面板伤害、特效都在），使徒技能也照常打得死人，
 * 只是打不穿那些"改血免疫"的模组生物。
 *
 * <h3>为什么技能伤害要统一走 {@link #damage}</h3>
 * 直接用 {@code entity.setHealth(getHealth() - 5)} 削血量会有浮点误差：
 * 例如把血量削到 {@code 1.0E-7} 这种"看着是 0、其实大于 0"的残值，
 * 生物永远不死。所以这里的约定是：
 * <ol>
 *   <li>用"有效血量 - 伤害"算新血量，<b>不做 0 下限钳制</b>（允许算到 0 以下）；</li>
 *   <li>算出的新血量 {@code <= 0} 时，再补一次"终结"（{@code hurt(Float.MAX_VALUE)}
 *       + 兜底 {@code kill()}），保证生物一定死透，不留残血。</li>
 * </ol>
 */
public final class BCCoreCompat {

    /** VitalProbe 的 modid，用于 {@link ModList#isLoaded}。 */
    public static final String MOD_ID = "vitalprobe";

    /** 对外 API 的类名。只引用字符串，所以不需要把 VitalProbe 加进依赖。 */
    private static final String API_CLASS = "org.bytechen.bccore.api.BCHealth";

    private static final Class<?> API;

    private static final Method TRIGGER_VOID_SWORD;
    /** {@code BCHealth.get(LivingEntity) -> float} */
    private static final Method GET_HEALTH;
    /** {@code BCHealth.set(LivingEntity, float) -> boolean} —— 不钳制，允许 ≤0 */
    private static final Method SET_HEALTH;
    /** {@code BCHealth.add(LivingEntity, float) -> boolean} —— 结果不会小于 0 */
    private static final Method ADD_HEALTH;

    static {
        Class<?> api = null;
        Method trigger = null;
        Method get = null;
        Method set = null;
        Method add = null;

        if (ModList.get().isLoaded(MOD_ID)) {
            try {
                api = Class.forName(API_CLASS);
                // 优先按签名精确查找，避免有重载时选错
                trigger = find(api, "triggerVoidSword", Player.class, ItemStack.class);
                get = find(api, "get", LivingEntity.class);
                set = find(api, "set", LivingEntity.class, float.class);
                add = find(api, "add", LivingEntity.class, float.class);
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
    }

    /**
     * 先按精确签名找；找不到再退一步只按"方法名 + 形参个数"找
     * （比如以后形参类型从 Player 换成 LivingEntity 也不会直接失效）。
     */
    private static Method find(Class<?> api, String name, Class<?>... params)
            throws ReflectiveOperationException {
        try {
            return api.getMethod(name, params);
        } catch (NoSuchMethodException first) {
            for (Method m : api.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params.length) {
                    return m;
                }
            }
            throw first;
        }
    }

    private BCCoreCompat() {}

    /** VitalProbe 是否加载且虚空剑 API 可用（兼容旧调用点）。 */
    public static boolean isAvailable() {
        return API != null && TRIGGER_VOID_SWORD != null;
    }

    /** 改血引擎（读/写血量）是否可用。 */
    public static boolean isHealthApiAvailable() {
        return API != null && GET_HEALTH != null && SET_HEALTH != null;
    }

    // ══════════════════════════════════════════════════════════════
    // 虚空剑（原有能力，保持不变）
    // ══════════════════════════════════════════════════════════════

    /**
     * 让 VitalProbe 执行一次虚空剑范围改血。
     *
     * <p>必须在服务端调用；客户端调用会直接返回 false（VitalProbe 内部也会挡一道）。
     *
     * @return 本次是否真的执行了一次范围改血；没装 VitalProbe 或状态不满足时为 false
     */
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

    // ══════════════════════════════════════════════════════════════
    // 改血 API（使徒技能用）
    // ══════════════════════════════════════════════════════════════

    /**
     * 读取实体的"有效血量"（即 {@code getHealth()} 的返回值）。
     *
     * @return 血量；VitalProbe 不可用或读取失败时返回 {@link Float#NaN}
     */
    public static float getHealth(@Nullable LivingEntity entity) {
        if (!isHealthApiAvailable() || entity == null) return Float.NaN;
        try {
            Object result = GET_HEALTH.invoke(null, entity);
            return result instanceof Float f ? f : Float.NaN;
        } catch (ReflectiveOperationException | LinkageError e) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 读取血量失败：{}", e.toString());
            return Float.NaN;
        }
    }

    /**
     * 把有效血量<b>精确</b>置为 {@code value}。
     *
     * <p><b>不做 0 下限钳制</b>：传入负数就是写负数。技能要"保证杀死"就必须允许写 0 以下，
     * 否则浮点残值会让生物卡在 1e-7 这种血量上永远不死。
     *
     * @return 是否成功写入
     */
    public static boolean setHealth(@Nullable LivingEntity entity, float value) {
        if (!isHealthApiAvailable() || entity == null) return false;
        try {
            return Boolean.TRUE.equals(SET_HEALTH.invoke(null, entity, value));
        } catch (ReflectiveOperationException | LinkageError e) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 写入血量失败：{}", e.toString());
            return false;
        }
    }

    /**
     * 在有效血量上增减 {@code delta}。
     *
     * <p>注意 VitalProbe 的 {@code add} 语义会把结果钳在 0 以上，所以"要留残值还是写负数"
     * 这件事由调用方决定 —— 技能伤害请用 {@link #damage}，不要直接用这个。
     */
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
     * <b>技能伤害统一入口</b>：优先走 VitalProbe 改血，VitalProbe 不可用时回退原版 {@code hurt}。
     *
     * <p>改血路径：
     * <pre>
     *   before = BCHealth.get(target)
     *   after  = before - amount        // 允许 ≤0，不做钳制
     *   BCHealth.set(target, after)     // 绕过一切"抗改血"实现
     *   if (after &lt;= 0) 终结(target)   // hurt(Float.MAX_VALUE) + kill() 兜底
     * </pre>
     *
     * <p>这样做的收益：伤害不受无敌帧、护甲、抗性、属性上限影响（改血是直接写存储位置），
     * 且"血被削到 0 以下"时一定会死，不会因为浮点误差留下打不死的残血。
     *
     * <p>必须在<b>服务端线程</b>调用。
     *
     * @param target 受击者
     * @param amount 伤害量（正数）
     * @param source 伤害来源，仅用于回退路径和终结击；可为 null（退化为 magic）
     * @return 本次是否真的造成了伤害
     */
    public static boolean damage(@Nullable LivingEntity target, float amount, @Nullable DamageSource source) {
        if (target == null || amount <= 0f || !target.isAlive()) return false;

        // 创造 / 旁观豁免：改血是直接写血量存储，绕过了原版 Player.hurt 里那条
        // isInvulnerableTo 的创造免疫。换句话说，装了 VitalProbe 之后"创造玩家测试 boss"
        // 会被自己的技能打死 —— 必须在这里自己挡一道。
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
                // 写失败（目标是"改血免疫"实现）→ 落到下面的原版兜底
            }
        }

        return hurtFallback(target, amount, source);
    }

    // ══════════════════════════════════════════════════════════════
    // 虚空剑「蹲下左键」用：无效化 + 精确置 -1
    // ══════════════════════════════════════════════════════════════

    /**
     * 无效化目标的"抗改血"姿态，让接下来的写血量一定落得下去。
     *
     * <p>这是"无视取消 hurt / 无敌帧"里<b>解除无敌窗口</b>的那一半：</p>
     * <ul>
     *   <li>{@code invulnerableTime = 0} —— 原版无敌帧。不清的话，目标 20 tick 内
     *       挨过任何一下，后续 {@code hurt} 会被 {@code LivingEntity.hurt} 里的
     *       {@code invulnerableTime > 10} 分支直接吃掉（返回 false 且不掉血）；</li>
     *   <li>{@code hurtTime = 0} —— 受击红闪倒计时。留着会让这一刀看起来"没打中"。</li>
     * </ul>
     *
     * <p>刻意<b>不碰</b>两样东西：</p>
     * <ol>
     *   <li>{@code lastHurt}（上一刀伤害的缓存）—— 它是 {@code protected}，够不着；
     *       而且无敌帧已经清零，值永远不会再参与结算，改了也没有收益；</li>
     *   <li>{@code setInvulnerable(true)} —— 那是实体级的永久无敌（凋灵出场动画、
     *       世界边界里的实体），属于"这个实体本来就该免疫"的语义，
     *       不该被一把武器悄悄抹掉。</li>
     * </ol>
     *
     * <p>必须在服务端调用。</p>
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
     * 把有效血量<b>精确</b>置为 {@code -1}（虚空剑「蹲下左键」的斩杀值）。
     *
     * <p>为什么是负数而不是 0：某些模组的血量存储会做"±残值"处理，
     * 写到 0 附近容易留在 {@code 1.0E-7} 这种"看着是 0、其实大于 0"的值上，
     * 生物于是永远不死。写 {@code -1} 之后，"{@code <= 0}"是确定的，
     * 调用方补一次终结击就一定能收掉它。</p>
     *
     * <p>走的是 {@link #setHealth}（不钳制），因此这里与 VitalProbe 全局的
     * {@code /vitalprobe} 设置一致：VitalProbe 的引擎设置决定强度，本方法只决定值。</p>
     *
     * <p><b>不做创造豁免</b>：这是"强制选中"语义的能力，创造/旁观的判定交给调用方
     * （见 {@code VoidSwordGuard}），这里保证"说要改就一定改"。</p>
     *
     * <p>必须在服务端调用。</p>
     *
     * @return 是否成功写入 {@code -1}
     */
    public static boolean setHealthToMinusOne(@Nullable LivingEntity target) {
        return setHealth(target, -1.0f);
    }

    /**
     * 按"最大生命值百分比"改血。
     *
     * <p>百分比取自<b>原版</b> {@link LivingEntity#getMaxHealth()}：改血引擎只负责血量本身，
     * 最大生命值不参与改写，所以这里读原版值就够了。
     *
     * @param percent 百分比（例如 5.0 表示最大生命值的 5%）
     */
    public static boolean damagePercentOfMax(@Nullable LivingEntity target, float percent,
                                             @Nullable DamageSource source) {
        if (target == null || percent <= 0f) return false;
        float max = target.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0f) return false;
        return damage(target, max * (percent / 100f), source);
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    /** 原版伤害兜底（没装 VitalProbe，或改血失败时）。 */
    private static boolean hurtFallback(LivingEntity target, float amount, @Nullable DamageSource source) {
        try {
            DamageSource src = source != null ? source : target.damageSources().magic();
            return target.hurt(src, amount);
        } catch (Throwable t) {
            ModUtils.LOGGER.warn("[BCCoreCompat] 原版兜底伤害失败：{}", t.toString());
            return false;
        }
    }

    /**
     * 终结击：血量已经被写到 ≤0，这里再走一遍真实的死亡流程，
     * 保证掉落物、死亡事件、Boss 血条等原版行为照常发生。
     *
     * <p>这是 VitalProbe 自己 {@code VoidSwordHandler.finishOff} 的同款做法：
     * 先 {@code hurt(Float.MAX_VALUE)}，要是还不死再 {@code kill()}。
     */
    private static void finishOff(LivingEntity target, @Nullable DamageSource source) {
        if (!target.isAlive()) return;
        try {
            DamageSource src = source != null ? source : target.damageSources().genericKill();
            target.hurt(src, Float.MAX_VALUE);
        } catch (Throwable ignored) {
            // 继续尝试别的路
        }
        if (target.isAlive()) {
            try {
                target.kill();
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
    }
}
