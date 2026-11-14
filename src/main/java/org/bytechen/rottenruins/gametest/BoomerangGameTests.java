package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.overworld.registry.entities.projectile.BoomerangEntity;

@GameTestHolder("rotten_ruins_boomerang_tests")
@PrefixGameTestTemplate(false)
public final class BoomerangGameTests {
    @GameTest(template = "empty", batch = "boomerang")
    public static void survivalLaunchAndCooldown(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = new ItemStack(RegisterItem.HALL_BOOMERANG.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem() == stack && stack.getCount() == 1 && stack.getDamageValue() == 1,
                "Launch must retain the held stack and charge exactly one durability");
        helper.assertTrue(player.getCooldowns().isOnCooldown(stack.getItem()), "Launch must start cooldown");
        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(stack.getDamageValue() == 1, "Cooldown must reject a second launch without charging durability");
        var projectiles = helper.getLevel().getEntitiesOfClass(BoomerangEntity.class,
                player.getBoundingBox().inflate(2), entity -> entity.getOwner() == player);
        helper.assertTrue(projectiles.size() == 1, "Cooldown must prevent a second projectile");
        projectiles.forEach(BoomerangEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "boomerang")
    public static void offhandAndCreativeCosts(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack main = new ItemStack(net.minecraft.world.item.Items.STICK);
        ItemStack stack = new ItemStack(RegisterItem.HALL_BOOMERANG.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        stack.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem() == stack && stack.getDamageValue() == 1 && player.getMainHandItem() == main,
                "Offhand launch must charge only the offhand boomerang");
        player.getCooldowns().removeCooldown(stack.getItem());
        player.getAbilities().instabuild = true;
        stack.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        helper.assertTrue(stack.getDamageValue() == 1 && stack.getCount() == 1,
                "Creative launch must retain count and durability");
        helper.getLevel().getEntitiesOfClass(BoomerangEntity.class, player.getBoundingBox().inflate(2),
                entity -> entity.getOwner() == player).forEach(BoomerangEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "boomerang")
    public static void returnDoesNotRefundOrDuplicate(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = new ItemStack(RegisterItem.HALL_BOOMERANG.get());
        stack.setDamageValue(10);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BoomerangEntity projectile = BoomerangEntity.create(helper.getLevel(), player, stack);
        projectile.beginReturn();
        projectile.tick();
        helper.assertTrue(projectile.isRemoved(), "Projectile at its owner must finish returning");
        helper.assertTrue(player.getMainHandItem() == stack && stack.getCount() == 1 && stack.getDamageValue() == 10,
                "Returning must neither create an inventory stack nor repair/recharge the held item");
        helper.assertTrue(player.getInventory().countItem(stack.getItem()) == 1, "Returning must not duplicate items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "boomerang")
    public static void sweptEntityHitReturnsAndDamages(GameTestHelper helper) {
        Player player = player(helper);
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(player.position().add(0, 0.5, 2.5));
        helper.getLevel().addFreshEntity(cow);
        BoomerangEntity projectile = BoomerangEntity.create(helper.getLevel(), player, new ItemStack(RegisterItem.HALL_BOOMERANG.get()));
        for (int i = 0; i < 4 && !projectile.isReturning(); i++) projectile.tick();
        helper.assertTrue(projectile.isReturning() && cow.getHealth() < cow.getMaxHealth(),
                "Swept collision must damage a target and start the return leg");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Owner must never be hit");
        projectile.discard();
        cow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "boomerang")
    public static void blockCollisionAndSavedReturn(GameTestHelper helper) {
        Player player = player(helper);
        BlockPos wall = BlockPos.containing(player.getEyePosition().add(0, 0, 2));
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        BoomerangEntity projectile = BoomerangEntity.create(helper.getLevel(), player, new ItemStack(RegisterItem.HALL_BOOMERANG.get()));
        for (int i = 0; i < 4 && !projectile.isReturning(); i++) projectile.tick();
        helper.assertTrue(projectile.isReturning(), "A wall must turn the boomerang back");
        CompoundTag saved = new CompoundTag();
        projectile.addAdditionalSaveData(saved);
        BoomerangEntity loaded = new BoomerangEntity(EntityTypeRegistry.BOOMERANG.get(), helper.getLevel());
        loaded.readAdditionalSaveData(saved);
        helper.assertTrue(loaded.isReturning() && loaded.getItem().is(RegisterItem.HALL_BOOMERANG.get()),
                "Saved projectile must retain its item and return state");
        projectile.discard();
        loaded.discard();
        helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "boomerang")
    public static void timedReturnAndMissingOwnerCleanup(GameTestHelper helper) {
        Player player = player(helper);
        BoomerangEntity projectile = BoomerangEntity.create(helper.getLevel(), player, new ItemStack(RegisterItem.HALL_BOOMERANG.get()));
        projectile.setPos(player.position().add(0, 12, 0));
        projectile.setDeltaMovement(new Vec3(0, 0.9, 0));
        for (int i = 0; i < 16; i++) projectile.tick();
        helper.assertTrue(projectile.isReturning(), "Unobstructed flight must turn back after outbound duration");
        player.discard();
        projectile.tick();
        helper.assertTrue(projectile.isRemoved(), "A projectile without its owner must be removed");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.getAbilities().instabuild = false;
        player.setNoGravity(true);
        return player;
    }
}
