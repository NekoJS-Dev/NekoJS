package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders managed API declarations (globals/types) for a single {@link ScriptType}
 * from the managed surface snapshot. Phase 1 only renders managed globals and types;
 * importable module declarations are NOT generated.
 *
 * <p>This generator operates purely on the {@link ApiTypeRef}/{@link ApiSignature} model;
 * it does NOT call {@code ClassDeclGenerator}, {@code MemberVisibilityQuery}, or raw reflection.
 */
public final class ManagedApiDeclarationGenerator {

    public ManagedApiDeclarationGenerator() {
    }

    /**
     * Generate TypeScript declaration content for managed globals of the given script type.
     *
     * @param managedApis map from ScriptType to its managed environment snapshot
     * @param scriptType  the target script type
     * @return the generated declaration content, or empty string if no managed APIs exist
     */
    public String generate(Map<ScriptType, ApiEnvironmentSnapshot> managedApis, ScriptType scriptType) {
        ApiEnvironmentSnapshot env = managedApis.get(scriptType);
        if (env == null) return "";

        ApiSurfaceSnapshot surface = env.surfaceSnapshot();
        List<ApiSymbol> symbols = surface.symbols();
        if (symbols.isEmpty()) return "";

        List<ApiSymbol> globals = symbols.stream()
                .filter(s -> "global".equals(s.id().kind()))
                .sorted(Comparator.comparing(s -> s.id().qualifiedName()))
                .toList();
        if (globals.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated managed API declarations for ").append(scriptType.name).append(".\n");
        sb.append("// Do not edit; regenerate with /nekojs probe.\n\n");
        sb.append("export {};\n\n");
        sb.append("declare global {\n");

        for (ApiSymbol symbol : globals) {
            String name = symbol.id().qualifiedName();
            List<ApiSignature> sigs = symbol.signatures();

            if (sigs.size() == 1) {
                ApiSignature sig = sigs.getFirst();
                sb.append("    const ").append(name).append(": ").append(renderSignature(sig)).append(";\n");
            } else {
                // Multiple overloads: render as intersection of parenthesized function types
                sb.append("    const ").append(name).append(": ");
                for (int i = 0; i < sigs.size(); i++) {
                    if (i > 0) sb.append(" & ");
                    sb.append("(").append(renderSignature(sigs.get(i))).append(")");
                }
                sb.append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    static String renderSignature(ApiSignature sig) {
        if (sig.isConstructor()) {
            return renderConstructorSignature(sig);
        }
        return renderFunctionSignature(sig);
    }

    private static String renderFunctionSignature(ApiSignature sig) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        renderParameters(sb, sig.parameters());
        sb.append(") => ");
        sb.append(renderTypeRef(sig.returnType()));
        return sb.toString();
    }

    private static String renderConstructorSignature(ApiSignature sig) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ new (");
        renderParameters(sb, sig.parameters());
        sb.append("): ");
        sb.append(renderTypeRef(sig.returnType()));
        sb.append(" }");
        return sb.toString();
    }

    private static void renderParameters(StringBuilder sb, List<ApiParameter> params) {
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            ApiParameter p = params.get(i);
            if (p.varargs()) {
                sb.append("...");
                sb.append(p.name());
                sb.append(": ");
                sb.append(renderTypeRef(p.type()));
                sb.append("[]");
            } else {
                sb.append(p.name());
                if (p.optional()) sb.append("?");
                sb.append(": ");
                sb.append(renderTypeRef(p.type()));
            }
        }
    }

    static String renderTypeRef(ApiTypeRef type) {
        return switch (type.kind()) {
            case PRIMITIVE -> mapPrimitive(type.name());
            case SYMBOL -> {
                // ApiTypeRef.symbol() stores kind:qualifiedName; extract qualifiedName
                String symName = type.name();
                int colonIdx = symName.indexOf(':');
                yield "$" + (colonIdx >= 0 ? symName.substring(colonIdx + 1) : symName);
            }
            case ARRAY -> renderTypeRef(type.arguments().getFirst()) + "[]";
            case UNION -> {
                List<String> members = new ArrayList<>();
                for (ApiTypeRef arg : type.arguments()) {
                    members.add(renderTypeRef(arg));
                }
                yield String.join(" | ", members);
            }
            case CALLBACK -> {
                ApiSignature sig = type.callbackSignature();
                StringBuilder cb = new StringBuilder();
                cb.append("(");
                renderParameters(cb, sig.parameters());
                cb.append(") => ");
                cb.append(renderTypeRef(sig.returnType()));
                yield cb.toString();
            }
            case VOID -> "void";
        };
    }

    private static String mapPrimitive(String name) {
        return switch (name) {
            case "boolean" -> "boolean";
            case "byte", "short", "int", "long", "float", "double" -> "number";
            case "char", "java.lang.String" -> "string";
            case "java.lang.Object" -> "unknown";
            case "void" -> "void";
            default -> name;
        };
    }
}
