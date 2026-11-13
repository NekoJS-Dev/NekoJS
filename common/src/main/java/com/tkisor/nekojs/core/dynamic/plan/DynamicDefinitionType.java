package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Locale;
import java.util.Optional;

/**
 * The closed set of dynamic registration candidate types. Only Item, SoundEvent and MobEffect
 * enter the candidate range, and this enum is that whole range: there is no channel that turns an
 * unknown type string into a registration capability. The payload members are direct entry points
 * for the three types, not a general catalog, and an unknown type name is rejected while the
 * member is resolved.
 *
 * <p>{@code apiName} is the script-facing member name ({@code event.item(...)} and so on);
 * {@code registryKey} is the full key of the target registry, used by Adapter requests and the
 * declaration surface. A type only becomes publicly active through its target Adapter, the
 * transaction and the sync gate. A type that has not been verified is recorded as <b>not
 * verified</b> and blocks opening; it is never rewritten as unavailable just because tests are
 * missing.
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

    /** The script-facing member name, as in {@code event.<apiName>(id, cb)}. */
    public String apiName() {
        return apiName;
    }

    /** The full key of the target registry, such as {@code minecraft:item}. */
    public String registryKey() {
        return registryKey;
    }

    /** Simple name of the target registry element type, for declarations and diagnostics; common
     * holds no MC types. */
    public String targetTypeName() {
        return targetTypeName;
    }

    /** Looks up a type by script member name. An unknown name returns empty, and the caller rejects
     * it without any conversion. */
    public static Optional<DynamicDefinitionType> byApiName(String name) {
        if (name == null) return Optional.empty();
        for (DynamicDefinitionType type : values()) {
            if (type.apiName.equals(name)) return Optional.of(type);
        }
        return Optional.empty();
    }

    /** Directory of all candidate type member names, for error messages; the enum declaration order
     * makes the text deterministic. */
    public static String apiNameDirectory() {
        StringBuilder sb = new StringBuilder("[");
        DynamicDefinitionType[] types = values();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].apiName);
        }
        return sb.append(']').toString();
    }

    /** Lowercase normalization helper shared by the string members of the builders. */
    static String normalizeToken(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + what + ": expected a non-blank string");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
