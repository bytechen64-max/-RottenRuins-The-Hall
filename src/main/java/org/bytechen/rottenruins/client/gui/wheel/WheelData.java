package org.bytechen.rottenruins.client.gui.wheel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.capability.TwistedPoint;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.StaffBase;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 轮盘 / 装载界面的<b>客户端数据视图</b>：把 {@link PlayerMagicPool} 的轮盘映射、
 * 法术堆、冷却和法力整理成 GUI 好用的形式。
 *
 * <p><b>只读</b>：这里不修改魔法池，所有写操作走 {@code WheelActionPacket} 由服务端裁决。
 * 客户端读到的仍是同步镜像，所以界面显示可能比服务端晚 1~5 tick（打开界面时会先
 * 请求一次全量同步，见 {@code MagicClientHandler}）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WheelData {

    /** 每个轮盘页的方位数（固定 8 个）。 */
    public static final int SLOTS_PER_PAGE = 8;
    /** 轮盘页数（I / II / III）。P0 只实装第 I 页的数据，其它页保留结构。 */
    public static final int PAGES = 3;

    /** 一个方位的完整视图；空位时 {@code stack} 为空堆、{@code poolSlot == -1}。 */
    public record Slot(int wheelIndex, int poolSlot, @NotNull ItemStack stack,
                       @Nullable IMagicBaseItem spell, @Nullable MagicType type,
                       boolean active, @NotNull String name, @NotNull String school,
                       @NotNull String using, int manaCost,
                       int cooldownTicks, float cooldownSeconds, float cooldownFrac,
                       float[] color) {

        public boolean occupied() {
            return poolSlot >= 0 && !stack.isEmpty() && spell != null;
        }

        public boolean passive() {
            return spell != null && spell.isPassive();
        }
    }

    private static final float[] EMPTY_COLOR = {0.30F, 0.38F, 0.56F};

    private WheelData() {
    }

    // ══════════════════════════════════════════════════════════════
    //  能力 / 玩家
    // ══════════════════════════════════════════════════════════════

    @Nullable
    public static PlayerMagicPool pool() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : MagicHandle.getPool(player);
    }

    @Nullable
    public static TwistedPoint manaCap() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : MagicHandle.getMana(player);
    }

    public static int mana() {
        TwistedPoint mana = manaCap();
        return mana == null ? 0 : mana.getMana();
    }

    public static int maxMana() {
        TwistedPoint mana = manaCap();
        return mana == null ? 1 : Math.max(1, mana.getMaxMana());
    }

    public static float manaFrac() {
        return Math.min(1.0F, mana() / (float) Math.max(1, maxMana()));
    }

    /** 玩家主手是否拿着法杖（轮盘施法的前置条件，P0 与右键一致只看主手）。 */
    public static boolean holdingStaff() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().getItem() instanceof StaffBase;
    }

    // ══════════════════════════════════════════════════════════════
    //  轮盘页 / 槽位
    // ══════════════════════════════════════════════════════════════

    /** 读取真实轮盘配置的某一页（全部 8 个方位）。 */
    public static Slot @NotNull [] page(int page) {
        PlayerMagicPool pool = pool();
        int[] mapping = new int[SLOTS_PER_PAGE];
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            mapping[i] = pool == null ? -1 : pool.getWheelSlot(page, i);
        }
        return fromMapping(mapping);
    }

    /** 把"方位 → 魔法池槽位"的映射转成视图（装载界面拖拽预览用草稿映射）。 */
    public static Slot @NotNull [] fromMapping(int[] mapping) {
        PlayerMagicPool pool = pool();
        Slot[] out = new Slot[SLOTS_PER_PAGE];
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int poolSlot = mapping != null && i < mapping.length ? mapping[i] : -1;
            ItemStack stack = pool != null && pool.isValidSlot(poolSlot)
                    ? pool.getStackInSlot(poolSlot) : ItemStack.EMPTY;
            out[i] = slot(i, poolSlot, stack, pool);
        }
        return out;
    }

    /** 单个方位视图（池槽位可以非法/为空）。 */
    @NotNull
    public static Slot slot(int wheelIndex, int poolSlot, @NotNull ItemStack stack,
                            @Nullable PlayerMagicPool pool) {
        IMagicBaseItem spell = stack.isEmpty() ? null : MagicStats.spellOf(stack);
        if (spell == null) {
            return new Slot(wheelIndex, -1, ItemStack.EMPTY, null, null, false,
                    "", "", "", 0, 0, 0.0F, 0.0F, EMPTY_COLOR);
        }
        MagicType type = spell.getMagicType();
        int magicId = 0;
        int cooldownTicks = MagicStats.cooldownTicks(minecraftPlayer(), stack);
        int remaining = 0;
        PlayerMagicPool effectivePool = pool != null ? pool : pool();
        if (effectivePool != null) {
            var id = MagicStats.magicId(stack);
            remaining = effectivePool.getCooldownRemaining(id);
        }
        float cooldownSeconds = remaining / 20.0F;
        float frac = cooldownTicks <= 0 ? 0.0F
                : Math.min(1.0F, remaining / (float) Math.max(1, cooldownTicks));
        boolean active = !spell.isPassive();
        return new Slot(wheelIndex, poolSlot, stack, spell, type, active,
                stack.getHoverName().getString(),
                schoolLabel(type),
                usingLabel(spell.getUsingType()),
                MagicStats.manaCost(minecraftPlayer(), stack),
                cooldownTicks, cooldownSeconds, frac, colorOf(type));
    }

    /** 魔法池里所有主动法术（装载界面左侧图鉴）。 */
    public static Slot @NotNull [] activeSpellsFromPool() {
        PlayerMagicPool pool = pool();
        if (pool == null) {
            return new Slot[0];
        }
        java.util.List<Slot> list = new java.util.ArrayList<>();
        int count = pool.getSlots();
        for (int i = 0; i < count; i++) {
            ItemStack stack = pool.getStackInSlot(i);
            if (stack.isEmpty() || !MagicStats.isSpell(stack)) {
                continue;
            }
            Slot slot = slot(-1, i, stack, pool);
            if (slot.active()) {
                list.add(slot);
            }
        }
        return list.toArray(new Slot[0]);
    }

    /** 轮盘上是否至少绑定了一个主动法术（决定 R 键能不能打开）。 */
    public static boolean hasAnyWheelSpell(int page) {
        PlayerMagicPool pool = pool();
        if (pool == null) {
            return false;
        }
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int poolSlot = pool.getWheelSlot(page, i);
            if (poolSlot >= 0 && !pool.getStackInSlot(poolSlot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  表现辅助
    // ══════════════════════════════════════════════════════════════

    /** 流派色（RGB 0..1），与设计稿 / 预览工具一致。 */
    public static float[] colorOf(@Nullable MagicType type) {
        if (type == null) {
            return EMPTY_COLOR;
        }
        return switch (type) {
            case FIRE -> new float[]{1.00F, 0.42F, 0.18F};
            case DEATH -> new float[]{0.85F, 0.30F, 0.95F};
            case VOID -> new float[]{0.55F, 0.35F, 1.00F};
            case MYSTERIOUS -> new float[]{0.40F, 0.80F, 1.00F};
            case NATURE -> new float[]{0.42F, 0.90F, 0.50F};
            case LIGHT -> new float[]{1.00F, 0.90F, 0.55F};
            case WATER -> new float[]{0.30F, 0.72F, 0.91F};
        };
    }

    public static int argb(@Nullable MagicType type, float alpha) {
        return argb(colorOf(type), alpha);
    }

    public static int argb(float[] color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int r = Math.max(0, Math.min(255, Math.round(color[0] * 255.0F)));
        int g = Math.max(0, Math.min(255, Math.round(color[1] * 255.0F)));
        int b = Math.max(0, Math.min(255, Math.round(color[2] * 255.0F)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static String schoolLabel(@Nullable MagicType type) {
        if (type == null) {
            return "";
        }
        return WheelI18n.tr("rotten_ruins.wheel.school." + type.name().toLowerCase(Locale.ROOT));
    }

    public static String usingLabel(@Nullable UsingType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case IMMEDIATELY -> WheelI18n.tr("rotten_ruins.wheel.using.instant");
            case CHARGE -> WheelI18n.tr("rotten_ruins.wheel.using.charge");
            case KEEP -> WheelI18n.tr("rotten_ruins.wheel.using.channel");
        };
    }

    /** 冷却读秒格式：0 = READY，>=10s 取整，否则一位小数。 */
    public static String formatCooldown(float seconds) {
        if (seconds <= 0.0F) {
            return WheelI18n.tr("rotten_ruins.wheel.ready");
        }
        if (seconds >= 10.0F) {
            return Math.round(seconds) + "s";
        }
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /** 客户端玩家（可能为 null；只在 client 侧调用）。 */
    @Nullable
    public static Player minecraftPlayer() {
        return Minecraft.getInstance().player;
    }
}
