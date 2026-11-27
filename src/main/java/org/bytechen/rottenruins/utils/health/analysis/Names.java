package org.bytechen.rottenruins.utils.health.analysis;

public final class Names {

    public String getHealthName = "getHealth";
    public String getHealthDesc = "()F";

    public String setHealthName = "setHealth";
    public String setHealthDesc = "(F)V";

    public String getMaxHealthName = "getMaxHealth";
    public String getMaxHealthDesc = "()F";

    public String livingEntityOwner = "net/minecraft/world/entity/LivingEntity";

    public String entityDataOwner = "net/minecraft/network/syncher/SynchedEntityData";
    public String getEntityDataName = "getEntityData";
    public String getEntityDataDesc = "()Lnet/minecraft/network/syncher/SynchedEntityData;";
    public String entityDataGetName = "get";
    public String entityDataGetDesc = "(Lnet/minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;";
    public String entityDataSetName = "set";
    public String entityDataSetDesc = "(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V";

    public String mapOwner = "java/util/Map";
    public String mapGetName = "get";
    public String mapGetDesc = "(Ljava/lang/Object;)Ljava/lang/Object;";
    public String mapGetOrDefaultName = "getOrDefault";
    public String mapGetOrDefaultDesc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

    public boolean isGetHealth(Sym s) {
        return s != null && s.kind == Sym.K.CALL
                && getHealthName.equals(s.name) && getHealthDesc.equals(s.desc);
    }

    public boolean isEntityDataGet(Sym s) {
        return s != null && s.kind == Sym.K.CALL
                && entityDataOwner.equals(s.owner)
                && entityDataGetName.equals(s.name)
                && entityDataGetDesc.equals(s.desc);
    }

    public boolean isMapLookup(Sym s) {
        if (s == null || s.kind != Sym.K.CALL) return false;
        if (!entityDataSafeMapOwner(s.owner)) return false;
        if (mapGetName.equals(s.name) && mapGetDesc.equals(s.desc) && s.args.size() == 1) return true;
        return mapGetOrDefaultName.equals(s.name) && mapGetOrDefaultDesc.equals(s.desc) && s.args.size() == 2;
    }

    private static boolean entityDataSafeMapOwner(String owner) {
        return "java/util/Map".equals(owner)
                || "java/util/HashMap".equals(owner)
                || "java/util/LinkedHashMap".equals(owner)
                || "java/util/concurrent/ConcurrentHashMap".equals(owner)
                || "java/util/TreeMap".equals(owner)
                || "it/unimi/dsi/fastutil/objects/Object2FloatMap".equals(owner)
                || "it/unimi/dsi/fastutil/ints/Int2FloatMap".equals(owner);
    }

    public static String boxedTypeOf(String owner) {
        if (owner == null) return null;
        return switch (owner) {
            case "java/lang/Float" -> "F";
            case "java/lang/Double" -> "D";
            case "java/lang/Integer" -> "I";
            case "java/lang/Long" -> "J";
            case "java/lang/Short" -> "S";
            case "java/lang/Byte" -> "B";
            default -> null;
        };
    }
}
