package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Read-only view of granted permanent and temporary stages for a player or server.
 */
@Doc("Read-only view of granted permanent and temporary stages for a player or server.")
public interface StageReadView {

    /**
     * Checks whether the given stage is currently active.
     *
     * @param stage stage identifier
     * @return true if the stage is active
     */
    @Doc("Checks whether the given stage is currently active.")
    @Param(name = "stage", value = "stage identifier")
    @Return("true if the stage is active")
    boolean has(String stage);

    /**
     * Checks whether at least one stage in {@code stages} is currently active.
     * Returns {@code false} when {@code stages} is null or empty.
     *
     * @param stages collection of stage identifiers to check
     * @return true if at least one listed stage is active
     */
    @Doc("Checks whether at least one stage in the collection is currently active. Returns false when null or empty.")
    @Param(name = "stages", value = "collection of stage identifiers to check")
    @Return("true if at least one listed stage is active")
    default boolean hasAny(@Nullable Collection<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return false;
        }
        for (String stage : stages) {
            if (stage != null && has(stage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether every stage in {@code stages} is currently active.
     * Returns {@code true} when {@code stages} is null or empty.
     *
     * @param stages collection of stage identifiers to check
     * @return true if all listed stages are active (or if the collection is null/empty)
     */
    @Doc("Checks whether every stage in the collection is currently active. Returns true when null or empty.")
    @Param(name = "stages", value = "collection of stage identifiers to check")
    @Return("true if all listed stages are active")
    default boolean hasAll(@Nullable Collection<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return true;
        }
        for (String stage : stages) {
            if (stage == null || !has(stage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns an immutable snapshot of all currently active stage identifiers.
     *
     * @return list of active stage identifiers
     */
    @Doc("Returns an immutable snapshot of all currently active stage identifiers.")
    @Return("list of active stage identifiers")
    List<String> getAll();

    /**
     * Returns remaining ticks for {@code stage}: {@code 0L} if absent, {@code -1L} if permanent,
     * or {@code > 0L} if temporary.
     *
     * @param stage stage identifier
     * @return {@code 0L} when absent, {@code -1L} when permanent, or positive remaining tick count when temporary
     */
    @Doc("Returns remaining ticks for the stage: 0 if absent, -1 if permanent, or > 0 if temporary.")
    @Param(name = "stage", value = "stage identifier")
    @Return("0 if absent, -1 if permanent, or positive remaining ticks if temporary")
    long remainingTicks(String stage);

    /**
     * Checks whether {@code stage} is currently active as a temporary expiring stage.
     *
     * @param stage stage identifier
     * @return true if the stage is currently active and temporary
     */
    @Doc("Checks whether the stage is currently active as a temporary expiring stage.")
    @Param(name = "stage", value = "stage identifier")
    @Return("true if the stage is active and temporary")
    default boolean isTemporary(String stage) {
        return remainingTicks(stage) > 0L;
    }
}
