package org.bytechen.hall.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;
import org.bytechen.hall.api.ICustomOutline;
import org.bytechen.hall.overworld.registry.effect.VerdictEffect;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictBeamEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictFieldEntity;
import org.bytechen.hall.overworld.registry.items.verdict.HeightFactor;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictCooldown;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDash;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDebug;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;
import org.bytechen.hall.utils.DomeriteStatsHelper;

import java.util.List;
import java.util.Map;

/**
 * 天穹裁决（{@code domerite_longsword}）—— 单支线次毕业级长剑。
 *
 * <h3>伤害构成</h3>
 * <p>所有伤害（普攻与三个技能）统一走 {@link VerdictDamage} 的
 * 「25 原版 hurt + 5 原版 setHealth」两道，合计 30 有效伤害。
 * 本类的属性面板只负责<b>显示</b>与其它系统（如模组兼容读取攻击力）的兜底 ——
 * 实际结算不走属性，因为原版 {@code hurt} 那半程就是固定 25。</p>
 *
 * <h3>右键：单键三态</h3>
 * <p>三个技能共用<b>同一个</b> {@link VerdictCooldown} 冷却池，
 * 所以用掉一招就等于用掉整个裁决。三态靠原版物品使用机制区分，<b>不需要任何自定义网络包</b>：</p>
 * <pre>
 *   右键短按 (&lt; 0.25s)        → ② 截空·凌空斩     朝视线突进
 *   右键蓄力 (≥ 0.25s) 后松开   → ① 天穹裁决        垂直光柱
 *   潜行 + 按住右键 0.6s        → ③ 裁决领域        空中剑阵
 * </pre>
 *
 * <h3>为什么重写 {@code useOnRelease()}</h3>
 * <p>原版 {@code LivingEntity.updateUsingItem()} 的收尾是这样的：</p>
 * <pre>
 *   if (--useItemRemaining &lt;= 0 &amp;&amp; !level.isClientSide &amp;&amp; !stack.useOnRelease()) {
 *       completeUsingItem();     // 直接完成，根本不调 releaseUsing()
 *   }
 * </pre>
 * <p>也就是说 {@code useOnRelease()} 返回 false（默认）时，<b>把物品按满时长就会跳过
 * {@code releaseUsing()}，转而走 {@code completeUsingItem()}</b> —— 蓄力满之后的松开
 * 会彻底消失，光柱永远放不出来。返回 true（弓就是这么做的）之后，
 * 满时长会挂在原地等玩家松手，松手统一走 {@code releaseUsing()}。
 * 这是本类最容易漏掉、且症状最莫名其妙的一个点。</p>
 *
 * <h3>三个能力各自的阶段</h3>
 * <ul>
 *   <li>② 突进 —— 阶段 2（{@link #castDash}）</li>
 *   <li>① 光柱 —— 阶段 1（{@link #castBeam}，待接 {@code VerdictBeamEntity}）</li>
 *   <li>③ 领域 —— 阶段 3（{@link #castField}，待接 {@code VerdictFieldEntity}）</li>
 * </ul>
 */
public class DomeriteLongsword extends SwordItem implements ICustomOutline {

    // ══════════════════════════════════════════════════════════════
    //  三态参数
    // ══════════════════════════════════════════════════════════════

    /**
     * 右键"使用"的总时长。
     *
     * <p><b>它同时决定了短按窗口和蓄力上限</b>，因为两者是同一个量：
     * {@code 松开时 held = USE_DURATION - remaining}。所以：</p>
     * <ul>
     *   <li>{@code held < SHORT_PRESS_TICKS} → 短按（突进）；</li>
     *   <li>{@code held >= SHORT_PRESS_TICKS} → 蓄力（光柱）。</li>
     * </ul>
     *
     * <p>取 {@value #USE_DURATION} tick = 1.5 秒，于是短按窗口是
     * {@value #SHORT_PRESS_TICKS} tick = 0.3 秒。
     * 早期版本用 25/5（0.25s 窗口），实测<b>太窄</b>：正常的一次鼠标点击
     * 加上客户端/服务端 tick 相位差，很容易就超过 5 tick 而被判成蓄力，
     * 于是"短按突进"变得几乎放不出来。加宽到 0.3 秒之后，
     * 蓄力仍然只需要多按 0.3 秒 —— 对刻意蓄力的操作几乎没有成本。</p>
     *
     * <p>上限放宽到 1.5 秒的另一个好处：{@code remaining} 在按下当帧就等于总时长，
     * 而释放包的往返延迟约 1~3 tick，所以窗口如果只有 0.25 秒，
     * "面板上显示按住了一会儿"和"服务端判定为短按"就会经常对不上。</p>
     */
    public static final int USE_DURATION = 30;

