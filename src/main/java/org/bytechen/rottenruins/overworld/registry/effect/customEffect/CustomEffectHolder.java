package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.rottenruins.overworld.registry.capability.base.AbstractCapability;
import org.bytechen.rottenruins.overworld.registry.capability.base.IModCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个实体身上的<b>自定义效果容器</b>（capability 实现）。
 *
 * <p>对应原版 {@code LivingEntity.activeEffects} 那个 Map，但由我们自己做持久化：
 * 通过 {@code AttachCapabilitiesEvent} 挂到<b>所有</b> {@link LivingEntity} 上，
 * 并实现 {@link IModCapability} 让 Forge 把 {@link #serializeNBT()} 写进实体 NBT
 * —— 于是重进存档、换维度、区块卸载重载都不会丢。</p>
 *
 * <p>内部用 {@link LinkedHashMap} 按效果 id 存唯一条目：同一个实体身上同一种效果
 * 只会有一个实例（重复施加走 {@link CustomEffectInstance#upgrade}），这一点和原版一致。</p>
 *
 * <p>业务代码不要直接 new 它 —— 用 {@code CustomEffects} 那层门面，
 * 它会负责取 capability、触发回调与同步。</p>
 */
public class CustomEffectHolder extends AbstractCapability<CustomEffectHolder>
        implements IModCapability {

    /** 实体 NBT 里的键名。 */
    public static final String NBT_KEY = "RottenRuinsCustomEffects";

    private final Map<ResourceLocation, CustomEffectInstance> effects = new LinkedHashMap<>();

    /** 有变更待同步（服务端用；客户端收到包后清掉）。 */
    private boolean dirty;

    public CustomEffectHolder() {
    }

    // ══════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════

    /** 某个效果的实例；没有则 {@code null}（对应原版 {@code getEffect}）。 */
    @Nullable
    public CustomEffectInstance get(@Nullable CustomEffectBase effect) {
        return effect == null ? null : effects.get(effect.getId());
    }

    /** 有没有这个效果（对应原版 {@code hasEffect}）。 */
    public boolean has(@Nullable CustomEffectBase effect) {
        return get(effect) != null;
    }

    /** 全部效果实例（只读视图，按施加顺序）。 */
    @NotNull
    public Collection<CustomEffectInstance> all() {
        return Collections.unmodifiableCollection(effects.values());
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public int size() {
        return effects.size();
    }

    // ══════════════════════════════════════════════════════════════
    // 写（低层，不做回调 / 不同步 —— 那两层在 CustomEffects 里）
    // ══════════════════════════════════════════════════════════════

    /**
     * 放入或合并。
     *
     * @return {@code true} = 是"新挂上"（原本没有这个效果）
     */
    public boolean put(@NotNull CustomEffectInstance instance) {
        ResourceLocation id = instance.getEffect().getId();
        CustomEffectInstance existing = effects.get(id);
        if (existing == null) {
            effects.put(id, instance);
            dirty = true;
            return true;
        }
        if (existing.upgrade(instance)) {
            dirty = true;
        }
        return false;
    }

    /** 移除某个效果，返回被移除的实例（没有则 {@code null}）。 */
    @Nullable
    public CustomEffectInstance remove(@Nullable CustomEffectBase effect) {
        if (effect == null) {
            return null;
        }
        CustomEffectInstance removed = effects.remove(effect.getId());
        if (removed != null) {
            dirty = true;
        }
        return removed;
    }

    /** 清空（返回被清掉的那些，方便逐个回调 {@code onRemoved}）。 */
    @NotNull
    public List<CustomEffectInstance> clear() {
        if (effects.isEmpty()) {
            return List.of();
        }
        List<CustomEffectInstance> removed = new ArrayList<>(effects.values());
        effects.clear();
        dirty = true;
        return removed;
    }

    // ══════════════════════════════════════════════════════════════
    // 同步标记
    // ══════════════════════════════════════════════════════════════

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // ══════════════════════════════════════════════════════════════
    // NBT
    // ══════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (effects.isEmpty()) {
            return tag;
        }
        ListTag list = new ListTag();
        for (CustomEffectInstance instance : effects.values()) {
            list.add(instance.save());
        }
        tag.put(NBT_KEY, list);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag nbt) {
        effects.clear();
        if (nbt.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag list = nbt.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CustomEffectInstance instance = CustomEffectInstance.load(list.getCompound(i));
                // id 找不到（存档来自旧版本 / 那个效果已经被删）就静默丢弃，
                // 不能让一条坏数据把实体身上其它效果一起干掉
                if (instance != null) {
                    effects.put(instance.getEffect().getId(), instance);
                }
            }
        }
        dirty = true;
    }

    /**
     * 给"客户端只做显示、不存盘"的场合用：把服务端发来的快照整体覆盖进来。
     * 与 {@link #deserializeNBT} 的区别是它不改 dirty（客户端不需要再同步回去）。
     */
    public void readSnapshot(@NotNull CompoundTag nbt) {
        deserializeNBT(nbt);
        dirty = false;
    }
}
