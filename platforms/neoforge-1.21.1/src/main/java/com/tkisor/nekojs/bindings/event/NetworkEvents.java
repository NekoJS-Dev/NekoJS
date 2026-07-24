package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;

public interface NetworkEvents {
    EventGroup GROUP = EventGroup.of("NetworkEvents");

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

    EventBusJS<NetworkDataEventJS, String> SERVER =
            GROUP.server("server", NetworkDataEventJS.class, CHANNEL_KEY);

    EventBusJS<NetworkDataEventJS, String> CLIENT =
            GROUP.client("client", NetworkDataEventJS.class, CHANNEL_KEY);
}
