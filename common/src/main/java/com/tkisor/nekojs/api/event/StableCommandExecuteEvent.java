package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a command parse or execution event.
 */
@Doc("Immutable portable snapshot of a command parse or execution event.")
public final class StableCommandExecuteEvent {
    private final String input;
    private final String sourceName;

    /**
     * Creates an immutable command execution event snapshot.
     *
     * @param input raw command input string; null is normalized to an empty string
     * @param sourceName command source text name; null is normalized to an empty string
     */
    @Doc("Creates an immutable command execution event snapshot.")
    @Param(name = "input", value = "raw command input string; null is normalized to an empty string")
    @Param(name = "sourceName", value = "command source text name; null is normalized to an empty string")
    public StableCommandExecuteEvent(String input, String sourceName) {
        this.input = Objects.requireNonNullElse(input, "");
        this.sourceName = Objects.requireNonNullElse(sourceName, "");
    }

    /**
     * Returns the raw command input string.
     *
     * @return non-null command input string
     */
    @Doc("Returns the raw command input string.")
    @Return("non-null command input string")
    public String getInput() {
        return input;
    }

    /**
     * Returns the command source display or text name.
     *
     * @return non-null command source name
     */
    @Doc("Returns the command source display or text name.")
    @Return("non-null command source name")
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableCommandExecuteEvent that)) return false;
        return Objects.equals(this.input, that.input)
                && Objects.equals(this.sourceName, that.sourceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, sourceName);
    }

    @Override
    public String toString() {
        return "StableCommandExecuteEvent{"
                + "input='" + input + "'"
                + ", sourceName='" + sourceName + "'"
                + "}";
    }
}
