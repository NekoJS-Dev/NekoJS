package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a client lifecycle or tick stage event.
 */
@Doc("Immutable portable snapshot of a client lifecycle or tick stage event.")
public final class StableClientEvent {
    private final String stage;

    /**
     * Creates an immutable client event snapshot.
     *
     * @param stage client event stage name; null is normalized to an empty string
     */
    @Doc("Creates an immutable client event snapshot.")
    @Param(name = "stage", value = "client event stage name; null is normalized to an empty string")
    public StableClientEvent(String stage) {
        this.stage = Objects.requireNonNullElse(stage, "");
    }

    /**
     * Returns the client event stage name.
     *
     * @return non-null client stage name
     */
    @Doc("Returns the client event stage name.")
    @Return("non-null client stage name")
    public String getStage() {
        return stage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableClientEvent that)) return false;
        return Objects.equals(this.stage, that.stage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stage);
    }

    @Override
    public String toString() {
        return "StableClientEvent{" + "stage='" + stage + "'" + "}";
    }
}
