package org.bytechen.rottenruins.utils.health.runtime;

public final class SetHealthSemantics {

    private static final float EPS = 0.005f;

    public enum Verdict {

        SET_VALUE,

        NO_EFFECT,

        ADDITIVE
    }

    private SetHealthSemantics() {}

    public static Verdict judge(float before, float target, float after) {
        if (Float.isNaN(before) || Float.isNaN(after)) return Verdict.NO_EFFECT;
        if (Math.abs(after - target) <= EPS) return Verdict.SET_VALUE;
        if (Math.abs(after - before) <= EPS) return Verdict.NO_EFFECT;
        return Math.abs(after - target) < Math.abs(before - target)
                ? Verdict.NO_EFFECT
                : Verdict.ADDITIVE;
    }

    public static String explain(Verdict v, float before, float target, float after) {
        return switch (v) {
            case SET_VALUE -> "setHealth 是标准「设值」语义";
            case NO_EFFECT -> "setHealth 调用后读数 " + before + " → " + after + "（没到 " + target + "）";
            case ADDITIVE -> "这个类的 setHealth 不是「设值」语义：请求 " + target
                    + "，读数却从 " + before + " 变成 " + after + "（往反方向走了）——已记下，不再使用 SET_HEALTH";
        };
    }
}
