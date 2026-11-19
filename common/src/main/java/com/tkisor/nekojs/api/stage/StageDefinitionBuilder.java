package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptNullable;
import com.tkisor.nekojs.api.data.NekoId;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fluent builder for configuring a {@link StageDefinition} inside {@link StageCustomizeEvent}.
 */
@Doc("Fluent builder for configuring metadata, notifications, and lifecycle callbacks of a stage.")
public final class StageDefinitionBuilder {
    private final String stage;
    private final boolean temporary;
    private final long defaultTicks;

    private String displayName = "";
    private String description = "";
    private @Nullable NekoId icon;
    private boolean showTitle;
    private boolean showChat;
    private boolean showActionBar;
    private String grantTitle = "";
    private String revokeMessage = "";
    private String grantSubtitle = "";
    private String grantChat = "";
    private String grantActionBar = "";
    private String revokeTitle = "";
    private String revokeSubtitle = "";
    private String revokeChat = "";
    private String revokeActionBar = "";
    private @Nullable NekoId sound;
    private @Nullable Consumer<StageTickContext> whenGranted;
    private @Nullable Consumer<StageTickContext> everyTick;
    private @Nullable Consumer<StageTickContext> whenExpired;

    StageDefinitionBuilder(String stage, boolean temporary, long defaultTicks) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4070] Stage identifier cannot be null or blank");
        }
        String trimmed = stage.trim();
        if (temporary && defaultTicks <= 0L) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Temporary stage '" + trimmed + "' requires defaultTicks > 0, got: " + defaultTicks);
        }
        this.stage = trimmed;
        this.temporary = temporary;
        this.defaultTicks = temporary ? defaultTicks : 0L;
    }

    /**
     * Sets the human-readable display name for this stage.
     *
     * @param displayName display name text
     * @return this builder
     */
    @Doc("Sets the human-readable display name for this stage.")
    @Param(name = "displayName", value = "display name text")
    @Return("this builder")
    public StageDefinitionBuilder displayName(@ScriptNullable @Nullable String displayName) {
        this.displayName = Objects.requireNonNullElse(displayName, "");
        return this;
    }

    /**
     * Sets the description text for this stage.
     *
     * @param description description text
     * @return this builder
     */
    @Doc("Sets the description text for this stage.")
    @Param(name = "description", value = "description text")
    @Return("this builder")
    public StageDefinitionBuilder description(@ScriptNullable @Nullable String description) {
        this.description = Objects.requireNonNullElse(description, "");
        return this;
    }

    /**
     * Sets the optional icon identifier associated with this stage.
     *
     * @param icon resource identifier of the stage icon, or null to clear
     * @return this builder
     */
    @Doc("Sets the optional icon identifier associated with this stage.")
    @Param(name = "icon", value = "resource identifier of the stage icon, or null to clear")
    @Return("this builder")
    public StageDefinitionBuilder icon(@ScriptNullable @Nullable NekoId icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Applies default notification channel flags from a {@link StageNotifyOptions} preset or override object.
     *
     * @param options notification options to apply as stage defaults
     * @return this builder
     */
    @Doc("Applies default notification channel flags from StageNotifyOptions.")
    @Param(name = "options", value = "notification options to apply as stage defaults")
    @Return("this builder")
    public StageDefinitionBuilder notifyOnGrant(@ScriptNullable @Nullable StageNotifyOptions options) {
        if (options == null) {
            return this;
        }
        if (Boolean.TRUE.equals(options.silent())) {
            this.showTitle = false;
            this.showChat = false;
            this.showActionBar = false;
            return this;
        }
        if (options.title() != null) {
            this.showTitle = options.title();
        }
        if (options.chat() != null) {
            this.showChat = options.chat();
        }
        if (options.actionBar() != null) {
            this.showActionBar = options.actionBar();
        }
        return this;
    }

    /**
     * Sets whether title banner notifications are enabled by default for this stage.
     *
     * @param showTitle true to show title notifications by default
     * @return this builder
     */
    @Doc("Sets whether title banner notifications are enabled by default for this stage.")
    @Param(name = "showTitle", value = "true to show title notifications by default")
    @Return("this builder")
    public StageDefinitionBuilder showTitle(boolean showTitle) {
        this.showTitle = showTitle;
        return this;
    }

    /**
     * Sets whether chat notifications are enabled by default for this stage.
     *
     * @param showChat true to send chat notifications by default
     * @return this builder
     */
    @Doc("Sets whether chat notifications are enabled by default for this stage.")
    @Param(name = "showChat", value = "true to send chat notifications by default")
    @Return("this builder")
    public StageDefinitionBuilder showChat(boolean showChat) {
        this.showChat = showChat;
        return this;
    }

    /**
     * Sets whether action-bar notifications are enabled by default for this stage.
     *
     * @param showActionBar true to show action-bar notifications by default
     * @return this builder
     */
    @Doc("Sets whether action-bar notifications are enabled by default for this stage.")
    @Param(name = "showActionBar", value = "true to show action-bar notifications by default")
    @Return("this builder")
    public StageDefinitionBuilder showActionBar(boolean showActionBar) {
        this.showActionBar = showActionBar;
        return this;
    }

    /**
     * Sets the custom title or message displayed when this stage is granted.
     *
     * @param grantTitle custom grant title/message text
     * @return this builder
     */
    @Doc("Sets the custom title or message displayed when this stage is granted.")
    @Param(name = "grantTitle", value = "custom grant title or message text")
    @Return("this builder")
    public StageDefinitionBuilder grantTitle(@ScriptNullable @Nullable String grantTitle) {
        this.grantTitle = Objects.requireNonNullElse(grantTitle, "");
        return this;
    }

    /**
     * Sets the custom message displayed when this stage is revoked.
     *
     * @param revokeMessage custom revoke message text
     * @return this builder
     */
    @Doc("Sets the custom message displayed when this stage is revoked.")
    @Param(name = "revokeMessage", value = "custom revoke message text")
    @Return("this builder")
    public StageDefinitionBuilder revokeMessage(@ScriptNullable @Nullable String revokeMessage) {
        this.revokeMessage = Objects.requireNonNullElse(revokeMessage, "");
        return this;
    }

    @Doc("Sets the grant subtitle template.")
    public StageDefinitionBuilder grantSubtitle(@ScriptNullable @Nullable String template) {
        this.grantSubtitle = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the grant chat message template.")
    public StageDefinitionBuilder grantChat(@ScriptNullable @Nullable String template) {
        this.grantChat = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the grant action-bar message template.")
    public StageDefinitionBuilder grantActionBar(@ScriptNullable @Nullable String template) {
        this.grantActionBar = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the revoke title template.")
    public StageDefinitionBuilder revokeTitle(@ScriptNullable @Nullable String template) {
        this.revokeTitle = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the revoke subtitle template.")
    public StageDefinitionBuilder revokeSubtitle(@ScriptNullable @Nullable String template) {
        this.revokeSubtitle = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the revoke chat message template.")
    public StageDefinitionBuilder revokeChat(@ScriptNullable @Nullable String template) {
        this.revokeChat = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the revoke action-bar message template.")
    public StageDefinitionBuilder revokeActionBar(@ScriptNullable @Nullable String template) {
        this.revokeActionBar = Objects.requireNonNullElse(template, "");
        return this;
    }

    @Doc("Sets the optional notification sound. Null restores the default behavior.")
    public StageDefinitionBuilder sound(@ScriptNullable @Nullable NekoId sound) {
        this.sound = sound;
        return this;
    }

    /**
     * Registers a callback invoked when this stage is granted.
     *
     * @param callback grant lifecycle callback
     * @return this builder
     */
    @Doc("Registers a callback invoked when this stage is granted.")
    @Param(name = "callback", value = "grant lifecycle callback")
    @Return("this builder")
    public StageDefinitionBuilder whenGranted(@ScriptNullable @Nullable Consumer<StageTickContext> callback) {
        this.whenGranted = callback;
        return this;
    }

    /**
     * Registers a callback invoked every server tick while this stage remains active.
     *
     * @param callback per-tick lifecycle callback
     * @return this builder
     */
    @Doc("Registers a callback invoked every server tick while this stage remains active.")
    @Param(name = "callback", value = "per-tick lifecycle callback")
    @Return("this builder")
    public StageDefinitionBuilder everyTick(@ScriptNullable @Nullable Consumer<StageTickContext> callback) {
        this.everyTick = callback;
        return this;
    }

    /**
     * Registers a callback invoked when this temporary stage expires.
     *
     * @param callback expiration lifecycle callback
     * @return this builder
     */
    @Doc("Registers a callback invoked when this temporary stage expires.")
    @Param(name = "callback", value = "expiration lifecycle callback")
    @Return("this builder")
    public StageDefinitionBuilder whenExpired(@ScriptNullable @Nullable Consumer<StageTickContext> callback) {
        this.whenExpired = callback;
        return this;
    }

    /**
     * Builds an immutable {@link StageDefinition} snapshot from the current builder configuration.
     *
     * @return immutable stage definition
     */
    @HideFromJS
    public StageDefinition toDefinition() {
        return new StageDefinition(
                stage,
                displayName,
                description,
                icon,
                temporary,
                defaultTicks,
                showTitle,
                showChat,
                showActionBar,
                grantTitle,
                revokeMessage,
                whenGranted,
                everyTick,
                whenExpired,
                grantSubtitle,
                grantChat,
                grantActionBar,
                revokeTitle,
                revokeSubtitle,
                revokeChat,
                revokeActionBar,
                sound);
    }
}
