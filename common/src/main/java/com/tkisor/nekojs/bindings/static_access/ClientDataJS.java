package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;

import java.util.Set;

/**
 * The {@code clientData} binding: client scripts get read-only access to the key-value data
 * that server scripts push with {@code ClientData.sync}. It lives in
 * {@link ClientDataStore#SHARED}, which the platform network layer writes when a sync packet
 * arrives.
 *
 * <p>Values round-trip through JSON: the server pushes string/number/bool/object/array/null,
 * and reads return the matching shape (Map/List/boxed primitives). Pushing the
 * same key again overwrites the earlier value; disconnecting or switching worlds clears the
 * store.
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

    /** Returns the value stored under the key as a Map, List or boxed primitive; returns
     * {@code null} when the key is absent or its value is null. */
    @Doc("Returns the value stored under the key, or null when missing (or when the value is JSON null).")
    @Param(name = "key", value = "data key the server pushed with ClientData.sync")
    @Return("the stored value converted from JSON; null when absent")
    public Object get(String key) {
        return ClientDataStore.SHARED.get(key);
    }

    /** Returns whether the key exists (also true when its value is null). */
    @Doc("Returns whether the key has been synced (true even when the stored value is JSON null).")
    @Param(name = "key", value = "data key to check")
    @Return("true when the key exists in the store")
    public boolean has(String key) {
        return ClientDataStore.SHARED.has(key);
    }

    /** Returns a snapshot of all current keys. */
    @Doc("Returns a snapshot of all currently synced keys.")
    @Return("unordered set of keys; empty when nothing is synced")
    public Set<String> keys() {
        return ClientDataStore.SHARED.keys();
    }

    /** Returns the current entry count. */
    @Doc("Returns how many keys are currently stored.")
    @Return("number of synced keys")
    public int size() {
        return ClientDataStore.SHARED.size();
    }
}
