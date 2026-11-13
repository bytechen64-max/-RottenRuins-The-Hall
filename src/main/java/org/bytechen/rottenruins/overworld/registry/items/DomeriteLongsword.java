package org.bytechen.rottenruins.overworld.registry.items;

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
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;
import org.bytechen.rottenruins.api.ICustomOutline;
import org.bytechen.rottenruins.api.ItemFxProvider;

import java.util.List;
import org.bytechen.rottenruins.overworld.registry.effect.VerdictEffect;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.ShockwaveEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictBeamEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictFieldEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictSwordDropEntity;
import org.bytechen.rottenruins.overworld.registry.items.verdict.HeightFactor;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictCooldown;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDamage;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDash;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDebug;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictTuning;
import org.bytechen.rottenruins.utils.DomeriteStatsHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 天穹裁决（{@code domerite_longsword}）—— 单支线次毕业级长剑。
 * <p>伤害统一走 {@link VerdictDamage} 的「原版 hurt + 原版 setHealth」两道：平砍与突进 25+5，光柱两波 24+5 与 12+4，领域剑气 18+5；属性面板只负责<b>显示</b>与兼容兜底。
 * <p>三个技能共用同一个 {@link VerdictCooldown} 冷却池：短按 &lt; 8 tick 突进，蓄满松手光柱，潜行 + 按住 14 tick 领域（三态全靠原版使用机制区分，不需要自定义网络包）。
 * <p><b>必须让 {@link #useOnRelease} 返回 true</b>：否则按满时长会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}，蓄满松手的光柱放不出来。
 */
public class DomeriteLongsword extends SwordItem implements ICustomOutline {

    /** 攻击距离加成（格）。{@code forge:entity_reach} 默认 3.0、上限 1024，所以 100 是合法普通值。 */
    public static final double ATTACK_REACH_BONUS = 5;

    /**
     * 攻击距离修饰符的 UUID：用固定字面量而不是随机生成 —— 同一修饰符在不同存档、不同端之间必须同名同 id，否则会被当成两个修饰符叠加。
     */
    private static final UUID REACH_MODIFIER_UUID =
            UUID.nameUUIDFromBytes(
                    (CrimsonVow.class.getName() + ":reach")
                            .getBytes(StandardCharsets.UTF_8));

    /**
     * 右键"使用"的总时长（tick），同时决定短按窗口与蓄力上限：30 tick = 1.5 秒，短按窗口 8 tick = 0.4 秒。窗口一路放宽，
     * 因为 {@code held} 会叠加按住时长 + 双端 tick 相位差 + 释放包往返延迟（<b>有抖动</b>），窗口过窄会把"点一下突进"误判成蓄力。
     */
    public static final int USE_DURATION = 30;

    /** 短按 / 蓄力的分界：按住不足 {@value #SHORT_PRESS_TICKS} tick（0.4 秒）算短按；必须<b>严格小于</b> {@link #FIELD_HOLD_TICKS}，否则领域永远不可达。 */
    public static final int SHORT_PRESS_TICKS = 8;

    /** 潜行按住多久放出领域（tick）= 0.7 秒，同时是"蓄光柱"这一段的上界：0~8 突进，8~14 光柱，≥14 领域（需潜行）。 */
    public static final int FIELD_HOLD_TICKS = 14;

    /** 光柱最短长度（格）。抬头不足这个角度时视为没瞄准，本次出招作废。 */
    public static final float BEAM_MIN_LENGTH = 3.0f;

    private static final String BEAM_UUID_KEY = "VerdictBeamUUID";

    /** 高空阈值：Y 超过它时，突进落点会额外补一圈冲击波涟漪。 */
    public static final double HIGH_ALTITUDE_Y = 150.0;

    /** 突进持续（tick）。0.6 秒 —— 原版三叉戟激流是 0.9 秒，这个更快更"轻"。 */
    private static final int DASH_TICKS = 12;
    /**
     * 突进用的速度衰减系数，<b>必须是空中值</b>：地面是 {@code 方块摩擦 × 0.91}（草方块 = 0.546），用错那个同样一次突进在地面只能滑 2 格、空中能滑 12 格。
     */
    private static final double DASH_DRAG = 0.91;
    /** 突进路径判定的采样步长（格）。每 ~2 格取一个采样盒，兼顾覆盖与开销。 */
    private static final double DASH_SAMPLE_STEP = 2.0;

    /** 贴地突进时额外给的向上初速，让"连按升空"成立。 */
    private static final double DASH_LIFT = 0.42;
    private static final double DASH_HIT_RADIUS = 0.9;
    private static final int DASH_DURABILITY = 2;

    public static final int BUFF_TICKS = 60;
    /** 每层 buff 给的光柱半径加成。{@code VerdictEffect} 要用，必须 public。 */
    public static final float BEAM_RADIUS_PER_LEVEL = 0.04f;
    public static final float FIELD_RATE_PER_LEVEL = 0.05f;
    public static final int MAX_BUFF_LEVEL = 5;

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

    /** 必须用 {@code TRANSLUCENT}，不能用默认的 {@code ADDITIVE}：加法混合下黑色等于"加 0"，黑的那半截会整个消失。 */
    @Override
    public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    /** 描边向外扩展的宽度（屏幕像素）。可以在配置里用 outlineWidthScale 整体缩放。 */
    @Override
    public float outlinePixelWidth() {
        return 4.5f;
    }

    @Override
    public boolean outlineEnabled(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.HEAD;
    }

    /** 面板基准伤害：只是<b>显示值</b>与兼容兜底，实际伤害由 {@link VerdictDamage} 固定结算成 25 + 5。 */
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
            dynamic.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                    REACH_MODIFIER_UUID, "reached", ATTACK_REACH_BONUS,
                    AttributeModifier.Operation.ADDITION));
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

    /** 借 {@code inventoryTick} 推进"突进中"状态：它只在物品确实在玩家身上时触发，丢剑后不会留下还在推人的幽灵状态。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide()) return;
        if (entity instanceof Player player) VerdictDash.tick(player);
    }

    /**
     * 普通攻击走裁决伤害管线。<b>刻意不调用 {@code super.hurtEnemy()}</b>：父类会再打一次 {@code getDamage()}（本类随 Y 缩放，最高 16），叠加就变成 41。
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return super.hurtEnemy(stack, target, attacker);
        }

        boolean struck = VerdictDamage.strike(target,
                player.damageSources().playerAttack(player), player);

        // 击退：父类那部分被关掉了，这里补回，否则长剑失去"重击"的重量感
        if (target.isAlive()) {
            target.knockback(0.4f,
                    Math.sin(player.getYRot() * (float) (Math.PI / 180.0)),
                    -Math.cos(player.getYRot() * (float) (Math.PI / 180.0)));
        }

        if (struck) grantVictory(player);

        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
    }

    /** 右键 → 进入蓄力状态。客户端与服务端<b>都</b>返回 {@code consume}：否则手臂不会摆，客户端也不进"使用中"状态、蓄力进度拿不到。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!VerdictTuning.skillsEnabled()) {
            return super.use(level, player, hand);
        }

        if (VerdictCooldown.onCooldown(player)) {
            // 原版冷却被 `MultiPlayerGameMode#useItem` 当作输入闸门，可能被 /clear、换物品或客户端重连丢失 —— 每次尝试重申一次就能自愈。
            if (!level.isClientSide()) {
                VerdictCooldown.syncVanilla(player);
            }
            if (level.isClientSide()) {
                int remain = VerdictCooldown.remaining(player);
                player.displayClientMessage(Component
                        .translatable("item.rotten_ruins.domerite_longsword.cooldown",
                                String.format("%.1f", remain / 20.0f))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 必须返回 true —— 返回 false 时"按满时长"会走 {@code completeUsingItem()} 而跳过 {@code releaseUsing()}，蓄满松手什么都不放。 */
    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    /** 持续举剑：原版 SPEAR 最接近"举剑蓄力"的姿势。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    /**
     * 每 tick 的蓄力表现。粒子走 {@code level.addParticle} 而不是 {@code sendParticles}：蓄力粒子是<b>纯观感</b>，而它每 tick 都在跑，
     * 走服务端广播时一个玩家按住 1.5 秒就是几十个包。
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(living instanceof Player player)) return;

        int held = USE_DURATION - remaining;

        // 领域：必须先 stopUsingItem() 再出招（它内部会调 releaseUsingItem()，顺序反过来会双重触发）；
        // 放在客户端条件之外是刻意的：客户端也要退出使用态，否则进度条与举剑姿势会一直挂着。
        if (player.isShiftKeyDown() && held >= FIELD_HOLD_TICKS) {
            player.stopUsingItem();
            boolean ok = false;
            if (!level.isClientSide()) {
                boolean enabled = VerdictTuning.fieldEnabled();
                ok = enabled && castField(level, player);
                // 诊断：领域"完全不可见"有四种可能（开关 / 冷却锁 / 生成失败 / 渲染），这一行区分前三种。
                VerdictDebug.log("field 触发 held=%d enabled=%b ok=%b entityCount=%d",
                        held, enabled, ok, countFields(level, player));
            }
            if (ok) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.35f);
            }
            return;
        }

        if (level.isClientSide()) {
            chargeClientParticles(player, held);
            // 阈值提示音放在客户端：这里知道 held 的确切值，服务端版本会被网络相位差搞成偶尔漏发/重复。
            if (held == SHORT_PRESS_TICKS || held == FIELD_HOLD_TICKS) {
                float pitch = held == SHORT_PRESS_TICKS ? 1.05f : 0.85f;
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                        0.45f, pitch);
            }
        }

        if (!level.isClientSide() && held > 1 && held % 4 == 0) {
            float pitch = 0.85f + 0.35f * (held / (float) USE_DURATION);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    0.35f, pitch);
        }
    }

    /** 蓄力期的粒子：脚下先出现一圈旋转并<b>向内收拢</b>的光点，过阈值后再叠一层下落光尘（刻意"收拢"而非"扩散"：蓄力语义是力量在聚集）。 */
    private static void chargeClientParticles(Player player, int held) {
        if (held <= 0 || held > USE_DURATION) return;
        float t = Mth.clamp(held / (float) USE_DURATION, 0f, 1f);

        double radius = Mth.lerp(t, 1.45, 0.55);
        int points = held % 2 == 0 ? 3 : 2;              // 奇数 tick 少一半，省开销
        double base = player.getY() + 0.15;
        for (int i = 0; i < points; i++) {
            double a = (held * 0.35 + i * (Math.PI * 2.0 / points));
            double r = radius * (0.85 + 0.3 * Math.sin(held * 1.7 + i * 2.1));
            player.level().addParticle(ParticleTypes.END_ROD,
                    player.getX() + Math.cos(a) * r, base, player.getZ() + Math.sin(a) * r,
                    -Math.cos(a) * 0.02, 0.004, -Math.sin(a) * 0.02);
        }

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
     * 松开右键 → 按 {@code held = USE_DURATION - remaining} 分流：{@code held < }{@link #SHORT_PRESS_TICKS}（不足 0.4 秒）走突进，否则走光柱；
     * {@code remaining} 取自服务端自己的 {@code useItemRemaining}，第三态（领域）在 {@code onUseTick} 里按住够时长自动触发。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide() || !(living instanceof Player player)) return;

        int held = USE_DURATION - remaining;
        boolean dash = held < SHORT_PRESS_TICKS;

        // 诊断：把分流判定依赖的每一个量都落盘（"右键没反应"的几种原因症状完全一样，看代码分不出来）。
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

    /**
     * 在玩家脚下生成一根竖直光柱。<b>伤害已经搬进 {@link VerdictBeamEntity}</b>（这一招是两波的：落柱那一瞬 + 扫描球下落到 45% 高度时），
     * 本方法只负责校验瞄准、结算冷却、立起实体、给一次起手反馈；低头不到 {@link #BEAM_MIN_LENGTH} 视为没瞄准，出招<b>作废且不进冷却</b>。
     */
    private static void castBeam(Level level, Player player, ItemStack stack) {
        // 视觉长度只影响"看得见多高"，判定另有上限（见 HeightFactor.BEAM_HIT_MAX_HEIGHT）。
        float length = HeightFactor.beamLengthFromPitch(player.getXRot());
        if (length < BEAM_MIN_LENGTH) {
            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 0.7f);
                player.displayClientMessage(Component
                        .translatable("item.rotten_ruins.domerite_longsword.no_aim")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;                                      // 没瞄准：不消耗冷却
        }

        if (!VerdictCooldown.begin(player)) return;

        float radius = HeightFactor.beamRadiusBlocks(player)
                * VerdictEffect.beamRadiusScale(player);
        double cx = player.getX();
        double cy = player.getY();                       // 脚底
        double cz = player.getZ();

        // 视觉：先让上一根光柱收束，再立新的 —— 覆盖而非 discard：重叠在加法混合下会叠成刺眼的白。
        fadePreviousBeam(level, player);

        // 扩散终点也吃"裁决层数"但不吃高度系数：高度已通过起始半径生效，再乘一次会二次放大到 32 格以上。
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

    private static void rememberBeam(Player player, VerdictBeamEntity beam) {
        if (beam == null) return;
        player.getPersistentData().putUUID(BEAM_UUID_KEY, beam.getUUID());
    }

    /** 把上一根光柱加快收束：不直接 {@code discard()}，而是把剩余生命压到淡出时长以内，让它自己走完既有的淡出曲线。 */
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

    private static int countFields(Level level, Player player) {
        return level.getEntitiesOfClass(VerdictFieldEntity.class,
                player.getBoundingBox().inflate(128.0)).size();
    }

    /**
     * 朝视线方向突进，路径上的敌人各吃一次裁决伤害。距离吃高度（{@link HeightFactor#dashDistanceBlocks}）但指数压到四次方，避免高空甩进虚空；
     * 贴地突进额外给向上初速：这是三个技能里<b>唯一能把自己推高</b>的一招，也是"越打越高、越高覆盖越大"循环的发动机。
     */
    private static void castDash(Level level, Player player, ItemStack stack) {
        if (!VerdictCooldown.begin(player)) {
            VerdictDebug.log("  castDash 被冷却池拒绝（begin 返回 false）");
            return;
        }

        float distance = HeightFactor.dashDistanceBlocks(player)
                * VerdictTuning.dashDistanceScale();
        Vec3 look = player.getLookAngle();

        // 位移：把"目标距离"反解成本 tick 该给的水平初速。原版地面摩擦是 0.546/tick（方块 0.6 × 0.91）不是空中的 0.91：
        // 同样初速在空中滑约 11.7 格、地面只滑约 2.0 格，所以"给一个速度"和"突进 12 格"是两回事；反解刻意用<b>空中</b>衰减（下面会抬起来，见 DASH_LIFT）。
        double speed = dashSpeedForDistance(distance);

        double vx = look.x * speed;
        double vz = look.z * speed;
        // 贴地时强制给向上初速：让衰减走空中的 0.91（距离才准），顺带就是"连按升空"的发动机；取 max 而不是覆盖，抬头突进才不会把人往下按。
        double vy = Math.max(look.y * speed * 0.35, player.onGround() ? DASH_LIFT : 0.0);

        player.setDeltaMovement(vx, vy, vz);
        player.hurtMarked = true;                        // 强制把速度同步给客户端
        player.hasImpulse = true;

        // 关键：单次给速度会被地面摩擦（0.546/tick）和客户端预测吃掉，所以登记一次"突进中"状态，由 inventoryTick 每 tick 重申速度。
        VerdictDash.start(player, look.x, look.z, speed);

        VerdictDebug.log("  castDash distance=%.2f speed=%.3f vel=(%.3f,%.3f,%.3f) onGround=%b y=%.1f",
                distance, speed, vx, vy, vz, player.onGround(), player.getY());

        // 沿途判定：早期实现只在释放那一瞬膨胀一个盒子、路径中段的怪全漏掉；正确做法是扫掠体。这里离散取样近似（每 ~2 格一个采样点），
        // 且用 Mth.ceil 而不是固定段数，让采样密度与距离无关（突进距离随 Y 变化 12~15.6 格，固定段数高空会漏怪）。
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
        spawnDashImpacts(level, player, look, firstHit);

        grantVictory(player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.25f);
        stack.hurtAndBreak(DASH_DURABILITY, player,
                e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        // 表现：拖尾复用已有的白色发光剑气，刻意不做新着色器（突进是高频动作，每发都跑一套新特效管线是纯浪费）。
        spawnDashTrail(level, player, look, distance, hits);

        if (hits > 0) {
            VerdictFeedback.playSound(level, player.position(),
                    SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, VerdictFeedback.hitPitch(hits));
        }

        // 相机：突进是"速度"动作，FOV 撑开 + 一次轻抖，位移才不像廉价平移。
        float w = VerdictFeedback.weightOf(Math.max(1, hits));
        VerdictFeedback.fovKick(player, 3.4f + 2.0f * w);
        VerdictFeedback.shakeNear(player, player.position().add(look.scale(distance * 0.5)),
                0.35f + 0.35f * w);

        if (player.getY() > HIGH_ALTITUDE_Y) {
            // 速度 3 格/秒 与 ShockwaveEntity 的 DEF_SPEED 一致，但那个常量是 private，spawn(...) 的形参本就是对外开放的调参入口。
            ShockwaveEntity.spawn(level, player.position(),
                    3.0f, HeightFactor.dashDistanceBlocks(player) * 0.7f,
                    24, 0.75f);
        }

        // 空放便宜、命中昂贵：突进同时承担"起手"和"移动"两种用途，空放照付全额冷却会让位移变成扣血、熄掉"越打越高"的循环。
        if (hits > 0) {
            VerdictCooldown.finish(player);
        } else {
            VerdictCooldown.finishMissed(player);
            // 空放便宜是设计，但玩家必须<b>知道</b>它是空的，否则"没打到人却也没怎么进冷却"会被误读成冷却坏了。
            if (!level.isClientSide()) {
                player.displayClientMessage(Component
                        .translatable("item.rotten_ruins.domerite_longsword.missed")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
    }

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
            ShockwaveEntity.spawn(level, p, 2.2f, 1.9f, 14, 0.55f);
        }
    }

    /** 沿突进方向铺一串剑气作为拖尾：原本固定 3 道、12 格上间距 3 格，读成"三个孤立的箭头" —— <b>拖尾的第一要义是连续</b>。 */
    private static void spawnDashTrail(Level level, Player player, Vec3 look,
                                       float distance, int hits) {
        int steps = Math.max(4, (int) Math.ceil(distance / TRAIL_SPACING));
        float boost = hits > 0 ? 1.18f : 1.0f;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) (steps + 1);
            Vec3 p = player.position().add(look.scale(distance * t)).add(0, 1.0, 0);
            float scale = (0.40f + 0.32f * t) * boost;
            // 存活时间给足（12~22 tick）：拖尾要在突进<b>结束之后</b>还留一会儿，否则最后两格是空的。
            SwordAuraEntity.spawn(level, p, scale, 0.85f,
                    SwordAuraEntity.APOSTLE_HEIGHT * (0.50f + 0.12f * t), 0.22f,
                    12 + Math.round(10 * t), 1.8f);
        }
    }

    /** 拖尾剑气的间距（格）。2.2 格在 12 格突进上给出 6 道，肉眼刚好连成一条线。 */
    private static final float TRAIL_SPACING = 2.2f;

    /**
     * 把一个目标水平距离反解成"本 tick 该给多少初速"：{@code v = distance · (1 - DRAG) / (1 - DRAG^n)}，DRAG 用 {@link #DASH_DRAG}（空中）。
     */
    private static double dashSpeedForDistance(double distance) {
        double decay = 1.0 - Math.pow(DASH_DRAG, DASH_TICKS);
        return distance * (1.0 - DASH_DRAG) / Math.max(decay, 1e-4);
    }

    /**
     * 在脚下展开裁决领域。<b>刻意不在"放下时立刻打一下"</b>：白送一次范围伤害会让它变成冷兵器版手雷，伤害完全由 {@link VerdictFieldEntity} 按 1.2 秒出剑节奏给。
     * @return 是否成功放出（用于决定要不要播音效）
     */
    private static boolean castField(Level level, Player player) {
        if (!VerdictCooldown.begin(player)) return false;

        ItemStack stack = player.getMainHandItem();
        float radius = HeightFactor.fieldRadiusBlocks(player) * VerdictTuning.fieldRadiusScale();

        VerdictFieldEntity field = VerdictFieldEntity.spawn(
                level, player, radius, VerdictFieldEntity.DEFAULT_MAX_AGE);

        // 展开表现只留一次向外扩开的冲击环：原本沿边界落 7 道立柱剑已撤掉（等角度 + 同半径 + 同高度读成程序生成的一圈装饰，且与地面光纹信息重复）。
        Vec3 center = field != null
                ? new Vec3(field.getX(), field.getY() + 0.05, field.getZ())
                : player.position().add(0, 0.05, 0);
        VerdictFeedback.impactRing(level, center,
                Math.max(3.0f, radius * 1.1f), 5.5f, 0.85f);

        VerdictFeedback.shakeNear(player, player.position(), 0.45f);
        VerdictFeedback.fovKick(player, -1.6f);
        VerdictFeedback.burst(level, player.position().add(0, 0.2, 0),
                ParticleTypes.END_ROD, 20, 0.12);

        grantVictory(player);
        stack.hurtAndBreak(4, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        VerdictCooldown.finish(player);
        return true;
    }

    /** 任意裁决系命中都会给自身叠一层「裁决」增益：每层光柱半径 +{@value #BEAM_RADIUS_PER_LEVEL}、领域出剑提速 +{@value #FIELD_RATE_PER_LEVEL}。 */
    private static void grantVictory(Player player) {
        MobEffectInstance cur = player.getEffect(VerdictEffect.INSTANCE);
        int level = cur == null ? 0 : cur.getAmplifier() + 1;
        if (level > MAX_BUFF_LEVEL) level = MAX_BUFF_LEVEL;

        player.addEffect(new MobEffectInstance(
                VerdictEffect.INSTANCE, BUFF_TICKS, level, false, false, true));
    }

    private static boolean isVerdictTarget(Entity e) {
        return e instanceof LivingEntity living && living.isAlive() && !living.isSpectator();
    }

    /**
     * 对盒内所有合法目标各打一次裁决伤害。
     * @param alreadyHit 已打过的目标：相邻采样盒重叠会让同一生物被取到两遍，不去重就会打出双倍伤害（null 表示不去重）
     */
    private static int strikeAllIn(Level level, Player player, AABB box, float hurt,
                                   @Nullable java.util.Set<java.util.UUID> alreadyHit) {
        List<Entity> hits = new java.util.ArrayList<>(
                level.getEntities(player, box, DomeriteLongsword::isVerdictTarget));
        return strikeEach(player, hits, hurt, alreadyHit);
    }

    private static int strikeAllIn(Level level, Player player, AABB box, float hurt) {
        return strikeAllIn(level, player, box, hurt, null);
    }

    /** 对一份已取好的实体列表逐个结算。<b>先拷一份再遍历</b>：伤害可能把目标打死，直接在原列表上遍历会 {@code ConcurrentModificationException}。 */
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
