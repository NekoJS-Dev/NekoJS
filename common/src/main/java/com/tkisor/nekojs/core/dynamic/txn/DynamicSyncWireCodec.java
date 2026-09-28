package com.tkisor.nekojs.core.dynamic.txn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JSON codec of the Dynamic Registry transaction wire (ticket 21 platform wiring):
 * encodes/decodes the server→client {@link DynamicSyncMessage} and the client→server
 * {@link DynamicSyncReply} into the single JSON string body of the existing play-phase
 * payload channel (ticket 17 register-once discipline — no second channel, no second
 * model). Lives in common so the JVM-verified protocol and the platform payload share
 * one serialization; no Minecraft/loader type is referenced.
 *
 * <p>Wire shape (schema {@code v:1}, field order fixed by this writer):
 * <pre>
 * s2c: {"v":1,"kind":"PREPARE|STATE_SYNC|COMMIT|ABORT","generation":12,"reason":"...",
 *        "entries":[{"definition":{"type":"ITEM","id":"mymod:ruby","mode":"WORLD",
 *                     "readings":["maxStackSize=16", ...],"fingerprint":"ab..."},
 *                    "owner":"server_scripts/main.js"}]}
 * c2s: {"v":1,"kind":"ACK","generation":12,"accepted":true,"reason":null}
 *      {"v":1,"kind":"ACTIVATION_REPORT","generation":12,"activated":true,"detail":null}
 * </pre>
 *
 * <p>Decoding is strict (malformed input throws {@link IllegalArgumentException} with an
 * actionable message): unknown schema version, unknown message kind, unknown candidate
 * type or register mode, and missing scalar fields are all rejected instead of silently
 * coerced. The receive side treats a decode failure as a dropped/recorded event per the
 * ticket 17 defense-line discipline — it never propagates into the platform network
 * thread.
 */
public final class DynamicSyncWireCodec {

    /** Current wire schema version (a future incompatible change must bump this). */
    public static final int SCHEMA_VERSION = 1;

    private DynamicSyncWireCodec() {}

    // ---- server → client ----

    /** Serializes a server message (PREPARE / STATE_SYNC / COMMIT / ABORT). */
    public static String encodeServerMessage(DynamicSyncMessage message) {
        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("kind", message.kind().name());
        root.addProperty("generation", message.generation());
        if (message.reason() != null) {
            root.addProperty("reason", message.reason());
        }
        JsonArray entries = new JsonArray();
        for (DynamicSyncMessage.Entry entry : message.entries()) {
            entries.add(encodeEntry(entry));
        }
        root.add("entries", entries);
        return root.toString();
    }

    /** Decodes a server message; strict — see class doc. */
    public static DynamicSyncMessage decodeServerMessage(String json) {
        JsonObject root = parseObject(json);
        DynamicSyncMessage.Kind kind = parseKind(requireString(root, "kind"));
        long generation = requireLong(root, "generation");
        String reason = optionalString(root, "reason");
        List<DynamicSyncMessage.Entry> entries = new ArrayList<>();
        if (root.has("entries")) {
            for (JsonElement element : requireArray(root, "entries")) {
                entries.add(decodeEntry(element));
            }
        }
        switch (kind) {
            case PREPARE: return new DynamicSyncMessage(DynamicSyncMessage.Kind.PREPARE, generation, entries, reason);
            case STATE_SYNC: return new DynamicSyncMessage(DynamicSyncMessage.Kind.STATE_SYNC, generation, entries, reason);
            case COMMIT:
                if (!entries.isEmpty()) {
                    throw malformed("a COMMIT message carries no entries; got " + entries.size());
                }
                return new DynamicSyncMessage(DynamicSyncMessage.Kind.COMMIT, generation, entries, reason);
            case ABORT: return new DynamicSyncMessage(DynamicSyncMessage.Kind.ABORT, generation, entries, reason);
            default: throw malformed("unknown message kind '" + kind + "'");
        }
    }

    // ---- client → server ----

    /** Serializes a client reply (ACK / ACTIVATION_REPORT). */
    public static String encodeReply(DynamicSyncReply reply) {
        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("kind", reply.kind().name());
        root.addProperty("generation", reply.generation());
        if (reply.kind() == DynamicSyncReply.Kind.ACK) {
            root.addProperty("accepted", reply.accepted());
            if (reply.reason() != null) {
                root.addProperty("reason", reply.reason());
            }
        } else {
            root.addProperty("activated", reply.activated());
            if (reply.detail() != null) {
                root.addProperty("detail", reply.detail());
            }
        }
        return root.toString();
    }

    /** Decodes a client reply; strict — see class doc. */
    public static DynamicSyncReply decodeReply(String json) {
        JsonObject root = parseObject(json);
        String kindName = requireString(root, "kind");
        long generation = requireLong(root, "generation");
        if ("ACK".equals(kindName)) {
            Boolean accepted = requireBoolean(root, "accepted");
            return DynamicSyncReply.ack(generation, accepted, optionalString(root, "reason"));
        }
        if ("ACTIVATION_REPORT".equals(kindName)) {
            Boolean activated = requireBoolean(root, "activated");
            return DynamicSyncReply.activationReport(generation, activated, optionalString(root, "detail"));
        }
        throw malformed("unknown reply kind '" + kindName + "'");
    }

