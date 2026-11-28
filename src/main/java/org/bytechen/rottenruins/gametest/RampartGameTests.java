package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological.RampartEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.bernie.geckolib.core.animatable.model.CoreBakedGeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.event.data.SoundKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.core.keyframe.event.data.CustomInstructionKeyframeData;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@GameTestHolder("rotten_ruins_rampart_tests")
@PrefixGameTestTemplate(false)
public final class RampartGameTests {
    @GameTest(template = "empty", batch = "rampart")
    public static void attackReturnsToIdleThroughControllerTransition(GameTestHelper helper) throws Exception {
        JsonObject animations;
        try (var stream = RampartGameTests.class.getResourceAsStream("/assets/rotten_ruins/animations/rampart.animation.json");
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        }
        var body = animations.getAsJsonObject("idle_body").getAsJsonObject("bones");
        var action = animations.getAsJsonObject("idle_action").getAsJsonObject("bones");
        var original = animations.getAsJsonObject("idle").getAsJsonObject("bones");
        for (String bone : original.keySet()) {
            helper.assertTrue(body.has(bone) != action.has(bone), "Each idle bone must have exactly one controller owner: " + bone);
            helper.assertTrue(original.get(bone).equals(body.has(bone) ? body.get(bone) : action.get(bone)), "Splitting idle must preserve its keyframes: " + bone);
        }
        for (String attack : new String[]{"attack", "attack2"}) {
            for (String bone : animations.getAsJsonObject(attack).getAsJsonObject("bones").keySet()) {
                helper.assertTrue(action.has(bone) && !body.has(bone), "Attack bones must return to idle through the same controller: " + bone);
            }
        }
        RampartEntity entity = rampart(helper);
        AnimatableManager<RampartEntity> manager = entity.getAnimatableInstanceCache().getManagerForId(entity.getId());
        var controller = manager.getAnimationControllers().get("attack_controller");
        var state = new AnimationState<>(entity, 0, 0, 0, false).withController(controller);
        var model = new ControllerTestModel(animations);
        var snapshots = new HashMap<String, software.bernie.geckolib.core.state.BoneSnapshot>();
        int tick = 0;
        for (; tick < 10; tick++) controller.process(model, state, Map.of(), snapshots, tick, false);
        // 驱动真实 GeckoLib 控制器状态机；同时覆盖同一攻击连续触发，避免恢复后无法再次出手。
        for (String attack : new String[]{"attack", "attack", "attack2", "attack2"}) {
            helper.assertTrue(controller.tryTriggerAnimation(attack), "Attack must remain triggerable after recovery");
            boolean sawAttack = false;
            int recoveryFrames = 0;
            for (int frame = 0; frame < 70; frame++, tick++) {
                controller.process(model, state, Map.of(), snapshots, tick, false);
                var current = controller.getCurrentAnimation();
                if (current != null && current.animation().name().equals(attack)) sawAttack = true;
                if (sawAttack && current != null && current.animation().name().equals("idle_action")
                        && controller.getAnimationState() == AnimationController.State.TRANSITIONING) recoveryFrames++;
            }
            helper.assertTrue(sawAttack && recoveryFrames >= 6, "Attack must blend back to idle over six ticks, rather than stopping its layer");
            helper.assertTrue(controller.getCurrentAnimation().animation().name().equals("idle_action"), "Recovery must settle into looping idle");
        }
        helper.succeed();
    }

    /** 使用资源内真实时长，省略骨骼网格，以便在专用服务器验证 GeckoLib 动画交接。 */
    private static final class ControllerTestModel implements CoreGeoModel<RampartEntity> {
        private final JsonObject animations;
        private final AnimationProcessor<RampartEntity> processor = new AnimationProcessor<>(this);
        private ControllerTestModel(JsonObject animations) { this.animations = animations; }
        @Override public CoreBakedGeoModel getBakedGeoModel(String name) { return null; }
        @Override public AnimationProcessor<RampartEntity> getAnimationProcessor() { return processor; }
        @Override public Animation getAnimation(RampartEntity entity, String name) {
            return new Animation(name, animations.getAsJsonObject(name).get("animation_length").getAsDouble() * 20,
                    Animation.LoopType.PLAY_ONCE, new BoneAnimation[0],
                    new Animation.Keyframes(new SoundKeyframeData[0], new ParticleKeyframeData[0], new CustomInstructionKeyframeData[0]));
        }
        @Override public void handleAnimations(RampartEntity entity, long id, AnimationState<RampartEntity> state) { }
    }