    /** 短按 / 蓄力的分界：按住不足 {@value #SHORT_PRESS_TICKS} tick（0.3 秒）算短按。 */
    public static final int SHORT_PRESS_TICKS = 6;

    /** 潜行按住多久放出领域（tick）。 */
    public static final int FIELD_HOLD_TICKS = 12;

    /** 光柱最短长度（格）。抬头不足这个角度时视为没瞄准，本次出招作废。 */
    public static final float BEAM_MIN_LENGTH = 3.0f;

    /** 记录"本玩家上一根光柱"的持久数据键，用于下一发放出时把旧的收束掉。 */
    private static final String BEAM_UUID_KEY = "VerdictBeamUUID";

    /** 高空阈值：Y 超过它时，突进落点会额外补一圈冲击波涟漪。 */
    public static final double HIGH_ALTITUDE_Y = 150.0;

    // ── 突进参数 ────────────────────────────────────────────────────
    /** 突进持续（tick）。0.6 秒 —— 原版三叉戟激流是 0.9 秒，这个更快更"轻"。 */
    private static final int DASH_TICKS = 12;
    /**
     * 突进用的速度衰减系数，<b>必须是空中值</b>。
     *
     * <p>原版两个衰减完全不是一个量级：空中 {@code 0.91}，地面
     * {@code 方块摩擦 × 0.91}（草方块 = 0.546）。用错那个的后果是
     * 同样一次突进在地面只能滑 2 格、在空中能滑 12 格。</p>
     */
    private static final double DASH_DRAG = 0.91;
    /** 突进路径判定的采样步长（格）。每 ~2 格取一个采样盒，兼顾覆盖与开销。 */
    private static final double DASH_SAMPLE_STEP = 2.0;

    /** 贴地突进时额外给的向上初速，让"连按升空"成立。 */
    private static final double DASH_LIFT = 0.42;
    /** 突进路径判定的横向膨胀半径（格）。 */
    private static final double DASH_HIT_RADIUS = 0.9;
    /** 每次突进消耗的耐久。 */
    private static final int DASH_DURABILITY = 2;

    /** buff 的持续时间（tick）。 */
    public static final int BUFF_TICKS = 60;
    /** 每层 buff 给的光柱半径加成。{@code VerdictEffect} 要用，必须 public。 */
    public static final float BEAM_RADIUS_PER_LEVEL = 0.04f;
    /** 每层 buff 给的领域出剑提速。{@code VerdictEffect} 要用，必须 public。 */
    public static final float FIELD_RATE_PER_LEVEL = 0.05f;
    /** buff 层数上限。 */
    public static final int MAX_BUFF_LEVEL = 5;

    // ══════════════════════════════════════════════════════════════
    //  描边（保持既有视觉语言：天蓝 ⇄ 近白，垂直流动）
    // ══════════════════════════════════════════════════════════════

    @Override
    public int outlineColor() {
        return 0xFF87CEFA;                               // 天蓝
    }

    @Override
    public int outlineSecondaryColor() {
        return 0xFFF0F8FF;                               // 近白
    }

    @Override
    public String outlineShaderKey() {
        return "gradient";                               // = 双色流动
    }

    /**
     * 必须用 {@code TRANSLUCENT}，不能用默认的 {@code ADDITIVE}。
     *
     * <p>加法混合下黑色等于"加 0"——黑的那半截会整个消失，只剩白色在闪，
     * 黑白交替根本不成立。换回 alpha 混合，黑色才真的压得下去。</p>
     */
    @Override
    public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    /** 描边向外扩展的宽度（屏幕像素）。可以在配置里用 outlineWidthScale 整体缩放。 */
    @Override
    public float outlinePixelWidth() {
        return 4.5f;
    }

