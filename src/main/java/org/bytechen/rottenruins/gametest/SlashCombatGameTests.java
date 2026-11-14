package org.bytechen.rottenruins.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.platform.forge.combat.slash.SlashServer;
import org.bytechen.rottenruins.gameplay.combat.slash.SlashEngine;

/** Isolated namespace so the combat integration checks can run without unrelated batches. */
@GameTestHolder("rotten_ruins_slash_tests")
@PrefixGameTestTemplate(false)
public final class SlashCombatGameTests {
    @GameTest(template="empty",batch="slash_strength",timeoutTicks=60)
    public static void strengthBoostsVerdictAreaDamage(GameTestHelper helper) {
        ServerPlayer p=player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RegisterItem.DOMERITE_LONGSWORD.get()));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,100,0));
        Cow a=cow(helper,p.position().add(0,0,2)),b=cow(helper,p.position().add(1,0,2));
        SlashEngine.request(p);
        helper.runAfterDelay(5,()->{
            helper.assertTrue(Math.abs(a.getHealth()-67)<.001&&Math.abs(b.getHealth()-67)<.001,
                    "Strength I adds 3 to Verdict's 25 hurt damage on every target; bypass stays 5");
            cleanup(helper,p,a,b);helper.succeed();
        });
    }
    @GameTest(template="empty",batch="slash_strength_critical",timeoutTicks=60)
    public static void strengthCriticalKeepsEnchantAndBypassAdditive(GameTestHelper helper) {
        ServerPlayer p=player(helper);ItemStack stack=new ItemStack(RegisterItem.DOMERITE_LONGSWORD.get());
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,1);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,100,1));
        p.setOnGround(false);p.fallDistance=1;
        Cow target=cow(helper,p.position().add(0,0,2));SlashEngine.request(p);
        p.setOnGround(true);p.fallDistance=0;
        helper.runAfterDelay(10,()->{
            helper.assertTrue(Math.abs(target.getHealth()-47.5F)<.001,
                    "Strength II Verdict critical deals (25+6)*1.5 + sharpness 1 + bypass 5");
            cleanup(helper,p,target);helper.succeed();
        });
    }
    @GameTest(template="empty",batch="slash_default_strength",timeoutTicks=60)
    public static void defaultSlashReadsStrengthFromAttackAttribute(GameTestHelper helper) {
        ServerPlayer p=player(helper);ItemStack stack=new ItemStack(RegisterItem.TRUE_COPPER.get());
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,1);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        Cow target=cow(helper,p.position().add(0,0,2));
        var weapon=(org.bytechen.rottenruins.api.combat.slash.ISlashWeapon)stack.getItem();
        var context=new org.bytechen.rottenruins.api.combat.slash.SlashContext(p,InteractionHand.MAIN_HAND,stack,
                org.bytechen.rottenruins.api.combat.slash.SlashPose.RIGHT_TO_LEFT,0,weapon.slashSpec(p,stack),
                new Vec3(0,0,1),new Vec3(1,0,0));
        for(int amplifier=0;amplifier<2;amplifier++) {
            p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,100,amplifier));
            float base=6+3*(amplifier+1);
            var normal=org.bytechen.rottenruins.platform.forge.combat.slash.SlashCriticals.resolve(context,weapon,target,false);
            var critical=org.bytechen.rottenruins.platform.forge.combat.slash.SlashCriticals.resolve(context,weapon,target,true);
            helper.assertTrue(Math.abs(normal.damage()-(base+1))<.001,"Default slash includes Strength once");
            helper.assertTrue(Math.abs(critical.damage()-(base*1.5F+1))<.001,"Strength participates in criticals; sharpness stays additive");
        }
        p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
        helper.assertTrue(Math.abs(weapon.slashDamage(context,target)-7)<.001,"Removing Strength restores normal damage");
        cleanup(helper,p,target);helper.succeed();
    }
    @GameTest(template="empty",batch="slash_verdict",timeoutTicks=60)
    public static void verdictAreaKeepsSplitDamageAndDurability(GameTestHelper helper) {
        ServerPlayer p=player(helper);ItemStack stack=new ItemStack(RegisterItem.DOMERITE_LONGSWORD.get());
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.setOnGround(true);p.fallDistance=0;
        var timing=((org.bytechen.rottenruins.api.combat.slash.ISlashWeapon)stack.getItem()).slashSpec(p,stack);
        helper.assertTrue(timing.cooldownTicks()==9&&timing.animationTicks()==8,"Verdict uses the faster 9 tick cooldown and 8 tick animation");
        Cow a=cow(helper,p.position().add(0,0,2)),b=cow(helper,p.position().add(1,0,2));
        SlashEngine.request(p);
        helper.runAfterDelay(5,()->{
            helper.assertTrue(Math.abs(a.getHealth()-70)<.001&&Math.abs(b.getHealth()-70)<.001,"Verdict AoE applies 25+5 once per target");
            helper.assertTrue(stack.getDamageValue()==1,"AoE consumes one durability per swing");
            cleanup(helper,p,a,b);helper.succeed();
        });
    }
    @GameTest(template="empty",batch="slash_verdict_critical",timeoutTicks=60)
    public static void verdictCriticalKeepsBypassUnmultiplied(GameTestHelper helper) {
        ServerPlayer p=player(helper);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RegisterItem.DOMERITE_LONGSWORD.get()));
        p.setOnGround(false);p.fallDistance=1;Cow target=cow(helper,p.position().add(0,0,2));SlashEngine.request(p);
        p.setOnGround(true);p.fallDistance=0;
        helper.runAfterDelay(10,()->{
            helper.assertTrue(Math.abs(target.getHealth()-57.5F)<.001,"Verdict critical deals 25*1.5+5, no second native hit");
            cleanup(helper,p,target);helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "slash_critical", timeoutTicks = 60)
    public static void fallingCriticalSnapshotHitsMultipleTargets(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ItemStack stack = new ItemStack(RegisterItem.TRUE_COPPER.get());
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.fallDistance = 1; p.setOnGround(false);
        Cow a = cow(helper, p.position().add(0, 0, 2)), b = cow(helper, p.position().add(1, 0, 2));
        SlashEngine.request(p);
        // Landing during the animation must not erase the critical accepted at start.
        p.fallDistance = 0; p.setOnGround(true);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(Math.abs(a.getHealth() - 90) < .001 && Math.abs(b.getHealth() - 90) < .001,
                    "Critical AoE deals base 6*1.5 + sharpness 1, once per target, even after landing");
            cleanup(helper, p, a, b); helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "slash_critical_rules", timeoutTicks = 60)
    public static void criticalRulesAndForgeOverrides(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ItemStack stack = new ItemStack(RegisterItem.TRUE_COPPER.get());
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Cow target = cow(helper, p.position().add(0, 0, 2));
        var weapon = (org.bytechen.rottenruins.api.combat.slash.ISlashWeapon) stack.getItem();
        var context = new org.bytechen.rottenruins.api.combat.slash.SlashContext(p, InteractionHand.MAIN_HAND, stack,
                org.bytechen.rottenruins.api.combat.slash.SlashPose.RIGHT_TO_LEFT, 0, weapon.slashSpec(p, stack),
                new Vec3(0,0,1), new Vec3(1,0,0));
        p.fallDistance=0; p.setOnGround(true);
        helper.assertTrue(!org.bytechen.rottenruins.gameplay.combat.slash.SlashCriticalPolicy.eligible(p), "Grounded attacks are normal");
        var normal = org.bytechen.rottenruins.platform.forge.combat.slash.SlashCriticals.resolve(context, weapon, target, false);
        helper.assertTrue(!normal.critical() && Math.abs(normal.damage()-7)<.001, "Normal base plus enchant damage");
        p.fallDistance=1; p.setOnGround(false);
        helper.assertTrue(org.bytechen.rottenruins.gameplay.combat.slash.SlashCriticalPolicy.eligible(p), "Falling charged attacks may crit");
        p.setSprinting(true);
        helper.assertTrue(org.bytechen.rottenruins.gameplay.combat.slash.SlashCriticalPolicy.eligible(p), "Sprint jump slashes may crit independently of native attack charge");
        p.setSprinting(false);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 100));
        helper.assertTrue(!org.bytechen.rottenruins.gameplay.combat.slash.SlashCriticalPolicy.eligible(p), "Blindness excludes vanilla critical");
        p.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        java.util.function.Consumer<net.minecraftforge.event.entity.player.CriticalHitEvent> deny = event -> {
            if(event.getEntity()==p)event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            var hit = org.bytechen.rottenruins.platform.forge.combat.slash.SlashCriticals.resolve(context,weapon,target,true);
            helper.assertTrue(!hit.critical() && Math.abs(hit.damage()-7)<.001, "Forge can deny a vanilla critical");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
        java.util.function.Consumer<net.minecraftforge.event.entity.player.CriticalHitEvent> allow = event -> {
            if(event.getEntity()==p) {event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);event.setDamageModifier(2);}
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(allow);
        try {
            var hit = org.bytechen.rottenruins.platform.forge.combat.slash.SlashCriticals.resolve(context,weapon,target,false);
            helper.assertTrue(hit.critical() && Math.abs(hit.damage()-13)<.001, "Forge can force a custom multiplier; enchant bonus stays additive");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(allow); }
        cleanup(helper,p,target); helper.succeed();
    }
    @GameTest(template = "empty", batch = "slash_area", timeoutTicks = 60)
    public static void multiTargetCooldownAndBehind(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RegisterItem.TRUE_COPPER.get()));
        Cow front = cow(helper, p.position().add(0, 0, 2));
        Cow side = cow(helper, p.position().add(1, 0, 2));
        Cow behind = cow(helper, p.position().add(0, 0, -2));
        SlashEngine.request(p); SlashEngine.request(p);
        helper.assertTrue(SlashEngine.remainingCooldownTicks(p)==12, "Independent server cooldown applies on acceptance");
        helper.assertTrue(!p.getCooldowns().isOnCooldown(RegisterItem.TRUE_COPPER.get()), "Slashing does not occupy the item skill cooldown");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(front.getHealth() < 100 && side.getHealth() < 100, "Both forward boxes take area damage");
            helper.assertTrue(behind.getHealth() == 100, "Rear target stays outside the arc");
            float health = front.getHealth();
            SlashEngine.request(p);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(front.getHealth() == health, "Repeated requests inside cooldown add no second damage");
                cleanup(helper, p, front, side, behind); helper.succeed();
            });
        });
    }
    @GameTest(template = "empty", batch = "slash_cancel", timeoutTicks = 60)
    public static void unequipAndNonInterfaceGate(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Cow target = cow(helper, p.position().add(0, 0, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        SlashEngine.request(p);
        helper.assertTrue(!p.getCooldowns().isOnCooldown(Items.DIAMOND_SWORD), "Non-interface weapons never start a slash");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RegisterItem.TRUE_COPPER.get()));
        SlashEngine.request(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(target.getHealth() == 100, "Unequipping cancels before impact");
            cleanup(helper, p, target); helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "slash_occlusion", timeoutTicks = 60)
    public static void solidWallBlocksSlash(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RegisterItem.TRUE_COPPER.get()));
        Cow target = cow(helper, p.position().add(0, 0, 2));
        BlockPos wall = p.blockPosition().offset(0, 0, 1);
        for (int x = -1; x <= 1; x++) for (int y = 0; y < 3; y++)
            helper.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
        SlashEngine.request(p);
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(target.getHealth() == 100, "Line of sight rejects targets behind a solid wall");
            cleanup(helper, p, target); helper.succeed();
        });
    }
    @GameTest(template="empty",batch="slash_skill_independence",timeoutTicks=60)
    public static void skillCooldownAndSlashCooldownAreIndependent(GameTestHelper helper) {
        ServerPlayer p=player(helper);var item=RegisterItem.TRUE_COPPER.get();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
        p.getCooldowns().addCooldown(item,80);float skill=p.getCooldowns().getCooldownPercent(item,0);
        Cow target=cow(helper,p.position().add(0,0,2));SlashEngine.request(p);
        helper.assertTrue(SlashEngine.remainingCooldownTicks(p)==12,"An active skill cooldown cannot block slashing");
        helper.assertTrue(p.getCooldowns().getCooldownPercent(item,0)==skill,"Slashing cannot overwrite or shorten the skill cooldown");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RegisterItem.DOMERITE_LONGSWORD.get()));SlashEngine.request(p);
        helper.assertTrue(SlashEngine.remainingCooldownTicks(p)==12,"Weapon swapping cannot bypass the independent slash cooldown");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        helper.runAfterDelay(13,()->{
            helper.assertTrue(SlashEngine.remainingCooldownTicks(p)==0,"Slash cooldown expires on server time");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));SlashEngine.request(p);
            helper.assertTrue(SlashEngine.remainingCooldownTicks(p)==12,"New slash starts while the skill cooldown is still active");
        });
        helper.runAfterDelay(20,()->{
            helper.assertTrue(Math.abs(target.getHealth()-94)<.001,"Accepted slash deals damage during an active skill cooldown");
            helper.assertTrue(p.getCooldowns().isOnCooldown(item),"Skill cooldown survives all slash requests");
            cleanup(helper,p,target);helper.succeed();
        });
    }
    @GameTest(template="empty",batch="slash_apex_window",timeoutTicks=60)
    public static void apexDuringWindupLatchesCritical(GameTestHelper helper) {
        ServerPlayer p=player(helper);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RegisterItem.TRUE_COPPER.get()));
        p.setOnGround(false);p.setSprinting(true);p.setDeltaMovement(0,.25,0);
        helper.assertTrue(!org.bytechen.rottenruins.gameplay.combat.slash.SlashCriticalPolicy.eligible(p),"Early ascent remains a normal attack");
        Cow target=cow(helper,p.position().add(0,0,2));SlashEngine.request(p);
        helper.runAfterDelay(2,()->{p.fallDistance=0;p.setDeltaMovement(0,.04,0);});
        helper.runAfterDelay(4,()->{p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);});
        helper.runAfterDelay(8,()->{
            helper.assertTrue(Math.abs(target.getHealth()-91)<.001,"Apex entered during windup gives 1.5x critical after landing, even sprinting with zero native charge");
            cleanup(helper,p,target);helper.succeed();
        });
    }
    private static ServerPlayer player(GameTestHelper helper) {
        // Do not invoke the real login pipeline: the mod's login sync handlers
        // expect a real Netty channel. This fixture exercises combat, not login.
        var p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "slash-test")) {
            @Override public void defineSynchedData() { super.defineSynchedData(); }
            @Override public float getAttackStrengthScale(float partial) { return 0; }
        };
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        for (int tick = 1; tick <= 20; tick++) helper.runAfterDelay(tick,
                () -> SlashServer.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, p)));
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        // Ground and clear air around each isolated scene, independent of structure size.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            helper.getLevel().setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 4; y++) helper.getLevel().setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        p.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0); p.setNoGravity(true); p.setOnGround(true);
        return p;
    }
    private static Cow cow(GameTestHelper helper, Vec3 pos) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        cow.moveTo(pos.x, pos.y, pos.z); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
        helper.getLevel().addFreshEntity(cow); return cow;
    }
    private static void cleanup(GameTestHelper helper, ServerPlayer player, Cow... cows) {
        for (Cow cow : cows) cow.discard();
        SlashServer.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
        player.discard();
    }
}
