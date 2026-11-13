//? if neoforge {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
//? if <26 {
/*import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
*///?}
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Event object for {@code ServerEvents.lootTables}, managing loot tables at the JSON level.
 *
 * <p>Posted during server data reload ({@code AddServerReloadListenersEvent}, before loot tables
 * are parsed). Scripts declare changes here, and when NeoForge later fires
 * {@link LootTableLoadEvent} for each non-empty table during the reload, pending edits are applied:
 * <ul>
 *   <li>{@code setJson} / {@code create}: parses the JSON and replaces the table (intended to allow new ids)</li>
 *   <li>{@code remove}: replaces the table with {@link LootTable#EMPTY} (the loader drops empty tables, effectively deleting them)</li>
 * </ul>
 * Pending edits persist across reloads (scripts re-declare them on each reload); {@code getJson}
 * and {@code getIds} read the tables from the most recent reload together with pending edits.
 */
public class LootTableEventJS {
    /** Pending table replacements by id. */
    private static final Map<Identifier, JsonObject> PENDING_SET = new ConcurrentHashMap<>();
    /** Table ids scheduled for removal. */
    private static final Set<Identifier> PENDING_REMOVE = ConcurrentHashMap.newKeySet();
    /** Tables loaded during the most recent reload after modifications, used to serialize {@link #getJson}. */
    private static final Map<Identifier, LootTable> LOADED_TABLES = new ConcurrentHashMap<>();
    /** Registry lookup from the most recent reload, used for codec serialization and parsing. */
    private static volatile HolderLookup.Provider REGISTRIES;

    /** Returns all known loot table ids, including script-created entries. */
    public List<String> getIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LOADED_TABLES.keySet().forEach(id -> ids.add(id.toString()));
        PENDING_SET.keySet().forEach(id -> ids.add(id.toString()));
        return List.copyOf(ids);
    }

    /** Returns the current JSON for {@code id} (including pending script edits), or {@code null} if unknown. */
    public JsonElement getJson(String id) {
        Identifier location = parseId(id);
        JsonObject pending = PENDING_SET.get(location);
        if (pending != null) {
            return pending;
        }
        LootTable table = LOADED_TABLES.get(location);
        HolderLookup.Provider registries = REGISTRIES;
        if (table == null || registries == null) {
            return null;
        }
        return LootTable.DIRECT_CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), table)
                .getOrThrow();
    }

    /** Replaces the loot table for {@code id} on the next reload. */
    public void setJson(String id, Object json) {
        Identifier location = parseId(id);
        PENDING_SET.put(location, toJsonObject(json));
        PENDING_REMOVE.remove(location);
    }

    /** Creates or replaces a loot table (alias for {@link #setJson}, intended to allow non-existent ids). */
    public void create(String id, Object json) {
        setJson(id, json);
    }

    /**
     * Reads the current JSON for {@code id} (including earlier script edits), passes a
     * {@link LootTableJS} wrapper to {@code consumer}, and stores the result back. Non-existent
     * ids start from an empty object (callers should set a type such as
     * {@code table.setType('minecraft:chest')}).
     */
    public void modify(String id, Consumer<LootTableJS> consumer) {
        JsonElement current = getJson(id);
        JsonObject json = current != null && current.isJsonObject()
                ? current.getAsJsonObject()
                : new JsonObject();
        consumer.accept(new LootTableJS(json));
        setJson(id, json);
    }

    /**
     * Modifies block loot tables matching {@code blockId}: a single block id, a {@code '#tag'}
     * (expanded to all blocks in that tag), or {@code '*'} (all known block tables).
     */
    public void modifyBlockLoot(String blockId, Consumer<LootTableJS> consumer) {
        if (blockId.startsWith("#")) {
            for (String tableId : blockTableIdsForTag(blockId.substring(1))) {
                modify(tableId, consumer);
            }
        } else if ("*".equals(blockId)) {
            for (String tableId : knownTableIds("blocks/")) {
                modify(tableId, consumer);
            }
        } else {
            modify(blockTableId(blockId), consumer);
        }
    }

    /** Modifies known block loot tables whose block id (such as {@code 'mymod:ore'}) matches {@code filter}. */
    public void modifyBlockLoot(Predicate<String> filter, Consumer<LootTableJS> consumer) {
        for (String tableId : knownTableIds("blocks/")) {
            if (filter.test(tableId.replace("/blocks/", ":"))) {
                modify(tableId, consumer);
            }
        }
    }

    /** Modifies entity loot tables matching {@code entityId}: a single entity id or {@code '*'} for all known entity tables. */
    public void modifyEntityLoot(String entityId, Consumer<LootTableJS> consumer) {
        if ("*".equals(entityId)) {
            for (String tableId : knownTableIds("entities/")) {
                modify(tableId, consumer);
            }
        } else {
            modify(entityTableId(entityId), consumer);
        }
    }

    /** Modifies known entity loot tables whose entity id (such as {@code 'minecraft:zombie'}) matches {@code filter}. */
    public void modifyEntityLoot(Predicate<String> filter, Consumer<LootTableJS> consumer) {
        for (String tableId : knownTableIds("entities/")) {
            if (filter.test(tableId.replace("/entities/", ":"))) {
                modify(tableId, consumer);
            }
        }
    }

    private static List<String> knownTableIds(String prefix) {
        List<String> ids = new ArrayList<>();
        for (String id : new LootTableEventJS().getIds()) {
            int slash = id.indexOf('/');
            if (slash >= 0 && id.substring(slash + 1).startsWith(prefix)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String blockTableId(String blockId) {
        Identifier id = parseId(blockId);
        return id.getNamespace() + ":blocks/" + id.getPath();
    }

    private static String entityTableId(String entityId) {
        Identifier id = parseId(entityId);
        return id.getNamespace() + ":entities/" + id.getPath();
    }

    /** Expands a block tag to the {@code <namespace>:blocks/<path>} loot table ids of its blocks. */
    private static List<String> blockTableIdsForTag(String tag) {
        List<String> ids = new ArrayList<>();
        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(tag));
//? if >=26 {
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey)) {
            holder.unwrapKey().ifPresent(key -> {
                Identifier location = key.identifier();
                ids.add(location.getNamespace() + ":blocks/" + location.getPath());
            });
        }
//?} else {
        /*BuiltInRegistries.BLOCK.getTag(tagKey).ifPresent(holders -> {
            for (Holder<Block> holder : holders) {
                holder.unwrapKey().ifPresent(key -> {
                    Identifier location = key.location();
                    ids.add(location.getNamespace() + ":blocks/" + location.getPath());
                });
            }
        });
*///?}
        return ids;
    }

    /** Schedules {@code id} for removal on the next reload, replacing it with an empty table. */
    public void remove(String id) {
        Identifier location = parseId(id);
        PENDING_REMOVE.add(location);
        PENDING_SET.remove(location);
    }

    /**
     * NeoForge event handler that applies pending edits and records loaded tables as each
     * non-empty loot table is parsed during reload, before any gameplay code consumes it.
     */
    public static void onLootTableLoad(LootTableLoadEvent event) {
        Identifier id = event.getName();
        HolderLookup.Provider registries = registryProvider(event);
        REGISTRIES = registries;
        if (PENDING_REMOVE.contains(id)) {
            event.setTable(LootTable.EMPTY);
        } else {
            JsonObject json = PENDING_SET.get(id);
            if (json != null) {
                if (registries == null) {
                    NekoJS.LOGGER.error(
                            "[NEKO-2301] Loot table JSON replacement unavailable for {}: registry context is unavailable on this node; keeping the previous table",
                            id);
                } else {
                    event.setTable(parse(json, registries));
                }
            }
        }
        LOADED_TABLES.put(id, event.getTable());
    }

    private static HolderLookup.Provider registryProvider(LootTableLoadEvent event) {
//? if >=26 {
        return event.getRegistries();
//?} else {
        /*MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.reloadableRegistries().lookup() instanceof HolderLookup.Provider provider ? provider : null;
*///?}
    }

    private static LootTable parse(JsonObject json, HolderLookup.Provider registries) {
        return LootTable.DIRECT_CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow();
    }

    private static JsonObject toJsonObject(Object json) {
        if (json instanceof JsonObject object) {
            return object;
        }
        return LootTableJS.toJsonObject(json);
    }

    private static Identifier parseId(String id) {
        return id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("nekojs", id);
    }
}
//?}
