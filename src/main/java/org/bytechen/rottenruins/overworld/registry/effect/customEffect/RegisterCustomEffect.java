package org.bytechen.rottenruins.overworld.registry.effect.customEffect;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.magic.effect.BarkArmorEffect;
import org.bytechen.rottenruins.content.magic.effect.FlameSlashEffect;
import org.bytechen.rottenruins.content.magic.effect.HolyWingsEffect;
import org.bytechen.rottenruins.content.magic.effect.ShieldEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects.ArcaneMarkEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects.CorrosionEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects.FrailtyEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.effects.LethargyEffect;
import org.bytechen.rottenruins.utils.ModUtils;

/**
 * <b>自定义效果的总注册入口</b> —— 对应原版 {@code RegisterEffect}，
 * 但这里注册的是本模组自己的效果系统（{@link CustomEffectRegistry}），
 * 不进 Forge 注册表、不需要图标贴图。
 *
 * <p>在 {@code HallMod} 构造期调用 {@link #init()} 一次即可（幂等）。</p>
 *
 * <h3>新增一个效果只要三步</h3>
 * <ol>
 *   <li>在 {@code customEffect/effects/} 下继承 {@link CustomEffectBase} 写行为；</li>
 *   <li>在这里加一个 {@code public static final} 常量并 {@link CustomEffectRegistry#register}；</li>
 *   <li>用 {@code CustomEffects.addEffect(entity, 常量, 时长, 层数)} 施加。</li>
 * </ol>
 *
 * <p><b>负面效果池</b>：{@link CustomEffectCategory#HARMFUL} 分类的效果会被
 * {@code ArcaneMarkHandler} 随机抽中、塞给带「奥数印记」的目标 —— 所以想扩充这个池子，
 * 只要分类是 HARMFUL 并在这里注册即可，不用改任何调用点。</p>
 */
public final class RegisterCustomEffect {

    /** 奥数印记：带印记的目标每次受伤 → 随机塞一个负面效果（行为在 ArcaneMarkHandler）。 */
    public static final ArcaneMarkEffect ARCANE_MARK =
            CustomEffectRegistry.register(new ArcaneMarkEffect(id("arcane_mark")));

    /** 腐蚀：护甲 -2/层。 */
    public static final CorrosionEffect CORROSION =
            CustomEffectRegistry.register(new CorrosionEffect(id("corrosion")));

    /** 迟滞：移速 -20%/层。 */
    public static final LethargyEffect LETHARGY =
            CustomEffectRegistry.register(new LethargyEffect(id("lethargy")));

    /** 衰竭：攻击力 -2/层。 */
    public static final FrailtyEffect FRAILTY =
            CustomEffectRegistry.register(new FrailtyEffect(id("frailty")));

    /** 护盾：抵挡固定次数伤害。 */
    public static final ShieldEffect SHIELD =
            CustomEffectRegistry.register(new ShieldEffect(id("shield")));

    /** 藤甲：免疫雷电/火焰之外的伤害。 */
    public static final BarkArmorEffect BARK_ARMOR =
            CustomEffectRegistry.register(new BarkArmorEffect(id("bark_armor")));

    /** 圣翼：鞘翅飞行 + 空中冲刺。 */
    public static final HolyWingsEffect HOLY_WINGS =
            CustomEffectRegistry.register(new HolyWingsEffect(id("holy_wings")));

    /** 焰斩：挥剑附带剑气。 */
    public static final FlameSlashEffect FLAME_SLASH =
            CustomEffectRegistry.register(new FlameSlashEffect(id("flame_slash")));

    /** 城墙重击附带的晕眩：生物 noai、玩家锁视角，详见 {@link org.bytechen.rottenruins.content.magic.effect.StunEffect}。 */
    public static final org.bytechen.rottenruins.content.magic.effect.StunEffect STUN =
            CustomEffectRegistry.register(new org.bytechen.rottenruins.content.magic.effect.StunEffect(id("stun")));

    /** 虚化：半透明 + 淡蓝白描边，详见 {@link org.bytechen.rottenruins.content.magic.effect.EtherealEffect}。 */
    public static final org.bytechen.rottenruins.content.magic.effect.EtherealEffect ETHEREAL =
            CustomEffectRegistry.register(new org.bytechen.rottenruins.content.magic.effect.EtherealEffect(id("ethereal")));

    private RegisterCustomEffect() {
    }

    /** 触发类加载 —— 常量初始化即完成注册，然后把注册表冻结。 */
    public static void init() {
        CustomEffectRegistry.freeze();
    }

    private static ResourceLocation id(String path) {
        return ModUtils.modLoc(path);
    }
}
