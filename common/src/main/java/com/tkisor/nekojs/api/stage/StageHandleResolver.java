package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Internal resolver bridge allowing {@code com.tkisor.nekojs.api.stage} and {@code com.tkisor.nekojs.api.event}
 * to obtain live {@link StageHandle} instances without importing {@code core.stage}.
 */
@HideFromJS
public final class StageHandleResolver {

    private static volatile Function<StagePlayerRef, StageHandle> playerResolver = ref -> StageHandle.EMPTY;
    private static volatile Supplier<StageHandle> serverResolver = () -> StageHandle.EMPTY;

    private StageHandleResolver() {
    }

    /**
     * Installs runtime resolvers for player and server stage handles.
     *
     * @param player resolver mapping a {@link StagePlayerRef} to its {@link StageHandle}
     * @param server supplier returning the active server {@link StageHandle}
     */
    @HideFromJS
    public static void install(
            @Nullable Function<StagePlayerRef, StageHandle> player,
            @Nullable Supplier<StageHandle> server) {
        playerResolver = player != null ? player : (ref -> StageHandle.EMPTY);
        serverResolver = server != null ? server : (() -> StageHandle.EMPTY);
    }

    /**
     * Resets both resolvers back to {@link StageHandle#EMPTY}.
     */
    @HideFromJS
    public static void reset() {
        playerResolver = ref -> StageHandle.EMPTY;
        serverResolver = () -> StageHandle.EMPTY;
    }

    /**
     * Resolves a {@link StageHandle} for the given player reference, returning {@link StageHandle#EMPTY}
     * when {@code ref} is null, fake, or unbound.
     *
     * @param ref player stage reference
     * @return non-null stage handle
     */
    @HideFromJS
    public static StageHandle resolvePlayer(@Nullable StagePlayerRef ref) {
        if (ref == null || ref.fake() || ref.uuid().isBlank()) {
            return StageHandle.EMPTY;
        }
        StageHandle resolved = playerResolver.apply(ref);
        return resolved != null ? resolved : StageHandle.EMPTY;
    }

    /**
     * Resolves the global server {@link StageHandle}, returning {@link StageHandle#EMPTY} when unbound.
     *
     * @return non-null server stage handle
     */
    @HideFromJS
    public static StageHandle resolveServer() {
        StageHandle resolved = serverResolver.get();
        return resolved != null ? resolved : StageHandle.EMPTY;
    }
}
