package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.data.NekoId;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Immutable definition of customized presentation metadata, default notification channels,
 * temporary duration defaults, and lifecycle callbacks for a stage.
 *
 * @param stage         non-blank stage identifier
 * @param displayName   human-readable display title (empty string when unset)
 * @param description   stage description text (empty string when unset)
 * @param icon          optional item or texture resource identifier used as an icon
 * @param temporary     whether this stage defaults to temporary duration semantics
 * @param defaultTicks  default tick duration when {@code temporary} is true (must be {@code > 0})
 * @param showTitle     whether granting/revoking shows a title banner by default
 * @param showChat      whether granting/revoking sends a chat message by default
 * @param showActionBar whether granting/revoking displays an action-bar message by default
 * @param grantTitle    custom grant message/title template (empty string when unset)
 * @param revokeMessage custom revoke message template (empty string when unset)
 * @param whenGranted   optional callback invoked when the stage is granted
 * @param everyTick     optional callback invoked each server tick while the stage is active
 * @param whenExpired   optional callback invoked when a temporary stage expires
 */
@Doc("Immutable definition of customized metadata, notifications, and lifecycle callbacks for a stage.")
public record StageDefinition(
        String stage,
        String displayName,
        String description,
        @Nullable NekoId icon,
        boolean temporary,
        long defaultTicks,
        boolean showTitle,
        boolean showChat,
        boolean showActionBar,
        String grantTitle,
        String revokeMessage,
        @Nullable Consumer<StageTickContext> whenGranted,
        @Nullable Consumer<StageTickContext> everyTick,
        @Nullable Consumer<StageTickContext> whenExpired,
        String grantSubtitle,
        String grantChat,
        String grantActionBar,
        String revokeTitle,
        String revokeSubtitle,
        String revokeChat,
        String revokeActionBar,
        @Nullable NekoId sound) {

    public StageDefinition(
            String stage,
            String displayName,
            String description,
            @Nullable NekoId icon,
            boolean temporary,
            long defaultTicks,
            boolean showTitle,
            boolean showChat,
            boolean showActionBar,
            String grantTitle,
            String revokeMessage,
            @Nullable Consumer<StageTickContext> whenGranted,
            @Nullable Consumer<StageTickContext> everyTick,
            @Nullable Consumer<StageTickContext> whenExpired) {
        this(stage, displayName, description, icon, temporary, defaultTicks, showTitle, showChat, showActionBar,
                grantTitle, revokeMessage, whenGranted, everyTick, whenExpired, "", "", "", "", "", "", "", null);
    }

    /**
     * Validates stage identifier and temporary duration invariants and normalizes nullable text fields.
     */
    public StageDefinition {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4070] Stage identifier cannot be null or blank");
        }
        stage = stage.trim();
        if (temporary && defaultTicks <= 0L) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Temporary stage '" + stage + "' requires defaultTicks > 0, got: " + defaultTicks);
        }
        if (!temporary && defaultTicks < 0L) {
            defaultTicks = 0L;
        }
        displayName = Objects.requireNonNullElse(displayName, "");
        description = Objects.requireNonNullElse(description, "");
        grantTitle = Objects.requireNonNullElse(grantTitle, "");
        revokeMessage = Objects.requireNonNullElse(revokeMessage, "");
        grantSubtitle = Objects.requireNonNullElse(grantSubtitle, "");
        grantChat = Objects.requireNonNullElse(grantChat, "");
        grantActionBar = Objects.requireNonNullElse(grantActionBar, "");
        revokeTitle = Objects.requireNonNullElse(revokeTitle, "");
        revokeSubtitle = Objects.requireNonNullElse(revokeSubtitle, "");
        revokeChat = Objects.requireNonNullElse(revokeChat, "");
        revokeActionBar = Objects.requireNonNullElse(revokeActionBar, "");
    }
}
