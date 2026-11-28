package org.bytechen.rottenruins.utils.health.analysis;

public final class WritePlan {

    public enum Loc { FIELD, STATIC_FIELD, MAP_ENTRY, ENTITY_DATA }

    public final Loc loc;

    public final String owner;
    public final String name;
    public final String desc;

    public final Sym receiver;

    public final Sym mapExpr;
    public final Sym keyExpr;

    public final String boxedDesc;

    public final Sym dataExpr;
    public final Sym accessorExpr;

    public final OutExpr value;

    public final String origin;

    private WritePlan(Loc loc, String owner, String name, String desc, Sym receiver,
                      Sym mapExpr, Sym keyExpr, String boxedDesc,
                      Sym dataExpr, Sym accessorExpr, OutExpr value, String origin) {
        this.loc = loc;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.receiver = receiver;
        this.mapExpr = mapExpr;
        this.keyExpr = keyExpr;
        this.boxedDesc = boxedDesc;
        this.dataExpr = dataExpr;
        this.accessorExpr = accessorExpr;
        this.value = value;
        this.origin = origin;
    }

    public static WritePlan field(String owner, String name, String desc, Sym receiver, OutExpr value, String origin) {
        return new WritePlan(Loc.FIELD, owner, name, desc, receiver, null, null, null, null, null, value, origin);
    }

    public static WritePlan staticField(String owner, String name, String desc, OutExpr value, String origin) {
        return new WritePlan(Loc.STATIC_FIELD, owner, name, desc, null, null, null, null, null, null, value, origin);
    }

    public static WritePlan mapEntry(Sym mapExpr, Sym keyExpr, String boxedDesc, OutExpr value, String origin) {
        return new WritePlan(Loc.MAP_ENTRY, null, null, null, null, mapExpr, keyExpr, boxedDesc, null, null, value, origin);
    }

    public static WritePlan entityData(Sym dataExpr, Sym accessorExpr, String boxedDesc, OutExpr value, String origin) {
        return new WritePlan(Loc.ENTITY_DATA, null, null, null, null, null, null, boxedDesc, dataExpr, accessorExpr, value, origin);
    }

    public String storageDesc() {
        return switch (loc) {
            case FIELD, STATIC_FIELD -> desc;
            case MAP_ENTRY, ENTITY_DATA -> boxedDesc;
        };
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        switch (loc) {
            case FIELD -> sb.append("字段 ").append(owner.replace('/', '.')).append('.').append(name)
                    .append(" : ").append(desc).append("  ← ").append(value)
                    .append("   [接收者: ").append(receiver).append(']');
            case STATIC_FIELD -> sb.append("静态字段 ").append(owner.replace('/', '.')).append('.').append(name)
                    .append(" : ").append(desc).append("  ← ").append(value);
            case MAP_ENTRY -> sb.append("Map 条目 ").append(mapExpr).append('[').append(keyExpr).append("]")
                    .append(" : ").append(boxedDesc).append("  ← ").append(value);
            case ENTITY_DATA -> sb.append("SynchedEntityData ").append(dataExpr).append(".set(")
                    .append(accessorExpr).append(", ").append(value).append(')');
        }
        if (origin != null) sb.append("   (").append(origin).append(')');
        return sb.toString();
    }

    @Override
    public String toString() { return describe(); }
}
