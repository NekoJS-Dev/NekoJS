package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code ClientData} binding, for server scripts only: pushes key-value data to clients,
 * where client scripts read it through {@code clientData.get(key)}. HUD scripts use it as their
 * data channel.
 *
 * <p>Values are serialized to JSON with gson and travel as a JSON string, so all three platforms
 * share one wire format. Only JSON types are supported (string/number/bool/object/array/null);
 * any other value throws {@link IllegalArgumentException}. Pushing the same key again overwrites
 * the earlier value; a client clears its data on disconnect or world change.
 */
@Doc("Server-side API to push key-value data to clients; client scripts read it via clientData.get(key).")
@Doc("Values must be JSON types (string/number/bool/object/array/null); re-pushed keys overwrite on the client.")
public final class ClientDataSyncJS {

    /** Upper bound on the serialized JSON text of one value, the same limit as pdata, so a script
     * cannot put a huge structure into a chat packet. */
    private static final int MAX_JSON_CHARS = 32768;

    private ClientDataSyncJS() {}

    /** Pushes the value to every online player. */
    @Doc("Pushes the value under the key to every connected player.")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void sync(String key, Object value) {
        var packet = createPacket(key, value);
        PlayPacketDispatchers.get().sendToAllPlayers(packet);
    }

    /** Pushes the value to one player. */
    @Doc("Pushes the value under the key to one specific player.")
    @Param(name = "player", value = "the receiving server-side player")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void syncTo(ServerPlayer player, String key, Object value) {
        var packet = createPacket(key, value);
        PlayPacketDispatchers.get().sendToPlayer(player, packet);
    }

    private static ClientDataSyncPacket createPacket(String key, Object value) {
        JsonElement json = ClientDataStore.toJsonElement(value);
        String serialized = json.toString();
        if (serialized.length() > MAX_JSON_CHARS) {
            // Throws to the script layer, where error reporting can show it, instead of skipping the push:
            // a failed push should fail visibly
            throw new IllegalArgumentException(
                    "client data value for key '" + key + "' serializes to " + serialized.length()
                            + " chars, over the " + MAX_JSON_CHARS + " limit");
        }
        return new ClientDataSyncPacket(key, serialized);
    }
}
