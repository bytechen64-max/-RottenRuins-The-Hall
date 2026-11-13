package org.bytechen.rottenruins.api.magic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link MagicStatProvider} 的注册表：写入时复制并按 {@link MagicStatProvider#priority()} 升序排序，求值时只读快照。
 * 线程约定：注册放在 mod 构造 / {@code FMLCommonSetupEvent}；求值发生在服务端 tick 与客户端渲染线程，
 * 读到的是已排序的不可变 volatile 快照，故并发读安全；运行期动态增删会重建快照。
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

    @NotNull
    public static List<MagicStatProvider> providers() {
        return snapshot;
    }

    /** 按 priority 升序排序；priority 相同时保持注册顺序（{@link List#sort} 是稳定排序）。 */
    private static void rebuild() {
        List<MagicStatProvider> copy = new ArrayList<>(PROVIDERS);
        copy.sort(Comparator.comparingInt(MagicStatProvider::priority));
        snapshot = List.copyOf(copy);
    }

    /** 折叠算子：current 是上一步的累计值，必须传进来 —— 修饰器链式累加（装备→附魔→buff），每步都要看上一步结果。 */
    @FunctionalInterface
    public interface IntOp {
        int apply(@NotNull MagicStatProvider provider, int current);
    }

    @FunctionalInterface
    public interface DoubleOp {
        double apply(@NotNull MagicStatProvider provider, double current);
    }

    @FunctionalInterface
    public interface FloatOp {
        float apply(@NotNull MagicStatProvider provider, float current);
    }

    public static int foldInt(int base, @NotNull IntOp op) {
        int value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    public static double foldDouble(double base, @NotNull DoubleOp op) {
        double value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    public static float foldFloat(float base, @NotNull FloatOp op) {
        float value = base;
        for (MagicStatProvider provider : snapshot) {
            value = op.apply(provider, value);
        }
        return value;
    }

    /** 释放前置条件开关：任一 provider 返回 false 即拒绝（如某个 debuff 禁止施法）。 */
    @FunctionalInterface
    public interface Gate {
        boolean test(@NotNull MagicStatProvider provider);
    }

    /** 所有 provider 都放行才返回 true；没注册任何 provider 或 gate 为 null 时也返回 true。 */
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
