package com.tkisor.nekojs.fabric;

/**
 * Clears only when the client leaves an existing world. NeoForge hooks client level unload, which
 * fires on both disconnect and dimension change; Fabric has no matching event, so watching the
 * client level instance is equivalent (a dimension change swaps the {@code ClientLevel} instance,
 * a disconnect makes it null).
 *
 * <p>The rule: report a clear only when leaving an existing world, meaning the last level seen was
 * non-null. The null-to-level change on the first join does not clear, because it would wipe the
 * data just pushed down on join (the pdata mirror and the clientData key-value store). Levels are
 * compared by identity, so every new level instance counts as a switch.
 *
 * <p>One instance per process, living as long as both hook points: {@code FabricPDataSync} holds
 * one for clearing the pdata mirror and {@code FabricPlayNetwork} holds one for clearing the
 * clientData store, so the two never interfere.
 */
public final class ClientLevelWatch {

    private Object lastLevel;

    /**
     * Feeds the current level instance (possibly null) once per client tick and returns whether the
     * client left its previous level. When it returns true the caller must clear its own client-side
     * mirror or store; when false, leave that data alone.
     */
    public boolean leftPreviousLevel(Object currentLevel) {
        if (currentLevel == lastLevel) return false;
        boolean leftPrevious = lastLevel != null;
        lastLevel = currentLevel;
        return leftPrevious;
    }
}
