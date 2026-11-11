package dev.latvian.mods.kubejs.event;

/** Only successful, non-cancellable dispatch is represented by this test-only result. */
public final class EventResult {
    public static final EventResult PASS = new EventResult();

    private EventResult() {
    }
}
