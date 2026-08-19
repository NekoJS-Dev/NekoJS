package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.NekoJSNetwork;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 1.12.2 {@code ClientData} binding: server-side API pushing key-value data to
 * clients. Client scripts read the values through the common {@code clientData}
 * binding. Values are serialized to JSON with gson and travel as a JSON string,
 * matching the wire format used by the NeoForge 26 / 1.21.1 packets.
 */
@Doc("Server-side API to push key-value data to clients; client scripts read it via clientData.get(key).")
@Doc("Values must be JSON types (string/number/bool/object/array/null); re-pushed keys overwrite on the client.")
public final class ClientDataSyncJS {

    /** Serialized JSON size limit per value (same cap as the pdata sync service). */
    private static final int MAX_JSON_CHARS = 32768;

    private ClientDataSyncJS() {}

    /**
     * Server-side: push the value under the key to every connected player.
     */
    @Doc("Server-side only: pushes the value under the key to all players.")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void sync(String key, Object value) {
        NekoJSNetwork.CHANNEL.sendToAll(createPacket(key, value));
    }

    /**
     * Server-side: push the value under the key to one specific player.
     */
    @Doc("Server-side only: pushes the value under the key to one player.")
    @Param(name = "player", value = "the receiving player")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void syncTo(EntityPlayerMP player, String key, Object value) {
        NekoJSNetwork.CHANNEL.sendTo(createPacket(key, value), player);
    }

    private static ClientDataSyncPacket createPacket(String key, Object value) {
        JsonElement json = ClientDataStore.toJsonElement(value);
        String serialized = json.toString();
        if (serialized.length() > MAX_JSON_CHARS) {
            // fail visibly to the script instead of silently skipping oversized payloads
            throw new IllegalArgumentException(
                    "client data value for key '" + key + "' serializes to " + serialized.length()
                            + " chars, over the " + MAX_JSON_CHARS + " limit");
        }
        return new ClientDataSyncPacket(key, serialized);
    }
}
