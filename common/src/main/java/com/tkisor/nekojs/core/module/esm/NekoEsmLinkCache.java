package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoPreparedModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoEsmLinkCache {
    private final NekoEsmLinker linker;
    private final Map<Key, Entry> links = new ConcurrentHashMap<>();

    public NekoEsmLinkCache(NekoEsmLinker linker) {
        this.linker = linker;
    }

    public NekoEsmLinkMetadata link(String moduleId, long revision, Path path, NekoPreparedModule prepared) throws IOException {
        Key key = new Key(moduleId, revision);
        Entry cached = links.get(key);
        if (cached != null && cached.prepared() == prepared) {
            return cached.metadata();
        }
        NekoEsmLinkMetadata metadata = linker.link(moduleId, path, prepared);
        links.put(key, new Entry(prepared, metadata));
        return metadata;
    }

    public void remove(String moduleId, long revision) {
        links.remove(new Key(moduleId, revision));
    }

    public void removeAll(String moduleId) {
        links.keySet().removeIf(key -> key.moduleId().equals(moduleId));
    }

    public void clear() {
        links.clear();
    }

    private record Key(String moduleId, long revision) {}

    private record Entry(NekoPreparedModule prepared, NekoEsmLinkMetadata metadata) {}
}
