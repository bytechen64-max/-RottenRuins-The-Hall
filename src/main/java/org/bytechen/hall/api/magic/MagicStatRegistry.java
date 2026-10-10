package org.bytechen.hall.api.magic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link MagicStatProvider} 的注册表。
 *
 * <p>注册进来的 provider 会被 {@link MagicStats} 在每次求值时按
 * {@link MagicStatProvider#priority()} 升序折叠。列表在写入时复制并排序，
 * 求值时只读，所以注册通常发生在 mod 初始化阶段、之后不再变动。</p>
 *
 * <p><b>线程约定</b>：注册请在 mod 构造 / {@code FMLCommonSetupEvent} 里做；
 * 求值发生在服务端 tick 与客户端渲染线程，读取的是一个已排序的不可变快照，
 * 因此并发读是安全的。运行期动态增删会重建快照。</p>
 */
public final class MagicStatRegistry {

    private static final List<MagicStatProvider> PROVIDERS = new ArrayList<>();

    /** 排序后的只读快照。求值路径只读它。 */
    private static volatile List<MagicStatProvider> snapshot = List.of();

    private MagicStatRegistry() {
    }

    /** 注册一个修饰来源。重复注册同一个实例会被忽略。 */
    public static synchronized void register(@NotNull MagicStatProvider provider) {
        if (PROVIDERS.contains(provider)) {
            return;
        }
        PROVIDERS.add(provider);
        rebuild();
    }

    /** 注销一个修饰来源。 */
    public static synchronized boolean unregister(@NotNull MagicStatProvider provider) {
        boolean removed = PROVIDERS.remove(provider);
        if (removed) {
            rebuild();
        }
        return removed;
    }

    /** 清空所有修饰来源（主要给测试用）。 */
    public static synchronized void clear() {
        PROVIDERS.clear();
        rebuild();
    }

    /** 当前已排序的 provider 快照，仅供 {@link MagicStats} 与调试使用。 */
    @NotNull
    public static List<MagicStatProvider> providers() {
        return snapshot;
    }

    /**
     * 按 priority 升序排序；priority 相同时保持注册顺序（{@link List#sort} 是稳定排序）。
     */
    private static void rebuild() {
        List<MagicStatProvider> copy = new ArrayList<>(PROVIDERS);
        copy.sort(Comparator.comparingInt(MagicStatProvider::priority));
        snapshot = List.copyOf(copy);
    }

    /**
     * 折叠算子：拿到「当前累计值」和「这一次要应用的 provider」，返回新的累计值。
     *
     * <p><b>为什么要把 current 传进来</b>：修饰器是链式的（先算装备、再算附魔、最后算 buff），
     * 每一步都必须看到上一步的结果。若算子只能拿到 provider，就没法把值串起来。</p>
     */
    @FunctionalInterface
    public interface IntOp {
        int apply(@NotNull MagicStatProvider provider, int current);
    }

    /** {@link IntOp} 的 double 版本。 */
    @FunctionalInterface
    public interface DoubleOp {
        double apply(@NotNull MagicStatProvider provider, double current);
    }

    /** {@link IntOp} 的 float 版本。 */
    @FunctionalInterface
    public interface FloatOp {
        float apply(@NotNull MagicStatProvider provider, float current);
    }

    /** 从 {@code base} 开始，按 priority 升序折叠 int。 */
    public static int foldInt(int base, @NotNull IntOp op) {
        int value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    /** 从 {@code base} 开始，按 priority 升序折叠 double。 */
    public static double foldDouble(double base, @NotNull DoubleOp op) {
        double value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    /** 从 {@code base} 开始，按 priority 升序折叠 float。 */
    public static float foldFloat(float base, @NotNull FloatOp op) {
        float value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    /**
     * 遍历所有 provider 求一次"是否允许"。任一返回 false 即拒绝。
     *
     * <p>给"以后可能出现的释放前置条件"留的口子：比如某个 debuff 禁止施法。</p>
     */
    @FunctionalInterface
    public interface Gate {
        boolean test(@NotNull MagicStatProvider provider);
    }

    /** 所有 provider 都放行才返回 true；没有注册任何 provider 时返回 true。 */
    public static boolean allAllow(@Nullable Gate gate) {
        if (gate == null) {
            return true;
        }
        for (MagicStatProvider provider : snapshot) {
            if (!gate.test(provider)) {
                return false;
            }
        }
        return true;
    }
}
