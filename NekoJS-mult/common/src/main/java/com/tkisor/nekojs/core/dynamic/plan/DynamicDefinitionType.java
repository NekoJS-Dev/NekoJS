package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Locale;
import java.util.Optional;

/**
 * 动态注册候选类型的<b>封闭集合</b>（ticket 16，AC3）：只有 Item、SoundEvent、MobEffect
 * 三类进入候选范围，且本枚举就是全部范围——不存在「未知 type 字符串转换成注册能力」的
 * 通道（payload 成员是三个类型直达入口，不是通用 catalog；未知 type 名在成员解析期
 * 即被拒绝）。
 *
 * <p>apiName 是脚本面成员名（{@code event.item(...)} 等，票面冻结的默认命名）；
 * registryKey 是目标注册表完整键（Adapter 请求与声明面使用）。类型只有通过目标
 * Adapter、事务与同步 gate 才可公开激活；未验证类型记录 <b>not verified</b> 并阻塞
 * 开放，不因缺测改写为 unavailable（见 baseline REPORT 能力表）。
 */
public enum DynamicDefinitionType {

    ITEM("item", "minecraft:item", "Item"),
    SOUND_EVENT("soundEvent", "minecraft:sound_event", "SoundEvent"),
    MOB_EFFECT("mobEffect", "minecraft:mob_effect", "MobEffect");

    private final String apiName;
    private final String registryKey;
    private final String targetTypeName;

    DynamicDefinitionType(String apiName, String registryKey, String targetTypeName) {
        this.apiName = apiName;
        this.registryKey = registryKey;
        this.targetTypeName = targetTypeName;
    }

    /** 脚本面成员名（{@code event.<apiName>(id, cb)}）。 */
    public String apiName() {
        return apiName;
    }

    /** 目标注册表完整键（如 {@code minecraft:item}）。 */
    public String registryKey() {
        return registryKey;
    }

    /** 目标注册表元素类型简名（声明/诊断用；common 不持有 MC 类型）。 */
    public String targetTypeName() {
        return targetTypeName;
    }

    /** 按脚本成员名查找；未知名返回 empty（调用方据此拒绝，不做任何转换）。 */
    public static Optional<DynamicDefinitionType> byApiName(String name) {
        if (name == null) return Optional.empty();
        for (DynamicDefinitionType type : values()) {
            if (type.apiName.equals(name)) return Optional.of(type);
        }
        return Optional.empty();
    }

    /** 全部候选类型的成员名目录（错误信息用，字典序确定）。 */
    public static String apiNameDirectory() {
        StringBuilder sb = new StringBuilder("[");
        DynamicDefinitionType[] types = values();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].apiName);
        }
        return sb.append(']').toString();
    }

    /** 小写规范化辅助（builder 字符串成员共用）。 */
    static String normalizeToken(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + what + ": expected a non-blank string");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
