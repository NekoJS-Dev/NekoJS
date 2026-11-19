package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a command registration event.
 */
@Doc("Immutable portable snapshot of a command registration event.")
public final class StableCommandRegistrationEvent {
    private final String environment;

    /**
     * Creates an immutable command registration event snapshot.
     *
     * @param environment command selection environment name; null is normalized to an empty string
     */
    @Doc("Creates an immutable command registration event snapshot.")
    @Param(name = "environment", value = "command selection environment name; null is normalized to an empty string")
    public StableCommandRegistrationEvent(String environment) {
        this.environment = Objects.requireNonNullElse(environment, "");
    }

    /**
     * Returns the command registration environment name.
     *
     * @return non-null command environment name
     */
    @Doc("Returns the command registration environment name.")
    @Return("non-null command environment name")
    public String getEnvironment() {
        return environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableCommandRegistrationEvent that)) return false;
        return Objects.equals(this.environment, that.environment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(environment);
    }

    @Override
    public String toString() {
        return "StableCommandRegistrationEvent{" + "environment='" + environment + "'" + "}";
    }
}
