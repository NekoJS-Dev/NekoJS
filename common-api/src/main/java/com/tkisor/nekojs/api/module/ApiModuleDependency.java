package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.surface.ApiVersionRange;

import java.util.Objects;

/**
 * 模块依赖声明：目标模块 id、版本区间与依赖类型。
 *
 * @param moduleId     目标模块 id，不能为 {@code null}
 * @param versionRange 版本区间（可为 {@code null}，取决于依赖类型）
 * @param type         依赖类型，不能为 {@code null}
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

    /** 依赖类型。 */
    public enum DependencyType {
        /** 模块依赖。 */
        MODULE,
        /** 能力依赖。 */
        CAPABILITY,
        /** portable 稳定契约依赖。 */
        PORTABLE_STABLE
    }
}
