package dev.latvian.mods.kubejs.event;

/**
 * Test-only binary marker for the non-cancellable respawn path.
 * Legacy Rhino cancellation and exit descriptors are deliberately not implemented.
 */
public interface KubeEvent {
    default void afterPosted(EventResult result) {
        throw new UnsupportedOperationException("NEKO-NEKU-PROOF-SCOPE: legacy afterPosted is outside the respawn proof");
    }
}
