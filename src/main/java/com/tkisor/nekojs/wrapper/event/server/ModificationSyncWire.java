//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import com.tkisor.nekojs.wrapper.network.NetworkJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-to-client transport for the committed Item/Block modification plan. */
public final class ModificationSyncWire {
    public static final String CHANNEL = "item_block_modification_sync";
    private static final String DECLARATIONS = "declarations";
    private static final String GENERATION = "generation";
    private static final Gson GSON = new Gson();
    private static final Type DECLARATION_LIST = new TypeToken<List<Map<String, Object>>>() { }.getType();
    private static final ModificationDomainOwner CLIENT_OWNER = new ModificationDomainOwner();

    private ModificationSyncWire() {
    }

    public static void broadcast(long generation, List<ModificationDeclaration> declarations) {
        NetworkJS.sendToAll(CHANNEL, encode(generation, declarations));
    }

    public static void sendTo(ServerPlayer player, long generation, List<ModificationDeclaration> declarations) {
        NetworkJS.sendToPlayer(player, CHANNEL, encode(generation, declarations));
    }

    public static void handleClient(CompoundTag data) {
        try {
            long generation = data.getLongOr(GENERATION, -1L);
            List<ModificationDeclaration> declarations = decode(data.getStringOr(DECLARATIONS, ""));
            CLIENT_OWNER.preflight(declarations);
            CLIENT_OWNER.apply(declarations);
            NekoJS.LOGGER.info("Applied server Item/Block modification sync generation={} ({} declaration(s))",
                    generation, declarations.size());
        } catch (Throwable failure) {
            NekoJS.LOGGER.error("[NEKO-3011] Server Item/Block modification sync rejected", failure);
        }
    }

    static CompoundTag encode(long generation, List<ModificationDeclaration> declarations) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ModificationDeclaration declaration : declarations) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", declaration.kind());
            entry.put("targetId", declaration.targetId());
            entry.put("properties", declaration.properties());
            entry.put("scriptId", declaration.scriptId());
            wire.add(entry);
        }
        CompoundTag data = new CompoundTag();
        data.putLong(GENERATION, generation);
        data.putString(DECLARATIONS, GSON.toJson(wire));
        return data;
    }

    static List<ModificationDeclaration> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("empty modification sync");
        List<Map<String, Object>> values = GSON.fromJson(JsonParser.parseString(encoded), DECLARATION_LIST);
        if (values == null) throw new IllegalArgumentException("null modification sync");
        List<ModificationDeclaration> declarations = new ArrayList<>();
        for (Map<String, Object> value : values) {
            Object kind = value.get("kind");
            Object targetId = value.get("targetId");
            if (!(kind instanceof String) || !(targetId instanceof String)) {
                throw new IllegalArgumentException("modification sync entry requires kind and targetId");
            }
            Map<String, Object> properties = value.get("properties") instanceof Map<?, ?> map
                    ? normalizeProperties(map) : Map.of();
            declarations.add(new ModificationDeclaration((String) kind, (String) targetId, properties,
                    value.get("scriptId") instanceof String scriptId ? scriptId : null));
        }
        return List.copyOf(declarations);
    }

    private static Map<String, Object> normalizeProperties(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("modification sync property key must be a string");
            }
            normalized.put(key, normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Double number && Double.isFinite(number)
                && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                && number == Math.rint(number)) {
            return number.intValue();
        }
        if (value instanceof Map<?, ?> map) return normalizeProperties(map);
        return value;
    }
}
//?}
