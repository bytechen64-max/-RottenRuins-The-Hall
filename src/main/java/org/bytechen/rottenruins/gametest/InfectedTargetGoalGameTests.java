package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;

@GameTestHolder(HallMod.MODID)
@PrefixGameTestTemplate(false)
public final class InfectedTargetGoalGameTests {
    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void movingTargetRefreshesAnUnfinishedPath(GameTestHelper helper) {
        var owner = new TestChaser(helper.getLevel());
        var target = helper.spawn(EntityType.COW, new BlockPos(3, 2, 1));
        owner.setPos(target.getX() - 10, target.getY(), target.getZ());
        owner.setTarget(target);
        owner.chase();
        var navigation = (RecordingNavigation) owner.getNavigation();
        helper.assertTrue(navigation.attempts == 1, "Start one pursuit path");
        target.setPos(target.getX() + 2, target.getY(), target.getZ());
        for (int i = 0; i < 30; i++) owner.chase();
        helper.assertTrue(navigation.attempts == 2, "Refresh the moving target's unfinished path once");
        for (int i = 0; i < 30; i++) owner.chase();
        helper.assertTrue(navigation.attempts == 2, "Do not recompute an unchanged unfinished path");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void replacementDoesNotInheritPathFailureBackoff(GameTestHelper helper) {
        var owner = new TestChaser(helper.getLevel());
        var first = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var second = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        owner.setPos(first.getX() - 10, first.getY(), first.getZ());
        var navigation = (RecordingNavigation) owner.getNavigation();
        navigation.succeeds = false;
        owner.setTarget(first);
        for (int i = 0; i < 125; i++) owner.chase();
        int before = navigation.attempts;
        helper.assertTrue(before == 3, "Unreachable targets must keep exponential retry throttling");
        owner.setTarget(second);
        owner.chase();
        helper.assertTrue(navigation.attempts == before + 1, "A replacement target must be pursued immediately");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void unseenTimeoutUsesElapsedTicks(GameTestHelper helper) {
        Mob owner = owner(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(3, 2, 1));
        for (int y = 1; y <= 5; y++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
            }
        }
        owner.getSensing().tick();
        helper.assertTrue(!owner.getSensing().hasLineOfSight(target), "The wall must obstruct sight");
        var goal = new InfectedTargetGoal.Builder(owner).build();
        owner.setTarget(target);
        helper.assertTrue(goal.canUse(), "Adopt an external target behind a wall");
        goal.start();
        owner.tickCount += 60;
        helper.assertTrue(goal.canContinueToUse(), "Keep the target during the sight grace period");
        owner.tickCount++;
        helper.assertTrue(!goal.canContinueToUse(), "Expire after 61 elapsed ticks, regardless of query count");
        goal.stop();
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void staleExternalTargetDoesNotBlockAcquisition(GameTestHelper helper) {
        Mob owner = owner(helper);
        var stale = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var next = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        var goal = new InfectedTargetGoal.Builder(owner).mustSee(false)
                .filter((mob, target) -> target == stale || target == next).build();
        owner.setTarget(stale);
        stale.discard();
        helper.assertTrue(goal.canUse(), "A removed external target must not block scanning");
        goal.start();
        helper.assertTrue(owner.getTarget() == next, "Acquire the remaining live target");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void externalTargetGetsLifecycleManagement(GameTestHelper helper) {
        Mob owner = owner(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var goal = new InfectedTargetGoal.Builder(owner).mustSee(false).build();
        owner.setTarget(target);
        helper.assertTrue(goal.canUse(), "Adopt an externally assigned target");
        goal.start();
        helper.assertTrue(goal.canContinueToUse(), "Keep the adopted target");
        target.discard();
        helper.assertTrue(!goal.canContinueToUse(), "Release a removed target");
        goal.stop();
        helper.assertTrue(owner.getTarget() == null, "Clear the stale reference");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void stopPreservesReplacementAndReacquiresImmediately(GameTestHelper helper) {
        Mob owner = owner(helper);
        var first = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var replacement = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        var goal = new InfectedTargetGoal.Builder(owner).mustSee(false)
                .filter((mob, target) -> target == first || target == replacement).build();
        helper.assertTrue(goal.canUse(), "Find a target");
        goal.start();
        helper.assertTrue(owner.getTarget() == first, "Choose the nearest target");
        owner.setTarget(replacement);
        helper.assertTrue(!goal.canContinueToUse(), "End ownership of the replaced target");
        goal.stop();
        helper.assertTrue(owner.getTarget() == replacement, "Do not erase another source's replacement");
        helper.assertTrue(goal.canUse(), "Adopt replacement immediately");
        goal.start();
        replacement.discard();
        goal.stop();
        helper.assertTrue(goal.canUse(), "Target loss must bypass the previous scan cooldown");
        goal.start();
        helper.assertTrue(owner.getTarget() == first, "Return to the remaining target");
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void retentionBufferDoesNotExpandAcquisitionRange(GameTestHelper helper) {
        Mob owner = owner(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        target.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
        var goal = new InfectedTargetGoal.Builder(owner).range(4).mustSee(false)
                .filter((mob, candidate) -> candidate == target).build();
        helper.assertTrue(!goal.canUse(), "Do not acquire outside the configured range");
        owner.setTarget(target);
        helper.assertTrue(goal.canUse(), "Retain an existing target inside the release buffer");
        goal.start();
        helper.assertTrue(goal.canContinueToUse(), "Crossing the acquisition boundary must not drop aggro");
        target.setPos(owner.getX() + 9, owner.getY(), owner.getZ());
        helper.assertTrue(!goal.canContinueToUse(), "Still release targets beyond the buffer");
        goal.stop();
        helper.succeed();
    }

    @GameTest(template = "potential_empty", batch = "target_goal")
    public static void invalidExternalTargetIsFilteredBeforeAdoption(GameTestHelper helper) {
        Mob owner = owner(helper);
        var ignored = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var allowed = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        var goal = new InfectedTargetGoal.Builder(owner).mustSee(false)
                .filter((mob, candidate) -> candidate == allowed).build();
        owner.setTarget(ignored);
        helper.assertTrue(goal.canUse(), "Replace an external target rejected by the custom filter");
        goal.start();
        helper.assertTrue(owner.getTarget() == allowed, "External assignment must obey target rules");
        helper.succeed();
    }

    private static Mob owner(GameTestHelper helper) {
        var owner = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        owner.setNoAi(true);
        return owner;
    }

    private static final class TestChaser extends AbstractRottenEntity {
        TestChaser(Level level) {
            super(EntityTypeRegistry.SCOUT.get(), level, entity -> {});
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new RecordingNavigation(this, level);
        }

        @Override
        protected boolean getAttack(LivingEntity target) { return false; }

        void chase() { tickChase(); }
    }

    private static final class RecordingNavigation extends GroundPathNavigation {
        int attempts;
        boolean succeeds = true;
        boolean moving;

        RecordingNavigation(Mob mob, Level level) { super(mob, level); }

        @Override
        public boolean moveTo(net.minecraft.world.entity.Entity target, double speed) {
            attempts++;
            moving = succeeds;
            return succeeds;
        }

        @Override
        public boolean isDone() { return !moving; }

        @Override
        public void stop() { moving = false; }
    }
}
