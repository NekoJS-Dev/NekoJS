package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code ClientData} 绑定（服务端脚本专用）：把 key-value 数据推送到客户端，客户端脚本经
 * {@code clientData.get(key)} 只读消费（未来 HUD 脚本的数据通道）。
 *
 * <p>值走 JSON 序列化（gson），网络传输为 JSON 字符串——三个平台共用同一种线格式；
 * 仅支持 JSON 类型（string/number/bool/object/array/null），其它值抛
 * {@link IllegalArgumentException}。同 key 重复推送后者覆盖；客户端断线/切世界时清空。
 */
@Doc("Server-side API to push key-value data to clients; client scripts read it via clientData.get(key).")
@Doc("Values must be JSON types (string/number/bool/object/array/null); re-pushed keys overwrite on the client.")
public final class ClientDataSyncJS {

    /** 单个值序列化后的 JSON 文本上限（与 pdata 同限）：防止脚本把超大结构塞进聊天包。 */
    private static final int MAX_JSON_CHARS = 32768;

    private ClientDataSyncJS() {}

    /** 推送给全部在线玩家。 */
    @Doc("Pushes the value under the key to every connected player.")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void sync(String key, Object value) {
        var packet = createPacket(key, value);
        PacketDistributor.sendToAllPlayers(packet);
    }

    /** 推送给指定玩家。 */
    @Doc("Pushes the value under the key to one specific player.")
    @Param(name = "player", value = "the receiving server-side player")
    @Param(name = "key", value = "data key the client reads with clientData.get(key)")
    @Param(name = "value", value = "JSON-compatible value (string/number/bool/object/array/null)")
    public static void syncTo(ServerPlayer player, String key, Object value) {
        var packet = createPacket(key, value);
        PacketDistributor.sendToPlayer(player, packet);
    }

    private static ClientDataSyncPacket createPacket(String key, Object value) {
        JsonElement json = ClientDataStore.toJsonElement(value);
        String serialized = json.toString();
        if (serialized.length() > MAX_JSON_CHARS) {
            // 抛给脚本层（error reporting 可见），而不是静默跳过——推送失败应当显式失败
            throw new IllegalArgumentException(
                    "client data value for key '" + key + "' serializes to " + serialized.length()
                            + " chars, over the " + MAX_JSON_CHARS + " limit");
        }
        return new ClientDataSyncPacket(key, serialized);
    }
}
