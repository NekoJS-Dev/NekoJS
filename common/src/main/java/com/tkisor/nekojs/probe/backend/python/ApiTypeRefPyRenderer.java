package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders an {@link ApiTypeRef} as a Python type string (PEP 484). Used by
 * {@link PythonClassRenderer} and {@link PythonProbeBackend}.
 *
 * <p>Best-effort: generic type variables all render as {@code Any} (no expansion to
 * {@code TypeVar}/{@code Generic}). A SYMBOL the probe did not collect (not in
 * {@code availableFqns}) also renders as {@code Any}, so no dangling reference makes pyright fail.
 */
public final class ApiTypeRefPyRenderer {
    private final Set<String> availableFqns;
    private final Map<String, String> symbolNames;
    private final String callableName;

    public ApiTypeRefPyRenderer(Set<String> availableFqns) {
        this(availableFqns, Map.of());
    }

    ApiTypeRefPyRenderer(Set<String> availableFqns, Map<String, String> symbolNames) {
        this(availableFqns, symbolNames, "Callable");
    }

    private ApiTypeRefPyRenderer(Set<String> availableFqns, Map<String, String> symbolNames, String callableName) {
        this.availableFqns = availableFqns;
        this.symbolNames = Map.copyOf(symbolNames);
        this.callableName = callableName;
    }

    ApiTypeRefPyRenderer withSymbolNames(Map<String, String> names) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(symbolNames);
        merged.putAll(names);
        return new ApiTypeRefPyRenderer(availableFqns, merged, callableName);
    }

    ApiTypeRefPyRenderer withCallableName(String name) {
        return new ApiTypeRefPyRenderer(availableFqns, symbolNames, name);
    }

    String callableName() {
        return callableName;
    }

    String symbolName(String fqn) {
        return symbolNames.getOrDefault(fqn, simplePyName(fqn));
    }

    /**
     * FQNs of the standard library collection types that take one type argument. Each renders as a
     * Python list of that argument ({@code list[T]}). The IR no longer collapses collections into
     * arrays, so this renderer decides the sugar; the table covers the java.util and stream
     * standard library types by name.
     */
    private static final Set<String> COLLECTION_FQNS = Set.of(
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
            "java.util.Collection", "java.util.Set", "java.util.HashSet", "java.util.TreeSet",
            "java.util.LinkedHashSet", "java.util.Queue", "java.util.Deque", "java.util.ArrayDeque",
            "java.util.Stack", "java.util.Vector", "java.util.Iterable");

    public String render(ApiTypeRef ref) {
        if (ref == null) return "Any";
        return switch (ref.kind()) {
            case VOID -> "None";
            case PRIMITIVE -> pyPrim(ref.name());
            case TYPE_VARIABLE -> "Any";
            case ARRAY -> "list[" + render(ref.arguments().get(0)) + "]";
            case UNION -> ref.arguments().stream().map(this::render).collect(Collectors.joining(" | "));
            case SYMBOL -> {
                String fqn = extractFqn(ref.name());
                if (!availableFqns.contains(fqn)) yield "Any";
                // A standard library collection with one type argument becomes list[argument].
                // Generic arguments of any other symbol are not rendered, because the Python stub
                // flattens generics.
                if (ref.arguments().size() == 1
                        && (COLLECTION_FQNS.contains(fqn) || fqn.startsWith("java.util.stream."))) {
                    yield "list[" + render(ref.arguments().get(0)) + "]";
                }
                yield symbolName(fqn);
            }
            case CALLBACK -> callableName + "[..., Any]";
        };
    }

    private static String pyPrim(String name) {
        return switch (name) {
            case "string", "char" -> "str";
            case "boolean" -> "bool";
            case "int", "byte", "short", "long" -> "int";
            case "float", "double", "number" -> "float";
            case "object" -> "Any";
            default -> "Any";
        };
    }

    /** Extracts the FQN from a symbol name such as {@code "java:net.Foo"}. */
    static String extractFqn(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }

    /** FQN to Python simple name: the last dot-separated segment, with {@code $} replaced by {@code _}. */
    static String simplePyName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        String simple = dot >= 0 ? fqn.substring(dot + 1) : fqn;
        return simple.replace('$', '_');
    }

    /**
     * Collects the fully qualified names of every SYMBOL the ref references, for collecting module
     * imports. It does not filter by availability.
     */
    static void collectSymbolFqns(ApiTypeRef ref, Set<String> out) {
        if (ref == null) return;
        switch (ref.kind()) {
            case SYMBOL -> {
                out.add(extractFqn(ref.name()));
                for (ApiTypeRef a : ref.arguments()) collectSymbolFqns(a, out);
            }
            case ARRAY -> collectSymbolFqns(ref.arguments().get(0), out);
            case UNION -> { for (ApiTypeRef a : ref.arguments()) collectSymbolFqns(a, out); }
            default -> { /* PRIMITIVE, VOID, TYPE_VARIABLE and CALLBACK contribute no SYMBOL import. */ }
        }
    }
}
