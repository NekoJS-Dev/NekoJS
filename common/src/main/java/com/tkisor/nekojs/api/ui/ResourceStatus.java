package com.tkisor.nekojs.api.ui;

/**
 * Outcome of resolving a controlled resource id against the resource roots.
 * A status always names the id; failures carry the {@link UiErrorCodes NEKO-} code
 * and an English message a maintainer can locate the failure with.
 *
 * @param state        resolution outcome
 * @param id           the requested id in canonical form, or the raw input when it failed to parse
 * @param resolvedPath the concrete location that backed the id, when resolved
 * @param code         {@link UiErrorCodes} constant for the failure, null when resolved
 * @param message      short English failure summary, null when resolved
 */
public record ResourceStatus(State state, String id, String resolvedPath, String code, String message) {
    /** Resolution outcome. */
    public enum State { RESOLVED, MISSING, INVALID }

    public static ResourceStatus resolved(String id, String resolvedPath) {
        return new ResourceStatus(State.RESOLVED, id, resolvedPath, null, null);
    }

    public static ResourceStatus invalid(String rawId) {
        return new ResourceStatus(State.INVALID, rawId, null, UiErrorCodes.INVALID_RESOURCE_ID,
                "invalid UI resource id: " + rawId);
    }

    public static ResourceStatus missing(UiResourceId id) {
        return new ResourceStatus(State.MISSING, id.toString(), null, UiErrorCodes.MISSING_RESOURCE,
                "UI resource not found in any resource root: " + id);
    }
}
