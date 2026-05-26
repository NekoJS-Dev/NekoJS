package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoPreparedModule;
import graal.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoEsmModuleRecordCache {
    private final Map<Key, NekoEsmModuleRecord> records = new ConcurrentHashMap<>();

    public NekoEsmModuleRecord get(String moduleId, long revision) {
        return records.get(new Key(moduleId, revision));
    }

    public NekoEsmModuleRecord getOrCreate(String moduleId, long revision, Path path, NekoPreparedModule prepared) {
        return records.computeIfAbsent(new Key(moduleId, revision), ignored -> new NekoEsmModuleRecord(moduleId, path, prepared));
    }

    public void removeAll(String moduleId) {
        records.keySet().removeIf(key -> key.moduleId().equals(moduleId));
    }

    public void clear() {
        records.clear();
    }

    public void captureNamespace(String moduleId, long revision, Value namespace) {
        NekoEsmModuleRecord record = get(moduleId, revision);
        if (record != null) {
            record.namespace(namespace);
        }
    }

    public void completeNamespace(String moduleId, long revision, Value namespace) {
        NekoEsmModuleRecord record = get(moduleId, revision);
        if (record != null) {
            record.evaluated(namespace);
        }
    }

    public void failNamespace(String moduleId, long revision, Throwable failure) {
        NekoEsmModuleRecord record = get(moduleId, revision);
        if (record != null) {
            record.failure(failure);
        }
    }

    private record Key(String moduleId, long revision) {}
}
