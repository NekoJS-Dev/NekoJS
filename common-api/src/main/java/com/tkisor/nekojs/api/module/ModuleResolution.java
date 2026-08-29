package com.tkisor.nekojs.api.module;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 模块解析结果：已激活模块与未激活模块列表。
 *
 * <p>两个列表均拷贝为不可变列表；通过 {@link #inactive(String)} 查询指定模块的未激活原因。
 */
public record ModuleResolution(
        List<ActiveModule> active,
        List<InactiveModule> inactive
) {
    public ModuleResolution {
        active = List.copyOf(active == null ? List.of() : active);
        inactive = List.copyOf(inactive == null ? List.of() : inactive);
    }

    /** 返回所有已激活模块 id。 */
    public List<String> activeIds() {
        return active.stream()
                .map(m -> m.descriptor().moduleId())
                .toList();
    }

    /** 查询指定模块 id 的未激活条目（若存在）。 */
    public Optional<InactiveModule> inactive(String moduleId) {
        return inactive.stream()
                .filter(m -> m.descriptor().moduleId().equals(moduleId))
                .findFirst();
    }
}
