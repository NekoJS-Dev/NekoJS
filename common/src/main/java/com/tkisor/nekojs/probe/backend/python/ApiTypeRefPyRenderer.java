package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link ApiTypeRef} → Python 类型字符串（PEP 484）。供 {@link PythonClassRenderer} 与
 * {@link PythonProbeBackend} 使用。
 *
 * <p>Phase 3 best-effort：泛型类型变量统一渲染为 {@code Any}（不展开 {@code TypeVar}/{@code Generic}）；
 * 未被 probe 收集（不在 {@code availableFqns}）的 SYMBOL 也渲染为 {@code Any}，避免悬空引用导致 pyright 报错。
 */
public final class ApiTypeRefPyRenderer {
    private final Set<String> availableFqns;

    public ApiTypeRefPyRenderer(Set<String> availableFqns) {
        this.availableFqns = availableFqns;
    }

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
                yield availableFqns.contains(fqn) ? simplePyName(fqn) : "Any";
            }
            case CALLBACK -> "Callable[..., Any]";
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

    /** symbol name 形如 {@code "java:net.Foo"} → 取 FQN。 */
    static String extractFqn(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }

    /** FQN → Python 简单名：最后一段点分，嵌套 {@code $} → {@code _}。 */
    static String simplePyName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        String simple = dot >= 0 ? fqn.substring(dot + 1) : fqn;
        return simple.replace('$', '_');
    }

    /** 收集 ref 引用的所有 SYMBOL 全限定名（供模块 import 收集用，不做可用性过滤）。 */
    static void collectSymbolFqns(ApiTypeRef ref, Set<String> out) {
        if (ref == null) return;
        switch (ref.kind()) {
            case SYMBOL -> out.add(extractFqn(ref.name()));
            case ARRAY -> collectSymbolFqns(ref.arguments().get(0), out);
            case UNION -> { for (ApiTypeRef a : ref.arguments()) collectSymbolFqns(a, out); }
            default -> { /* PRIMITIVE/VOID/TYPE_VARIABLE/CALLBACK 无 SYMBOL import */ }
        }
    }
}
