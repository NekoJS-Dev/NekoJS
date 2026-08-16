package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * UUID 工具：随机、字符串解析与按名字派生（v3）。
 */
@Doc("UUID helpers: random, string-parsed, and name-derived UUIDs.")
public final class UUIDJS {
    /** 生成随机 UUID（v4）。 */
    @Doc("Generates a new random UUID (version 4).")
    @Return("a fresh random UUID")
    public UUID random() {
        return UUID.randomUUID();
    }

    /** 解析标准 UUID 字符串形式。 */
    @Doc("Parses the canonical hyphenated UUID string form.")
    @Param(name = "value", value = "canonical UUID string in hyphenated hex form")
    @Return("the parsed UUID; throws on malformed input")
    public UUID fromString(String value) {
        return UUID.fromString(value);
    }

    /** 由名字的 UTF-8 字节派生确定性 UUID（v3，同名恒同值）。 */
    @Doc("Derives a deterministic UUID (version 3) from the UTF-8 bytes of the name.")
    @Param(name = "value", value = "any string; the same name always yields the same UUID")
    @Return("the name-derived UUID")
    public UUID fromName(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
