package com.tkisor.nekojs.api.recipe;

public interface IRecipeManagerExtension {
    void nekojs$applyScripts();

    /** Replays scripts and reports all observed callback and decode failures. Unsupported hosts fail closed. */
    default boolean nekojs$applyScriptsChecked() {
        return false;
    }

    /**
     * Captures this manager's replay baseline before an asynchronous resource reload. Inspect only
     * after that reload completes; success requires a completed replay and no intervening failure.
     * The observation is operation-local and must not be retained after completion.
     */
    default ReplayObservation nekojs$observeScriptReplays() {
        return () -> false;
    }

    /**
     * Summarizes this manager's fresh lifecycle after its resource reload completes. Use only when
     * that reload installed a different manager; existing managers require a captured baseline.
     */
    default boolean nekojs$scriptReplaysSucceededSinceCreation() {
        return false;
    }

    @FunctionalInterface
    interface ReplayObservation {
        boolean succeeded();
    }
}
