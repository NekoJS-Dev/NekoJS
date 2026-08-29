package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record LegacyGlobalReservation(String globalName, ApiSymbolId targetId) {
    public LegacyGlobalReservation {
        Objects.requireNonNull(globalName, "globalName");
        Objects.requireNonNull(targetId, "targetId");
    }
}
