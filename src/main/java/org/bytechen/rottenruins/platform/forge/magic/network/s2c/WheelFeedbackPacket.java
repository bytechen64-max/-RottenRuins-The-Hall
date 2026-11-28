package org.bytechen.rottenruins.platform.forge.magic.network.s2c;




import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;


/**
 * 服务端 → 客户端：轮盘手势的<b>结果回执</b>。
 *
 * <p>存在的意义：轮盘是"按住 R 的会话"，客户端必须知道服务端到底接受了哪一步
 * （开始蓄力成功？维持起手成功？<code>KEEP</code> 起手失败是没蓝还是没目标？），
 * 否则界面会在错误的状态里继续转圈。回执只带结果码与少量数字，不带任何状态。</p>
 *
 * <p>走项目已有的 {@code UniversalPacket} + {@code PacketActionRegistry}，
 * 和 {@code HeartLosePacket}/{@code BloodBurstPacket} 同一套；不新增包序号。</p>
 */
public final class WheelFeedbackPacket {

    public static final String ACTION = "rotten_ruins:wheel_feedback";

    /** 与 {@code WheelActionPacket} 的动作编号保持一致。 */
    public static final int ACT_CONFIG = 0;
    public static final int ACT_BEGIN = 1;
    public static final int ACT_CAST = 2;
    public static final int ACT_END = 3;
    public static final int ACT_CANCEL = 4;

    private WheelFeedbackPacket() {
    }

    // ══════════════════════════════════════════════════════════════
    //  发送端（服务端）
    // ══════════════════════════════════════════════════════════════

    public static void send(ServerPlayer player, int action, CastResult result,
                            int page, int index, int heldTicks, float chargeRatio) {
        if (player == null || result == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", action);
        tag.putString("r", result.name());
        tag.putInt("pg", page);
        tag.putInt("ix", index);
        tag.putInt("held", heldTicks);
        tag.putFloat("charge", chargeRatio);
        NetworkHelper.sendToPlayer(player,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // ══════════════════════════════════════════════════════════════
    //  接收端（客户端）
    // ══════════════════════════════════════════════════════════════

}
