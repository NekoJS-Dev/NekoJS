package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.event.ScriptBindingSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

/** Runs current-schema checks against source facts that contain no runtime state. */
public final class ScriptSourcePreflight {
    private ScriptSourcePreflight() {}

    public static void validate(Path file, String source, ScriptBindingSchema.View view) {
        var parsed = new ParsedSource(source);
        GlobalBindingMemberValidator.validate(file, source, view, parsed);
        EventCallbackSourceValidator.validate(file, source, view, parsed);
    }

    /**
     * Bounded source analysis owned by one runtime, shared by its generation sessions.
     * Only parsing and local declarations are retained; schema checks and reporting run
     * on every call. Files are still read and authorized by the caller. The character
     * budget bounds retained source; parser products are linear in that source size.
     */
    public static final class Cache implements AutoCloseable {
        private final LinkedHashMap<InputKey, ParsedSource> sources = new LinkedHashMap<>(16, 0.75f, true);
        private final int maxEntries;
        private final long maxCharacters;
        private long characters;
        private boolean closed;

        public Cache() {
            this(128, 512L * 1024);
        }

        Cache(int maxEntries, long maxCharacters) {
            this.maxEntries = maxEntries;
            this.maxCharacters = maxCharacters;
        }

        public void validate(Path file, String source, ScriptBindingSchema.View view) {
            validateLegacy(file, source, view, false);
        }

        public void validateBindings(Path file, String source, ScriptBindingSchema.View view) {
            validateLegacy(file, source, view, true);
        }

        public void check(Path file, String source, ScriptBindingSchema.View view,
                          String stage, boolean bindingsOnly) {
            if (source.isEmpty() || view.lookup().isEmpty()) return;
            validate(new InputKey(file, stage), source, view, bindingsOnly, true);
        }

        private void validateLegacy(Path file, String source, ScriptBindingSchema.View view, boolean bindingsOnly) {
            validate(new InputKey(file, "legacy"), source, view, bindingsOnly, false);
        }

        private void validate(InputKey key, String source, ScriptBindingSchema.View view,
                              boolean bindingsOnly, boolean propagateParseFailure) {
            Path file = key.file();
            ParsedSource parsed = source(key, source);
            try {
                if (propagateParseFailure) parsed.ast();
                GlobalBindingMemberValidator.validate(file, source, view, parsed);
                if (!bindingsOnly) EventCallbackSourceValidator.validate(file, source, view, parsed);
            } finally {
                // A parser failure belongs to this call, never a retained exception graph.
                if (parsed.failed()) {
                    synchronized (this) {
                        if (sources.remove(key, parsed)) characters -= source.length();
                    }
                }
            }
        }

        private synchronized ParsedSource source(InputKey key, String source) {
            if (closed || key.file() == null || source == null || source.isEmpty()
                    || source.length() > maxCharacters) return new ParsedSource(source);
            ParsedSource current = sources.get(key);
            if (current != null && current.source.equals(source)) return current;
            if (current != null) characters -= current.source.length();
            ParsedSource parsed = new ParsedSource(source);
            sources.put(key, parsed);
            characters += source.length();
            var oldest = sources.entrySet().iterator();
            while (sources.size() > maxEntries || characters > maxCharacters) {
                characters -= oldest.next().getValue().source.length();
                oldest.remove();
            }
            return parsed;
        }

        public synchronized void invalidate(Predicate<Path> matches) {
            var iterator = sources.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (matches.test(entry.getKey().file())) {
                    characters -= entry.getValue().source.length();
                    iterator.remove();
                }
            }
        }

        public synchronized void clear() {
            sources.clear();
            characters = 0;
        }

        @Override
        public synchronized void close() {
            closed = true;
            clear();
        }
    }

    private record InputKey(Path file, String stage) {}

    /** Lazy parsing preserves each validator's empty-input gate and parse-failure reporting. */
    static final class ParsedSource {
        private final String source;
        private boolean parsed;
        private ValNode.Block ast;
        private RuntimeException failure;
        private GlobalBindingMemberValidator.SourceFacts bindingFacts;

        ParsedSource(String source) {
            this.source = source;
        }

        synchronized ValNode.Block ast() {
            if (!parsed) {
                try {
                    ast = ValParser.parse(source);
                    parsed = true;
                } catch (RuntimeException error) {
                    failure = error;
                    parsed = true;
                }
            }
            if (failure != null) throw failure;
            return ast;
        }

        synchronized GlobalBindingMemberValidator.SourceFacts bindingFacts(ValNode.Block ast) {
            if (bindingFacts == null) {
                bindingFacts = GlobalBindingMemberValidator.sourceFacts(ast, source);
            }
            return bindingFacts;
        }

        synchronized boolean failed() {
            return failure != null;
        }
    }
}
