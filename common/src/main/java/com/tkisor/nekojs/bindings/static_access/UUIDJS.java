package com.tkisor.nekojs.bindings.static_access;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UUIDJS {
    public UUID random() {
        return UUID.randomUUID();
    }

    public UUID fromString(String value) {
        return UUID.fromString(value);
    }

    public UUID fromName(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