    // ---- entry (de)serialization ----

    private static JsonObject encodeEntry(DynamicSyncMessage.Entry entry) {
        DynamicDefinition definition = entry.definition();
        JsonObject definitionJson = new JsonObject();
        definitionJson.addProperty("type", definition.type().name());
        definitionJson.addProperty("id", definition.id());
        definitionJson.addProperty("mode", definition.mode().name());
        JsonArray readings = new JsonArray();
        definition.readings().forEach(readings::add);
        definitionJson.add("readings", readings);
        definitionJson.addProperty("fingerprint", definition.fingerprint());

        JsonObject entryJson = new JsonObject();
        entryJson.add("definition", definitionJson);
        entryJson.addProperty("owner", entry.ownerScriptId());
        return entryJson;
    }

    private static DynamicSyncMessage.Entry decodeEntry(JsonElement element) {
        JsonObject entryJson = elementAsObject(element, "entry");
        JsonObject definitionJson = childObject(entryJson, "definition");
        DynamicDefinitionType type = parseType(requireString(definitionJson, "type"));
        String id = requireString(definitionJson, "id");
        DynamicRegisterMode mode = parseMode(requireString(definitionJson, "mode"));
        List<String> readings = new ArrayList<>();
        if (definitionJson.has("readings")) {
            for (JsonElement reading : requireArray(definitionJson, "readings")) {
                if (!reading.isJsonPrimitive() || !reading.getAsJsonPrimitive().isString()) {
                    throw malformed("definition readings must be strings: " + reading);
                }
                readings.add(reading.getAsString());
            }
        }
        String fingerprint = requireString(definitionJson, "fingerprint");
        String owner = requireString(entryJson, "owner");
        DynamicDefinition definition = new DynamicDefinition(type, id, mode, readings, fingerprint);
        return new DynamicSyncMessage.Entry(definition, owner);
    }

    // ---- strict parsing helpers ----

    private static JsonObject parseObject(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw malformed("not valid JSON: " + e.getMessage());
        }
        JsonObject object = elementAsObject(root, "root");
        long version = requireLong(object, "v");
        if (version != SCHEMA_VERSION) {
            throw malformed("unsupported wire schema version " + version + " (expected " + SCHEMA_VERSION + ")");
        }
        return object;
    }

    private static JsonObject elementAsObject(JsonElement element, String what) {
        if (element == null || !element.isJsonObject()) {
            throw malformed("expected a JSON object for '" + what + "'");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject childObject(JsonObject parent, String member) {
        if (!parent.has(member)) {
            throw malformed("missing '" + member + "' object");
        }
        return elementAsObject(parent.get(member), member);
    }

    private static String requireString(JsonObject object, String member) {
        if (!object.has(member) || !object.get(member).isJsonPrimitive()
                || !object.get(member).getAsJsonPrimitive().isString()) {
            throw malformed("missing or non-string '" + member + "'");
        }
        return object.get(member).getAsString();
    }

    private static long requireLong(JsonObject object, String member) {
        if (!object.has(member) || !object.get(member).isJsonPrimitive()
                || !object.get(member).getAsJsonPrimitive().isNumber()) {
            throw malformed("missing or non-numeric '" + member + "'");
        }
        return object.get(member).getAsLong();
    }

    private static Boolean requireBoolean(JsonObject object, String member) {
        if (!object.has(member) || !object.get(member).isJsonPrimitive()
                || !object.get(member).getAsJsonPrimitive().isBoolean()) {
            throw malformed("missing or non-boolean '" + member + "'");
        }
        return object.get(member).getAsBoolean();
    }

    private static String optionalString(JsonObject object, String member) {
        return object.has(member) && object.get(member).isJsonPrimitive()
                && object.get(member).getAsJsonPrimitive().isString()
                        ? object.get(member).getAsString() : null;
    }

    private static JsonArray requireArray(JsonObject object, String member) {
        if (!object.has(member) || !object.get(member).isJsonArray()) {
            throw malformed("missing or non-array '" + member + "'");
        }
        return object.get(member).getAsJsonArray();
    }

    private static DynamicSyncMessage.Kind parseKind(String name) {
        try {
            return DynamicSyncMessage.Kind.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw malformed("unknown message kind '" + name + "'");
        }
    }

    private static DynamicDefinitionType parseType(String name) {
        try {
            return DynamicDefinitionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw malformed("unknown candidate type '" + name
                    + "' (the frozen set is item/soundEvent/mobEffect)");
        }
    }

    private static DynamicRegisterMode parseMode(String name) {
        try {
            return DynamicRegisterMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw malformed("unknown register mode '" + name + "'");
        }
    }

    private static IllegalArgumentException malformed(String detail) {
        return new IllegalArgumentException(
                "Malformed dynamic registry sync message: " + detail.toLowerCase(Locale.ROOT));
    }
}
