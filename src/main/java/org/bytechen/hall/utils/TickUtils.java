package org.bytechen.hall.utils;

public class TickUtils
{
    /**
     * 周期计时器（包含 tickCount = 0 时刻）
     * 当 tickCount 是 time 的非负整数倍时返回 true。
     *
     * @param tickCount 当前已流逝的 tick 数（通常从 0 开始累加）
     * @param time      触发周期（必须大于 0），表示每隔 time 个 tick 触发一次
     * @return 若 time > 0 且 tickCount 能被 time 整除，则返回 true；否则返回 false
     */
    public static boolean tickCountTimer(int tickCount, int time)
    {
        return time > 0 && tickCount % time == 0;
    }

    /**
     * 周期计时器（不包含起始 tickCount = 0 时刻）
     * 当 tickCount 是 time 的正整数倍时返回 true，即第一次触发发生在第 time 个 tick。
     *
     * @param tickCount 当前已流逝的 tick 数（通常从 0 开始累加）
     * @param time      触发周期（必须大于 0），表示每隔 time 个 tick 触发一次
     * @return 若 time > 0 且 tickCount 能被 time 整除，且 tickCount 不等于 0，则返回 true；否则返回 false
     */
    public static boolean tickCountTimerWithoutStart(int tickCount, int time)
    {
        return time > 0 && tickCount % time == 0 && tickCount != 0;
    }
}
