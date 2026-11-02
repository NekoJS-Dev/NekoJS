package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import java.util.*;

/** Immutable query data detached from classes, guest contexts, and mutable Probe caches. */
public final class AuthoringSnapshot {
    private final JsonObject document;
    private final Map<String, List<JsonObject>> resources;
    private final Map<String, JsonObject> symbols;
    private final Map<String, List<JsonObject>> ownerMembers;

    public AuthoringSnapshot(JsonObject document) {
        this.document = document.deepCopy();
        if (!"1".equals(AuthoringJson.string(document, "schemaVersion", ""))) throw AuthoringJson.invalid("/schemaVersion", "Unsupported snapshot schema.");
        AuthoringJson.string(document, "environmentId", "");
        AuthoringJson.string(document, "snapshotId", "");
        Map<String, List<JsonObject>> resources = new LinkedHashMap<>();
        Map<String, JsonObject> symbols = new LinkedHashMap<>();
        JsonObject source = AuthoringJson.object(this.document, "resources", "");
        for (String name : List.of("symbols", "members", "events", "inputAdapters", "declarations")) {
            JsonElement entries = source.get(name);
            if (entries == null || !entries.isJsonArray()) throw AuthoringJson.invalid("/resources/" + name, "Missing snapshot resource.");
            List<JsonObject> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement item : entries.getAsJsonArray()) {
                if (!item.isJsonObject()) throw AuthoringJson.invalid("/resources/" + name, "Invalid snapshot record.");
                JsonObject record = item.getAsJsonObject();
                String id = AuthoringJson.string(record, "symbolId", "");
                if (!seen.add(id)) throw AuthoringJson.invalid("/resources/" + name, "Duplicate snapshot record ID.");
                list.add(record);
                if (name.equals("symbols")) symbols.put(id, record);
            }
            list.sort(Comparator.comparing(record -> record.get("symbolId").getAsString()));
            resources.put(name, List.copyOf(list));
        }
        this.resources = Map.copyOf(resources);
        this.symbols = Map.copyOf(symbols);
        Map<String, List<JsonObject>> owners = new LinkedHashMap<>();
        for (JsonObject record : resources.get("members")) {
            if (record.has("ownerSymbolId")) owners.computeIfAbsent(record.get("ownerSymbolId").getAsString(), key -> new ArrayList<>()).add(record);
        }
        Map<String, List<JsonObject>> frozen = new LinkedHashMap<>();
        owners.forEach((owner, records) -> frozen.put(owner, List.copyOf(records)));
        this.ownerMembers = Map.copyOf(frozen);
    }

    public String id() { return document.get("snapshotId").getAsString(); }
    public String environmentId() { return document.get("environmentId").getAsString(); }
    public JsonObject document() { return document.deepCopy(); }
    JsonObject symbol(String id) { return symbols.get(id); }
    List<JsonObject> records(String resource) {
        List<JsonObject> records = resources.get(resource);
        if (records == null) throw AuthoringJson.invalid("/from", "Unknown query resource.");
        return records;
    }
    List<JsonObject> members(String owner) { return ownerMembers.getOrDefault(owner, List.of()); }
    boolean complete() { return document.has("complete") && document.get("complete").getAsBoolean(); }
}
