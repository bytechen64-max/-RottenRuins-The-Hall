package org.bytechen.rottenruins.content.item;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.RegisterAttributes;

/**
 * 学识：右键使用，永久 +1 通用法强 +1 法术槽。
 *
 * <p>每种学识每个玩家最多吃 {@link #MAX_USES} 次（记在玩家持久化数据里），
 * 用过的次数用属性修饰器的名字区分，所以多次使用不会互相覆盖。</p>
 */
public class KnowledgeTome extends Item {

    public static final int MAX_USES = 2;

    /** 三个分支各自一条使用次数。 */
    public enum Branch {
        MORTAL, INFERNAL, END
    }

    private final Branch branch;

    public KnowledgeTome(Branch branch) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        this.branch = branch;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        int used = usedCount(server, branch);
        if (used >= MAX_USES) {
            server.displayClientMessage(
                    Component.translatable("message.rotten_ruins.knowledge_exhausted").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        grant(server, branch, used);
        setUsedCount(server, branch, used + 1);
        if (!server.getAbilities().instabuild) {
            stack.shrink(1);
        }
        server.displayClientMessage(
                Component.translatable("message.rotten_ruins.knowledge_gained").withStyle(ChatFormatting.AQUA), true);
        server.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.15F);
        return InteractionResultHolder.success(stack);
    }

    /** 这个玩家对该种学识已经吃了几次。 */
    public static int usedCount(ServerPlayer player, Branch branch) {
        return player.getPersistentData().getInt(dataKey(branch));
    }

    private static void setUsedCount(ServerPlayer player, Branch branch, int used) {
        player.getPersistentData().putInt(dataKey(branch), used);
    }

    private static String dataKey(Branch branch) {
        return "rotten_ruins.knowledge." + branch.name().toLowerCase(Locale.ROOT);
    }

    /** 第 {@code index} 次使用的修饰器：法强 +1、法术槽 +1。 */
    private static void grant(ServerPlayer player, Branch branch, int index) {
        String base = "rotten_ruins.knowledge." + branch.name().toLowerCase(Locale.ROOT) + "." + (index + 1);
        addPermanent(player, RegisterAttributes.SPELL_POWER.get(), base + ".power", 1.0D);
        addPermanent(player, RegisterAttributes.MAGIC_SLOTS.get(), base + ".slot", 1.0D);
    }

    private static void addPermanent(ServerPlayer player, @Nullable Attribute attribute, String name, double amount) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        if (instance.getModifier(id) != null) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }
}
