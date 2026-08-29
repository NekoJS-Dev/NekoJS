package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;

import java.util.Set;

/**
 * {@code clientData} 绑定：客户端脚本只读访问服务端经 {@code ClientData.sync} 推送下来的
 * 键值数据（存于 {@link ClientDataStore#SHARED}，由平台网络层在收到同步包时写入）。
 *
 * <p>值按 JSON 往返：服务端推 string/number/bool/object/array/null，这里取回的也是对应形态
 * （Map/List/装箱原始值）。同 key 重复推送后者覆盖；断线/切世界时存储清空。
 *
 * <pre>
 * // server_scripts
 * ClientData.sync('hud:mana', { current: 12, max: 20 })
 * // client_scripts
 * const mana = clientData.get('hud:mana')
 * if (clientData.has('hud:mana')) { ... }
 * </pre>
 */
@Doc("Read-only client-side view of key-value data pushed by server scripts via ClientData.sync.")
@Doc("Values round-trip JSON types only (string/number/bool/object/array/null); re-pushed keys overwrite.")
public final class ClientDataJS {

    /** 读取 key 对应值（Map/List/装箱原始值）；不存在或值为 null 时返回 {@code null}。 */
    @Doc("Returns the value stored under the key, or null when missing (or when the value is JSON null).")
    @Param(name = "key", value = "data key the server pushed with ClientData.sync")
    @Return("the stored value converted from JSON; null when absent")
    public Object get(String key) {
        return ClientDataStore.SHARED.get(key);
    }

    /** 是否存在该 key（值为 null 时也为 true）。 */
    @Doc("Returns whether the key has been synced (true even when the stored value is JSON null).")
    @Param(name = "key", value = "data key to check")
    @Return("true when the key exists in the store")
    public boolean has(String key) {
        return ClientDataStore.SHARED.has(key);
    }

    /** 当前全部 key 快照。 */
    @Doc("Returns a snapshot of all currently synced keys.")
    @Return("unordered set of keys; empty when nothing is synced")
    public Set<String> keys() {
        return ClientDataStore.SHARED.keys();
    }

    /** 当前条目数。 */
    @Doc("Returns how many keys are currently stored.")
    @Return("number of synced keys")
    public int size() {
        return ClientDataStore.SHARED.size();
    }
}
