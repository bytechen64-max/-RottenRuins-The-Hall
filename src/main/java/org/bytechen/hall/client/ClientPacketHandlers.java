package org.bytechen.hall.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void init() {
        // Wire up S2C client-side packet handlers here
    }
}
