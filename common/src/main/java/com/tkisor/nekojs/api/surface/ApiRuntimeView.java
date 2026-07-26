package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Set;

public interface ApiRuntimeView {
    ApiEnvironmentSnapshot environmentSnapshot();
    Set<String> memberNames(ApiSymbolId typeId);
    Object invoke(ApiSymbolId memberId, String signatureKey, Object receiver, List<Object> arguments) throws Exception;
}
