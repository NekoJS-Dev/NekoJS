package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;

/**
 * 脚本侧网络事件：接收来自对端的 {@link com.tkisor.nekojs.network.NekoScriptPayload}。
 *
 * <p>按 {@code channel} 分发——只有注册了相同 channel 的监听器会被触发，避免跨 channel 串扰。
 * 监听器按 {@link com.tkisor.nekojs.api.ScriptType} 归属，{@code /nekojs reload} 时自动清理。
 *
 * <pre>{@code
 * // server_scripts：接收客户端发来的包
 * NetworkEvents.server("my_channel", event => {
 *   console.log(event.channel, event.data, event.player)
 * })
 * // client_scripts：接收服务端发来的包
 * NetworkEvents.client("my_channel", event => {
 *   console.log(event.channel, event.data)
 * })
 * }</pre>
 */
public interface NetworkEvents {
    EventGroup GROUP = EventGroup.of("NetworkEvents");

    /** 按 channel 名分发：监听器注册时传入 channel，只有该 channel 的包到达时触发。 */
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

    /** SERVER 端总线：客户端 → 服务端的包。脚本在 server_scripts 监听。 */
    EventBusJS<NetworkDataEventJS, String> SERVER =
            GROUP.server("server", NetworkDataEventJS.class, CHANNEL_KEY);

    /** CLIENT 端总线：服务端 → 客户端的包。脚本在 client_scripts 监听。 */
    EventBusJS<NetworkDataEventJS, String> CLIENT =
            GROUP.client("client", NetworkDataEventJS.class, CHANNEL_KEY);
}
