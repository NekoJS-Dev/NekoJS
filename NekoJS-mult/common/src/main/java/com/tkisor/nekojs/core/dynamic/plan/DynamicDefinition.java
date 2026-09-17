package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 一条<b>全规范化</b>的动态注册定义（ticket 16，AC5）：不可变快照，携带类型、规范化 id、
 * mode、全部可写属性的规范化读数与 definition fingerprint。
 *
 * <p>fingerprint 覆盖 Builder 输入的全部可写属性读数（含 mode）——不依赖对象身份，
 * 也不只看部分字段；{@code 约定连带声明}在冻结的三个类型范围内即「builder 的完整
 * 属性集」（Item/SoundEvent/MobEffect 无自动连带注册对象，spec 08 一期边界）。
 * 相同定义（跨实例、跨 reload）得到相同 fingerprint；任一字段变化即产生不同 fingerprint。
 * 由 {@link #of} 从 builder 派生（唯一构造路径，保证规范化不变量）。
 */
public record DynamicDefinition(
        DynamicDefinitionType type,
        String id,
        DynamicRegisterMode mode,
        List<String> readings,
        String fingerprint) {

    /** vanilla Identifier 形状（MC-free 校验）：小写字母/数字/点/下划线/连字符。 */
    private static final Pattern NAMESPACE = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Pattern PATH = Pattern.compile("^[a-z0-9_./-]+$");

    public DynamicDefinition {
        readings = List.copyOf(readings == null ? List.of() : readings);
    }

    /**
     * 从 builder 派生规范化定义（脚本面 {@code event.item(id, cb)} 的收集终点）。
     *
     * @param rawId 脚本提供的 id（空白拒绝；无命名空间时按 vanilla 惯例取
     *              {@code minecraft:}；大写/非法字符拒绝——与旧入口
     *              {@code Identifier.parse} 的错误面同语义，MC-free 实现）
     */
    public static DynamicDefinition of(DynamicDefinitionType type, String rawId, DynamicDefinitionBuilder builder) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(builder, "builder");
        String id = normalizeId(rawId);
        List<String> readings =
                DynamicBuilderContract.of(builderClass(builder)).normalizedPropertyReadings(builder);
        DynamicRegisterMode mode = modeOf(builder);
        return new DynamicDefinition(type, id, mode, readings,
                fingerprint(type, id, mode, readings));
    }

    /** 计划/存储键：{@code minecraft:item|mymod:ruby}。 */
    public String key() {
        return type.registryKey() + "|" + id;
    }

    /** 声明面/诊断用的完整描述（类型 + id + 读数）。 */
    public String describe() {
        return type.apiName() + "('" + id + "', " + readings + ")";
    }

    // ---- id 规范化（MC-free；与旧入口 Identifier.parse 语义对齐） ----

    static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException(
                    "DynamicRegistry expects an id like 'mymod:boom' (namespace:path)");
        }
        String trimmed = rawId.trim();
        String namespace;
        String path;
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            namespace = "minecraft";
            path = trimmed;
        } else if (separator == 0 || separator == trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid id '" + trimmed + "': expected 'namespace:path'");
        } else {
            namespace = trimmed.substring(0, separator);
            path = trimmed.substring(separator + 1);
        }
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "Invalid id '" + trimmed + "': namespace and path must be lowercase"
                            + " [a-z0-9_.-]/[a-z0-9_./-] like 'mymod:ruby'");
        }
        return namespace + ":" + path;
    }

    // ---- fingerprint（确定性 sha256，规范化输入） ----

    static String fingerprint(
            DynamicDefinitionType type, String id, DynamicRegisterMode mode, List<String> readings) {
        StringBuilder canonical = new StringBuilder("dyn-v1|");
        canonical.append(type.apiName()).append('|').append(id).append('|')
                .append(mode.name().toLowerCase(Locale.ROOT)).append('|');
        readings.forEach(reading -> canonical.append(reading).append(';'));
        return sha256(canonical.toString());
    }

    private static DynamicRegisterMode modeOf(DynamicDefinitionBuilder builder) {
        String mode = builder.getMode();
        if (mode == null) {
            return DynamicRegisterMode.WORLD;
        }
        return DynamicRegisterMode.parse(mode);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DynamicDefinitionBuilder> builderClass(DynamicDefinitionBuilder builder) {
        return (Class<? extends DynamicDefinitionBuilder>) builder.getClass();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 测试可见性辅助：读数集合包含判断（语义测试断言用）。 */
    public boolean hasReading(String name, String value) {
        return readings.contains(name + "=" + value);
    }

    /** 冻结三类型的受支持注册表键集合（诊断/测试用）。 */
    public static Set<String> supportedRegistryKeys() {
        return Set.of(DynamicDefinitionType.ITEM.registryKey(),
                DynamicDefinitionType.SOUND_EVENT.registryKey(),
                DynamicDefinitionType.MOB_EFFECT.registryKey());
    }
}
