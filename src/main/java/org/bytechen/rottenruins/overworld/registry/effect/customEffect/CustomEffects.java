package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.s2c.CustomEffectSyncPacket;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.CapabilityAttacher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 自实现效果的<b>对外门面</b> —— 名字与形状都照着原版 {@code LivingEntity} 上的那组方法，
 * 只是变成了静态工具（因为数据在 capability 里，不在实体字段上）。
 *
 * <h3>和原版用法对照</h3>
 * <pre>{@code
 * // 原版
 * entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
 * entity.removeEffect(MobEffects.SPEED);
 * if (entity.hasEffect(MobEffects.SPEED)) { ... }
 * entity.getEffect(MobEffects.SPEED);
 *
 * // 本系统（完全同构）
 * CustomEffects.addEffect(entity, new CustomEffectInstance(FROSTBITE, 200, 1));
 * CustomEffects.removeEffect(entity, FROSTBITE);
 * if (CustomEffects.hasEffect(entity, FROSTBITE)) { ... }
 * CustomEffects.getEffect(entity, FROSTBITE);
 * }</pre>
 *
 * <h3>三种入口的区别</h3>
 * <ul>
 *   <li>{@link #addEffect} —— 完整流程：{@code canApply} → 合并 → {@code onAdded} →
 *       属性修饰器 → 同步；</li>
 *   <li>{@link #putEffect} —— 只管塞进去，不触发任何回调（给"从 NBT/快照还原"用）；</li>
 *   <li>{@link #tick} —— 每 tick 的驱动，由 {@code ForgeEventHandler#onLivingTick} 统一调用。</li>
 * </ul>
 *
 * <p><b>服务端权威</b>：所有回调（{@code applyEffectTick} / {@code onAdded} / {@code onRemoved}）
 * 只在服务端触发。客户端拿到的是一份用于显示的副本，自己倒计时，不做任何逻辑。</p>
 */
public final class CustomEffects {

    /** 每多少 tick 兜底同步一次（给"剩余时长"这类会变但没触发 dirty 的东西）。 */
    private static final int RESYNC_INTERVAL = 40;

    private CustomEffects() {
    }

    // ══════════════════════════════════════════════════════════════
    // 取容器
    // ══════════════════════════════════════════════════════════════

    /** 取某个实体身上的效果容器；没挂载 capability 时返回 {@code null}。 */
    @Nullable
    public static CustomEffectHolder getHolder(@Nullable LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return CapabilityAttacher.getCapability(entity, CapabilityRegistry.CUSTOM_EFFECT_CAP, null);
    }

    // ══════════════════════════════════════════════════════════════
    // 施加 / 移除 / 查询（对应原版同名方法）
    // ══════════════════════════════════════════════════════════════

    /**
     * 施加一个效果。与原版 {@code LivingEntity#addEffect(MobEffectInstance)} 对应。
     *
     * @return {@code true} = 施加成功（新挂上或被刷新），{@code false} = 被 {@code canApply}
     *         拒绝 / 已存在同层同长 / 容器不存在
     */
    public static boolean addEffect(@NotNull LivingEntity entity,
                                    @NotNull CustomEffectInstance instance) {
        CustomEffectHolder holder = getHolder(entity);
        if (holder == null || !entity.isAlive() || entity.level().isClientSide()) {
            return false;
        }
        CustomEffectBase effect = instance.getEffect();
        if (!effect.canApply(entity)) {
            return false;
        }

        boolean fresh = holder.put(instance);
        CustomEffectInstance current = holder.get(effect);
        if (current == null) {
            return false;
        }

        // 新挂上、或被刷新到更强 → 重新走一遍"挂载"流程
        effect.onAdded(entity, current);
        effect.applyAttributeModifiers(entity, current.getAmplifier());

        syncToClients(entity, holder, true);
        return fresh;
    }

    /** 便捷重载：直接给效果本体 + 时长 + 层数（对应原版三参构造）。 */
    public static boolean addEffect(@NotNull LivingEntity entity, @NotNull CustomEffectBase effect,
                                    int duration, int amplifier) {
        return addEffect(entity, new CustomEffectInstance(effect, duration, amplifier));
    }

    /** 便捷重载：用效果自己的默认时长、0 层。 */
    public static boolean addEffect(@NotNull LivingEntity entity, @NotNull CustomEffectBase effect) {
        return addEffect(entity, new CustomEffectInstance(effect));
    }

    /** 移除一个效果（对应原版 {@code removeEffect}），会触发 {@code onRemoved} 与属性摘除。 */
    public static boolean removeEffect(@NotNull LivingEntity entity,
                                       @Nullable CustomEffectBase effect) {
        CustomEffectHolder holder = getHolder(entity);
        if (holder == null || effect == null) {
            return false;
        }
        CustomEffectInstance removed = holder.remove(effect);
        if (removed == null) {
            return false;
        }
        effect.removeAttributeModifiers(entity, removed.getAmplifier());
        effect.onRemoved(entity, removed);
        syncToClients(entity, holder, true);
        return true;
    }

    /** 清空全部（对应原版 {@code removeAllEffects}），逐个回调 {@code onRemoved}。 */
    public static boolean removeAllEffects(@NotNull LivingEntity entity) {
        CustomEffectHolder holder = getHolder(entity);
        if (holder == null || holder.isEmpty()) {
            return false;
        }
        List<CustomEffectInstance> removed = holder.clear();
        for (CustomEffectInstance instance : removed) {
            CustomEffectBase effect = instance.getEffect();
            effect.removeAttributeModifiers(entity, instance.getAmplifier());
            effect.onRemoved(entity, instance);
        }
        syncToClients(entity, holder, true);
        return true;
    }

    /** 有没有这个效果（对应原版 {@code hasEffect}）。 */
    public static boolean hasEffect(@Nullable LivingEntity entity,
                                    @Nullable CustomEffectBase effect) {
        CustomEffectHolder holder = getHolder(entity);
        return holder != null && holder.has(effect);
    }

    /** 取实例，没有则 {@code null}（对应原版 {@code getEffect}）。 */
    @Nullable
    public static CustomEffectInstance getEffect(@Nullable LivingEntity entity,
                                                 @Nullable CustomEffectBase effect) {
        CustomEffectHolder holder = getHolder(entity);
        return holder == null ? null : holder.get(effect);
    }

    /** 当前层数（0 = 没有这个效果）。 */
    public static int getAmplifier(@Nullable LivingEntity entity,
                                   @Nullable CustomEffectBase effect) {
        CustomEffectInstance instance = getEffect(entity, effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** 剩余时长（tick）；没有这个效果返回 0。 */
    public static int getDuration(@Nullable LivingEntity entity,
                                  @Nullable CustomEffectBase effect) {
        CustomEffectInstance instance = getEffect(entity, effect);
        return instance == null ? 0 : instance.getDuration();
    }

    /** 全部效果实例（只读）；没挂载时返回空集合。 */
    @NotNull
    public static Collection<CustomEffectInstance> getActiveEffects(@Nullable LivingEntity entity) {
        CustomEffectHolder holder = getHolder(entity);
        return holder == null ? List.of() : holder.all();
    }

    // ══════════════════════════════════════════════════════════════
    // 每 tick 驱动
    // ══════════════════════════════════════════════════════════════

    /**
     * 每 tick 驱动（双端都会调，但只有服务端会跑逻辑）。
     *
     * <p>服务端：{@code onEntityTick} → 倒计时 → 周期回调 → 到期移除 → 兜底同步。</p>
     * <p>客户端：只倒计时，让剩余秒数在本地看起来是连续变化的（原版也是这么做的）。</p>
     */
    public static void tick(@NotNull LivingEntity entity) {
        CustomEffectHolder holder = getHolder(entity);
        if (holder == null || holder.isEmpty()) {
            return;
        }

        if (entity.level().isClientSide()) {
            tickClient(holder);
            return;
        }

        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        // 死了就全清（对应原版"死亡时移除效果"）
        if (!entity.isAlive()) {
            removeAllEffects(entity);
            return;
        }

        boolean changed = false;
        for (CustomEffectInstance instance : List.copyOf(holder.all())) {
            CustomEffectBase effect = instance.getEffect();
            effect.onEntityTick(level, entity, instance);

            if (!instance.isInfinite() && effect.isDurationEffectTick(instance.getDuration(),
                    instance.getAmplifier())) {
                effect.applyEffectTick(level, entity, instance);
            }

            if (!instance.tickDown()) {
                effect.removeAttributeModifiers(entity, instance.getAmplifier());
                effect.onRemoved(entity, instance);
                holder.remove(effect);
                changed = true;
            }
        }

        if (changed || entity.tickCount % RESYNC_INTERVAL == 0) {
            syncToClients(entity, holder, changed);
        }
    }

    /** 客户端只倒计时，不做任何逻辑。 */
    private static void tickClient(@NotNull CustomEffectHolder holder) {
        for (CustomEffectInstance instance : List.copyOf(holder.all())) {
            if (!instance.tickDown()) {
                holder.remove(instance.getEffect());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 同步
    // ══════════════════════════════════════════════════════════════

    /**
     * 把当前快照发给追踪该实体的客户端。
     *
     * @param force {@code true} = 无视 dirty 直接发（施加 / 移除时用）
     */
    public static void syncToClients(@NotNull LivingEntity entity, @NotNull CustomEffectHolder holder,
                                     boolean force) {
        if (!force && !holder.isDirty()) {
            return;
        }
        holder.clearDirty();

        // 玩家自己也看不到自己身上有没有"不可见"效果 —— 全是不可见效果时直接不发，省带宽。
        // 但"容器空了"必须发：那份空快照是客户端清除残留（例如效果自然到期）的唯一途径，
        // 之前这里一视同仁地跳过，导致效果在服务端已经结束、客户端那份还在。
        if (!holder.isEmpty()
                && holder.all().stream().noneMatch(i -> i.getEffect().isVisible())) {
            return;
        }
        NetworkHelper.sendToClient(entity, new CustomEffectSyncPacket(entity.getId(),
                holder.serializeNBT()));
    }

    /** 强制同步一次（命令 / 调试用）。 */
    public static void syncNow(@NotNull LivingEntity entity) {
        CustomEffectHolder holder = getHolder(entity);
        if (holder != null) {
            syncToClients(entity, holder, true);
        }
    }

    /** 玩家与服务端校验用的便捷判断：这个实体的效果容器是否已挂上。 */
    public static boolean hasHolder(@Nullable LivingEntity entity) {
        return getHolder(entity) != null;
    }

    /** 只给调试/命令用：当前是否服务端（避免在客户端误用写入口）。 */
    public static boolean isServerSide(@NotNull LivingEntity entity) {
        return !entity.level().isClientSide() && !(entity instanceof Player player && player.isRemoved());
    }
}