    @GameTest(template = "empty", batch = "rampart")
    public static void shieldBlocksFrontAndReducesRearByNinetyPercent(GameTestHelper helper) {
        TestRampart rampart = rampart(helper);
        var player = helper.makeMockPlayer();
        player.setPos(rampart.getX(), rampart.getY(), rampart.getZ() + 4);
        float before = rampart.getHealth();
        helper.assertTrue(!rampart.hurt(helper.getLevel().damageSources().playerAttack(player), 20), "Front attack must be blocked");
        helper.assertTrue(rampart.getHealth() == before, "Blocked attacks cannot remove health");
        player.setPos(rampart.getX(), rampart.getY(), rampart.getZ() - 4);
        helper.assertTrue(rampart.hurt(helper.getLevel().damageSources().playerAttack(player), 20), "Rear attack must connect");
        helper.assertTrue(Math.abs(before - rampart.getHealth() - 2) < 0.001, "Rear damage must be reduced by exactly 90% before armor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "rampart")
    public static void projectilesUseImpactSideRatherThanShooterSide(GameTestHelper helper) {
        TestRampart rampart = rampart(helper);
        var player = helper.makeMockPlayer();
        player.setPos(rampart.getX(), rampart.getY(), rampart.getZ() - 8);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(rampart.getX(), rampart.getY(), rampart.getZ() + 2);
        helper.assertTrue(!rampart.hurt(helper.getLevel().damageSources().arrow(arrow, player), 20), "An arrow arriving from the front must be blocked even if its owner is behind");
        arrow.setPos(rampart.getX(), rampart.getY(), rampart.getZ() - 2);
        player.setPos(rampart.getX(), rampart.getY(), rampart.getZ() + 8);
        float before = rampart.getHealth();
        helper.assertTrue(rampart.hurt(helper.getLevel().damageSources().arrow(arrow, player), 20), "An arrow arriving from behind must connect");
        helper.assertTrue(Math.abs(before - rampart.getHealth() - 2) < 0.001, "Rear arrows retain only 10% damage");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "rampart")
    public static void axeBreaksShieldAndShieldRecovers(GameTestHelper helper) {
        TestRampart rampart = rampart(helper);
        var player = helper.makeMockPlayer();
        player.setPos(rampart.getX(), rampart.getY(), rampart.getZ() + 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        float before = rampart.getHealth();
        rampart.hurt(helper.getLevel().damageSources().playerAttack(player), 20);
        helper.assertTrue(!rampart.isShielding() && Math.abs(before - rampart.getHealth() - 20) < 0.001, "Axe breaks the shield and deals full damage");
        for (int i = 0; i < SHIELD_RECOVERY - 1; i++) rampart.tick();
        helper.assertTrue(!rampart.isShielding(), "Shield must stay broken for the full cooldown");
        rampart.tick();
        helper.assertTrue(rampart.isShielding(), "Shield recovers after five seconds");
        helper.succeed();
    }

    private static final int SHIELD_RECOVERY = 100;

    @GameTest(template = "empty", batch = "rampart")
    public static void attacksHitOnceAtAnimationTimeAndKeepTheirDirection(GameTestHelper helper) {
        for (boolean slam : new boolean[]{true, false}) {
            TestRampart rampart = rampart(helper);
            var cow = EntityType.COW.create(helper.getLevel());
            cow.setPos(rampart.getX(), rampart.getY(), rampart.getZ() + 3);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            cow.setHealth(200);
            helper.getLevel().addFreshEntity(cow);
            long seed = 0;
            while (RandomSource.create(seed).nextBoolean() != slam) seed++;
            rampart.getRandom().setSeed(seed);
            rampart.begin(cow);
            helper.assertTrue(!rampart.begin(cow), "An active attack cannot be restarted");
            // 6 tick 切换过渡后，仍在动画内部的 1.46 秒 / 1 秒命中。
            int hitTick = 6 + (slam ? 29 : 20);
            for (int i = 1; i < hitTick; i++) rampart.tick();
            helper.assertTrue(cow.getHealth() == 200, "Windup must not deal early damage");
            rampart.tick();
            float healthAfterHit = cow.getHealth();
            helper.assertTrue(healthAfterHit < 200, "Attack must land on its specified animation tick");
            cow.setPos(rampart.getX(), rampart.getY(), rampart.getZ() - 3);
            for (int i = hitTick; i < 6 + (slam ? 35 : 34); i++) {
                rampart.tick();
                helper.assertTrue(Math.abs(rampart.getYRot()) < 0.001, "Attack must keep its initial yaw when the target circles behind");
            }
            helper.assertTrue(cow.getHealth() == healthAfterHit && !rampart.isAttacking(), "One animation deals damage once and then finishes");
            cow.discard();
        }
        helper.succeed();
    }

    private static TestRampart rampart(GameTestHelper helper) {
        TestRampart entity = new TestRampart(helper.getLevel());
        BlockPos position = helper.absolutePos(new BlockPos(3, 3, 3));
        entity.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        entity.setNoGravity(true);
        entity.getAttribute(Attributes.ARMOR).setBaseValue(0);
        entity.setYRot(0);
        entity.setYBodyRot(0);
        return entity;
    }

    private static final class TestRampart extends RampartEntity {
        private TestRampart(Level level) { super(EntityTypeRegistry.RAMPART.get(), level, e -> {}); }
        @Override public void aiStep() { }
        @Override protected void tickChase() { }
        private boolean begin(LivingEntity target) { return getAttack(target); }
    }
}
