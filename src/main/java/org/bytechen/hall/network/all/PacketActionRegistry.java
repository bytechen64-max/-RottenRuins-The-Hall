package org.bytechen.hall.network.all;

import org.bytechen.hall.network.all.tools.UniversalPacketData;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class PacketActionRegistry {
    private static final Map<String, BiConsumer<UniversalPacketData<?>, NetworkEvent.Context>> HANDLERS = new HashMap<>();

    public static void register(String action, BiConsumer<UniversalPacketData<?>, NetworkEvent.Context> handler) {
        HANDLERS.put(action, handler);
    }

    public static void dispatch(String action, UniversalPacketData<?> data, NetworkEvent.Context context) {
        BiConsumer<UniversalPacketData<?>, NetworkEvent.Context> handler = HANDLERS.get(action);
        if (handler != null) handler.accept(data, context);
    }
}
