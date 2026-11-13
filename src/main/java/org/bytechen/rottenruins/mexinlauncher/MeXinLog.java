package org.bytechen.rottenruins.mexinlauncher;

/**
 * MeXin 的日志。
 *
 * <p>转换发生在 <b>launch plugin 层</b>，那时模组的 log4j logger 还没就绪，
 * 所以这里直接用 {@code System.out} 并统一加前缀 {@code [MeXin]}（和 Forge 早期日志同风格）。</p>
 */
final class MeXinLog {

    private static final String PREFIX = "[MeXin] ";

    private MeXinLog() {
    }

    static void info(String message) {
        System.out.println(PREFIX + message);
    }

    static void warn(String message) {
        System.out.println(PREFIX + "WARN " + message);
    }

    static void error(String message, Throwable throwable) {
        System.out.println(PREFIX + "ERROR " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
    }
}
