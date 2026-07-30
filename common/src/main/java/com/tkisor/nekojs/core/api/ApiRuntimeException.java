package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.error.ApiInvocationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ApiRuntimeException extends ApiInvocationException {

    private final String code;
    private final String symbolId;
    private final String platform;
    private final String minecraftVersion;
    private final String requiredCapability;
    private final String replacement;

    public ApiRuntimeException(String code, String message) {
        this(code, message, null, null, null, null, null, null);
    }

    public ApiRuntimeException(
            String code,
            String message,
            String symbolId,
            String platform,
            String minecraftVersion,
            String requiredCapability,
            String replacement) {
        this(code, message, symbolId, platform, minecraftVersion, requiredCapability, replacement, null);
    }

    public ApiRuntimeException(
            String code,
            String message,
            String symbolId,
            String platform,
            String minecraftVersion,
            String requiredCapability,
            String replacement,
            Throwable cause) {
        this(code, message, symbolId, platform, minecraftVersion, requiredCapability, replacement, Map.of(), cause);
    }

    public ApiRuntimeException(
            String code,
            String message,
            String symbolId,
            String platform,
            String minecraftVersion,
            String requiredCapability,
            String replacement,
            Map<String, String> extraDetails,
            Throwable cause) {
        super(code, message, metadata(symbolId, platform, minecraftVersion, requiredCapability, replacement, extraDetails), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.symbolId = symbolId;
        this.platform = platform;
        this.minecraftVersion = minecraftVersion;
        this.requiredCapability = requiredCapability;
        this.replacement = replacement;
    }

    public String code() {
        return code;
    }

    public Optional<String> symbolId() {
        return Optional.ofNullable(symbolId);
    }

    public Optional<String> platform() {
        return Optional.ofNullable(platform);
    }

    public Optional<String> minecraftVersion() {
        return Optional.ofNullable(minecraftVersion);
    }

    public Optional<String> requiredCapability() {
        return Optional.ofNullable(requiredCapability);
    }

    public Optional<String> replacement() {
        return Optional.ofNullable(replacement);
    }

    public Map<String, String> metadata() {
        Map<String, String> metadata = new LinkedHashMap<>(details());
        metadata.put("code", code());
        return Map.copyOf(metadata);
    }

    private static Map<String, String> metadata(
            String symbolId,
            String platform,
            String minecraftVersion,
            String requiredCapability,
            String replacement,
            Map<String, String> extraDetails) {
        Objects.requireNonNull(extraDetails, "extraDetails");
        Map<String, String> meta = new LinkedHashMap<>(extraDetails);
        Optional.ofNullable(symbolId).ifPresent(v -> meta.put("symbolId", v));
        Optional.ofNullable(platform).ifPresent(v -> meta.put("platform", v));
        Optional.ofNullable(minecraftVersion).ifPresent(v -> meta.put("minecraftVersion", v));
        Optional.ofNullable(requiredCapability).ifPresent(v -> meta.put("requiredCapability", v));
        Optional.ofNullable(replacement).ifPresent(v -> meta.put("replacement", v));
        return Map.copyOf(meta);
    }
}
