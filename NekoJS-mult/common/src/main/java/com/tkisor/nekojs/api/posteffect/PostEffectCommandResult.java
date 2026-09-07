package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Structured result for a post-effect state command. */
@Doc("Result returned by PostEffectController state commands, including unsupported capabilities.")
public record PostEffectCommandResult(
        Status status,
        @Nullable String effectId,
        String code,
        String message
) {
    /** Stable result statuses; adapters must not turn unsupported into success. */
    public enum Status {
        ACTIVATED,
        CLEARED,
        ALREADY_CLEAR,
        UNSUPPORTED,
        NOT_FOUND,
        INVALID,
        FAILED
    }

    public static final String RUNTIME_PIPELINE_UNSUPPORTED = "runtime_pipeline_unsupported";
    public static final String RESOURCE_RENDERER_UNSUPPORTED = "resource_renderer_unsupported";

    public PostEffectCommandResult {
        status = Objects.requireNonNull(status, "status");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    public boolean succeeded() {
        return status == Status.ACTIVATED || status == Status.CLEARED || status == Status.ALREADY_CLEAR;
    }

    public boolean accepted() {
        return succeeded();
    }

    public boolean isUnsupported() {
        return status == Status.UNSUPPORTED;
    }

    public static PostEffectCommandResult activated(String id) {
        return new PostEffectCommandResult(Status.ACTIVATED, id, "activated", "Post effect activated: " + id);
    }

    public static PostEffectCommandResult cleared() {
        return new PostEffectCommandResult(Status.CLEARED, null, "cleared", "Post effect cleared");
    }

    public static PostEffectCommandResult alreadyClear() {
        return new PostEffectCommandResult(Status.ALREADY_CLEAR, null, "already_clear", "No post effect is active");
    }

    public static PostEffectCommandResult unsupportedRuntime(String id) {
        return new PostEffectCommandResult(
                Status.UNSUPPORTED,
                id,
                RUNTIME_PIPELINE_UNSUPPORTED,
                "Runtime post effect is unsupported by this renderer adapter: " + id);
    }

    public static PostEffectCommandResult unsupportedResource(String id) {
        return new PostEffectCommandResult(
                Status.UNSUPPORTED,
                id,
                RESOURCE_RENDERER_UNSUPPORTED,
                "Resource-backed post effect is unsupported by this renderer adapter: " + id);
    }

    public static PostEffectCommandResult notFound(String id) {
        return new PostEffectCommandResult(Status.NOT_FOUND, id, "not_found", "Post effect is not in the catalog: " + id);
    }

    public static PostEffectCommandResult invalid(String id, String message) {
        return new PostEffectCommandResult(Status.INVALID, id, "invalid", message);
    }

    public static PostEffectCommandResult failed(@Nullable String id, String message) {
        return new PostEffectCommandResult(Status.FAILED, id, "failed", message);
    }
}
