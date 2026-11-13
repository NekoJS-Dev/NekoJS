package com.tkisor.nekojs.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of probe backends, keyed by {@code (languageId, name)}.
 *
 * <p>One language can have several backends with different names. {@link #lock()} fails fast when
 * two backends share the same language and name, with the conflict report style of
 * {@code ProbeRegistry}. {@link #backendsFor(String)} returns them by
 * {@link ProbeBackend#priority()}, highest first, and the highest one is the default backend for that
 * language.
 */
public final class ProbeBackendRegistry {

    /** The locked registry built during bootstrap, injected through {@link #setInstance(ProbeBackendRegistry)}.
     *  Commands and the coordinator read it at runtime through {@link #get()}. */
    private static volatile ProbeBackendRegistry INSTANCE;

    /** Returns the registry singleton injected during bootstrap. Throws when nothing was injected. */
    public static ProbeBackendRegistry get() {
        ProbeBackendRegistry inst = INSTANCE;
        if (inst == null) {
            throw new IllegalStateException("ProbeBackendRegistry has not been initialized");
        }
        return inst;
    }

    /**
     * Bootstrap only: injects the locked registry singleton. A second bootstrap from a full reload
     * replaces the old singleton with the new registry instead of throwing, because reload can re-enter.
     */
    public static synchronized void setInstance(ProbeBackendRegistry registry) {
        INSTANCE = java.util.Objects.requireNonNull(registry, "registry");
    }

    private final Map<String, Map<String, ProbeBackend>> byLanguage = new LinkedHashMap<>();
    private final List<String> registrars = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private volatile boolean locked = false;

    /**
     * Registers a backend.
     *
     * @param backend must not be null
     * @param source  where the registration came from (mod id or class name), used to diagnose conflicts
     */
    public synchronized void register(ProbeBackend backend, String source) {
        if (locked) {
            throw new IllegalStateException("Cannot register probe backend after bootstrap is complete");
        }
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(source, "source");
        if (backend.languageId() == null || backend.languageId().isBlank()) {
            throw new IllegalArgumentException("ProbeBackend.languageId() must not be blank");
        }
        if (backend.name() == null || backend.name().isBlank()) {
            throw new IllegalArgumentException("ProbeBackend.name() must not be blank");
        }

        String entry = backend.languageId() + ":" + backend.name() + " (" + source + ")";
        registrars.add(entry);

        Map<String, ProbeBackend> names = byLanguage.computeIfAbsent(backend.languageId(), k -> new LinkedHashMap<>());
        if (names.containsKey(backend.name())) {
            conflicts.add(entry);
        } else {
            names.put(backend.name(), backend);
        }
    }

    /** Locks the registry. Throws when any (language, name) pair was registered twice. */
    public synchronized void lock() {
        locked = true;
        if (conflicts.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║            [PROBE BACKEND CONFLICT] 冲突！               ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
        sb.append("以下 Probe backend 的 (language, name) 重复：\n\n");
        for (String c : conflicts) {
            sb.append("  - ").append(c).append('\n');
        }
        sb.append("\n同一 (语言, 名字) 只允许一个 backend。请改名或移除冲突项。\n");
        sb.append("\nDuplicate (language, name) probe backends are not allowed. Rename or remove the conflicting backend.\n");
        throw new IllegalStateException(sb.toString());
    }

    public synchronized boolean isLocked() {
        return locked;
    }

    /** Returns every backend for the language, highest priority first. */
    public synchronized List<ProbeBackend> backendsFor(String languageId) {
        Map<String, ProbeBackend> names = byLanguage.getOrDefault(languageId, Map.of());
        return names.values().stream()
                .sorted(Comparator.comparingInt(ProbeBackend::priority).reversed())
                .toList();
    }

    /** Returns the highest-priority backend for the language, which {@code /nekojs probe <lang>} uses by
     *  default. */
    public synchronized Optional<ProbeBackend> defaultBackend(String languageId) {
        return backendsFor(languageId).stream().findFirst();
    }

    /** Returns the backend registered under exactly this language and name. */
    public synchronized Optional<ProbeBackend> backend(String languageId, String name) {
        return Optional.ofNullable(byLanguage.getOrDefault(languageId, Map.of()).get(name));
    }

    /** Returns the language ids that have backends, sorted, for command completion. */
    public synchronized Set<String> languages() {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(byLanguage.keySet()));
    }

    /** Returns every backend in language registration order, for cross-language walks such as
     *  {@code resetEditorConfig}. */
    public synchronized List<ProbeBackend> allBackends() {
        List<ProbeBackend> out = new ArrayList<>();
        for (Map<String, ProbeBackend> names : byLanguage.values()) {
            out.addAll(names.values());
        }
        return out;
    }

    /** Returns every recorded registration, including conflicting ones, for {@code /nekojs probe list}. */
    public synchronized List<String> registrars() {
        return List.copyOf(registrars);
    }
}
