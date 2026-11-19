package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Defines whether a stage requirement evaluates player stages, server stages, or a combination of both.
 */
@Doc("Defines whether a stage requirement evaluates player stages, server stages, or a combination of both.")
public enum StageScope {
    PLAYER("player"),
    SERVER("server"),
    EITHER("either"),
    BOTH("both");

    private final String id;

    StageScope(String id) {
        this.id = id;
    }

    /**
     * Returns the lowercase canonical identifier for this stage scope.
     *
     * @return canonical stage scope identifier
     */
    @Doc("Returns the lowercase canonical identifier for this stage scope.")
    @Return("canonical stage scope identifier")
    public String id() {
        return id;
    }

    /**
     * Resolves a stage scope from a string identifier, defaulting null or blank values to {@link #PLAYER}.
     *
     * @param raw raw scope string (case-insensitive; trimmed before matching)
     * @return resolved stage scope
     * @throws IllegalArgumentException if {@code raw} is non-blank and does not match a known scope
     */
    @Doc("Resolves a stage scope from a string identifier, defaulting null or blank values to PLAYER.")
    @Param(name = "raw", value = "raw scope string ('player', 'server', 'either', or 'both')")
    @Return("resolved stage scope")
    public static StageScope fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAYER;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "player" -> PLAYER;
            case "server" -> SERVER;
            case "either" -> EITHER;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException("[NEKO-4071] Unknown stage scope: " + raw);
        };
    }

    /**
     * Evaluates whether a stage requirement is satisfied for the given player and server state.
     *
     * @param playerHas whether the player possesses the stage
     * @param serverHas whether the server possesses the stage
     * @return true if the scope condition is satisfied
     */
    @Doc("Evaluates whether a stage requirement is satisfied for the given player and server state.")
    @Param(name = "playerHas", value = "whether the player possesses the stage")
    @Param(name = "serverHas", value = "whether the server possesses the stage")
    @Return("true if the scope condition is satisfied")
    public boolean evaluate(boolean playerHas, boolean serverHas) {
        return switch (this) {
            case PLAYER -> playerHas;
            case SERVER -> serverHas;
            case EITHER -> playerHas || serverHas;
            case BOTH -> playerHas && serverHas;
        };
    }
}
