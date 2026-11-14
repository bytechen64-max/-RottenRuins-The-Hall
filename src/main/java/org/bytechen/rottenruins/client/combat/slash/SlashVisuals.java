package org.bytechen.rottenruins.client.combat.slash;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.api.combat.slash.*;
import org.bytechen.rottenruins.client.rend.slash.SlashMotion;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Register client-only extensions during client setup; weapon classes stay dedicated-server safe. */
public final class SlashVisuals {
    public record Context(AbstractClientPlayer player, ItemStack stack, SlashPose pose, int combo,
                          float elapsedTicks, float progress, boolean firstPerson, SlashTrail trail) {}
    public interface Handler {
        default SlashMotion.Pose sample(Context context) {
            // Keep an active attack hidden at its entry point even at progress zero;
            // falling back to vanilla for that frame would flash the idle weapon.
            float progress = context.progress() == 0 ? 0.0001F : context.progress();
            return SlashMotion.sample(SlashMotion.Direction.valueOf(context.pose().name()), progress);
        }
        default void onStart(Context context) {}
        default void onEnd(Context context, boolean cancelled) {}
        /** Render callback; can run multiple times per frame (e.g. outline capture). */
        default void transform(Context context, PoseStack poses) {}
    }
    private static final Handler DEFAULT = new Handler() {};
    private static final Map<Item, Handler> HANDLERS = new ConcurrentHashMap<>();
    private SlashVisuals() {}
    public static void register(Item item, Handler handler) { HANDLERS.put(item, handler); }
    public static Handler get(Item item) { return HANDLERS.getOrDefault(item, DEFAULT); }
}
