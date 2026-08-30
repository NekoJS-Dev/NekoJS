package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ApiRuntimeView {
    ApiEnvironmentSnapshot environmentSnapshot();
    Set<String> memberNames(ApiSymbolId typeId);
    Optional<ApiSymbol> findSymbol(ApiSymbolId id);
    Map<String, ApiSymbol> symbolsByJsName(ScriptTypeId type);
    Object invoke(ApiSymbolId memberId, String signatureKey, Object receiver, List<Object> arguments) throws Exception;
}
