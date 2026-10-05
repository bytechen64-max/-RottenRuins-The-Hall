package org.bytechen.hall.overworld.registry.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.bytechen.hall.api.magic.MagicStats;
import org.bytechen.hall.api.magic.ManaRestoreEvent;
import org.bytechen.hall.overworld.registry.CapabilityRegistry;
import org.bytechen.hall.overworld.registry.capability.interfaces.ByteNumberAbility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家的<b>法力池</b>（扭曲点）。
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>初始与上限：<b>1000</b>（{@link MagicStats#BASE_MAX_MANA}）。
 *       上限不是写死的 —— 每次读都走 {@link MagicStats#maxMana(Player)}，
 *       所以属性修饰器与附魔改了上限之后立刻生效。</li>
 *   <li>回复：每秒 <b>+1</b>（{@link MagicStats#BASE_REGEN_PER_SECOND}）。</li>
 *   <li>消耗：{@link #spend(int)} —— <b>不够就不扣、返回 false</b>，
 *       调用方（{@code MagicHandle}）据此取消这次释放。</li>
 *   <li>击杀回复：按被击杀生物的最大生命值折算（{@link MagicStats#killMana}），
 *       由死亡事件驱动，见 {@code MagicEventHandlers}。</li>
 *   <li><b>所有</b>回复都会抛 {@link ManaRestoreEvent}（可取消、可改数值）。</li>
 * </ul>
 *
 * <h3>为什么回复量走 float + 小数累加器</h3>
 * <p>自然回蓝"每秒 1 点"换算到每 tick 是 {@code 0.05}。如果在这里就取整，
 * 那 0.05 永远攒不成 1，"每秒 +1" 会彻底回不上蓝。<br>
 * 更重要的：{@link ManaRestoreEvent} 的监听器会把数值乘一个倍率
 * （例如属性 {@code hall:mana_restore} 给 1.5），
 * {@code 1 × 1.5 = 1.5} —— 如果每次取整，得到的是"每秒 2 或 1"，
 * 而不是精确的 1.5。<br>
 * 所以顺序是：<b>事件先作用在小数上 → 累加进 {@link #restoreBuffer} →
 * 攒够整点才真正进 {@link #mana}</b>。这样 1.5/秒 就是精确的 1.5/秒。</p>
 *
 * <h3>相对早期版本修掉的问题</h3>
 * <ol>
 *   <li>NBT 键原来叫 {@code "hell:twisted"} —— 命名空间 {@code hell} 在本模组里根本不存在
 *       （模组 id 是 {@code hall}）。现在用 {@code hall} 命名空间，并且<b>兼容读取旧键</b>。</li>
 *   <li>原来 {@code LazyOptional.of(() -> this)} 建了个自引用却没有 {@code invalidate()}，
 *       实体卸载后这条 optional 一直活着。现在由 {@link #invalidate()} 交还。</li>
 *   <li>原来那个 {@code @SubscribeEvent TickingHealMagic} 是个空方法体，
 *       而且所在类没有被注册成事件订阅者 —— 永远不可能被调用。回蓝改为由
 *       {@code MagicEventHandlers} 在玩家 tick 里驱动。</li>
 * </ol>
 *
 * <p>同时仍然实现 {@link ByteNumberAbility} 并响应旧的 {@code TWIST_CAP}，
 * 所以既有调用点不会失效。</p>
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
    /**
     * 服务端推过来的法力上限；{@code -1} 表示"还没收到过，用本地现算的"。
     * <p>只在客户端起作用：属性修饰器/附魔若引用了只有服务端才准的数据，
     * 客户端自己算出来的上限会和实际不符，法力条就会画错。</p>
     */
    private int syncedMaxMana = -1;

    private final LazyOptional<TwistedPoint> self = LazyOptional.of(() -> this);
    private boolean dirty;

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

    // ══════════════════════════════════════════════════════════════
    // 上限 / 回复速率（每次现算，所以修饰器改了立刻生效）
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    // 法力读写
    // ══════════════════════════════════════════════════════════════

    /** 当前法力。 */
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

    /**
     * <b>扣除法力</b>：不够就一点都不扣，返回 {@code false}。
     *
     * <p>语义刻意做成"全有或全无"，而不是"扣到 0 为止" ——
     * 因为调用方要靠返回值决定"取消这次释放"。扣一半再取消会让玩家白掉法力。</p>
     *
     * @param amount 要扣的量；{@code <= 0} 视为成功且不改变法力
     */
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

    /**
     * 增加法力（来源记为 {@link ManaRestoreEvent.Cause#OTHER}）。
     *
     * @return <b>实际</b>加进去的整数点（被事件削减、或法力已满时会更少）
     */
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

    // ══════════════════════════════════════════════════════════════
    // 每 tick 回蓝
    // ══════════════════════════════════════════════════════════════

    /**
     * 推进一 tick 的自然回蓝。由 {@code MagicEventHandlers} 在服务端玩家 tick 里调用。
     *
     * <p>换算成"每 tick {@code N/20} 点"，交给 {@link #applyRestore} 走事件与小数累加。</p>
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

    // ══════════════════════════════════════════════════════════════
    // 统一的回复入口（事件 → 小数累加 → 取整进法力）
    // ══════════════════════════════════════════════════════════════

    /**
     * 所有回复的唯一实现。
     *
     * <p>顺序：抛 {@link ManaRestoreEvent}（可取消 / 可改数值）→ 把最终小数累加进
     * {@link #restoreBuffer} → 攒够整点才写进 {@link #mana} → 抛 {@code Post}。</p>
     *
     * @return 实际加进法力的整数点
     */
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

    // ══════════════════════════════════════════════════════════════
    // ByteNumberAbility（保留以兼容既有调用点）
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    // 同步钩子
    // ══════════════════════════════════════════════════════════════

    /** 是否有未同步到客户端的改动（由 {@code MagicSync} 消费）。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 同步完成后清除脏标记。 */
    public void clearDirty() {
        dirty = false;
    }

    /**
     * 客户端应用服务端推来的状态。
     *
     * <p>刻意<b>不</b>标脏：这是"接收"而不是"修改"，标脏会让客户端以为自己有
     * 待同步的改动，语义就乱了。同时把小数缓存清零，
     * 免得服务端的整数值和本地的零点几叠加出跳变。</p>
     */
    public void applyServerState(int serverMana, int serverMaxMana) {
        this.syncedMaxMana = serverMaxMana > 0 ? serverMaxMana : -1;
        this.mana = clamp(serverMana);
        this.restoreBuffer = 0.0d;
        this.dirty = false;
    }

    // ══════════════════════════════════════════════════════════════
    // 能力提供者 / 序列化
    // ══════════════════════════════════════════════════════════════

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
