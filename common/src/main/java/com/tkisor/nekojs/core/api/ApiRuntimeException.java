package com.tkisor.nekojs.core.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ApiRuntimeException extends RuntimeException {

    private final String code;
    private final String symbolId;
    private final String platform;
    private final String minecraftVersion;
    private final String requiredCapability;
    private final String replacement;

    public ApiRuntimeException(String code, String message) {
        this(code, message, null, null, null, null, null);
    }

    public ApiRuntimeException(
            String code,
            String message,
            String symbolId,
            String platform,
            String minecraftVersion,
            String requiredCapability,
            String replacement) {
        super(message);
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
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("code", code);
        Optional.ofNullable(symbolId).ifPresent(v -> meta.put("symbolId", v));
        Optional.ofNullable(platform).ifPresent(v -> meta.put("platform", v));
        Optional.ofNullable(minecraftVersion).ifPresent(v -> meta.put("minecraftVersion", v));
        Optional.ofNullable(requiredCapability).ifPresent(v -> meta.put("requiredCapability", v));
        Optional.ofNullable(replacement).ifPresent(v -> meta.put("replacement", v));
        return Map.copyOf(meta);
    }
}
