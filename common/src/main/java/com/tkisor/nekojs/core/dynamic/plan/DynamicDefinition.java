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
 * One fully normalized dynamic registration definition: an immutable snapshot carrying the type,
 * the normalized id, the mode, the normalized readings of every writable property, and the
 * definition fingerprint.
 *
 * <p>The fingerprint covers the readings of every writable builder property, mode included. It
 * depends neither on object identity nor on a subset of fields; across the frozen three types the
 * builder property set is the whole definition, since Item, SoundEvent and MobEffect register no
 * companion objects. The same definition yields the same fingerprint across instances and
 * reloads, and any changed field yields a different one. {@link #of} is the only way to construct
 * a definition, which keeps the normalization rules in place.
 */
public record DynamicDefinition(
        DynamicDefinitionType type,
        String id,
        DynamicRegisterMode mode,
        List<String> readings,
        String fingerprint) {

    /** Vanilla identifier shape, checked without MC types: lowercase letters, digits, dot,
     * underscore and hyphen. */
    private static final Pattern NAMESPACE = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Pattern PATH = Pattern.compile("^[a-z0-9_./-]+$");

    public DynamicDefinition {
        readings = List.copyOf(readings == null ? List.of() : readings);
    }

    /**
     * Derives a normalized definition from a builder; this is where the script face
     * {@code event.item(id, cb)} ends collection.
     *
     * @param rawId the id the script supplied. A blank id is rejected, a missing namespace gets
     *              {@code minecraft:} by vanilla convention, and uppercase or illegal characters
     *              are rejected. The error behavior matches {@code Identifier.parse}, implemented
     *              without MC types.
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

    /** The plan and store key, shaped like {@code minecraft:item|mymod:ruby}. */
    public String key() {
        return type.registryKey() + "|" + id;
    }

    /** Full description for declarations and diagnostics: type, id and readings. */
    public String describe() {
        return type.apiName() + "('" + id + "', " + readings + ")";
    }

    // ---- Id normalization: no MC types, same semantics as Identifier.parse ----

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

    // ---- Fingerprint: deterministic sha256 over normalized input ----

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

    /** Test helper: whether the readings contain this name=value pair, used by semantic test
     * assertions. */
    public boolean hasReading(String name, String value) {
        return readings.contains(name + "=" + value);
    }

    /** The supported registry keys of the three frozen types, for diagnostics and tests. */
    public static Set<String> supportedRegistryKeys() {
        return Set.of(DynamicDefinitionType.ITEM.registryKey(),
                DynamicDefinitionType.SOUND_EVENT.registryKey(),
                DynamicDefinitionType.MOB_EFFECT.registryKey());
    }
}
