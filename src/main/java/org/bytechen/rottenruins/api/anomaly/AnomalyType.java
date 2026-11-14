package org.bytechen.rottenruins.api.anomaly;

/**
 * 异常类型枚举 —— 定义所有异常种类。
 * 新增异常类型只需添加枚举常量并在平台效果解析器中接入即可。
 */
public enum AnomalyType {
    HEAT("heat", 10),
    COLD("cold", 10),
    ACID("acid", 10);

    private final String id;
    private final int maxProgress;

    AnomalyType(String id, int maxProgress) {
        this.id = id;
        this.maxProgress = maxProgress;
    }

    public String getId() {
        return id;
    }

    /** 异常进度上限（满层触发阈值） */
    public int getMaxProgress() {
        return maxProgress;
    }

    /** 适应 Buff 时长（tick），默认 15s = 300 tick */
    public int getAdaptationDurationTicks() {
        return 300;
    }
}
