package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Objects;

/**
 * 单个 mod 的只读元信息快照（脚本侧类型名 {@code ModInfo}，由 {@code @ContractReceiver("ModInfo")} 指定）。
 *
 * <p>由 {@link PlatformFacade#getInfo(String)} 返回；三个字段均为非空、非空白字符串。不可变。
 *
 * @param id      mod id
 * @param name    mod 显示名
 * @param version mod 版本字符串
 */
@ContractReceiver("ModInfo")
public record ModInfoValue(String id, String name, String version) {
    /** Compact constructor: rejects {@code null} or blank components. */
    public ModInfoValue {
        id = requireText(id, "id");
        name = requireText(name, "name");
        version = requireText(version, "version");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
