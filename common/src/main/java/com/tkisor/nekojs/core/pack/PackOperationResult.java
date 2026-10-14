package com.tkisor.nekojs.core.pack;

import java.util.List;

/** Final outcome of a local enablement request, separate from script execution. */
public record PackOperationResult(Status status, String selector, List<String> matches) {
    public PackOperationResult {
        matches = List.copyOf(matches);
    }

    public enum Status {
        SAVED,
        NOT_FOUND,
        AMBIGUOUS,
        READ_ONLY,
        SAVE_FAILED
    }

    public boolean succeeded() {
        return status == Status.SAVED;
    }
}
