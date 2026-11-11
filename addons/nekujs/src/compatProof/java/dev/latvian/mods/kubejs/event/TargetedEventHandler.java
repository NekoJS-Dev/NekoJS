package dev.latvian.mods.kubejs.event;

import java.util.Objects;

/** Target definitions preserve metadata; targeted dispatch is explicitly outside this proof. */
public class TargetedEventHandler<T> extends EventHandler {
    TargetedEventHandler(EventHandler previous, EventTargetType<T> type) {
        super(previous.group, previous.name, previous.proofPhase(), previous.eventType);
        if (previous.proofHasResult()) {
            hasResult();
        }
        target = Objects.requireNonNull(type, "type");
        targetRequired = true;
    }
}