    /** 默认实现只覆盖世界上下文；这把剑在物品栏图标里也要描一圈。 */
    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.HEAD;
    }

    // ══════════════════════════════════════════════════════════════
    //  属性面板
    // ══════════════════════════════════════════════════════════════

    /**
     * 面板基准伤害。注意这只是<b>显示值</b>与兼容兜底 ——
     * 实际伤害由 {@link VerdictDamage} 固定结算成 25 + 5。
     */
    private static final float BASE_DAMAGE = 8.0F;

    public DomeriteLongsword(Properties properties) {
        super(Tiers.NETHERITE, (int) BASE_DAMAGE, -2.4f, properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return DomeriteStatsHelper.getScaledDurability(stack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) return original;

        float scaledBonus = DomeriteStatsHelper.getScaledAttackBonus(stack);
        float total = BASE_DAMAGE + scaledBonus;

        HashMultimap<Attribute, AttributeModifier> dynamic = HashMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier mod = entry.getValue();
            if (attr == Attributes.ATTACK_DAMAGE) {
                dynamic.put(attr, new AttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_UUID, "Domerite damage", total,
                        AttributeModifier.Operation.ADDITION));
            } else if (attr == Attributes.ATTACK_SPEED) {
                dynamic.put(attr, new AttributeModifier(
                        Item.BASE_ATTACK_SPEED_UUID, "Domerite speed", -2.4f,
                        AttributeModifier.Operation.ADDITION));
            } else {
                dynamic.put(attr, mod);
            }
        }
        return dynamic;
    }

    // ══════════════════════════════════════════════════════════════
    //  每 tick：推进突进
    // ══════════════════════════════════════════════════════════════

    /**
     * 物品在背包里每 tick 被调用一次 —— 借它推进"突进中"状态。
     *
     * <p>为什么不另写一个 Forge 事件监听器：突进是<b>这把剑的技能</b>，
     * 状态跟着物品走最自然；而且 {@code inventoryTick} 天然只在物品确实
     * 在玩家身上时才触发，不需要额外的持有判定，也不会在玩家丢掉剑之后
     * 留下一个还在推他的幽灵状态（{@link VerdictDash#tick} 里还有一层
     * "状态不存在就直接返回"的兜底）。</p>
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide()) return;
        if (entity instanceof Player player) VerdictDash.tick(player);
    }

    // ══════════════════════════════════════════════════════════════
    //  普攻：25 + 5
    // ══════════════════════════════════════════════════════════════

    /**
     * 普通攻击走裁决伤害管线。
     *
     * <p><b>刻意不调用 {@code super.hurtEnemy()}</b>：父类会自己再打一次
     * {@code getDamage()}（本类随 Y 缩放，最高 16），叠加之后就变成 25+16 = 41。
     * 这里显式关掉属性伤害，让"25 + 5"是唯一的伤害形状。</p>
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return super.hurtEnemy(stack, target, attacker);
        }

        boolean struck = VerdictDamage.strike(target,
                player.damageSources().playerAttack(player), player);

        // ── 击退：父类那部分被关掉了，这里补回，否则长剑失去"重击"的重量感 ──
        if (target.isAlive()) {
            target.knockback(0.4f,
                    Math.sin(player.getYRot() * (float) (Math.PI / 180.0)),
                    -Math.cos(player.getYRot() * (float) (Math.PI / 180.0)));
        }

        if (struck) grantVictory(player);

        // 耐久：等价于父类的 1 点消耗（不触发横扫与属性伤害）
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
    }

    // ══════════════════════════════════════════════════════════════
    //  右键：单键三态
    // ══════════════════════════════════════════════════════════════

    /**
     * 右键 → 进入蓄力状态。
     *
     * <p>客户端与服务端<b>都</b>返回 {@code consume}：客户端预测不一致的话手臂不会摆，
     * 而且客户端不进"使用中"状态，蓄力的视觉反馈（后续要接的剑身眩光）就拿不到进度。</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 总开关关掉时完全退回原版剑行为（父类会走默认的 pass）
        if (!VerdictTuning.skillsEnabled()) {
            return super.use(level, player, hand);
        }

        // 冷却中：提示剩余时间，客户端直接不给起手
        if (VerdictCooldown.onCooldown(player)) {
            // 每次尝试都把原版物品冷却重申一遍。理由：原版冷却被
            // `MultiPlayerGameMode#useItem` 当作输入闸门（isOnCooldown 时本地 PASS、连包都不发），
            // 而它可能被 /clear、物品被换掉、或客户端重连后丢失 ——
            // 重申一次就能自愈，不需要玩家重登。
            if (!level.isClientSide()) {
                VerdictCooldown.syncVanilla(player);
            }
            if (level.isClientSide()) {
                int remain = VerdictCooldown.remaining(player);
                player.displayClientMessage(Component
                        .translatable("item.hall.domerite_longsword.cooldown",
                                String.format("%.1f", remain / 20.0f))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 必须返回 true —— 见类注释里那段 {@code updateUsingItem} 的说明。
     * 返回 false 时"按满时长"会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}，
     * 于是蓄力到顶后的松开什么都不放。
     */
    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;
    }

    /** 蓄力时长上限。 */
    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    /** 持续举剑（可以换成 BOW 之类的姿势，目前用原版 SPEAR 最接近"举剑蓄力"）。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    /** 蓄力时的剑身提示音节奏：让玩家"听得到"自己蓄了多久。 */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {        if (!(living instanceof Player player)) return;

        int held = USE_DURATION - remaining;

        // ── ③ 领域：潜行按住够时长，直接触发 ──
        //  必须先 stopUsingItem() 再出招：它内部会调 releaseUsingItem()，
        //  而 releaseUsingItem() 对"已经空掉的 useItem"会提前 return ——
        //  于是随手松手不会再放出突进/光柱。顺序反过来（先出招再停）会双重触发。
        //
        //  放在客户端条件之外是刻意的：客户端也要退出使用态，否则
        //  物品的使用进度条与举剑姿势会一直挂着，和服务端不同步。
        if (player.isShiftKeyDown() && held >= FIELD_HOLD_TICKS) {
            player.stopUsingItem();
            boolean ok = false;
            if (!level.isClientSide()) {
                boolean enabled = VerdictTuning.fieldEnabled();
                ok = enabled && castField(level, player);
                // 诊断：领域"完全不可见"有四种可能（开关 / 冷却锁 / 生成失败 / 渲染问题），
                // 这一行把前三种一次性区分开。渲染侧的探针在 VerdictFieldRenderer 里。
                VerdictDebug.log("field 触发 held=%d enabled=%b ok=%b entityCount=%d",
                        held, enabled, ok, countFields(level, player));
            }
            if (ok) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.35f);
            }
            return;
        }

        // ── 蓄力进度音（每 5 tick 一声，音调随蓄力升高）──
        if (!level.isClientSide() && held > 0 && held % 5 == 0) {
            float pitch = 0.85f + 0.35f * (held / (float) USE_DURATION);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    0.35f, pitch);
        }
    }

    /**
     * 松开右键 → 按按住时长分流到短按（突进）或蓄力（光柱）。
     *
     * <p>{@code remaining} 取自服务端自己的 {@code useItemRemaining}：
     * 客户端发 {@code ServerboundPlayerActionPacket(RELEASE_USE_ITEM)}，
     * 服务端处理时调 {@code releaseUsingItem()}，此时 {@code useItemRemaining}
     * 已经按服务端自己的 tick 递减过。</p>
     *
     * <p>分流：{@code held = USE_DURATION - remaining}，
     * {@code held < }{@link #SHORT_PRESS_TICKS}（即按住不足 0.3 秒）走突进，否则走光柱。</p>
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide() || !(living instanceof Player player)) return;

        int held = USE_DURATION - remaining;

        // 诊断：把分流判定依赖的每一个量都落盘。
        // "右键没反应"有四种可能原因（分流条件 / 配置开关 / 冷却锁 / 服务端根本没收到释放包），
        // 症状完全一样、看代码分不出来，所以这里把实际取值写出来。
        VerdictDebug.log("releaseUsing remaining=%d held=%d/%d branch=%s "
                        + "dashEnabled=%b beamEnabled=%b onCooldown=%b cdRemain=%d",
                remaining, held, SHORT_PRESS_TICKS,
                held < SHORT_PRESS_TICKS ? "DASH" : "BEAM",
                VerdictTuning.dashEnabled(), VerdictTuning.beamEnabled(),
                VerdictCooldown.onCooldown(player), VerdictCooldown.remaining(player));

        if (held < SHORT_PRESS_TICKS) {
            if (VerdictTuning.dashEnabled()) castDash(level, player, stack);
        } else {
            if (VerdictTuning.beamEnabled()) castBeam(level, player, stack);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ① 天穹裁决 · 垂直光柱（阶段 1）
    // ══════════════════════════════════════════════════════════════

    /**
     * 在玩家脚下生成一根竖直光柱。
     *
     * <p><b>阶段 1 待办</b>：光柱的视觉与判定接 {@code VerdictBeamEntity}。
     * 本轮先用一条纯几何的竖直 AABB 把伤害管线验通 ——
     * 判定范围与将来实体的圆柱体保持一致（半径 × 长度），
     * 所以接上实体之后数值不需要重调。</p>
     *
     * <p>长度由抬头角决定：抬头 90° 给满 {@link HeightFactor#BEAM_MAX_LENGTH}，
     * 低头不到 {@link #BEAM_MIN_LENGTH} 视为没瞄准，本次出招<b>作废且不进冷却</b>。</p>
     */
    private static void castBeam(Level level, Player player, ItemStack stack) {
        float length = HeightFactor.beamLengthFromPitch(player.getXRot());
        if (length < BEAM_MIN_LENGTH) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 0.7f);
            return;                                      // 没瞄准：不消耗冷却
        }

        if (!VerdictCooldown.begin(player)) return;

        // 覆盖 = 高度系数（你在哪）× 裁决层数（你打得多顺）
        float radius = HeightFactor.beamRadiusBlocks(player)
                * VerdictEffect.beamRadiusScale(player);
        double cx = player.getX();
        double cy = player.getY();                       // 脚底
        double cz = player.getZ();

        // ── 判定：从脚底向上的一条竖直柱体 ──
        //  几何契约与 VerdictBeamEntity / VerdictBeamRenderer 完全一致：
        //  「底面中心 + 半径 radius + 长度 length」。改范围必须三处一起改。
        AABB box = new AABB(
                cx - radius, cy, cz - radius,
                cx + radius, cy + length, cz + radius);
        strikeAllIn(level, player, box, VerdictDamage.BEAM_HURT);

        // ── 视觉：先让上一根光柱收束，再立新的 ──
        //  收束而不是立刻 discard，是因为"旧的那根一闪而没"比"两根重叠"更 affordable：
        //  重叠在加法混合下会叠成刺眼的白，而快速淡出读起来像"裁决被重新执行"。
        fadePreviousBeam(level, player);
        VerdictBeamEntity beam = VerdictBeamEntity.spawn(
                level, new Vec3(cx, cy, cz), radius, length);
        rememberBeam(player, beam);

        grantVictory(player);

        level.playSound(null, cx, cy, cz,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.45f, 1.6f);
        stack.hurtAndBreak(3, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        VerdictCooldown.finish(player);
    }

    /** 记下本次光柱的 UUID，好在下一发放出时把旧的关掉。 */
    private static void rememberBeam(Player player, VerdictBeamEntity beam) {
        if (beam == null) return;
        player.getPersistentData().putUUID(BEAM_UUID_KEY, beam.getUUID());
    }

    /**
     * 把上一根光柱加快收束。
     *
     * <p>不直接 {@code discard()}：目标是"让位"而不是"抹掉"。做法是把它的
     * 剩余生命压到淡出时长以内，于是它会自己走完那条既有的淡出曲线。</p>
     */
    private static void fadePreviousBeam(Level level, Player player) {
        var data = player.getPersistentData();
        if (!data.hasUUID(BEAM_UUID_KEY)) return;
        var prev = data.getUUID(BEAM_UUID_KEY);
        data.remove(BEAM_UUID_KEY);

        if (prev == null || level.isClientSide()) return;
        AABB area = player.getBoundingBox().inflate(96.0);
        for (VerdictBeamEntity old : level.getEntitiesOfClass(VerdictBeamEntity.class, area)) {
            if (old.getUUID().equals(prev)) {
                old.setMaxAge(old.getAge() + (int) VerdictBeamEntity.FADE_OUT_TICKS);
            }
        }
    }

    /** 诊断用：数一下玩家周围现存的领域实体数量。 */
    private static int countFields(Level level, Player player) {
        return level.getEntitiesOfClass(VerdictFieldEntity.class,
                player.getBoundingBox().inflate(128.0)).size();
    }

    // ══════════════════════════════════════════════════════════════
    //  ② 截空 · 凌空斩（阶段 2）
    // ══════════════════════════════════════════════════════════════

    /**
     * 朝视线方向突进，路径上的敌人各吃一次裁决伤害。
     *
     * <p>距离吃高度（{@link HeightFactor#dashDistanceBlocks}），
     * 但指数被压到四次方，避免高空时把人甩进虚空。</p>
     *
     * <p>贴地突进会额外给一点向上初速：这是三个技能里<b>唯一能把自己推高</b>的一招，
     * 因而是"越打越高、越高覆盖越大"这条循环的发动机。</p>
     */
    private static void castDash(Level level, Player player, ItemStack stack) {
        if (!VerdictCooldown.begin(player)) {
            VerdictDebug.log("  castDash 被冷却池拒绝（begin 返回 false）");
            return;
        }

        float distance = HeightFactor.dashDistanceBlocks(player)
                * VerdictTuning.dashDistanceScale();
        Vec3 look = player.getLookAngle();

        // ── 位移：把"目标距离"换算成本 tick 该给的水平初速 ──
        //
        //  这里有个很容易踩的坑：**原版地面摩擦是 0.546/tick**（方块 0.6 × 0.91），
        //  而不是空中那个 0.91。同样给 0.95 的初速，
        //  在空中能滑约 11.7 格，在地面只滑约 2.0 格 —— 差 5 倍以上。
        //  所以"给一个速度"和"突进 12 格"是两回事，必须先把距离反解成速度。
        //
        //  反解用的是**空中**的衰减系数（0.91），因为我们下面会把玩家从地面抬起来（见 DASH_LIFT）：
        //  突进本来就不该被地形摩擦吃掉距离，而且贴地滑行会因为脚下方块不同
        //  （草 0.6 / 冰 0.98）导致同样的招式在不同地面上距离完全不同，那不可接受。
        double speed = dashSpeedForDistance(distance);

        double vx = look.x * speed;
        double vz = look.z * speed;
        // 贴地时强制给一个向上初速：
        //  · 让衰减走空中的 0.91，距离才准；
        //  · 顺带就是"连按升空"这条玩法的发动机 —— 每次短按净升约 1 格。
        // 取 max 而不是覆盖，是为了抬头突进时不会把玩家往下按。
        double vy = Math.max(look.y * speed * 0.35, player.onGround() ? DASH_LIFT : 0.0);

        player.setDeltaMovement(vx, vy, vz);
        player.hurtMarked = true;                        // 强制把速度同步给客户端
        player.hasImpulse = true;

        // 关键：单次给速度会被地面摩擦（0.546/tick）和客户端预测吃掉，
        // 所以登记一次"突进中"状态，由 inventoryTick 每 tick 重申速度直到走完。
        // 详见 VerdictDash 的类注释。
        VerdictDash.start(player, look.x, look.z, speed);

        VerdictDebug.log("  castDash distance=%.2f speed=%.3f vel=(%.3f,%.3f,%.3f) onGround=%b y=%.1f",
                distance, speed, vx, vy, vz, player.onGround(), player.getY());

        // ── 沿途判定：沿突进路径逐点取样 ──
        //
        //  早期实现是"以玩家为中心膨胀一个盒子"——只在**释放那一瞬**判一次，
        //  于是只能打到起手时贴着你的怪，路径中段的完全漏掉（玩家反馈"打不到沿途生物"）。
        //
        //  正确做法是拿扫掠体：把玩家碰撞箱沿方向走一遍。
        //  这里用离散取样近似（每 ~2 格一个采样点）。用 Mth.ceil 而不是固定段数，
        //  是为了让采样密度与距离无关 —— 突进距离会随 Y 坐标变化（12~15.6 格），
        //  固定段数会让高空时采样变稀、漏掉中间的怪。
        int samples = Math.max(2, Mth.ceil(distance / DASH_SAMPLE_STEP));
        AABB box = player.getBoundingBox();

        // 去重：同一个生物可能同时落在两个相邻采样点的盒子里，那只该吃一次伤害。
        java.util.Set<java.util.UUID> alreadyHit = new java.util.HashSet<>();
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 center = player.position().add(look.scale(distance * t));
            AABB sweep = box.move(center.x - player.getX(), center.y - player.getY(),
                    center.z - player.getZ()).inflate(DASH_HIT_RADIUS, 0.6, DASH_HIT_RADIUS);
            strikeAllIn(level, player, sweep, VerdictDamage.DASH_HURT, alreadyHit);
        }

        grantVictory(player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.25f);
        stack.hurtAndBreak(DASH_DURABILITY, player,
                e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        // ── 表现：用现有资产，零新着色器 ──
        //  拖尾 = 几个已有的白色发光剑气（STYLE_DEFAULT），沿视线方向拉开距离、逐级缩小。
        //  刻意不给它做新着色器：突进是高频动作（5 秒冷却、命中还能返还），
        //  每一发都跑一套新特效管线是纯浪费；而这个资产本来就是这个模组的"剑气"语言。
        spawnDashTrail(level, player, look, distance);

        // 高空突进落点补一圈冲击波涟漪：既是速度感的收尾，
        // 也顺手把"你在很高的地方"这件事反馈给玩家（高度是这把剑的资源）。
        if (player.getY() > HIGH_ALTITUDE_Y) {
            // 速度 3 格/秒 与 ShockwaveEntity 的默认值一致；它有 DEF_SPEED 但这个常量是 private，
            // 而 spawn(...) 的形参本来就是对外开放的调参入口，所以这里直接给值。
            ShockwaveEntity.spawn(level, player.position(),
                    3.0f, HeightFactor.dashDistanceBlocks(player) * 0.7f,
                    24, 0.75f);
        }

        VerdictCooldown.finish(player);
    }

    /**
     * 沿突进方向铺一串剑气作为拖尾。
     *
     * <p>用随机 Y 旋转（{@link SwordAuraEntity} 内部按 entity id 派生）让每一道朝向都不同，
     * 于是串起来读作"一道被撕开的轨迹"而不是"三个整齐的箭头"。</p>
     */
    private static void spawnDashTrail(Level level, Player player, Vec3 look, float distance) {
        final int steps = 3;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) (steps + 1);
            Vec3 p = player.position().add(look.scale(distance * t)).add(0, 1.0, 0);
            // 越靠后的越大、活越久：形成"从自己身上甩出去"的方向感
            float scale = 0.45f + 0.35f * t;
            SwordAuraEntity.spawn(level, p, scale, 0.85f,
                    SwordAuraEntity.APOSTLE_HEIGHT * 0.55f, 0.22f,
                    10 + i * 2, 1.8f);
        }
    }

    /**
     * 把一个目标水平距离反解成"本 tick 该给多少初速"。
     *
     * <p>运动模型是原版那套几何级数：每 tick 位置增加 {@code v}，然后 {@code v *= DRAG}。</p>
     * <pre>
     *   n tick 的总位移 = v · (1 - DRAG^n) / (1 - DRAG)
     *   ⇒ v = distance · (1 - DRAG) / (1 - DRAG^n)
     * </pre>
     *
     * <p>用 {@link #DASH_DRAG} = 0.91（空中）而不是地面摩擦，
     * 理由见 {@link #castDash} 里那段注释。</p>
     */
    private static double dashSpeedForDistance(double distance) {
        double decay = 1.0 - Math.pow(DASH_DRAG, DASH_TICKS);
        return distance * (1.0 - DASH_DRAG) / Math.max(decay, 1e-4);
    }

    // ══════════════════════════════════════════════════════════════
    //  ③ 裁决领域 · 空中剑阵（阶段 3）
    // ══════════════════════════════════════════════════════════════

    /**
     * 在脚下展开裁决领域。
     *
     * <p><b>刻意不再"放下时立刻打一下"。</b>早期版本这么做是为了先跑通三态，
     * 但它其实会破坏这个技能的定位：领域的价值在于"这片地一直有裁决"，
     * 一发放就白送一次范围伤害，会让玩家把它当成一个冷兵器版的手雷——
     * 放完就走，而不是站进去打。现在伤害完全由 {@link VerdictFieldEntity}
     * 按 1.2 秒的出剑节奏给，玩家必须接受"站进领域"这个前提才能吃到收益。</p>
     *
     * <h3>半径的两条上限</h3>
     * <ul>
     *   <li>{@code HeightFactor.fieldRadiusBlocks} —— 吃高度，越高管得越宽；</li>
     *   <li>{@code VerdictEffect.fieldRateScale} 不作用在半径上，而是作用在出剑频率上
     *       （见实体里的 {@code SWORD_INTERVAL} 注释）。裁决层数加的是"打得多顺"，
     *       不是"管得多宽" —— 两条曲线各管一段。</li>
     * </ul>
     *
     * @return 是否成功放出（用于决定要不要播音效）
     */
    private static boolean castField(Level level, Player player) {
        if (!VerdictCooldown.begin(player)) return false;

        ItemStack stack = player.getMainHandItem();
        float radius = HeightFactor.fieldRadiusBlocks(player) * VerdictTuning.fieldRadiusScale();

        VerdictFieldEntity.spawn(level, player, radius, VerdictFieldEntity.DEFAULT_MAX_AGE);

        grantVictory(player);
        stack.hurtAndBreak(4, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        VerdictCooldown.finish(player);
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  裁决层数：把"高度"变成可携带的资源
    // ══════════════════════════════════════════════════════════════

    /**
     * 任意裁决系命中都会给自身叠一层「裁决」增益。
     *
     * <p>加的不是伤害（伤害已定死 30），而是<b>覆盖</b>：
     * 每层给光柱半径 +{@value #BEAM_RADIUS_PER_LEVEL}、领域出剑提速
     * +{@value #FIELD_RATE_PER_LEVEL}。这样高度系数负责"你在哪"，
     * 裁决层数负责"你打得多顺"，两条曲线各管一段、不互相放大。</p>
     */
    private static void grantVictory(Player player) {
        MobEffectInstance cur = player.getEffect(VerdictEffect.INSTANCE);
        int level = cur == null ? 0 : cur.getAmplifier() + 1;
        if (level > MAX_BUFF_LEVEL) level = MAX_BUFF_LEVEL;

        player.addEffect(new MobEffectInstance(
                VerdictEffect.INSTANCE, BUFF_TICKS, level, false, false, true));
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    /** 裁决系技能的合法目标：活着的生物，排除旁观者。 */
    private static boolean isVerdictTarget(Entity e) {
        return e instanceof LivingEntity living && living.isAlive() && !living.isSpectator();
    }

    /**
     * 对盒内所有合法目标各打一次裁决伤害。
     *
     * @param alreadyHit 本次技能已经打过的目标。突进的沿途判定会沿路径取样多次，
     *                   相邻采样盒的重叠区会让同一个生物被取到两遍 ——
     *                   不靠它去重就会打出双倍伤害。传 null 表示不需要去重。
     */
    private static void strikeAllIn(Level level, Player player, AABB box, float hurt,
                                    @Nullable java.util.Set<java.util.UUID> alreadyHit) {
        List<Entity> hits = new java.util.ArrayList<>(
                level.getEntities(player, box, DomeriteLongsword::isVerdictTarget));
        strikeEach(player, hits, hurt, alreadyHit);
    }

    /** 单次判定（不需要去重的场景）。 */
    private static void strikeAllIn(Level level, Player player, AABB box, float hurt) {
        strikeAllIn(level, player, box, hurt, null);
    }

    /**
     * 对一份已经取好的实体列表逐个结算。
     *
     * <p>{@code strikeEach} 内部会<b>拷一份再遍历</b>：伤害可能把目标打死，
     * 死亡会让实体从 level 的 tick 列表里移除，直接在原列表上遍历 +
     * 死亡移除是 {@code ConcurrentModificationException} 的经典来源。</p>
     */
    private static void strikeEach(Player player, List<Entity> hits, float hurt,
                                   @Nullable java.util.Set<java.util.UUID> alreadyHit) {
        List<Entity> safe = new java.util.ArrayList<>(hits);
        var source = player.damageSources().playerAttack(player);
        for (Entity e : safe) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (alreadyHit != null && !alreadyHit.add(living.getUUID())) continue;   // 已打过
            VerdictDamage.strike(living, source, hurt, VerdictDamage.BYPASS_PART, player);
        }
    }
}
