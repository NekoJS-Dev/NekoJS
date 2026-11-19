package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a registry tag update notification.
 */
@Doc("Immutable portable snapshot of a registry tag update notification.")
public final class StableTagsUpdatedEvent {
    private final String cause;
    private final boolean clientPacket;

    /**
     * Creates an immutable tags-updated event snapshot.
     *
     * @param cause update cause name; null is normalized to an empty string
     * @param clientPacket true when tags arrived from a client network packet
     */
    @Doc("Creates an immutable tags-updated event snapshot.")
    @Param(name = "cause", value = "update cause name; null is normalized to an empty string")
    @Param(name = "clientPacket", value = "true when tags arrived from a client network packet")
    public StableTagsUpdatedEvent(String cause, boolean clientPacket) {
        this.cause = Objects.requireNonNullElse(cause, "");
        this.clientPacket = clientPacket;
    }

    /**
     * Returns the tag update cause name.
     *
     * @return non-null update cause name
     */
    @Doc("Returns the tag update cause name.")
    @Return("non-null update cause name")
    public String getCause() {
        return cause;
    }

    /**
     * Returns whether the tag update originated from a client sync packet.
     *
     * @return true when updated from a client packet
     */
    @Doc("Returns whether the tag update originated from a client sync packet.")
    @Return("true when updated from a client packet")
    public boolean isClientPacket() {
        return clientPacket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableTagsUpdatedEvent that)) return false;
        return Objects.equals(this.cause, that.cause)
                && this.clientPacket == that.clientPacket;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cause, clientPacket);
    }

    @Override
    public String toString() {
        return "StableTagsUpdatedEvent{"
                + "cause='" + cause + "'"
                + ", clientPacket=" + clientPacket
                + "}";
    }
}
