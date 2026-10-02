package org.bytechen.hall.overworld.registry.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictSwordDropEntity;
import org.bytechen.hall.overworld.registry.items.verdict.HeightFactor;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictCooldown;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDash;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDebug;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictFeedback;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictTuning;
import org.bytechen.hall.utils.DomeriteStatsHelper;

import java.util.List;
import java.util.Map;

/**
 * 天穹裁决（{@code domerite_longsword}）—— 单支线次毕业级长剑。
 *
 * <h3>伤害构成</h3>
 * <p>所有伤害统一走 {@link VerdictDamage} 的「原版 hurt + 原版 setHealth」两道。
 * 平砍与突进是 25 + 5 = 30；光柱是<b>两波</b>（24+5 与 12+4 ≈ 45，随扫描球下落分两次结算）；
 * 领域剑气是 18 + 5。本类的属性面板只负责<b>显示</b>与其它系统（如模组兼容读取攻击力）的兜底 ——
 * 实际结算不走属性，因为原版 {@code hurt} 那半程是固定值。</p>
 *
 * <h3>反馈</h3>
 * <p>三个技能共用 {@link VerdictFeedback} 这一个反馈入口：命中顿帧、命中音调随命中数升高、
 * 粒子爆发、相机抖动与 FOV 冲击。共同入口是"这套技能有统一识别度"的前提 ——
 * 各写各的 playSound 只会在画面上留下三套互不相干的特效。</p>
 *
 * <h3>右键：单键三态</h3>
 * <p>三个技能共用<b>同一个</b> {@link VerdictCooldown} 冷却池，
 * 所以用掉一招就等于用掉整个裁决。三态靠原版物品使用机制区分，<b>不需要任何自定义网络包</b>：</p>
 * <pre>
 *   右键短按 (&lt; 0.4s = 8 tick)   → ② 截空·凌空斩     朝视线突进
 *   右键蓄力 (≥ 0.4s) 后松开       → ① 天穹裁决        垂直光柱（两波伤害）
 *   潜行 + 按住右键 0.7s (=14 tick) → ③ 裁决领域        空中剑阵
 * </pre>
 * <p>时间轴上是一条干净的阶梯，阈值处各有一声提示音 —— 玩家<b>听</b>得出来自己在哪一档。</p>
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
     * {@value #SHORT_PRESS_TICKS} tick = 0.4 秒。</p>
     *
     * <p>窗口的历史：25/5（0.25s）→ 30/6（0.3s）→ 现在的 30/8（0.4s）。
     * 每一次都在放宽，理由是同一个：正常的一次鼠标点击落在服务端时，
     * {@code held} 会叠加"按住时长 + 客户端/服务端 tick 相位差 + 释放包的往返延迟"，
     * 而这个和是<b>有抖动</b>的。窗口越窄，被判成蓄力的比例就越高，
     * 于是"点一下突进"时灵时不灵 —— 那是最伤手感的一类 bug，
     * 因为它让玩家觉得"这个技能看我脸色"。</p>
     *
     * <p>放宽到 0.4 秒的代价几乎为零：蓄力本来就需要按住，
     * 从 0.3 秒开始蓄还是从 0.4 秒开始蓄，对刻意的操作没有区别。</p>
     */
    public static final int USE_DURATION = 30;

    /**
     * 短按 / 蓄力的分界：按住不足 {@value #SHORT_PRESS_TICKS} tick（0.4 秒）算短按。
     * <p>必须<b>严格小于</b> {@link #FIELD_HOLD_TICKS}，否则第三态（领域）永远不可达。</p>
     */
    public static final int SHORT_PRESS_TICKS = 8;

    /**
     * 潜行按住多久放出领域（tick）= 0.7 秒。
     *
     * <p>它同时是"蓄光柱"这一段的上界：按住到 {@value} tick 还没松手（且潜行中）
     * 就会自动放出领域。于是三态在时间轴上是一条干净的阶梯：</p>
     * <pre>
     *   0 ~ 8 tick   松手 → ② 突进
     *   8 ~ 14 tick  松手 → ① 光柱
     *   ≥ 14 tick    自动 → ③ 领域（需潜行）
     * </pre>
     * <p>配合 {@code onUseTick} 里的阈值提示音，玩家<b>听</b>就能数出自己在哪一档。</p>
     */
    public static final int FIELD_HOLD_TICKS = 14;

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

    /**
     * 每 tick 的蓄力表现：<b>声音 + 粒子 + 三态阈值提示</b>。
     *
     * <h3>为什么要给客户端粒子</h3>
     * <p>在这之前，蓄力期<b>唯一的反馈是每 5 tick 一声</b>，画面完全不动。
     * 于是"按住右键"这个动作在视觉上就是——什么都没发生，然后突然一条光柱。
     * 玩家读不出自己蓄到哪了，也读不出松手会放哪一招。</p>
     *
     * <h3>为什么粒子走 {@code level.addParticle} 而不是 {@code sendParticles}</h3>
     * <p>蓄力粒子是<b>纯观感</b>：只有本人看得见就够，别人看不到也不影响任何判定。
     * 走客户端本地生成就等于零网络开销 —— 而它每 tick 都在跑，
     * 一旦走服务端广播，一个玩家按住右键 1.5 秒就是几十个包。</p>
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(living instanceof Player player)) return;

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

        // ── 蓄力表现 ──
        if (level.isClientSide()) {
            chargeClientParticles(player, held);
            // 阈值提示音放在客户端：这里知道 held 的确切值，
            // 服务端版本会被网络相位差搞成偶尔漏发/重复。
            if (held == SHORT_PRESS_TICKS || held == FIELD_HOLD_TICKS) {
                float pitch = held == SHORT_PRESS_TICKS ? 1.05f : 0.85f;
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                        0.45f, pitch);
            }
        }

        // ── 蓄力进度音（每 4 tick 一声，音调随蓄力升高）──
        //  从每 5 tick 收紧到 4 tick：配合粒子环之后，节奏必须是可数的，
        //  否则"还有多久到顶"依然只能靠感觉。
        if (!level.isClientSide() && held > 1 && held % 4 == 0) {
            float pitch = 0.85f + 0.35f * (held / (float) USE_DURATION);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    0.35f, pitch);
        }
    }

    /**
     * 蓄力期的粒子：脚下先出现一圈缓慢旋转并<b>向内收拢</b>的光点，
     * 过阈值之后再叠一层往下汇聚的直落光尘。
     *
     * <p>形状刻意做成"收拢"而不是"扩散"：蓄力的语义是力量在聚集。
     * 扩散的粒子会读成招式已经出手了，与"还在蓄"矛盾。</p>
     *
     * @param held 已经按住的 tick 数
     */
    private static void chargeClientParticles(Player player, int held) {
        if (held <= 0 || held > USE_DURATION) return;
        float t = Mth.clamp(held / (float) USE_DURATION, 0f, 1f);

        // ── ① 收拢环：半径随蓄力缩小，同时整体亮度提升（用粒子数量表达）──
        double radius = Mth.lerp(t, 1.45, 0.55);
        int points = held % 2 == 0 ? 3 : 2;              // 奇数 tick 少一半，省开销
        double base = player.getY() + 0.15;
        for (int i = 0; i < points; i++) {
            double a = (held * 0.35 + i * (Math.PI * 2.0 / points));
            // 加点径向抖动，避免读成一台机器在转
            double r = radius * (0.85 + 0.3 * Math.sin(held * 1.7 + i * 2.1));
            player.level().addParticle(ParticleTypes.END_ROD,
                    player.getX() + Math.cos(a) * r, base, player.getZ() + Math.sin(a) * r,
                    -Math.cos(a) * 0.02, 0.004, -Math.sin(a) * 0.02);
        }

        // ── ② 越过短按阈值之后，加一层从上方落下的光尘（"越来越近的顶"）──
        if (held >= SHORT_PRESS_TICKS && held % 2 == 0) {
            double a = held * 0.9;
            double r = 0.9 - 0.5 * t;
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    player.getX() + Math.cos(a) * r,
                    player.getY() + 2.1 - 1.4 * t,
                    player.getZ() + Math.sin(a) * r,
                    0.0, -0.05 - 0.06 * t, 0.0);
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
     * {@code held < }{@link #SHORT_PRESS_TICKS}（即按住不足 0.4 秒）走突进，否则走光柱。
     * 第三态（领域）不在这里 —— 它在 {@code onUseTick} 里按住够时长就自动触发。</p>
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide() || !(living instanceof Player player)) return;

        int held = USE_DURATION - remaining;
        boolean dash = held < SHORT_PRESS_TICKS;

        // 诊断：把分流判定依赖的每一个量都落盘。
        // "右键没反应"有四种可能原因（分流条件 / 配置开关 / 冷却锁 / 服务端根本没收到释放包），
        // 症状完全一样、看代码分不出来，所以这里把实际取值写出来。
        VerdictDebug.log("releaseUsing remaining=%d held=%d/%d branch=%s "
                        + "dashEnabled=%b beamEnabled=%b onCooldown=%b cdRemain=%d",
                remaining, held, SHORT_PRESS_TICKS,
                dash ? "DASH" : "BEAM",
                VerdictTuning.dashEnabled(), VerdictTuning.beamEnabled(),
                VerdictCooldown.onCooldown(player), VerdictCooldown.remaining(player));

        if (dash) {
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
     * <p><b>伤害已经搬进 {@link VerdictBeamEntity}</b>：这一招现在是"两波"的
     * （落柱那一瞬 + 扫描球下落到 45% 高度时），而延迟结算需要一个每 tick 被处理、
     * 有生命周期的东西 —— 光柱实体本身就是。这样做的额外好处是
     * 「视觉扫到哪」与「伤害打到哪」在时间上是同一件事。</p>
     *
     * <p>本方法只负责：校验瞄准、结算冷却、立起实体、给一次起手反馈。</p>
     *
     * <p>长度由抬头角决定：抬头 90° 给满 {@link HeightFactor#BEAM_MAX_LENGTH}，
     * 低头不到 {@link #BEAM_MIN_LENGTH} 视为没瞄准，本次出招<b>作废且不进冷却</b>。</p>
     */
    private static void castBeam(Level level, Player player, ItemStack stack) {
        // 视觉长度：抬头角决定。它现在只影响"看得见多高"，判定另有上限 ——
        // 见 HeightFactor.BEAM_HIT_MAX_HEIGHT 的说明。
        float length = HeightFactor.beamLengthFromPitch(player.getXRot());
        if (length < BEAM_MIN_LENGTH) {
            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 0.7f);
                // 原本这里是<b>完全静默</b>的：玩家蓄满力松手，什么都没发生，
                // 只有一声放气。整个动作看起来就是"没反应"。
                // 明确告诉他为什么没打出去，比让他自己猜强得多。
                player.displayClientMessage(Component
                        .translatable("item.hall.domerite_longsword.no_aim")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;                                      // 没瞄准：不消耗冷却
        }

        if (!VerdictCooldown.begin(player)) return;

        // 起始覆盖 = 高度系数（你在哪）× 裁决层数（你打得多顺）
        float radius = HeightFactor.beamRadiusBlocks(player)
                * VerdictEffect.beamRadiusScale(player);
        double cx = player.getX();
        double cy = player.getY();                       // 脚底
        double cz = player.getZ();

        // ── 视觉：先让上一根光柱收束，再立新的 ──
        //  收束而不是立刻 discard，是因为"旧的那根一闪而没"比"两根重叠"更 affordable：
        //  重叠在加法混合下会叠成刺眼的白，而快速淡出读起来像"裁决被重新执行"。
        fadePreviousBeam(level, player);

        // ── 扩散终点也吃"裁决层数"，但不吃高度系数 ──
        //  层数给的是"打得多顺" → 扩散得更狠，与它给光柱半径加成的定位一致。
        //  高度系数不参与：它已经通过起始半径生效了，再乘一次就变成二次放大，
        //  高空的柱子会直冲 32 格以上，那是失控而不是"更强"。
        float targetRadius = VerdictBeamEntity.DEFAULT_TARGET_RADIUS
                * VerdictEffect.beamRadiusScale(player);
        VerdictBeamEntity beam = VerdictBeamEntity.spawn(
                level, new Vec3(cx, cy, cz), radius, targetRadius, length,
                VerdictBeamEntity.DEFAULT_GROW_TICKS, VerdictBeamEntity.DEFAULT_MAX_AGE, player);
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
        java.util.Set<java.util.UUID> firstHit = new java.util.HashSet<>();
        int hits = 0;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 center = player.position().add(look.scale(distance * t));
            AABB sweep = box.move(center.x - player.getX(), center.y - player.getY(),
                    center.z - player.getZ()).inflate(DASH_HIT_RADIUS, 0.6, DASH_HIT_RADIUS);

            // 先把这一段的合法目标记下来，好在打完之后在它们身上补"被斩中"的表现 ——
            // 原本命中与未命中的画面完全一样，只有一声 PLAYER_ATTACK_SWEEP。
            var inSlice = new java.util.ArrayList<>(
                    level.getEntities(player, sweep, DomeriteLongsword::isVerdictTarget));
            hits += strikeEach(player, inSlice, VerdictDamage.DASH_HURT, alreadyHit);
            for (Entity e : inSlice) {
                if (e instanceof LivingEntity living && living.isAlive()
                        && alreadyHit.contains(living.getUUID())) {
                    firstHit.add(living.getUUID());
                }
            }
        }
        // 只对"第一次吃到这一记突进"的目标补表现，避免沿途采样把它刷成一片白
        spawnDashImpacts(level, player, look, firstHit);

        grantVictory(player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.25f);
        stack.hurtAndBreak(DASH_DURABILITY, player,
                e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        // ── 表现：用现有资产，零新着色器 ──
        //  拖尾 = 一串已有的白色发光剑气，沿突进路径铺开、逐级缩小。
        //  刻意不给它做新着色器：突进是高频动作，每一发都跑一套新特效管线是纯浪费；
        //  而这个资产本来就是这个模组的"剑气"语言。
        spawnDashTrail(level, player, look, distance, hits);

        // 命中音调随命中数升高：玩家不用看屏幕就知道这一冲扫到了几个人
        if (hits > 0) {
            VerdictFeedback.playSound(level, player.position(),
                    SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, VerdictFeedback.hitPitch(hits));
        }

        // ── 相机：突进是"速度"动作，这一招的轻重全在镜头上 ──
        //  匀速平移之所以廉价，一半原因是镜头在整个过程中纹丝不动。
        //  FOV 撑开 + 一次轻抖，位移立刻变成"被甩出去"。
        float w = VerdictFeedback.weightOf(Math.max(1, hits));
        VerdictFeedback.fovKick(player, 3.4f + 2.0f * w);
        VerdictFeedback.shakeNear(player, player.position().add(look.scale(distance * 0.5)),
                0.35f + 0.35f * w);

        // 高空突进落点补一圈冲击波涟漪：既是速度感的收尾，
        // 也顺手把"你在很高的地方"这件事反馈给玩家（高度是这把剑的资源）。
        if (player.getY() > HIGH_ALTITUDE_Y) {
            // 速度 3 格/秒 与 ShockwaveEntity 的默认值一致；它有 DEF_SPEED 但这个常量是 private，
            // 而 spawn(...) 的形参本来就是对外开放的调参入口，所以这里直接给值。
            ShockwaveEntity.spawn(level, player.position(),
                    3.0f, HeightFactor.dashDistanceBlocks(player) * 0.7f,
                    24, 0.75f);
        }

        // ── 空放便宜、命中昂贵 ──
        //  突进同时承担"起手"和"移动"两种用途（贴地连按能升空），而走位、跨沟、
        //  抢先手的场合本来就没有敌人可打。空放照付全额冷却会让位移变成扣血，
        //  于是"越打越高"这条循环的发动机就熄火了。详见 VerdictCooldown.finishMissed。
        if (hits > 0) {
            VerdictCooldown.finish(player);
        } else {
            VerdictCooldown.finishMissed(player);
            // 空放便宜是设计，但玩家必须<b>知道</b>它是空的 ——
            // 否则"这一冲没打到人却也没怎么进冷却"会被误读成冷却坏了。
            if (!level.isClientSide()) {
                player.displayClientMessage(Component
                        .translatable("item.hall.domerite_longsword.missed")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
    }

    /**
     * 在每个被这一记突进斩中的目标身上补一次命中表现。
     *
     * <p>内容刻意压得很省：一点粒子 + 一道短暂的空间涟漪。
     * 突进可以一次扫到十几个目标，这里每多一个目标就是实打实的渲染开销，
     * 所以<b>不给每个目标生成剑气实体</b>（那是每目标一到两个实体）。</p>
     *
     * <p>落点用"玩家 → 目标"连线上靠近目标的一点，而不是目标正中心：
     * 斩击看起来应该是从自己身上递出去的，而不是在敌人身体里炸开。</p>
     */
    private static void spawnDashImpacts(Level level, Player player, Vec3 look,
                                         java.util.Set<java.util.UUID> targets) {
        if (targets.isEmpty()) return;
        var area = player.getBoundingBox().inflate(
                HeightFactor.dashDistanceBlocks(player) + 8.0);
        int shown = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> targets.contains(e.getUUID()))) {
            if (shown++ >= 6) break;                     // 上限：群怪场景不做 N 份表现
            Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
            Vec3 fromTarget = at.subtract(player.position()).normalize();
            Vec3 p = at.subtract(fromTarget.scale(0.9));

            VerdictFeedback.burst(level, p, ParticleTypes.CRIT, 6, 0.18);
            VerdictFeedback.burst(level, p, ParticleTypes.END_ROD, 4, 0.10);
            // 一道小而短的空间涟漪：它本来就是这个项目的"斩击"视觉语言
            ShockwaveEntity.spawn(level, p, 2.2f, 1.9f, 14, 0.55f);
        }
    }

    /**
     * 沿突进方向铺一串剑气作为拖尾。
     *
     * <h3>密度是这条拖尾成不成立的关键</h3>
     * <p>原本固定 <b>3 道</b>：12 格的距离上只放 3 个点，间距 3 格，
     * 而突进持续 0.6 秒 —— 肉眼读到的就是"三个孤立的箭头飞过去了"，
     * 完全连不成一条轨迹。<b>拖尾的第一要义是连续</b>，密度不够时
     * 加多少特效都救不回来。</p>
     *
     * <p>现在按距离算道数（每 {@value #TRAIL_SPACING} 格一道），
     * 并且让相邻两道略微交叠（后续道比前一道更长），于是它们读作
     * "一道被撕开的轨迹"而不是一串整齐的箭头。</p>
     *
     * @param hits 本次命中数；命中时拖尾整体放大一档，作为"打穿了"的区别
     */
    private static void spawnDashTrail(Level level, Player player, Vec3 look,
                                       float distance, int hits) {
        int steps = Math.max(4, (int) Math.ceil(distance / TRAIL_SPACING));
        float boost = hits > 0 ? 1.18f : 1.0f;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) (steps + 1);
            Vec3 p = player.position().add(look.scale(distance * t)).add(0, 1.0, 0);
            // 越靠后的越大、活越久：形成"从自己身上甩出去"的方向感
            float scale = (0.40f + 0.32f * t) * boost;
            // 存活时间给足（12~22 tick）：拖尾要在突进<b>结束之后</b>还留一会儿，
            // 否则一次 12 tick 的位移配 10 tick 的剑光，最后两格是空的。
            SwordAuraEntity.spawn(level, p, scale, 0.85f,
                    SwordAuraEntity.APOSTLE_HEIGHT * (0.50f + 0.12f * t), 0.22f,
                    12 + Math.round(10 * t), 1.8f);
        }
    }

    /** 拖尾剑气的间距（格）。2.2 格在 12 格突进上给出 6 道，肉眼刚好连成一条线。 */
    private static final float TRAIL_SPACING = 2.2f;

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

        VerdictFieldEntity field = VerdictFieldEntity.spawn(
                level, player, radius, VerdictFieldEntity.DEFAULT_MAX_AGE);

        // ── 展开表现：只留"这片地从此归裁决管"这一件事 ──
        //
        //  这里原本沿边界落下 7 道立柱剑。撤掉了，因为它读起来是"程序生成的
        //  一圈装饰"：等角度分布 + 同一半径 + 同一高度 + 落地时间只差 1 tick；
        //  而地面光纹本身已经有一圈边界环和六分辐条 —— 再加一圈等距立柱，
        //  信息重复，还把地面光纹盖住了。
        //
        //  它原本要解决的问题（"领域就是一个突然出现的圆盘"）现在由别的东西接手：
        //   · 光纹自己的淡入（FADE_IN_TICKS = 6）与整片呼吸（shader 的 ⑥ 段）；
        //   · 每次真正出剑时的落剑 —— 那才是"剑阵"该出现的地方，而且它每次
        //     都对应一次真实伤害，不是布景。
        //
        //  展开因此只留一次从中心向外扩开的冲击环：把"范围被划出来了"说清楚，
        //  然后把画面交给光纹。
        Vec3 center = field != null
                ? new Vec3(field.getX(), field.getY() + 0.05, field.getZ())
                : player.position().add(0, 0.05, 0);
        VerdictFeedback.impactRing(level, center,
                Math.max(3.0f, radius * 1.1f), 5.5f, 0.85f);

        // 领域是三个技能里最"仪式性"的一招：展开的那一瞬给一次下沉式的镜头回应，
        // 与随后 10 秒的持续脉冲区分开（持续期间不再抖，否则会一直晃）。
        VerdictFeedback.shakeNear(player, player.position(), 0.45f);
        VerdictFeedback.fovKick(player, -1.6f);
        VerdictFeedback.burst(level, player.position().add(0, 0.2, 0),
                ParticleTypes.END_ROD, 20, 0.12);

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
     * @return 本次真正造成伤害的目标数（用于反馈强度：命中越多，音越高、抖得越重）
     */
    private static int strikeAllIn(Level level, Player player, AABB box, float hurt,
                                   @Nullable java.util.Set<java.util.UUID> alreadyHit) {
        List<Entity> hits = new java.util.ArrayList<>(
                level.getEntities(player, box, DomeriteLongsword::isVerdictTarget));
        return strikeEach(player, hits, hurt, alreadyHit);
    }

    /** 单次判定（不需要去重的场景）。 */
    private static int strikeAllIn(Level level, Player player, AABB box, float hurt) {
        return strikeAllIn(level, player, box, hurt, null);
    }

    /**
     * 对一份已经取好的实体列表逐个结算。
     *
     * <p>{@code strikeEach} 内部会<b>拷一份再遍历</b>：伤害可能把目标打死，
     * 死亡会让实体从 level 的 tick 列表里移除，直接在原列表上遍历 +
     * 死亡移除是 {@code ConcurrentModificationException} 的经典来源。</p>
     *
     * <p>每个真正吃到伤害的目标都会走一次 {@link VerdictFeedback#landed}：
     * 那是"打实了"的视觉重量来源（目标闪红 + 动作被压一帧），
     * 也是三个技能唯一的共同反馈通道。</p>
     */
    private static int strikeEach(Player player, List<Entity> hits, float hurt,
                                  @Nullable java.util.Set<java.util.UUID> alreadyHit) {
        List<Entity> safe = new java.util.ArrayList<>(hits);
        var source = player.damageSources().playerAttack(player);
        int landed = 0;
        for (Entity e : safe) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (alreadyHit != null && !alreadyHit.add(living.getUUID())) continue;   // 已打过
            if (VerdictDamage.strike(living, source, hurt, VerdictDamage.BYPASS_PART, player)) {
                VerdictFeedback.landed(living, VerdictFeedback.weightOf(safe.size()));
                landed++;
            }
        }
        return landed;
    }
}
