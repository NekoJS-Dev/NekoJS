package com.tkisor.nekojs.core.preflight;

import com.tkisor.nekojs.api.preflight.ScriptPreflight;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Descriptor;
import com.tkisor.nekojs.api.preflight.ScriptPreflightRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Frozen preflight providers from one plugin bootstrap. Installing a provider does not select it. */
public final class ScriptPreflightCatalog {
    private final Map<String, Entry> entries;
    private final Map<String, Descriptor> descriptors;

    private ScriptPreflightCatalog(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
        this.descriptors = entries.values().stream().map(Entry::descriptor)
                .collect(Collectors.toUnmodifiableMap(Descriptor::id, descriptor -> descriptor));
    }

    public Map<String, Descriptor> descriptors() {
        return descriptors;
    }

    public Optional<Descriptor> find(String id) {
        return Optional.ofNullable(descriptors.get(id));
    }

    Entry require(String id) {
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("[NEKO-1028] Unknown preflight provider: " + id);
        return entry;
    }

    record Entry(Descriptor descriptor, ScriptPreflight provider) {}

    public static final class Collector implements ScriptPreflightRegistry {
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private ScriptPreflightCatalog snapshot;

        @Override
        public void register(ScriptPreflight provider) {
            requireOpen();
            if (provider == null) throw new IllegalArgumentException("[NEKO-1028] Preflight provider is required");
            Descriptor descriptor = provider.descriptor();
            if (descriptor == null) {
                throw new IllegalArgumentException("[NEKO-1028] Preflight provider must declare a descriptor");
            }
            // A freeze may have completed during the plugin callback.
            synchronized (this) {
                requireOpen();
                if (entries.putIfAbsent(descriptor.id(), new Entry(descriptor, provider)) != null) {
                    throw new IllegalArgumentException("[NEKO-1028] Duplicate preflight provider: " + descriptor.id());
                }
            }
        }

        private synchronized void requireOpen() {
            if (snapshot != null) throw new IllegalStateException("[NEKO-1028] Preflight registration has closed");
        }

        public synchronized ScriptPreflightCatalog freeze() {
            if (snapshot == null) snapshot = new ScriptPreflightCatalog(entries);
            return snapshot;
        }
    }
}
