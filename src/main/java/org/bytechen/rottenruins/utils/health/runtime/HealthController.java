package org.bytechen.rottenruins.utils.health.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bytechen.rottenruins.utils.health.analysis.HealthAnalyzer;
import org.bytechen.rottenruins.utils.health.analysis.Names;
import org.bytechen.rottenruins.utils.health.analysis.Sym;
import org.bytechen.rottenruins.utils.health.analysis.WritePlan;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HealthController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float EPSILON = 0.01f;
    private static final int MAX_PLANS_TRIED = 4;

    public static final HealthController INSTANCE = new HealthController();

    private volatile AccessStrength strength = AccessStrength.VARHANDLE;
    private volatile boolean enabled = true;
    private volatile boolean allowEscalation = true;
    private volatile boolean allowFallbacks = true;
    private volatile boolean allowMaxHealthFallback = true;
    private volatile boolean allowMirrorSync = true;
    private volatile boolean allowEncodedProbe = true;
    private volatile boolean allowMutator = true;
    private volatile boolean allowRecode = true;
    private volatile int stickyWriteAttempts = 1;

    private volatile int stickyHoldTicks = 1;
    private volatile boolean debug = false;

    private volatile Names names = new Names();
    private volatile ClassByteSource byteSource;
    private volatile PlanApplier applier = new PlanApplier(new Names());

    private final Map<Class<?>, Profile> profiles = new ConcurrentHashMap<>();

    private final Set<Class<?>> reported = ConcurrentHashMap.newKeySet();

    private final Map<LivingEntity, Watch> watches =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final class Watch {
        final RevertWatch.State state;

        Watch(float target, float revertValue, int attemptsLeft, int holdTicks) {
            this.state = new RevertWatch.State(target, revertValue, attemptsLeft, holdTicks);
        }
    }

    private HealthController() {}

    public synchronized void bootstrap() {
        Names resolved = NameRegistry.resolve();
        this.names = resolved;
        this.applier = new PlanApplier(resolved);
        this.byteSource = new ClassByteSource(HealthController.class.getClassLoader());
        LOGGER.info("[改血引擎] 改血引擎就绪（强度={}）", strength);
    }

    public void configure(AccessStrength strength, boolean enabled, boolean allowEscalation,
                          boolean allowFallbacks, boolean allowMaxHealthFallback,
                          boolean allowMirrorSync, boolean allowEncodedProbe, boolean allowMutator,
                          boolean allowRecode, int stickyWriteAttempts, int stickyHoldTicks, boolean debug) {
        this.strength = strength == null ? AccessStrength.VARHANDLE : strength;
        this.enabled = enabled;
        this.allowEscalation = allowEscalation;
        this.allowFallbacks = allowFallbacks;
        this.allowMaxHealthFallback = allowMaxHealthFallback;
        this.allowMirrorSync = allowMirrorSync;
        this.allowEncodedProbe = allowEncodedProbe;
        this.allowMutator = allowMutator;
        this.allowRecode = allowRecode;
        this.stickyWriteAttempts = Math.max(0, stickyWriteAttempts);
        this.stickyHoldTicks = Math.max(0, stickyHoldTicks);
        this.debug = debug;
    }

    public Names names() { return names; }

    public AccessStrength strength() { return strength; }

    public static final class Profile {
        public final Class<?> type;

        public final Class<?> analyzed;
        public final HealthAnalyzer.Result result;
        public final List<WritePlan> plans;
        public final String failure;

        public volatile String lastStrategy = "未使用";

        Profile(Class<?> type, Class<?> analyzed, HealthAnalyzer.Result result, String failure) {
            this.type = type;
            this.analyzed = analyzed == null ? type : analyzed;
            this.result = result;
            this.plans = result == null ? List.of() : result.plans;
            this.failure = failure;
        }

        public boolean viaSuperclass() { return analyzed != type; }

        public boolean resolvable() { return !plans.isEmpty(); }
    }

    public Profile profile(Class<?> type) {
        return profiles.computeIfAbsent(type, this::buildProfile);
    }

    private Profile buildProfile(Class<?> type) {
        ClassByteSource source = byteSource;
        if (source == null) return new Profile(type, type, null, "引擎尚未初始化");

        BytecodeTarget.Choice choice = BytecodeTarget.choose(type, source);
        if (choice == null) {
            return new Profile(type, type, null,
                    "读不到 " + BytecodeTarget.internalNameOf(type) + ".class 的字节码"
                            + (BytecodeTarget.isHidden(type) ? "（这是隐藏类）" : "")
                            + "，父类链上也找不到可读的类");
        }

        String note = null;
        if (choice.viaSuperclass()) {
            note = "字节码回退到父类 " + choice.analyzed().getName()
                    + "（" + BytecodeTarget.reason(type) + "）";
            LOGGER.info("[改血引擎] {} 的字节码拿不到，已回退到父类 {} 继续分析（父类方案不一定命中本类的覆写，"
                    + "仍以写入后的回读校验为准）", type.getName(), choice.analyzed().getName());
        }
        return analyzeProfile(type, choice.analyzed(), choice.node(), source, note);
    }

    private Profile analyzeProfile(Class<?> type, Class<?> analyzed, org.objectweb.asm.tree.ClassNode node,
                                   ClassByteSource source, String note) {
        try {
            HealthAnalyzer.Result result = HealthAnalyzer.analyze(node, source, names);
            if (result == null) {
                return new Profile(type, analyzed, null, join(note, "该类的 getHealth() 无法解析（期望名字 "
                        + names.getHealthName + names.getHealthDesc + "）"));
            }
            String failure = result.plans.isEmpty() ? join(note, result.note) : note;
            return new Profile(type, analyzed, result, failure);
        } catch (Throwable e) {
            LOGGER.warn("[改血引擎] 分析 {} 时抛出异常（实际分析的类是 {}）", type.getName(), analyzed.getName(), e);
            return new Profile(type, analyzed, null, join(note, "分析异常: " + e));
        }
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + "；" + b;
    }

    public void clearCache() { profiles.clear(); }

    public record Outcome(boolean success, String strategy, float before, float after, float requested) {

        public Outcome(boolean success, String strategy, float before, float after) {
            this(success, strategy, before, after, Float.NaN);
        }

        public boolean partial() {
            return !success && !Float.isNaN(requested) && Math.abs(after - before) > 0.005f;
        }

        public String describe() {
            String head = success ? "成功" : partial() ? "部分成功" : "失败";
            String s = head + " [" + strategy + "] " + before + " → " + after;
            if (partial()) s += "（目标 " + requested + "，被目标模组自身的限制挡住）";
            return s;
        }
    }

    public float readHealth(LivingEntity entity) {
        try {
            return entity.getHealth();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    public float readMaxHealth(LivingEntity entity) {
        try {
            return entity.getMaxHealth();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    private float readHealthSettled(LivingEntity entity, float expected) {
        float v = readHealth(entity);
        for (int i = 0; i < 5; i++) {
            if (Float.isNaN(v) || Math.abs(v - expected) < EPSILON) return v;
            Thread.onSpinWait();
            float again = readHealth(entity);
            if (!Float.isNaN(again)) v = again;
        }
        return v;
    }

    public Outcome setHealth(LivingEntity entity, float target) {
        return setHealth(entity, target, true);
    }

    private Outcome setHealth(LivingEntity entity, float target, boolean sticky) {
        if (entity == null) return new Outcome(false, "无效目标", Float.NaN, Float.NaN);
        float before = readHealth(entity);
        if (Float.isNaN(before)) return new Outcome(false, "读取血量失败", Float.NaN, Float.NaN);
        if (Math.abs(before - target) < EPSILON) return new Outcome(true, "已是目标值", before, before);

        if (!enabled) {
            entity.setHealth(target);
            return new Outcome(true, "SET_HEALTH(引擎已关闭)", before, readHealth(entity));
        }

        Outcome outcome = writeOnce(entity, target, before);
        if (outcome.success() && sticky && stickyWriteAttempts > 0) {
            watches.put(entity, new Watch(target, before, stickyWriteAttempts, stickyHoldTicks));
        }
        return outcome;
    }

    private Outcome writeOnce(LivingEntity entity, float target, float before) {
        Profile profile = profile(entity.getClass());
        int planIndex = 0;
        for (WritePlan plan : profile.plans) {
            if (planIndex++ >= MAX_PLANS_TRIED) break;
            Object snapshot = applier.snapshot(plan, entity);
            AccessStrength s = strength;
            boolean applied = false;
            int mirrored = 0;
            while (s != null) {
                int result = allowMirrorSync
                        ? applier.applyWithMirror(plan, entity, target, s, before)
                        : (applier.apply(plan, entity, target, s) ? 0 : -1);
                if (result >= 0) { applied = true; mirrored = result; break; }
                if (plan.loc == WritePlan.Loc.MAP_ENTRY) {
                    LOGGER.warn("[改血引擎] Map 方案写入失败（强度 {}）: {}", s, applier.describeMapState(plan, entity));
                } else if (reported.add(entity.getClass())) {
                    LOGGER.warn("[改血引擎] 方案写入失败（强度 {}）: {}", s, applier.describePlanWrite(plan, entity, target, readHealth(entity)));
                }
                s = allowEscalation ? FieldAccess.escalate(s) : null;
            }
            if (applied) {
                float after = readHealthSettled(entity, target);
                if (Math.abs(after - target) < EPSILON) {
                    profile.lastStrategy = "ANALYZED#" + (planIndex - 1) + "/" + s + " " + plan.describe()
                            + (mirrored > 0 ? "  (+" + mirrored + " 张镜像表)" : "");
                    return new Outcome(true, profile.lastStrategy, before, after);
                }
                if (reported.add(entity.getClass())) {
                    LOGGER.warn("[改血引擎] 方案写入成功但回读不符（强度 {}）: {}",
                            s, applier.describePlanWrite(plan, entity, target, after));
                }
                applier.restore(plan, entity, snapshot);
            }
        }

        if (!allowFallbacks) {
            return new Outcome(false, "无可用的写入方案", before, readHealth(entity));
        }

        if (mapSweep(entity, target, before)) {
            profile.lastStrategy = "MAP_SWEEP";
            return new Outcome(true, profile.lastStrategy, before, readHealth(entity));
        }

        if (allowEncodedProbe) {
            String detail = encodedProbe(entity, target, before);
            if (detail != null) {
                profile.lastStrategy = "ENCODED_PROBE " + detail;
                return new Outcome(true, profile.lastStrategy, before, readHealth(entity));
            }
        }

        if (!additiveSetHealth.contains(entity.getClass())) {
            try {
                entity.setHealth(target);
            } catch (Throwable ignored) {
            }
            float after = readHealthSettled(entity, target);
            SetHealthSemantics.Verdict verdict = SetHealthSemantics.judge(before, target, after);
            if (verdict == SetHealthSemantics.Verdict.ADDITIVE) {
                additiveSetHealth.add(entity.getClass());
                LOGGER.warn("[改血引擎] {} {}", entity.getClass().getName(),
                        SetHealthSemantics.explain(verdict, before, target, after));
            }
            if (Math.abs(after - target) < EPSILON) {
                profile.lastStrategy = "SET_HEALTH";
                return new Outcome(true, profile.lastStrategy, before, after);
            }
        }

        if (allowRecode) {
            Outcome o = recode(entity, target, before);
            if (o != null) return o;
        }

        if (allowMutator) {
            MutatorDriver.Result m = mutator(entity, target, before);
            if (m != null) {
                profile.lastStrategy = "MUTATOR " + m.method() + " / " + m.mode();
                return new Outcome(m.success(), profile.lastStrategy, m.before(), m.after(), target);
            }
        }

        if (fieldScan(entity, target, before)) {
            profile.lastStrategy = "FIELD_SCAN";
            return new Outcome(true, profile.lastStrategy, before, readHealth(entity));
        }

        if (allowMaxHealthFallback && maxHealthFallback(entity, target, before)) {
            profile.lastStrategy = "MAX_HEALTH";
            return new Outcome(true, profile.lastStrategy, before, readHealth(entity));
        }

        profile.lastStrategy = "全部失败（getHealth 声明于 " + (profile.result == null ? "?" : profile.result.declaringClass) + "）";
        return new Outcome(false, "所有策略均未生效", before, readHealth(entity));
    }

    public void tickServer() {
        if (watches.isEmpty()) return;
        List<LivingEntity> expired = new ArrayList<>();
        List<LivingEntity> toRewrite = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        synchronized (watches) {
            for (Map.Entry<LivingEntity, Watch> entry : watches.entrySet()) {
                LivingEntity entity = entry.getKey();
                Watch w = entry.getValue();
                String who = entity == null ? "?" : entity.getName().getString();
                if (entity == null || entity.isRemoved()) { expired.add(entity); continue; }

                RevertWatch.Decision d = RevertWatch.step(w.state, readHealth(entity));
                switch (d.action()) {
                    case DONE -> {
                        expired.add(entity);
                        if (who != null && w.state.holdTicksLeft > 0 && debug) {
                            notices.add(who + " 的持续压制成功：目标 " + w.state.target + " 已稳住");
                        }
                    }
                    case STABLE_WAIT -> {  }
                    case REWRITE -> toRewrite.add(entity);
                    case HOLD_EXPIRED -> {
                        toRewrite.add(entity);
                        notices.add(who + " 的持续压制窗口已用尽（stickyHoldTicks），读数仍被对面顶回");
                    }
                    case GIVE_UP -> {
                        expired.add(entity);
                        notices.add(who + " 的血量被模组逻辑反复还原，已放弃（目标 " + w.state.target
                                + "）。如果这是「每 tick 强制回满」那类目标，可以把 general.stickyHoldTicks 调大");
                    }
                    case FORGET -> expired.add(entity);
                }
                if (d.note() != null) {
                    notices.add(who + " " + d.note() + " —— 判定为对面有每 tick 生效的不变量"
                            + "（典型是限伤/回满），已切换为持续压制模式");
                }
            }
            for (LivingEntity e : expired) watches.remove(e);
        }
        for (String n : notices) LOGGER.info("[改血引擎] {}", n);
        for (LivingEntity entity : toRewrite) {
            Watch w;
            synchronized (watches) { w = watches.get(entity); }
            if (w == null) continue;
            Outcome outcome = setHealth(entity, w.state.target, false);
            if (debug || !outcome.success()) {
                LOGGER.info("[改血引擎] 重写 {} → {}（{}）", entity.getName().getString(), w.state.target,
                        w.state.holdTicksLeft > 0
                                ? "持续压制中，剩 " + w.state.holdTicksLeft + " tick"
                                : "普通重写，剩 " + w.state.attemptsLeft + " 次");
            }
        }
    }

    private boolean mapSweep(LivingEntity entity, float target, float before) {
        Profile profile = profile(entity.getClass());
        if (profile.result == null) return false;
        ClassLoader loader = entity.getClass().getClassLoader();
        int written = applier.mapSweep(entity, profile.result.returns, profile.result.declaringClass,
                loader, before, target);
        if (written > 0 && Math.abs(readHealthSettled(entity, target) - target) < EPSILON) {
            if (debug) LOGGER.info("[改血引擎] MAP_SWEEP 写入 {} 张表", written);
            return true;
        }
        return false;
    }

    public Outcome addHealth(LivingEntity entity, float delta) {        float before = readHealth(entity);
        if (Float.isNaN(before)) return new Outcome(false, "读取血量失败", Float.NaN, Float.NaN);
        return setHealth(entity, Math.max(0f, before + delta));
    }

    private String encodedProbe(LivingEntity entity, float target, float before) {
        float live = readHealth(entity);
        float want = Math.abs(live - target) <= EPSILON ? before : target;

        for (ContainerHit hit : containerHits(entity)) {
            Object value = hit.value();
            if (value == null || hit.key() == null) continue;
            if (!EncodedValueWriter.supported(value)) continue;
            EncodedValueWriter.Codec codec = (hit.raw() instanceof Map<?, ?>)
                    ? EncodedValueWriter.codecForMap(value, hit.raw(), hit.key())
                    : (hit.raw() instanceof NodeContainer nc)
                    ? EncodedValueWriter.codecFor(value, nc, nc.findByKey(hit.key()))
                    : null;
            String tag = hit.display() + "[" + keyName(hit.key()) + "]";
            String done = runProbe(entity, codec, tag, want);
            if (done != null) return done;
        }
        return null;
    }

    private String runProbe(LivingEntity entity, EncodedValueWriter.Codec codec, String tag, float want) {
        if (codec == null) return null;
        String full = tag + " " + codec.desc;
        boolean ok = EncodedValueWriter.write(codec, () -> readHealth(entity), want,
                debug ? m -> LOGGER.info("[改血引擎] ENCODED_PROBE {}: {}", full, m) : null);
        if (!ok) return null;
        float after = readHealthSettled(entity, want);
        if (Math.abs(after - want) < EPSILON) return full + " → " + want;
        if (debug && reported.add(entity.getClass())) {
            LOGGER.warn("[改血引擎] ENCODED_PROBE {} 探测命中但回读不符: {} → {}", full, want, after);
        }
        return null;
    }

    private List<Object> keyCandidates(LivingEntity entity) {
        List<Object> keys = new ArrayList<>(5);
        keys.add(entity);
        try {
            UUID uuid = entity.getUUID();
            if (uuid != null) {
                keys.add(uuid);
                keys.add(uuid.toString());
            }
        } catch (Throwable ignored) {
        }
        try {
            keys.add(entity.getId());
        } catch (Throwable ignored) {
        }
        try {
            keys.add(entity.getStringUUID());
        } catch (Throwable ignored) {
        }
        return keys;
    }

    private static String keyName(Object key) {
        if (key instanceof LivingEntity e) return e.getName().getString();
        String s = String.valueOf(key);
        return s.length() > 12 ? s.substring(0, 12) + "…" : s;
    }

    private MutatorDriver.Result mutator(LivingEntity entity, float target, float before) {
        ClassByteSource source = byteSource;
        if (source == null) return null;
        ClassLoader loader = entity.getClass().getClassLoader();

        List<Object> holders = holderCandidates(entity);

        List<Class<?>> searchIn = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ContainerHit hit : containerHits(entity)) {
            if (seen.add(hit.owner().getName())) searchIn.add(hit.owner());
        }
        for (Class<?> c : MutatorFinder.referencedClasses(entity.getClass(), source, loader)) {
            if (seen.add(c.getName())) searchIn.add(c);
        }
        for (Object holder : holders) {
            for (Class<?> c : MutatorFinder.referencedClasses(holder.getClass(), source, loader)) {
                if (seen.add(c.getName())) searchIn.add(c);
            }
        }

        for (Class<?> owner : searchIn) {
            List<MutatorFinder.Candidate> candidates = MutatorFinder.find(owner, entity.getClass(), source);
            if (candidates.isEmpty()) continue;
            if (debug) {
                LOGGER.info("[改血引擎] 在 {} 里找到 {} 个候选写入原语: {}", owner.getSimpleName(),
                        candidates.size(), candidates);
            }
            MutatorDriver.Result r = MutatorDriver.drive(entity, holders, () -> readHealth(entity),
                    candidates, target,
                    debug ? m -> LOGGER.info("[改血引擎] MUTATOR {}", m) : null,
                    s -> LOGGER.info("[改血引擎] MUTATOR 放弃（{}）：{}", owner.getSimpleName(), s));
            if (r != null) {
                boolean wrongWay = r.mode() != null && r.mode().contains("方向错误");
                if (wrongWay) {
                    LOGGER.warn("[改血引擎] MUTATOR {} 把读数推到了反方向且无法还原：{} —— {}",
                            owner.getSimpleName(), r.describe(), r.after());
                    return r;
                }
                LOGGER.info("[改血引擎] MUTATOR {} —— {}", owner.getSimpleName(), r.describe());
                return r;
            }
        }
        return null;
    }

    private List<Object> holderCandidates(LivingEntity entity) {
        List<Object> out = new ArrayList<>();
        out.add(entity);
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    Class<?> t = f.getType();
                    if (t.isPrimitive() || t == String.class || t.isArray()) continue;
                    String tn = t.getName();
                    if (tn.startsWith("java.") || tn.startsWith("javax.") || tn.startsWith("net.minecraft.")
                            || tn.startsWith("net.minecraftforge.")) continue;
                    Object v;
                    try {
                        f.setAccessible(true);
                        v = f.get(entity);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (v != null) out.add(v);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private final Set<Class<?>> additiveSetHealth = ConcurrentHashMap.newKeySet();

    private Outcome recode(LivingEntity entity, float target, float before) {
        ClassByteSource source = byteSource;
        if (source == null) return null;

        for (ContainerHit hit : containerHits(entity)) {
            Class<?> storage = hit.owner();
            Recoder.Container container = Recoder.containerOf(hit.raw());
            if (container == null || hit.key() == null || hit.value() == null) continue;

            Recoder.Recipe recipe = Recoder.find(hit.value(), storage, source, before,
                    debug ? m -> LOGGER.info("[改血引擎] RECODE {} {}", hit.display(), m) : null);
            if (recipe == null) continue;
            Recoder.Result res = Recoder.write(container, hit.key(), recipe, target,
                    () -> readHealth(entity), debug ? m -> LOGGER.info("[改血引擎] RECODE {}", m) : null);
            if (res == null) continue;
            float after = readHealthSettled(entity, target);
            if (Math.abs(after - target) < EPSILON) {
                LOGGER.info("[改血引擎] RECODE {} —— {}", hit.display(), res.describe());
                return new Outcome(true, "RECODE " + res.recipe(), before, after);
            }
        }
        return null;
    }

    private static Object readStaticField(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) return null;
                f.setAccessible(true);
                return f.get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private record ContainerHit(Class<?> owner, String field, Object raw, Object key, Object value) {
        String display() { return owner.getSimpleName() + "." + field; }
    }

    private List<ContainerHit> containerHits(LivingEntity entity) {
        List<ContainerHit> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Profile profile = profile(entity.getClass());
        ClassLoader loader = entity.getClass().getClassLoader();
        List<Object> keys = keyCandidates(entity);

        for (WritePlan plan : profile.plans) {
            for (MutatorFinder.Store store : MutatorFinder.storesIn(plan)) {
                if (!seen.add(store.key())) continue;
                Class<?> storage = MemberResolver.load(store.owner(), loader);
                if (storage == null) continue;
                Object raw = readStaticField(storage, store.field());
                if (raw == null) continue;
                Object[] kv = firstValue(raw, keys);
                out.add(new ContainerHit(storage, store.field(), raw, kv[0], kv[1]));
            }
        }

        if (profile.result != null) {
            for (Class<?> owner : PlanApplier.ownerClasses(profile.result.returns,
                    profile.result.declaringClass, loader)) {
                for (NodeContainer nc : NodeContainer.scanStatic(owner)) {
                    String name = nc.name;
                    if (!seen.add(owner.getName() + "#" + name)) continue;
                    Object[] kv = firstValue(nc, keys);
                    out.add(new ContainerHit(owner, name, nc, kv[0], kv[1]));
                }
                for (PlanApplier.NamedMap nm : PlanApplier.candidateMaps(owner)) {
                    if (!seen.add(owner.getName() + "#" + nm.name())) continue;
                    Object[] kv = firstValue(nm.map(), keys);
                    out.add(new ContainerHit(owner, nm.name(), nm.map(), kv[0], kv[1]));
                }
            }
        }

        for (NodeContainer nc : NodeContainer.scanInstance(entity)) {
            if (!seen.add(entity.getClass().getName() + "#" + nc.name)) continue;
            Object[] kv = firstValue(nc, keys);
            out.add(new ContainerHit(entity.getClass(), nc.name, nc, kv[0], kv[1]));
        }
        for (InstanceContainer ic : instanceMaps(entity)) {
            if (!seen.add(entity.getClass().getName() + "#" + ic.name())) continue;
            Object[] kv = firstValue(ic.value(), keys);
            out.add(new ContainerHit(entity.getClass(), ic.name(), ic.value(), kv[0], kv[1]));
        }
        return out;
    }

    private record InstanceContainer(String name, Object value) {}

    private List<InstanceContainer> instanceMaps(LivingEntity entity) {
        List<InstanceContainer> out = new ArrayList<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    if (!Map.class.isAssignableFrom(f.getType())) continue;
                    java.lang.reflect.Field raw = f;
                    raw.setAccessible(true);
                    Object v;
                    try {
                        v = raw.get(entity);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (v != null) out.add(new InstanceContainer(c.getSimpleName() + "." + f.getName(), v));
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private Object[] firstValue(Object container, List<Object> keys) {
        for (Object key : keys) {
            Object v = containerValue(container, key);
            if (v != null) return new Object[]{key, v};
        }
        return new Object[]{null, null};
    }

    private Object containerValue(Object container, Object key) {
        if (container == null || key == null) return null;
        if (container instanceof Map<?, ?> m) {
            try {
                return m.get(key);
            } catch (Throwable t) {
                return null;
            }
        }
        if (container instanceof NodeContainer nc) {
            try {
                return nc.valueOf(nc.findByKey(key));
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public List<String> capability(LivingEntity entity) {
        List<String> out = new ArrayList<>();
        Class<?> actual = entity.getClass();
        Profile profile = profile(actual);
        float reading = readHealth(entity);

        out.add("实体: " + entity.getName().getString() + "   (" + actual.getName() + ")");
        out.add("有效血量 = " + reading + "   上限 = " + readMaxHealth(entity));
        if (BytecodeTarget.isHidden(actual)) {
            out.add("字节码: 隐藏类（defineHiddenClass），读不到 .class 资源");
        }
        if (profile.viaSuperclass()) {
            out.add("字节码回退: 已改用父类 " + profile.analyzed.getName());
        }
        out.add(profile.resolvable()
                ? "✔ ANALYZED: " + profile.plans.get(0).describe()
                : "· ANALYZED: 无精确方案（" + profile.failure + "）");

        List<ContainerHit> hits = containerHits(entity);
        if (hits.isEmpty()) {
            out.add("存储容器: 没找到（静态与实例字段里都没有形状匹配的容器）");
        } else {
            for (ContainerHit h : hits) {
                Object v = h.value();
                out.add("存储容器: " + h.display() + "  →  "
                        + (v == null ? "这张表里没有该实体" : "值=" + brief(v)));
            }
        }

        ClassByteSource source = byteSource;
        List<String> routes = new ArrayList<>();
        if (profile.resolvable()) routes.add("ANALYZED（精确方案）");
        for (ContainerHit h : hits) {
            if (h.value() == null || source == null) continue;
            if (h.value() instanceof Number) routes.add("MAP_SWEEP（值是数字）");
            if (EncodedValueWriter.supported(h.value())) routes.add("ENCODED_PROBE（值是" + brief(h.value()) + "）");
            List<MutatorFinder.Candidate> cands =
                    MutatorFinder.find(h.owner(), h.field(), actual, source);
            if (!cands.isEmpty()) {
                routes.add("MUTATOR（可借 " + cands.size() + " 个原语，首选 "
                        + cands.get(0).display() + "）");
            }
            Recoder.Recipe recipe = Recoder.find(h.value(), h.owner(), source, reading, null);
            if (recipe != null) routes.add("RECODE（配方 " + recipe.describe() + "，一次到位）");
        }

        if (source != null) {
            boolean setHealthDoubtful = additiveSetHealth.contains(actual);
            Set<String> scanned = new LinkedHashSet<>();
            for (ContainerHit h : hits) scanned.add(h.owner().getName());
            List<Class<?>> wide = new ArrayList<>(
                    MutatorFinder.referencedClasses(actual, source, actual.getClassLoader()));
            for (Object holder : holderCandidates(entity)) {
                wide.addAll(MutatorFinder.referencedClasses(holder.getClass(), source, actual.getClassLoader()));
            }
            for (Class<?> owner : wide) {
                if (!scanned.add(owner.getName())) continue;
                List<MutatorFinder.Candidate> cands = MutatorFinder.find(owner, actual, source);
                if (cands.isEmpty()) continue;
                routes.add("MUTATOR·宽松（" + owner.getSimpleName() + " 可借 " + cands.size()
                        + " 个原语，首选 " + cands.get(0).display() + "）");
            }
            if (setHealthDoubtful) {
                routes.add("SET_HEALTH 已排除（该类的 setHealth 是加血语义）");
            }
        }
        if (routes.isEmpty()) {
            out.add("预计: 改不动 —— 没找到容器 / 值形态不支持 / 凑不出配方与原语");
        } else {
            out.add("预计路线: " + String.join("  |  ", new LinkedHashSet<>(routes)));
        }
        return out;
    }

    private static String brief(Object v) {
        String s = String.valueOf(v);
        return s.length() > 48 ? s.substring(0, 48) + "…" : s;
    }

    private boolean fieldScan(LivingEntity entity, float target, float current) {
        float maxHealth = readMaxHealth(entity);
        List<Field> candidates = new ArrayList<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t != float.class && t != double.class && t != int.class && t != long.class) continue;
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Object v = null;
                try {
                    v = f.get(entity);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(v instanceof Number n)) continue;
                double d = n.doubleValue();
                boolean direct = Math.abs(d - current) < 0.05;
                boolean inverted = !Float.isNaN(maxHealth) && Math.abs(d - (maxHealth - current)) < 0.05;
                if (direct || inverted) candidates.add(f);
            }
        }
        if (candidates.isEmpty()) return false;

        for (Field f : candidates) {
            Object original;
            try {
                original = f.get(entity);
            } catch (Throwable t) {
                continue;
            }
            if (!(original instanceof Number n)) continue;
            double d = n.doubleValue();
            double[] attempts = Math.abs(d - current) < 0.05
                    ? new double[]{target, maxHealth - target}
                    : new double[]{maxHealth - target, target};
            for (double value : attempts) {
                if (!writeField(entity, f, value)) continue;
                float after = readHealth(entity);
                if (Math.abs(after - target) < EPSILON) return true;
                writeField(entity, f, d);
            }
        }
        return false;
    }

    private boolean writeField(LivingEntity entity, Field f, double value) {
        Class<?> type = f.getType();
        String desc = type == float.class ? "F" : type == double.class ? "D"
                : type == int.class ? "I" : "J";
        AccessStrength s = strength;
        while (s != null) {
            if (FieldAccess.write(f.getDeclaringClass(), f.getName(), desc, entity, value, s)) return true;
            s = allowEscalation ? FieldAccess.escalate(s) : null;
        }
        return false;
    }

    private boolean maxHealthFallback(LivingEntity entity, float target, float current) {
        float max = readMaxHealth(entity);
        if (Float.isNaN(max) || Math.abs(max - current) > 0.05f) return false;
        try {
            AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
            if (instance == null) return false;
            double before = instance.getBaseValue();
            double delta = current - target;
            instance.setBaseValue(Math.max(1.0, before - delta));
            float after = readHealth(entity);
            if (Math.abs(after - target) < EPSILON) return true;
            instance.setBaseValue(before);
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public void logSelfCheck() {
        try {
            Class<?> living = MemberResolver.load(names.livingEntityOwner, HealthController.class.getClassLoader());
            if (living == null) {
                LOGGER.warn("[改血引擎] 自检跳过：找不到 {}", names.livingEntityOwner);
                return;
            }
            Profile profile = profile(living);
            LOGGER.info("[改血引擎] 启动自检 —— {}", String.join("\n[改血引擎] ", describe(living).split("\n")));
            if (!profile.resolvable()) {
                LOGGER.warn("[改血引擎] 自检未能在 {} 上还原出写入方案（{}）", living.getName(), profile.failure);
            }
        } catch (Throwable t) {
            LOGGER.warn("[改血引擎] 启动自检异常", t);
        }
    }

    public List<String> probe(LivingEntity entity) {
        List<String> out = new ArrayList<>();
        out.add("实体: " + entity.getName().getString() + "   (" + entity.getClass().getName() + ")");
        out.add("有效血量 getHealth() = " + readHealth(entity)
                + "   上限 getMaxHealth() = " + readMaxHealth(entity));
        out.add("注入成员名: getHealth=" + names.getHealthName + names.getHealthDesc
                + "  setHealth=" + names.setHealthName + names.setHealthDesc);

        ClassByteSource source = byteSource;
        out.add("字节码源: " + (source == null ? "未初始化 ✗" : "就绪 ✔"));

        Class<?> actual = entity.getClass();
        String internal = BytecodeTarget.internalNameOf(actual);
        boolean bytesOk = source != null && source.find(internal) != null;
        if (bytesOk) {
            out.add("读取 " + internal + ".class : 成功 ✔");
        } else if (BytecodeTarget.isHidden(actual)) {
            out.add("读取 " + internal + ".class : 失败 ✗ —— 这是**隐藏类**"
                    + "（MethodHandles.Lookup.defineHiddenClass 定义，不注册进任何类加载器，没有 .class 资源）");
        } else {
            out.add("读取 " + internal + ".class : 失败 ✗（跨模组类加载有问题）");
        }

        Profile profile = profile(actual);
        if (profile.viaSuperclass()) {
            out.add("字节码回退: 本类是隐藏类，已改用父类 " + profile.analyzed.getName() + " 的字节码分析");
            out.add("   └ 注意：本类可能覆写了 getHealth()，父类方案不一定命中；");
            out.add("     写入后会回读校验，不符即判定失败并还原（这正是下面「试改」那一行在做的事）");
        }
        if (profile.result == null) {
            out.add("逆向: 失败 —— " + profile.failure);
        } else {
            for (String line : profile.result.describe().split("\n")) out.add(line);
        }
        out.add("最近一次策略: " + profile.lastStrategy);

        int idx = 0;
        for (WritePlan plan : profile.plans) {
            if (plan.loc != WritePlan.Loc.MAP_ENTRY) { idx++; continue; }
            ClassLoader loader = actual.getClassLoader();
            try {
                ValueEvaluator ev = new ValueEvaluator(entity);
                Object map = ev.eval(plan.mapExpr);
                Object key = ev.eval(plan.keyExpr);
                Class<?> owner = MemberResolver.load(plan.mapExpr.owner, loader);
                out.add("方案 #" + idx + " 的 Map: " + map + "  key=" + key
                        + "  当前值=" + (map instanceof Map<?, ?> mm ? mm.get(key) : "?"));
                if (owner != null) {
                    for (PlanApplier.NamedMap candidate : PlanApplier.candidateMaps(owner)) {
                        String value = candidate.map() instanceof Map<?, ?> cm ? String.valueOf(cm.get(key)) : "?";
                        out.add("   候选表 " + candidate.name() + " = " + candidate.map()
                                + "   该实体当前值 = " + value
                                + (candidate.map() == map ? "   ← 就是主表" : ""));
                    }
                }
            } catch (Throwable t) {
                out.add("方案 #" + idx + " 求值失败: " + t);
            }
            idx++;
        }

        float before = readHealth(entity);
        if (!Float.isNaN(before)) {
            Outcome outcome = writeOnce(entity, Math.max(0f, before - 1f), before);
            out.add("试改 " + before + " → " + Math.max(0f, before - 1f) + " : " + outcome.describe());
            writeOnce(entity, before, readHealth(entity));
            out.add("已还原为 " + readHealth(entity));
        }
        return out;
    }

    public String describe(Class<?> type) {
        Profile p = profile(type);
        StringBuilder sb = new StringBuilder();
        sb.append("实体类: ").append(type.getName()).append('\n');
        sb.append("  最近策略: ").append(p.lastStrategy).append('\n');
        if (p.result == null) {
            sb.append("  逆向结果: 无（").append(p.failure).append("）");
            return sb.toString();
        }
        sb.append(p.result.describe());
        return sb.toString();
    }
}
