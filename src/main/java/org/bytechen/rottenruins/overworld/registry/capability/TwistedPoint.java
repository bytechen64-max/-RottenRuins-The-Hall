package org.bytechen.rottenruins.overworld.registry.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.api.magic.ManaRestoreEvent;
import org.bytechen.rottenruins.overworld.registry.CapabilityRegistry;
import org.bytechen.rottenruins.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家的法力池（扭曲点）。上限每次现算走 {@link MagicStats#maxMana(Player)}，修饰器/附魔改了即刻生效；
 * {@link #spend(int)} 是全有或全无（不够就不扣），调用方据返回值取消释放。
 * 回复走 float + 小数累加器：每 tick 仅 0.05，先抛 {@link ManaRestoreEvent} 让倍率作用在小数上，
 * 攒够整点才写进 {@link #mana} —— 否则 ×1.5 会被逐次取整吃掉，"每秒 1.5"退化成 1 或 2。
 * 仍实现 {@link ByteNumberAbility} 并响应旧的 {@code TWIST_CAP}，既有调用点不会失效。
 */
public class TwistedPoint implements ByteNumberAbility, ICapabilitySerializable<CompoundTag> {

    /** NBT：当前法力。 */
    public static final String NBT_MANA = "HallMagicMana";
    /** NBT：小数法力的累加器（保精度用；键名沿用早期版本，免得旧存档丢这一位）。 */
    public static final String NBT_RESTORE_BUFFER = "HallMagicRegenBuffer";
    /** 旧版本的 NBT 键，只用于兼容读取。 */
    private static final String LEGACY_NBT_KEY = "hell:twisted";

    private final Player owner;
    private int mana;
    /** 任何回复都先累到这里，攒够 1 点才进 {@link #mana}。 */
    private double restoreBuffer;
    /** 服务端推来的上限；-1 = 还没收到。只在客户端用：本地现算可能与实际不符，法力条会画错。 */
    private int syncedMaxMana = -1;

    private final LazyOptional<TwistedPoint> self = LazyOptional.of(() -> this);
    private boolean dirty;

    /**
     * 建池：初始法力 = 当前上限。本构造在 {@code AttachCapabilitiesEvent} 里被调用（Entity 构造期间，
     * 属性表还没建出来，故此刻必无修饰器）；旧存档的真实法力随后由 {@link #deserializeNBT} 覆盖。
     */
    public TwistedPoint(@NotNull Player owner) {
        this.owner = owner;
        this.mana = MagicStats.maxMana(owner);
    }

    /** 指定初始法力（会被钳到 {@code [0, 上限]}）。 */
    public TwistedPoint(@NotNull Player owner, int initialMana) {
        this.owner = owner;
        this.mana = clamp(initialMana);
    }

    @NotNull
    public Player getOwner() {
        return owner;
    }

    /** 当前法力上限。客户端在收到过服务端数值之后以服务端为准。 */
    public int getMaxMana() {
        if (syncedMaxMana > 0 && owner.level().isClientSide()) {
            return syncedMaxMana;
        }
        return MagicStats.maxMana(owner);
    }

    /** 当前每秒回蓝（可能带小数）。 */
    public double getRegenPerSecond() {
        return MagicStats.manaRegenPerSecond(owner);
    }

    public int getMana() {
        return mana;
    }

    /** 直接设置法力（钳到 {@code [0, 上限]}）。<b>不</b>抛 {@link ManaRestoreEvent}。 */
    public void setMana(int value) {
        int clamped = clamp(value);
        if (clamped != mana) {
            mana = clamped;
            dirty = true;
        }
    }

    /** 扣除法力：不够就一点都不扣并返回 false（调用方据此取消释放，扣一半会白掉法力）。 */
    public boolean spend(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (mana < amount) {
            return false;
        }
        mana -= amount;
        dirty = true;
        return true;
    }

    /** 增加法力（来源 OTHER）；返回实际加进去的整数点。 */
    public int addMana(int amount) {
        return applyRestore(amount, ManaRestoreEvent.Cause.OTHER, null);
    }

    /** 增加法力并显式声明来源（例如法术回复用 {@code SPELL}）。 */
    public int addMana(int amount, @NotNull ManaRestoreEvent.Cause cause,
                       @Nullable LivingEntity victim) {
        return applyRestore(amount, cause, victim);
    }

    /** 按被击杀生物的最大生命值回蓝。返回实际回复量。 */
    public int addKillRefund(@NotNull LivingEntity victim) {
        float amount = MagicStats.killMana(owner, victim);
        if (amount <= 0f) {
            return 0;
        }
        return applyRestore(amount, ManaRestoreEvent.Cause.KILL, victim);
    }

    public boolean isFull() {
        return mana >= getMaxMana();
    }

    /** 法力百分比 {@code 0.0~1.0}，给 HUD 用。 */
    public float getFillRatio() {
        int max = getMaxMana();
        if (max <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, mana / (float) max));
    }

    /**
     * 推进一 tick 自然回蓝（由 {@code MagicEventHandlers} 在服务端玩家 tick 调用）：
     * 换算成每 tick {@code N/20} 点交给 {@link #applyRestore} 走事件与小数累加。
     */
    public void tickRegen() {
        if (isFull()) {
            // 满了就把小数缓存清掉，否则掉一点血会瞬间补一大口
            restoreBuffer = 0.0d;
            return;
        }
        double perTick = getRegenPerSecond() / 20.0d;
        if (perTick <= 0.0d) {
            return;
        }
        applyRestore(perTick, ManaRestoreEvent.Cause.REGEN, null);
    }

    /** 所有回复的唯一入口：事件（可取消 / 可改数值）→ 小数累加 → 攒够整点进 mana → 抛 Post。 */
    private int applyRestore(double amount, @NotNull ManaRestoreEvent.Cause cause,
                             @Nullable LivingEntity victim) {
        if (amount <= 0.0d) {
            return 0;
        }

        ManaRestoreEvent event = new ManaRestoreEvent(owner, cause, victim, (float) amount);
        if (MinecraftForge.EVENT_BUS.post(event) || event.isCanceled()) {
            return 0;
        }
        float requested = event.getAmount();
        if (requested <= 0f) {
            return 0;
        }

        // 关键：先累加小数，再取整。这样 ×1.5 之类的倍率不会被每次取整吃掉。
        restoreBuffer += requested;
        int whole = (int) Math.floor(restoreBuffer);
        if (whole <= 0) {
            return 0;
        }
        restoreBuffer -= whole;

        int before = mana;
        mana = clamp(mana + whole);
        int added = mana - before;
        if (added > 0) {
            dirty = true;
        }
        if (added < whole) {
            // 撞到上限了：把小数缓存清掉，否则"满蓝期间"会悄悄攒一大笔
            restoreBuffer = 0.0d;
        }

        if (added > 0) {
            MinecraftForge.EVENT_BUS.post(new ManaRestoreEvent.Post(owner, cause, requested, added));
        }
        return added;
    }

    // ByteNumberAbility（保留以兼容既有调用点）

    @Override
    public int getNumber() {
        return getMana();
    }

    @Override
    public void setNumber(int i) {
        setMana(i);
    }

    @Override
    public void plus(int i) {
        addMana(i);
    }

    @Override
    public void minus(int i) {
        setMana(mana - i);
    }

    /** 是否有未同步到客户端的改动（由 {@code MagicSync} 消费）。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 同步完成后清除脏标记。 */
    public void clearDirty() {
        dirty = false;
    }

    /** 客户端应用服务端状态：刻意不标脏（接收而非修改），并清零小数缓存避免叠加出跳变。 */
    public void applyServerState(int serverMana, int serverMaxMana) {
        this.syncedMaxMana = serverMaxMana > 0 ? serverMaxMana : -1;
        this.mana = clamp(serverMana);
        this.restoreBuffer = 0.0d;
        this.dirty = false;
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                             @Nullable Direction side) {
        // 同一份实现同时响应带类型的 MANA_CAP 与旧的 TWIST_CAP
        if (capability == CapabilityRegistry.MANA_CAP
                || capability == CapabilityRegistry.TWIST_CAP) {
            return self.cast();
        }
        return LazyOptional.empty();
    }

    /** 实体被移除时交还 optional。 */
    public void invalidate() {
        self.invalidate();
    }

    @Override
    @NotNull
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_MANA, mana);
        tag.putDouble(NBT_RESTORE_BUFFER, restoreBuffer);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag tag) {
        if (tag.contains(NBT_MANA)) {
            mana = clamp(tag.getInt(NBT_MANA));
        } else if (tag.contains(LEGACY_NBT_KEY)) {
            // 兼容旧存档（旧键是 "hell:twisted"）
            mana = clamp(tag.getInt(LEGACY_NBT_KEY));
        } else {
            mana = getMaxMana();
        }
        restoreBuffer = tag.getDouble(NBT_RESTORE_BUFFER);
        dirty = false;
    }

    /** 钳到 {@code [0, 上限]}。 */
    private int clamp(int value) {
        return Math.max(0, Math.min(getMaxMana(), value));
    }
}
