package org.bytechen.rottenruins.utils.health.runtime;

public enum AccessStrength {

    REFLECTION,
    VARHANDLE,
    UNSAFE;

    public static AccessStrength parse(String s, AccessStrength fallback) {
        if (s == null) return fallback;
        for (AccessStrength v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return fallback;
    }
}
