package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Iterator;
import java.util.List;

/**
 * Read-only view of a single registry; this is what {@code Registry.get(...)} returns to scripts.
 *
 * <p>Every method returns only primitive types; Minecraft native objects are never exposed.
 *
 * <p>The view implements {@link Iterable}, so when the engine proxies it, a script can iterate it
 * directly with {@code for...of} (by entry id string), matching the {@code Iterable} shape of KubeJS
 * {@code RegistryWrapper}: {@code for (const id of Registry.get('minecraft:item'))}.
 */
@ContractReceiver
public interface RegistryView extends Iterable<String> {
    /** Whether the registry itself exists. */
    boolean exists();

    /** Ids of all entries in the registry, including the namespace. */
    List<String> all();

    /** Whether the registry contains the given id. */
    boolean has(String id);

    /** Ids of all entries under the given tag. */
    List<String> tag(String tagId);

    /**
     * Ids of all data map types registered for this registry, for example
     * {@code neoforge:furnace_fuels}.
     */
    List<String> dataMapIds();

    /**
     * Reads the data map value of the given entry as a JSON string; returns {@code null} if the entry
     * or the type does not exist.
     */
    String dataMapValue(String dataMapTypeId, String id);

    @Override
    default Iterator<String> iterator() {
        return all().iterator();
    }
}
