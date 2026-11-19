package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Mutable handle for reading, granting, revoking, and synchronizing stages on a player or server.
 */
@Doc("Mutable handle for reading, granting, revoking, and synchronizing stages on a player or server.")
public interface StageHandle extends StageReadView {

    /**
     * No-op stage handle returned for fake, absent, or unresolved players/servers.
     */
    @HideFromJS
    StageHandle EMPTY = new StageHandle() {
        @Override
        public boolean has(String stage) {
            return false;
        }

        @Override
        public boolean hasAny(@Nullable Collection<String> stages) {
            return false;
        }

        @Override
        public boolean hasAll(@Nullable Collection<String> stages) {
            return stages == null || stages.isEmpty();
        }

        @Override
        public List<String> getAll() {
            return List.of();
        }

        @Override
        public long remainingTicks(String stage) {
            return 0L;
        }

        @Override
        public boolean isTemporary(String stage) {
            return false;
        }

        @Override
        public boolean add(String stage) {
            return false;
        }

        @Override
        public boolean add(String stage, StageNotifyOptions options) {
            return false;
        }

        @Override
        public boolean addAll(Collection<String> stages) {
            return false;
        }

        @Override
        public boolean addAll(Collection<String> stages, StageNotifyOptions options) {
            return false;
        }

        @Override
        public boolean grantTemporary(String stage, long ticks) {
            return false;
        }

        @Override
        public boolean grantTemporary(String stage, long ticks, StageNotifyOptions options) {
            return false;
        }

        @Override
        public boolean remove(String stage) {
            return false;
        }

        @Override
        public boolean remove(String stage, StageNotifyOptions options) {
            return false;
        }

        @Override
        public boolean removeAll(Collection<String> stages) {
            return false;
        }

        @Override
        public boolean removeAll(Collection<String> stages, StageNotifyOptions options) {
            return false;
        }

        @Override
        public boolean set(String stage, boolean enabled) {
            return false;
        }

        @Override
        public boolean toggle(String stage) {
            return false;
        }

        @Override
        public boolean clear() {
            return false;
        }

        @Override
        public boolean replace(Collection<String> stages) {
            return false;
        }

        @Override
        public boolean sync() {
            // No-op for empty handle.
            return false;
        }
    };

    /**
     * Wraps a {@link StageReadView} into a client-side handle that permits reads and rejects all mutations.
     *
     * @param delegate read view backing client stage queries (falls back to {@link #EMPTY} if null)
     * @return read-only client stage handle
     */
    @HideFromJS
    static StageHandle clientReadOnly(@Nullable StageReadView delegate) {
        StageReadView readView = delegate != null ? delegate : EMPTY;
        return new StageHandle() {
            private static IllegalStateException rejectClientMutation() {
                return new IllegalStateException(
                        "[NEKO-4070] Cannot mutate stages on a client player handle; mutate stages on the server");
            }

            @Override
            public boolean has(String stage) {
                return readView.has(stage);
            }

            @Override
            public boolean hasAny(@Nullable Collection<String> stages) {
                return readView.hasAny(stages);
            }

            @Override
            public boolean hasAll(@Nullable Collection<String> stages) {
                return readView.hasAll(stages);
            }

            @Override
            public List<String> getAll() {
                return readView.getAll();
            }

            @Override
            public long remainingTicks(String stage) {
                return readView.remainingTicks(stage);
            }

            @Override
            public boolean isTemporary(String stage) {
                return readView.isTemporary(stage);
            }

            @Override
            public boolean add(String stage) {
                throw rejectClientMutation();
            }

            @Override
            public boolean add(String stage, StageNotifyOptions options) {
                throw rejectClientMutation();
            }

            @Override
            public boolean addAll(Collection<String> stages) {
                throw rejectClientMutation();
            }

            @Override
            public boolean addAll(Collection<String> stages, StageNotifyOptions options) {
                throw rejectClientMutation();
            }

            @Override
            public boolean grantTemporary(String stage, long ticks) {
                throw rejectClientMutation();
            }

            @Override
            public boolean grantTemporary(String stage, long ticks, StageNotifyOptions options) {
                throw rejectClientMutation();
            }

            @Override
            public boolean remove(String stage) {
                throw rejectClientMutation();
            }

            @Override
            public boolean remove(String stage, StageNotifyOptions options) {
                throw rejectClientMutation();
            }

            @Override
            public boolean removeAll(Collection<String> stages) {
                throw rejectClientMutation();
            }

            @Override
            public boolean removeAll(Collection<String> stages, StageNotifyOptions options) {
                throw rejectClientMutation();
            }

            @Override
            public boolean set(String stage, boolean enabled) {
                throw rejectClientMutation();
            }

            @Override
            public boolean toggle(String stage) {
                throw rejectClientMutation();
            }

            @Override
            public boolean clear() {
                throw rejectClientMutation();
            }

            @Override
            public boolean replace(Collection<String> stages) {
                throw rejectClientMutation();
            }

            @Override
            public boolean sync() {
                throw rejectClientMutation();
            }
        };
    }

    /**
     * Grants a stage using default notification rules.
     *
     * @param stage stage identifier
     * @return true if the stage set changed
     */
    @Doc("Grants a stage using default notification rules.")
    @Param(name = "stage", value = "stage identifier")
    @Return("true if the stage set changed")
    default boolean add(String stage) {
        return add(stage, StageNotifyOptions.DEFAULT);
    }

