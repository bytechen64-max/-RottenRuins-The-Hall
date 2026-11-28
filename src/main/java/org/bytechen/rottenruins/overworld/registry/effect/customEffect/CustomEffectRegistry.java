package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.utils.ModUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义效果的<b>注册表</b> —— 对应原版 {@code ForgeRegistries.MOB_EFFECTS}，
 * 但只是一个普通 Map，不占 Forge 注册表、不需要 datapack、不需要图标贴图。
 *
 * <p>为什么用 {@link LinkedHashMap}：注册顺序就是 HUD 里的显示顺序，
 * 保持"代码里写的顺序 = 界面上看到的顺序"，排查时也好读。</p>
 *
 * <p>用法（照原版 {@code DeferredRegister} 的习惯，但更简单）：</p>
 * <pre>{@code
 * public static final FrostbiteEffect FROSTBITE =
 *         CustomEffectRegistry.register(new FrostbiteEffect(id("frostbite")));
 *
 * // 之后：
 * CustomEffectBase effect = CustomEffectRegistry.get(id("frostbite"));
 * }</pre>
 *
 * <h3>冻结（{@link #freeze()}）</h3>
 * <p>正常运行期不允许再新增效果：注册表一旦冻结，{@link #register} 会抛异常。
 * 这是为了防"在事件回调里顺手 new 一个效果"这种会让 id 表无限膨胀的写法。
 * 客户端与服务端的注册表必须一致（都在 mod 构造期注册），冻结正是这条约定的强制手段。</p>
 */
public final class CustomEffectRegistry {

    private static final Map<ResourceLocation, CustomEffectBase> BY_ID = new LinkedHashMap<>();

    private static boolean frozen;

    private CustomEffectRegistry() {
    }

    /**
     * 注册一个效果。
     *
     * @return 传进来的那个实例，方便写成 {@code public static final X = register(new X())}
     * @throws IllegalStateException id 重复，或注册表已经冻结
     */
    @NotNull
    public static <T extends CustomEffectBase> T register(@NotNull T effect) {
        if (frozen) {
            throw new IllegalStateException("自定义效果注册表已冻结，不能再注册：" + effect.getId());
        }
        CustomEffectBase previous = BY_ID.putIfAbsent(effect.getId(), effect);
        if (previous != null) {
            throw new IllegalStateException("自定义效果 id 重复：" + effect.getId()
                    + "（已存在 " + previous.getClass().getSimpleName() + "）");
        }
        return effect;
    }

    /** 按 id 取效果；没注册过返回 {@code null}。 */
    @Nullable
    public static CustomEffectBase get(@Nullable ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 全部已注册效果（注册顺序，只读）。 */
    @NotNull
    public static Collection<CustomEffectBase> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int size() {
        return BY_ID.size();
    }

    /**
     * 冻结注册表。由 {@code RegisterCustomEffect#freeze} 在 mod 构造期末尾调用一次，
     * 之后任何 {@link #register} 都会失败。
     */
    public static void freeze() {
        if (frozen) {
            return;
        }
        frozen = true;
        ModUtils.LOGGER.info("[CustomEffect] 已注册 {} 个自定义效果（注册表已冻结）", BY_ID.size());
    }

    public static boolean isFrozen() {
        return frozen;
    }
}
