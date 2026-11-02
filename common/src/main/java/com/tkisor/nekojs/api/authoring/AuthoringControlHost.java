package com.tkisor.nekojs.api.authoring;

import com.tkisor.nekojs.api.ScriptType;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** The live, owner-thread adapter for explicitly enabled authoring controls. */
public interface AuthoringControlHost {
    /**
     * Returns the current root/server lifetime identity without blocking. A changed identity invalidates
     * queued work. A negative identity means no live server is available. The identity must remain
     * stable while starting an owner-thread callback; asynchronous adapters must guard their own
     * later owner-thread handoffs against server replacement.
     */
    long epoch();

    /** Target-local lifetime identity; identities from different Script Types are not comparable. */
    default long epoch(ScriptType type) {
        return switch (type) {
            case SERVER -> epoch();
            case CLIENT, TEST, STARTUP -> -1;
        };
    }

    /**
     * Dispatches onto the actual Minecraft server owner thread, or runs inline when already there.
     * Never wait for the callback here. Throw before admission when dispatch is unavailable; never
     * throw after executing or admitting it. The callback must be invoked at most once.
     */
    void enqueue(Runnable operation);

    /** Dispatches to the selected Script Type's owner thread without waiting for it. */
    default void enqueue(ScriptType type, Runnable operation) {
        if (type != ScriptType.SERVER && type != ScriptType.TEST) {
            throw new UnsupportedOperationException("[NEKO-1019] The selected reload owner thread is unavailable.");
        }
        enqueue(operation);
    }

    /**
     * Executes a normalized command without a leading slash, using a fixed least-privilege non-player
     * source. Collect only this request's plain-text feedback, never global logs. The adapter must not
     * allow player impersonation or elevate privileges based on request data. Success is the actual
     * command outcome, not merely successful queueing.
     */
    CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) throws Exception;

    /**
     * Completes a full SERVER reload through the existing candidate transaction and recipe/resource
     * follow-ups. Complete the stage only when these finish; a queued task or command return value is
     * insufficient. Never block the server owner thread while waiting for asynchronous resource reload.
     * Report a committed candidate separately from failed postprocessing; do not claim rollback of
     * arbitrary script side effects. This legacy entry point handles SERVER only.
     */
    CompletionStage<ReloadResult> reloadServer(Consumer<String> output) throws Exception;

    /** Completes a full target reload and its follow-ups; adapters opt into CLIENT and TEST explicitly. */
    default CompletionStage<ReloadResult> reload(ScriptType type, Consumer<String> output) throws Exception {
        if (type != ScriptType.SERVER) {
            throw new UnsupportedOperationException("[NEKO-1019] The selected Script Type does not support live reload.");
        }
        return reloadServer(output);
    }

    record CommandResult(int returnValue, boolean success) {}

    record ReloadResult(boolean committed, boolean postprocessingSucceeded) {}
}
