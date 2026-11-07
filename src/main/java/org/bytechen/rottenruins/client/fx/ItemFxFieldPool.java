package org.bytechen.rottenruins.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import org.bytechen.rottenruins.utils.ModUtils;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 離屏「场」的生命周期管理：效果声明 {@link ItemFxFieldSpec}，这里负责真的开出来、
 * 跟着主 RT 改尺寸、以及在不再被声明时回收。
 *
 * <p>为什么值得单独一层：今天的描边把遮罩与四张模糊图直接写成静态字段，各自
 * {@code if (target == null) new TextureTarget(...)}。加第二个效果时，这五张图的
 * 尺寸策略、清理时机、以及"谁负责清"会立刻打架。收到这里之后，规则只有一条：
 * <b>场的内容由声明它的效果负责，场的生死由池负责。</b></p>
 *
 * <p>S1 只接 {@link ItemFxFormat#RGBA8}（MC 的 {@code TextureTarget} 直接就够）。
 * 浮点场需要自建 FBO 包装，跟第一个真正需要跨帧累积的效果（火焰）一起做。</p>
 */
public final class ItemFxFieldPool {

    private static final Map<String, Entry> FIELDS = new LinkedHashMap<>();
    private static final Map<String, ItemFxFloatTarget> FLOAT_FIELDS = new LinkedHashMap<>();

    private ItemFxFieldPool() { }

    private static final class Entry {
        final ItemFxFieldSpec spec;
        @Nullable TextureTarget target;

        Entry(ItemFxFieldSpec spec) {
            this.spec = spec;
        }
    }

    private static String key(String effectId, String fieldName) {
        return effectId + "/" + fieldName;
    }

    /**
     * 取得（必要时创建 / 改尺寸）一张场。
     *
     * <p>过滤固定 LINEAR：剪影要被环形合成双线性采样出 0~1 的边缘，模糊图要能放大回全分辨率；
     * NEAREST 会把两者都变成马赛克。</p>
     */
    @Nullable
    public static TextureTarget get(String effectId, ItemFxFieldSpec spec, RenderTarget main) {
        if (main == null || main.width <= 0 || main.height <= 0) return null;
        if (spec.format() != ItemFxFormat.RGBA8) {
            // 浮点场请走 getFloat —— 两条路的返回类型不同（一个是 MC 的 TextureTarget，
            // 一个是自建 FBO 包装），混用会直接编译不过，这里只做一次提示。
            ModUtils.LOGGER.warn("[ItemFx] 场 {} 是 {}，应当用 getFloat 取；本次跳过",
                    key(effectId, spec.name()), spec.format());
            return null;
        }
        int wantW = spec.widthOf(main.width);
        int wantH = spec.heightOf(main.height);

        Entry entry = FIELDS.computeIfAbsent(key(effectId, spec.name()), k -> new Entry(spec));
        TextureTarget target = entry.target;
        if (target == null) {
            target = new TextureTarget(wantW, wantH, false, Minecraft.ON_OSX);
            entry.target = target;
        } else if (target.width != wantW || target.height != wantH) {
            target.resize(wantW, wantH, Minecraft.ON_OSX);
        }
        target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        return target;
    }

    /**
     * 取得（必要时创建 / 改尺寸）一张<b>浮点</b>场。
     *
     * <p>跨帧累积必须用它：8bit 会把低值截断，羽流的尾部还没飘出去就死了。</p>
     */
    @Nullable
    public static ItemFxFloatTarget getFloat(String effectId, ItemFxFieldSpec spec, RenderTarget main) {
        if (main == null || main.width <= 0 || main.height <= 0) return null;
        if (spec.format() == ItemFxFormat.RGBA8) {
            ModUtils.LOGGER.warn("[ItemFx] 场 {} 是 RGBA8，应当用 get 取；本次跳过",
                    key(effectId, spec.name()));
            return null;
        }
        int wantW = spec.widthOf(main.width);
        int wantH = spec.heightOf(main.height);
        ItemFxFloatTarget target = FLOAT_FIELDS.computeIfAbsent(key(effectId, spec.name()),
                k -> new ItemFxFloatTarget());
        target.ensure(wantW, wantH, spec.format());
        return target;
    }

    /** 便捷取法：按名字找已声明过的场（不重新声明）。找不到返回 null。 */
    @Nullable
    public static TextureTarget find(String effectId, String fieldName) {
        Entry entry = FIELDS.get(key(effectId, fieldName));
        return entry == null ? null : entry.target;
    }

    /** 诊断用：当前池里有哪些场、多大。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        FIELDS.forEach((k, e) -> sb.append(k)
                .append(e.target == null ? "(未创建)" : "=" + e.target.width + "x" + e.target.height)
                .append(' '));
        FLOAT_FIELDS.forEach((k, t) -> sb.append(k)
                .append(t.isCreated() ? "=" + t.width() + "x" + t.height() : "(未创建)")
                .append(' '));
        return sb.toString().trim();
    }

    /** 只在测试/切换世界时用：把池清空（GL 资源由 MC 自己回收）。 */
    public static void clear() {
        FIELDS.clear();
        FLOAT_FIELDS.forEach((k, t) -> t.release());
        FLOAT_FIELDS.clear();
    }
}
