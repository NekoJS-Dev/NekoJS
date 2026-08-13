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
 * 多 backend 注册表：按 {@code (languageId, name)} 二维登记。
 *
 * <p>同一语言可有多个 backend（不同 name）；同名同语言在 {@link #lock()} 时 fail-fast 崩溃，
 * 沿用旧 {@code ProbeRegistry} 的冲突报告风格。{@link #backendsFor(String)} 按 {@link ProbeBackend#priority()}
 * 降序返回，最高者优先作为该语言的默认 backend。
 */
public final class ProbeBackendRegistry {

    /** Bootstrap 期创建并 lock 后通过 {@link #setInstance(ProbeBackendRegistry)} 注入；运行期命令/协调器经 {@link #get()} 取用。 */
    private static volatile ProbeBackendRegistry INSTANCE;

    /** 返回 bootstrap 注入的注册表单例（未注入时抛异常）。 */
    public static ProbeBackendRegistry get() {
        ProbeBackendRegistry inst = INSTANCE;
        if (inst == null) {
            throw new IllegalStateException("ProbeBackendRegistry has not been initialized");
        }
        return inst;
    }

    /** Bootstrap 专用：注入锁定后的注册表单例。仅允许设置一次。 */
    public static synchronized void setInstance(ProbeBackendRegistry registry) {
        if (INSTANCE != null) {
            throw new IllegalStateException("ProbeBackendRegistry instance already set");
        }
        INSTANCE = java.util.Objects.requireNonNull(registry, "registry");
    }

    private final Map<String, Map<String, ProbeBackend>> byLanguage = new LinkedHashMap<>();
    private final List<String> registrars = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private volatile boolean locked = false;

    /**
     * 注册一个 backend。
     *
     * @param backend 非 null
     * @param source  注册来源（mod id / 类名），用于冲突诊断
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

    /** 锁定注册表；若存在 (语言, 名字) 冲突则抛异常崩溃。 */
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

    /** 该语言下所有 backend，按 priority 降序。 */
    public synchronized List<ProbeBackend> backendsFor(String languageId) {
        Map<String, ProbeBackend> names = byLanguage.getOrDefault(languageId, Map.of());
        return names.values().stream()
                .sorted(Comparator.comparingInt(ProbeBackend::priority).reversed())
                .toList();
    }

    /** 该语言下 priority 最高的 backend（用作 {@code /nekojs probe <lang>} 的默认）。 */
    public synchronized Optional<ProbeBackend> defaultBackend(String languageId) {
        return backendsFor(languageId).stream().findFirst();
    }

    /** 精确指定 (语言, 名字) 的 backend。 */
    public synchronized Optional<ProbeBackend> backend(String languageId, String name) {
        return Optional.ofNullable(byLanguage.getOrDefault(languageId, Map.of()).get(name));
    }

    /** 已注册 backend 的语言集合（用于命令补全）。 */
    public synchronized Set<String> languages() {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(byLanguage.keySet()));
    }

    /** 所有已登记条目（含冲突项），用于 {@code /nekojs probe list}。 */
    public synchronized List<String> registrars() {
        return List.copyOf(registrars);
    }
}
