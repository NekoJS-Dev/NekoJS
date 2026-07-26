package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.surface.ApiVersionRange;

import java.util.Objects;

public record ApiModuleDependency(String moduleId, ApiVersionRange versionRange) {
    public ApiModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId");
    }
}
