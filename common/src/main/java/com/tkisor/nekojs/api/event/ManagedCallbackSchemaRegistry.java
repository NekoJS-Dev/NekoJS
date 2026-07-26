package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ManagedCallbackSchemaRegistry {

    public record CallbackSchema(String displayName, Set<String> memberNames) {
        public CallbackSchema {
            memberNames = Set.copyOf(memberNames == null ? Set.of() : memberNames);
        }
    }

    private static final Map<String, Map<String, CallbackSchema>> SCHEMA = new ConcurrentHashMap<>();

    private ManagedCallbackSchemaRegistry() {}

    public static void install(Map<ScriptType, ApiSurfaceSnapshot> snapshotsByType) {
        Objects.requireNonNull(snapshotsByType, "snapshotsByType");
        Map<String, Map<String, CallbackSchema>> next = new HashMap<>();
        for (var entry : snapshotsByType.entrySet()) {
            ApiSurfaceSnapshot snapshot = entry.getValue();
            if (snapshot == null) continue;
            extractCallbackSchemas(snapshot, next);
        }
        SCHEMA.clear();
        SCHEMA.putAll(next);
    }

    public static CallbackSchema resolve(String groupName, String eventName) {
        Map<String, CallbackSchema> group = SCHEMA.get(groupName);
        if (group == null) return null;
        return group.get(eventName);
    }

    public static boolean isKnownGroup(String groupName) {
        return SCHEMA.containsKey(groupName);
    }

    public static void clear() {
        SCHEMA.clear();
    }

    private static void extractCallbackSchemas(ApiSurfaceSnapshot snapshot, Map<String, Map<String, CallbackSchema>> target) {
        for (ApiSymbol symbol : snapshot.symbols()) {
            ApiSymbolId id = symbol.id();
            if (!id.kind().equals("global")) continue;
            String globalName = id.qualifiedName();
            for (ApiSignature sig : symbol.signatures()) {
                for (ApiParameter param : sig.parameters()) {
                    ApiTypeRef type = param.type();
                    if (type.kind() == ApiTypeRef.Kind.CALLBACK && type.callbackSignature() != null) {
                        ApiSignature cbSig = type.callbackSignature();
                        for (ApiParameter cbParam : cbSig.parameters()) {
                            ApiTypeRef payloadType = cbParam.type();
                            if (payloadType.kind() == ApiTypeRef.Kind.SYMBOL) {
                                ApiSymbolId payloadId = ApiSymbolId.parse(payloadType.name());
                                Set<String> members = snapshot.symbols().stream()
                                        .filter(s -> s.id().kind().equals("member")
                                                && s.id().qualifiedName().startsWith(payloadId.qualifiedName() + "."))
                                        .map(s -> {
                                            String qn = s.id().qualifiedName();
                                            int dot = qn.indexOf('.', payloadId.qualifiedName().length() + 1);
                                            return dot > 0
                                                    ? qn.substring(payloadId.qualifiedName().length() + 1, dot)
                                                    : qn.substring(payloadId.qualifiedName().length() + 1);
                                        })
                                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                                String displayName = payloadId.qualifiedName();
                                Map<String, CallbackSchema> events = target.computeIfAbsent(globalName, k -> new HashMap<>());
                                events.put(displayName, new CallbackSchema(displayName, members));
                            }
                        }
                    }
                }
            }
        }
    }
}
