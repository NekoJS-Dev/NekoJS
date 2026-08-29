package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.ScriptTypeId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FrozenApiRegistry implements ApiRuntimeView {

    private final EnvironmentKey environmentKey;
    private final ApiEnvironmentSnapshot snapshot;
    private final Map<ApiSymbolId, ApiSymbol> symbolsById;
    private final Map<String, ApiSymbol> globals;
    private final Map<String, Map<String, ApiSymbol>> moduleExports;
    private final Map<ApiSymbolId, Map<String, ApiInvoker>> invokerIndex;

    FrozenApiRegistry(
            EnvironmentKey environmentKey,
            ApiEnvironmentSnapshot snapshot,
            Map<ApiSymbolId, ApiSymbol> symbolsById,
            Map<String, ApiSymbol> globals,
            Map<String, Map<String, ApiSymbol>> moduleExports,
            Map<ApiSymbolId, Map<String, ApiInvoker>> invokerIndex) {
        this.environmentKey = Objects.requireNonNull(environmentKey, "environmentKey");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.symbolsById = Map.copyOf(symbolsById);
        this.globals = Map.copyOf(globals);
        this.moduleExports = Map.copyOf(moduleExports);
        this.invokerIndex = Map.copyOf(invokerIndex);
    }

    public Optional<ApiSymbol> find(ApiSymbolId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(symbolsById.get(id));
    }

    @Override
    public Optional<ApiSymbol> findSymbol(ApiSymbolId id) {
        return find(id);
    }

    public ApiSymbol require(ApiSymbolId id) {
        return find(id).orElseThrow(() ->
                new com.tkisor.nekojs.api.surface.ApiResolutionException("SYMBOL_NOT_FOUND",
                        "Symbol not found: " + id,
                        Map.of("symbolId", id.value())));
    }

    public Map<String, ApiSymbol> globals(ScriptType type) {
        Objects.requireNonNull(type, "type");
        ScriptTypeId scriptTypeId = ScriptTypeId.fromScriptType(type);
        if (environmentKey.scriptType() != scriptTypeId) {
            return Map.of();
        }
        return globals;
    }

    public Map<String, ApiSymbol> globalsByScriptTypeId(ScriptTypeId scriptTypeId) {
        Objects.requireNonNull(scriptTypeId, "scriptTypeId");
        if (environmentKey.scriptType() != scriptTypeId) {
            return Map.of();
        }
        return globals;
    }

    public Map<String, ApiSymbol> moduleExports(String moduleId, ScriptType type) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(type, "type");
        ScriptTypeId scriptTypeId = ScriptTypeId.fromScriptType(type);
        if (environmentKey.scriptType() != scriptTypeId) {
            return Map.of();
        }
        return moduleExports.getOrDefault(moduleId, Map.of());
    }

    public Map<String, ApiSymbol> moduleExportsByScriptTypeId(String moduleId, ScriptTypeId scriptTypeId) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(scriptTypeId, "scriptTypeId");
        if (environmentKey.scriptType() != scriptTypeId) {
            return Map.of();
        }
        return moduleExports.getOrDefault(moduleId, Map.of());
    }

    public ApiInvoker invoker(ApiSymbolId memberId, String signatureKey) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(signatureKey, "signatureKey");

        Map<String, ApiInvoker> invokers = invokerIndex.get(memberId);
        if (invokers == null) {
            throw new com.tkisor.nekojs.api.surface.ApiResolutionException("INVOKER_NOT_FOUND",
                    "No invokers found for symbol " + memberId,
                    Map.of("symbolId", memberId.value()));
        }

        ApiInvoker invoker = invokers.get(signatureKey);
        if (invoker == null) {
            throw new com.tkisor.nekojs.api.surface.ApiResolutionException("INVOKER_NOT_FOUND",
                    "No invoker found for signature " + signatureKey + " in symbol " + memberId,
                    Map.of("symbolId", memberId.value(), "signatureKey", signatureKey));
        }

        return invoker;
    }

    @Override
    public Map<String, ApiSymbol> symbolsByJsName(com.tkisor.nekojs.api.surface.ScriptTypeId type) {
        Objects.requireNonNull(type, "type");
        if (environmentKey.scriptType() != type) {
            return Map.of();
        }
        Map<String, ApiSymbol> result = new LinkedHashMap<>();
        result.putAll(globals);
        for (Map<String, ApiSymbol> exports : moduleExports.values()) {
            result.putAll(exports);
        }
        return result;
    }

    @Override
    public ApiEnvironmentSnapshot environmentSnapshot() {
        return snapshot;
    }

    @Override
    public Set<String> memberNames(com.tkisor.nekojs.api.surface.ApiSymbolId typeId) {
        Objects.requireNonNull(typeId, "typeId");
        return symbolsById.keySet().stream()
                .filter(id -> id.kind().equals("member")
                        && id.qualifiedName().startsWith(typeId.qualifiedName() + "."))
                .map(id -> {
                    String qualifiedName = id.qualifiedName();
                    int dotIndex = qualifiedName.indexOf('.', typeId.qualifiedName().length() + 1);
                    return dotIndex > 0 ? qualifiedName.substring(typeId.qualifiedName().length() + 1, dotIndex)
                            : qualifiedName.substring(typeId.qualifiedName().length() + 1);
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Object invoke(com.tkisor.nekojs.api.surface.ApiSymbolId memberId, String signatureKey,
                         Object receiver, List<Object> arguments) throws Exception {
        ApiInvoker invoker = invoker(memberId, signatureKey);
        return invoker.invoke(receiver, arguments);
    }

    public EnvironmentKey environmentKey() {
        return environmentKey;
    }
}
