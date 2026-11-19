package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

/**
 * Per-operation notification overrides when granting or revoking stages.
 *
 * @param silent    when {@code Boolean.TRUE}, suppresses all notification channels
 * @param title     explicit override for title banner notifications, or {@code null} to inherit from {@link StageDefinition}
 * @param chat      explicit override for chat message notifications, or {@code null} to inherit from {@link StageDefinition}
 * @param actionBar explicit override for action-bar notifications, or {@code null} to inherit from {@link StageDefinition}
 */
@Doc("Per-operation notification overrides when granting or revoking stages.")
@ContractReceiver
public record StageNotifyOptions(
        @Nullable Boolean silent,
        @Nullable Boolean title,
        @Nullable Boolean chat,
        @Nullable Boolean actionBar) {

    /** Inherits all notification channels from the target {@link StageDefinition} (all false if undefined). */
    public static final StageNotifyOptions DEFAULT = new StageNotifyOptions(null, null, null, null);

    /** Suppresses all notification channels regardless of stage definition defaults. */
    public static final StageNotifyOptions SILENT =
            new StageNotifyOptions(Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);

    /** Enables title, chat, and action-bar notifications regardless of stage definition defaults. */
    public static final StageNotifyOptions ALL =
            new StageNotifyOptions(Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);

    /**
     * Creates notification options with explicit tri-state channel overrides.
     *
     * @param silent    whether to silence all channels
     * @param title     explicit title channel override, or {@code null} to inherit
     * @param chat      explicit chat channel override, or {@code null} to inherit
     * @param actionBar explicit action-bar channel override, or {@code null} to inherit
     * @return notification options instance
     */
    @Doc("Creates notification options with explicit tri-state channel overrides.")
    @Param(name = "silent", value = "whether to silence all channels")
    @Param(name = "title", value = "explicit title channel override, or null to inherit")
    @Param(name = "chat", value = "explicit chat channel override, or null to inherit")
    @Param(name = "actionBar", value = "explicit action-bar channel override, or null to inherit")
    @Return("notification options instance")
    public static StageNotifyOptions of(
            @Nullable Boolean silent,
            @Nullable Boolean title,
            @Nullable Boolean chat,
            @Nullable Boolean actionBar) {
        return new StageNotifyOptions(silent, title, chat, actionBar);
    }

    /**
     * Returns silent notification options that suppress all channels.
     *
     * @return {@link #SILENT}
     */
    @Doc("Returns silent notification options that suppress all channels.")
    @Return("silent notification options")
    public static StageNotifyOptions ofSilent() {
        return SILENT;
    }

    /**
     * Returns {@link #SILENT} when {@code silent} is true, or {@link #DEFAULT} otherwise.
     *
     * @param silent whether notifications should be silenced
     * @return resolved notification options
     */
    @Doc("Returns SILENT when silent is true, or DEFAULT otherwise.")
    @Param(name = "silent", value = "whether notifications should be silenced")
    @Return("resolved notification options")
    public static StageNotifyOptions silent(boolean silent) {
        return silent ? SILENT : DEFAULT;
    }

    /**
     * Resolves whether a title notification should be shown for the given stage definition.
     *
     * @param def optional stage definition providing default notification settings
     * @return true if the title notification should be displayed
     */
    @Doc("Resolves whether a title notification should be shown for the given stage definition.")
    @Param(name = "def", value = "optional stage definition providing default notification settings")
    @Return("true if the title notification should be displayed")
    public boolean showTitle(@Nullable StageDefinition def) {
        if (Boolean.TRUE.equals(silent)) {
            return false;
        }
        if (title != null) {
            return title;
        }
        return def != null && def.showTitle();
    }

    /**
     * Resolves whether a chat notification should be shown for the given stage definition.
     *
     * @param def optional stage definition providing default notification settings
     * @return true if the chat notification should be displayed
     */
    @Doc("Resolves whether a chat notification should be shown for the given stage definition.")
    @Param(name = "def", value = "optional stage definition providing default notification settings")
    @Return("true if the chat notification should be displayed")
    public boolean showChat(@Nullable StageDefinition def) {
        if (Boolean.TRUE.equals(silent)) {
            return false;
        }
        if (chat != null) {
            return chat;
        }
        return def != null && def.showChat();
    }

    /**
     * Resolves whether an action-bar notification should be shown for the given stage definition.
     *
     * @param def optional stage definition providing default notification settings
     * @return true if the action-bar notification should be displayed
     */
    @Doc("Resolves whether an action-bar notification should be shown for the given stage definition.")
    @Param(name = "def", value = "optional stage definition providing default notification settings")
    @Return("true if the action-bar notification should be displayed")
    public boolean showActionBar(@Nullable StageDefinition def) {
        if (Boolean.TRUE.equals(silent)) {
            return false;
        }
        if (actionBar != null) {
            return actionBar;
        }
        return def != null && def.showActionBar();
    }
}
