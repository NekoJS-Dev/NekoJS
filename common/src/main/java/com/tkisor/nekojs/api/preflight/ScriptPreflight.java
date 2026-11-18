package com.tkisor.nekojs.api.preflight;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.compiler.FrontendSemantics.Input;
import com.tkisor.nekojs.api.event.ScriptBindingSchema.BindingMembers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Contributes a check independently of the language frontend. */
public interface ScriptPreflight {
    Descriptor descriptor();

    /**
     * Creates a worker for one runtime root. Workers may be called by different script-type owners;
     * retain only thread-safe, pure analysis facts, never requests, schemas or diagnostic receivers.
     */
    Worker open() throws Exception;

    @FunctionalInterface
    interface Worker extends AutoCloseable {
        /** Returns ordinary findings. A thrown exception is a provider failure, not a finding. */
        List<Diagnostic> check(Request request) throws Exception;

        @Override
        default void close() throws Exception {}
    }

    enum Stage { SOURCE, OUTPUT }
    enum Scope { FULL, BINDINGS_ONLY }
    enum Severity { WARNING, ERROR }

    /** Frozen compatibility metadata. Inputs are declared separately for each supported stage. */
    record Descriptor(String id, String revision, Map<Stage, Set<Input>> inputs,
                      Set<Scope> scopes, Set<String> requiredCapabilities) {
        private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

        public Descriptor {
            requireId(id);
            if (revision == null || revision.isBlank()) {
                throw invalid("Preflight provider revision is missing for " + id);
            }
            inputs = inputs.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
            scopes = Set.copyOf(scopes);
            requiredCapabilities = Set.copyOf(requiredCapabilities);
            requiredCapabilities.forEach(Descriptor::requireId);
            if (inputs.isEmpty() || inputs.values().stream().anyMatch(Set::isEmpty) || scopes.isEmpty()) {
                throw invalid("Preflight provider has no supported inputs or scopes: " + id);
            }
        }

        /** An unknown representation or missing capability never satisfies this declaration. */
        public boolean supports(Stage stage, Scope scope, Input input, Set<String> capabilities) {
            return stage != null && scope != null && input != null && capabilities != null
                    && inputs.getOrDefault(stage, Set.of()).contains(input)
                    && scopes.contains(scope) && capabilities.containsAll(requiredCapabilities);
        }

        private static void requireId(String id) {
            if (id == null || !ID.matcher(id).matches()) {
                throw invalid("Invalid preflight identifier: " + id);
            }
        }
    }

    /** The current input and schema data. A frontend ID can be absent for an unnamed frontend. */
    record Request(Path path, ScriptType scriptType, String frontendId, Stage stage, Scope scope,
                   Input input, Set<String> capabilities, String text, String sourceMap,
                   Map<String, BindingMembers> bindings, Set<String> globals) {
        public Request {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(text, "text");
            capabilities = Set.copyOf(capabilities);
            bindings = Map.copyOf(bindings);
            globals = Set.copyOf(globals);
        }
    }

    /** One-based coordinates and an optional zero-based UTF-16 offset in the checked text. */
    record Position(int line, int column, int offset) {
        public Position {
            if (line < 1 || column < 1 || offset < -1) {
                throw invalid("Invalid preflight position: " + line + ":" + column + " at " + offset);
            }
        }
    }

    /** A finding in the checked text; engine code supplies phase, mapping and provider attribution. */
    record Diagnostic(String code, Severity severity, String message, Position position, String dedupKey) {
        public Diagnostic {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw invalid("Preflight diagnostic requires a code and message");
            }
            Objects.requireNonNull(severity, "severity");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("[NEKO-1028] " + message);
    }
}
