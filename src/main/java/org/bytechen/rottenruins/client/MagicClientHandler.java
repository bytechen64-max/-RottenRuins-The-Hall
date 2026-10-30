package org.bytechen.rottenruins.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.bytechen.rottenruins.client.gui.MagicScreen;
import org.bytechen.rottenruins.network.c2s.MagicPoolActionPacket;
import org.lwjgl.glfw.GLFW;

/**
 * 魔法星图的<b>客户端入口</b>：负责注册按键、以及按下时打开界面。
 *
 * <h3>按键</h3>
 * <p>{@code M}，冲突上下文 {@link KeyConflictContext#IN_GAME} —— 在聊天栏 /
 * 物品栏里按 M 不会被吃掉。键位可以在原版「控制」里改，分类归到本模组名下。</p>
 *
 * <h3>为什么打开界面前先发一个同步请求</h3>
 * <p>客户端那份魔法池是服务端推过来的镜像，正常情况下最多滞后
 * {@code MagicSync.POOL_INTERVAL_TICKS} 个 tick。但玩家可能刚登录、刚重生、
 * 或者刚在别处改过槽位，界面一打开就显示旧内容会很像 bug。所以打开前让服务端
 * <b>无条件</b>推一次（{@link MagicPoolActionPacket#sendSyncRequest()}），
 * 界面先按手里的镜像画，包到了自然刷新。</p>
 *
 * <h3>为什么循环 consumeClick 而不是只判断一次</h3>
 * <p>{@link KeyMapping#consumeClick()} 会清空队列。如果不消费就走，
 * 界面关闭后的下一个 tick 会立刻又弹出来。这里<b>无论界面开没开都消费</b>，
 * 只在"当前没有其他界面"时真正打开。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MagicClientHandler {

    /** 打开魔法星图的按键。 */
    public static final KeyMapping OPEN_MAGIC = new KeyMapping(
            "key.rotten_ruins.open_magic",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.rotten_ruins");

    private MagicClientHandler() {
    }

    /** 注册到 Forge 总线。在 {@code ClientPacketHandlers.init()} 里调用。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(MagicClientHandler::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        while (OPEN_MAGIC.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                MagicPoolActionPacket.sendSyncRequest();
                mc.setScreen(new MagicScreen());
            }
        }
    }
}