    /**
     * Grants a stage with explicit notification options.
     *
     * @param stage   stage identifier
     * @param options notification overrides
     * @return true if the stage set changed
     */
    @Doc("Grants a stage with explicit notification options.")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "options", value = "notification overrides")
    @Return("true if the stage set changed")
    boolean add(String stage, StageNotifyOptions options);

    /**
     * Grants all stages in the collection using default notification rules.
     *
     * @param stages stage identifiers to grant
     * @return true if at least one stage changed
     */
    @Doc("Grants all stages in the collection using default notification rules.")
    @Param(name = "stages", value = "stage identifiers to grant")
    @Return("true if at least one stage changed")
    default boolean addAll(Collection<String> stages) {
        return addAll(stages, StageNotifyOptions.DEFAULT);
    }

    /**
     * Grants all stages in the collection with explicit notification options.
     *
     * @param stages  stage identifiers to grant
     * @param options notification overrides
     * @return true if at least one stage changed
     */
    @Doc("Grants all stages in the collection with explicit notification options.")
    @Param(name = "stages", value = "stage identifiers to grant")
    @Param(name = "options", value = "notification overrides")
    @Return("true if at least one stage changed")
    boolean addAll(Collection<String> stages, StageNotifyOptions options);

    /**
     * Grants a temporary stage for {@code ticks} server ticks using default notification rules.
     *
     * @param stage stage identifier
     * @param ticks positive duration in ticks
     * @return true if the stage state changed
     */
    @Doc("Grants a temporary stage for the given number of ticks using default notification rules.")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "ticks", value = "positive duration in ticks")
    @Return("true if the stage state changed")
    default boolean grantTemporary(String stage, long ticks) {
        return grantTemporary(stage, ticks, StageNotifyOptions.DEFAULT);
    }

    /**
     * Grants a temporary stage for {@code ticks} server ticks with explicit notification options.
     *
     * @param stage   stage identifier
     * @param ticks   positive duration in ticks
     * @param options notification overrides
     * @return true if the stage state changed
     */
    @Doc("Grants a temporary stage for the given number of ticks with explicit notification options.")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "ticks", value = "positive duration in ticks")
    @Param(name = "options", value = "notification overrides")
    @Return("true if the stage state changed")
    boolean grantTemporary(String stage, long ticks, StageNotifyOptions options);

    /**
     * Revokes a stage using default notification rules.
     *
     * @param stage stage identifier
     * @return true if the stage was present and removed
     */
    @Doc("Revokes a stage using default notification rules.")
    @Param(name = "stage", value = "stage identifier")
    @Return("true if the stage was present and removed")
    default boolean remove(String stage) {
        return remove(stage, StageNotifyOptions.DEFAULT);
    }

    /**
     * Revokes a stage with explicit notification options.
     *
     * @param stage   stage identifier
     * @param options notification overrides
     * @return true if the stage was present and removed
     */
    @Doc("Revokes a stage with explicit notification options.")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "options", value = "notification overrides")
    @Return("true if the stage was present and removed")
    boolean remove(String stage, StageNotifyOptions options);

    /**
     * Revokes all stages in the collection using default notification rules.
     *
     * @param stages stage identifiers to revoke
     * @return true if at least one stage was removed
     */
    @Doc("Revokes all stages in the collection using default notification rules.")
    @Param(name = "stages", value = "stage identifiers to revoke")
    @Return("true if at least one stage was removed")
    default boolean removeAll(Collection<String> stages) {
        return removeAll(stages, StageNotifyOptions.DEFAULT);
    }

    /**
     * Revokes all stages in the collection with explicit notification options.
     *
     * @param stages  stage identifiers to revoke
     * @param options notification overrides
     * @return true if at least one stage was removed
     */
    @Doc("Revokes all stages in the collection with explicit notification options.")
    @Param(name = "stages", value = "stage identifiers to revoke")
    @Param(name = "options", value = "notification overrides")
    @Return("true if at least one stage was removed")
    boolean removeAll(Collection<String> stages, StageNotifyOptions options);

    /**
     * Enables or disables a stage according to {@code enabled}.
     *
     * @param stage   stage identifier
     * @param enabled true to grant, false to revoke
     * @return true if the stage state changed
     */
    @Doc("Enables or disables a stage according to the boolean flag.")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "enabled", value = "true to grant, false to revoke")
    @Return("true if the stage state changed")
    default boolean set(String stage, boolean enabled) {
        return enabled ? add(stage) : remove(stage);
    }

    /**
     * Toggles whether the stage is currently granted.
     *
     * @param stage stage identifier
     * @return true if the stage state changed
     */
    @Doc("Toggles whether the stage is currently granted.")
    @Param(name = "stage", value = "stage identifier")
    @Return("true if the stage state changed")
    default boolean toggle(String stage) {
        return set(stage, !has(stage));
    }

    /**
     * Removes all stages from this target.
     *
     * @return true if at least one stage was cleared
     */
    @Doc("Removes all stages from this target.")
    @Return("true if at least one stage was cleared")
    boolean clear();

    /**
     * Replaces the entire stage set with {@code stages}.
     *
     * @param stages new collection of active stage identifiers
     * @return true if the resulting stage set differs from the previous state
     */
    @Doc("Replaces the entire stage set with the given collection of stages.")
    @Param(name = "stages", value = "new collection of active stage identifiers")
    @Return("true if the resulting stage set differs from the previous state")
    boolean replace(Collection<String> stages);

    /**
     * Synchronizes the current stage snapshot to relevant connected clients.
     */
    @Doc("Synchronizes the current stage snapshot to relevant connected clients.")
    boolean sync();
}
