package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

/**
 * Root-owned, bounded reuse of pure built-in transformations, never guest or schema state.
 * One owner has one pipeline with immutable code-generation configuration; entries cannot
 * cross that policy boundary. The character budget includes authored code and generated maps.
 */
final class BuiltinModuleTransformCache {
    private final LinkedHashMap<Path, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxEntries;
    private final long maxCharacters;
    private long characters;
    private long epoch;
    private boolean closed;

    BuiltinModuleTransformCache() {
        this(256, 8L * 1024 * 1024);
    }

    BuiltinModuleTransformCache(int maxEntries, long maxCharacters) {
        this.maxEntries = maxEntries;
        this.maxCharacters = maxCharacters;
    }

    NekoPreparedModule prepare(Path file, String source, NekoModulePipeline.LanguageBinding binding,
                               Preparation preparation) throws Exception {
        // Plugin ids and extensions do not establish purity. Custom frontends must run each time.
        if (binding.plugin() != NekoJavaScriptLanguagePlugin.INSTANCE
                && binding.plugin() != NekoTypeScriptLanguagePlugin.INSTANCE) {
            return preparation.prepare();
        }
        long observedEpoch;
        synchronized (this) {
            Entry entry = entries.get(file);
            if (!closed && entry != null && entry.source().equals(source) && entry.binding().equals(binding)) {
                return entry.prepared();
            }
            observedEpoch = epoch;
        }
        // Compilation does not hold the cache monitor; duplicate pure work is safe across sessions.
        NekoPreparedModule prepared = preparation.prepare();
        long weight = (long) source.length() + prepared.code().length()
                + (prepared.sourceMap() == null ? 0 : prepared.sourceMap().length());
        synchronized (this) {
            // Clear/invalidate/close cannot be undone by an already-running compilation.
            if (closed || epoch != observedEpoch || weight > maxCharacters) return prepared;
            Entry old = entries.remove(file);
            if (old != null) characters -= old.characters();
            entries.put(file, new Entry(source, binding, prepared, weight));
            characters += weight;
            var oldest = entries.entrySet().iterator();
            while (entries.size() > maxEntries || characters > maxCharacters) {
                characters -= oldest.next().getValue().characters();
                oldest.remove();
            }
        }
        return prepared;
    }

    synchronized void invalidate(Predicate<Path> matches) {
        epoch++;
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (matches.test(entry.getKey())) {
                characters -= entry.getValue().characters();
                iterator.remove();
            }
        }
    }

    synchronized void clear() {
        epoch++;
        entries.clear();
        characters = 0;
    }

    synchronized void close() {
        closed = true;
        clear();
    }

    @FunctionalInterface
    interface Preparation {
        NekoPreparedModule prepare() throws Exception;
    }

    private record Entry(String source, NekoModulePipeline.LanguageBinding binding,
                         NekoPreparedModule prepared, long characters) {}
}
