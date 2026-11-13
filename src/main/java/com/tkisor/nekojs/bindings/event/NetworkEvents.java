package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;

/**
 * Script-side network events: receives a {@link com.tkisor.nekojs.network.NekoScriptPayload} from
 * the other side.
 *
 * <p>Dispatch is per {@code channel}: only listeners registered for the same channel fire, so
 * channels do not see each other's packets. A listener belongs to one
 * {@link com.tkisor.nekojs.api.ScriptType} and is cleaned up on {@code /nekojs reload}.
 *
 * <pre>{@code
 * // server_scripts: receive packets sent by a client
 * NetworkEvents.server("my_channel", event => {
 *   console.log(event.channel, event.data, event.player)
 * })
 * // client_scripts: receive packets sent by the server
 * NetworkEvents.client("my_channel", event => {
 *   console.log(event.channel, event.data)
 * })
 * }</pre>
 */
public interface NetworkEvents {
    EventGroup GROUP = EventGroup.of("NetworkEvents");

    /**
     * Dispatches by channel name: a listener passes its channel when registering and fires only when
     * a packet for that channel arrives.
     */
    DispatchKey<NetworkDataEventJS, String> CHANNEL_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(NetworkDataEventJS event) {
            return event.getChannel();
        }
    };

    /** SERVER bus: packets from client to server. Scripts listen in server_scripts. */
    EventBusJS<NetworkDataEventJS, String> SERVER =
            GROUP.server("server", NetworkDataEventJS.class, CHANNEL_KEY);

    /** CLIENT bus: packets from server to client. Scripts listen in client_scripts. */
    EventBusJS<NetworkDataEventJS, String> CLIENT =
            GROUP.client("client", NetworkDataEventJS.class, CHANNEL_KEY);
}
