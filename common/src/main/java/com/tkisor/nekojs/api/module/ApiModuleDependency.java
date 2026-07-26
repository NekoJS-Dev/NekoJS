package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.surface.ApiVersionRange;

import java.util.Objects;

public record ApiModuleDependency(
        String moduleId,
        ApiVersionRange versionRange,
        DependencyType type
) {
    public ApiModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(type, "type");
    }

    public enum DependencyType {
        MODULE,
        CAPABILITY,
        PORTABLE_STABLE
    }
}
