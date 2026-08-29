package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * 加载器版本区间，支持精确匹配或半开区间 {@code [min, max)}。
 *
 * @param min   下界
 * @param max   上界
 * @param exact 是否精确匹配（true 时 min==max）
 */
public record LoaderVersionRange(LoaderVersion min, LoaderVersion max, boolean exact) {

    /** 精确匹配指定版本。 */
    public static LoaderVersionRange exact(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        return new LoaderVersionRange(version, version, true);
    }

    /** 构造半开区间 {@code [min, max)}。 */
    public static LoaderVersionRange range(LoaderVersion min, LoaderVersion max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        return new LoaderVersionRange(min, max, false);
    }

    /** 判断版本是否落在区间内（精确时相等，否则 {@code min <= v < max}）。 */
    public boolean matches(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        if (exact) {
            return min.compareTo(version) == 0;
        }
        return min.compareTo(version) <= 0 && version.compareTo(max) < 0;
    }
}
