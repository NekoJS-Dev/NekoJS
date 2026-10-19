package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.event.ScriptBindingSchema;

import java.nio.file.Path;

/** Runs both current-schema checks with one parse confined to this validation call. */
public final class ScriptSourcePreflight {
    private ScriptSourcePreflight() {}

    public static void validate(Path file, String source, ScriptBindingSchema.View view) {
        var parsed = new ParsedSource(source);
        GlobalBindingMemberValidator.validate(file, source, view, parsed);
        EventCallbackSourceValidator.validate(file, source, view, parsed);
    }

    /** Lazy parsing preserves each validator's empty-input gate and parse-failure reporting. */
    static final class ParsedSource {
        private final String source;
        private boolean parsed;
        private ValNode.Block ast;
        private Throwable failure;

        ParsedSource(String source) {
            this.source = source;
        }

        ValNode.Block ast() throws Throwable {
            if (!parsed) {
                try {
                    ast = ValParser.parse(source);
                } catch (Throwable error) {
                    failure = error;
                } finally {
                    parsed = true;
                }
            }
            if (failure != null) throw failure;
            return ast;
        }
    }
}
