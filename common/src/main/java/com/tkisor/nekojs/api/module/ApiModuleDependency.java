package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.surface.ApiVersionRange;

import java.util.Objects;

/**
 * Declaration of a module dependency: the target module id, the version range and the dependency type.
 *
 * @param moduleId     target module id, never {@code null}
 * @param versionRange version range, may be {@code null} depending on the dependency type
 * @param type         dependency type, never {@code null}
 */
public record ApiModuleDependency(
        String moduleId,
        ApiVersionRange versionRange,
        DependencyType type
) {
    public ApiModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(type, "type");
    }

    /** Kind of dependency. */
    public enum DependencyType {
        /** Depends on another module. */
        MODULE,
        /** Depends on a capability. */
        CAPABILITY,
        /** Depends on a portable stable contract. */
        PORTABLE_STABLE
    }
}
