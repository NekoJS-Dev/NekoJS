package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ManagedCallbackSchemaRegistry {

    /** 契约 payload 字段的类型信息：PORTABLE 有可移植类型；NATIVE 类型不承诺。 */
    public enum ContractFieldKind {
        PORTABLE, NATIVE
    }

    public record ContractFieldType(ContractFieldKind kind, ApiTypeRef portType) {
        public static ContractFieldType portable(ApiTypeRef portType) {
            return new ContractFieldType(ContractFieldKind.PORTABLE, portType);
        }

        public static ContractFieldType nativeField() {
            return new ContractFieldType(ContractFieldKind.NATIVE, null);
        }
    }

    public record CallbackSchema(String displayName, Set<String> memberNames, Map<String, ContractFieldType> fieldTypes) {
        public CallbackSchema {
            memberNames = Set.copyOf(memberNames == null ? Set.of() : memberNames);
            fieldTypes = Map.copyOf(fieldTypes == null ? Map.of() : fieldTypes);
        }

        public CallbackSchema(String displayName, Set<String> memberNames) {
            this(displayName, memberNames, Map.of());
        }
    }

    // RISK-C7: {@code volatile} so {@link #install} can swap the whole map in a
    // single atomic write. Previously {@code SCHEMA.clear(); SCHEMA.putAll(next);}
    // was two steps and a concurrent {@link #resolve} could observe the map empty
    // in between. Inner maps are {@link ConcurrentHashMap} so
    // {@link #installContractEvents} can mutate them in place after install.
    private static volatile Map<String, Map<String, CallbackSchema>> SCHEMA = new ConcurrentHashMap<>();

    private ManagedCallbackSchemaRegistry() {}

    public static void install(Map<ScriptType, ApiSurfaceSnapshot> snapshotsByType) {
        Objects.requireNonNull(snapshotsByType, "snapshotsByType");
        Map<String, Map<String, CallbackSchema>> next = new ConcurrentHashMap<>();
        for (var entry : snapshotsByType.entrySet()) {
            ApiSurfaceSnapshot snapshot = entry.getValue();
            if (snapshot == null) continue;
            extractCallbackSchemas(snapshot, next);
        }
        // RISK-C7: single atomic publication of the fully-built map — no transient
        // empty window visible to {@link #resolve}/{@link #isKnownGroup}.
        SCHEMA = next;
    }

    /**
     * 把运行时反射的事件（{@code EventContractReflector} 从 {@code EventGroup} 派生）注入回调 schema。
     *
     * <p>键按 {@code (group, eventName)} 映射——与 {@link EventCallbackSourceValidator}
     * 的查询一致（现有 {@link #install(Map)} 从契约符号提取的映射键是
     * {@code (globalName, payloadTypeName)}，与事件查询键不匹配，实际不命中事件回调）。
     * 成员集合为契约 payload 字段名（PORTABLE/NATIVE 均为脚本可访问的属性名）。
     *
     * <p>契约字段是跨平台承诺：即使某平台事件类反射缺该成员（实现缺口），
     * 契约字段仍放行——这正是"契约即权威"的意义。
     */
    public static void installContractEvents(List<NormativeApiContract.ContractEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (var event : events) {
            Map<String, CallbackSchema> group =
                    SCHEMA.computeIfAbsent(event.group(), ignored -> new ConcurrentHashMap<>());
            Set<String> memberNames = new HashSet<>();
            Map<String, ContractFieldType> fieldTypes = new HashMap<>();
            for (var field : event.payload()) {
                memberNames.add(field.name());
                fieldTypes.put(field.name(), switch (field.kind()) {
                    case PORTABLE -> ContractFieldType.portable(field.portType());
                    case NATIVE -> ContractFieldType.nativeField();
                });
            }
            String displayName = event.group() + "." + event.name();
            group.put(event.name(), new CallbackSchema(displayName, memberNames, fieldTypes));
        }
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
