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
import java.util.TreeMap;

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
        boolean usesJsonInput = symbols.stream().anyMatch(ManagedApiDeclarationGenerator::usesJsonInput);
        boolean usesNbtInput = symbols.stream().anyMatch(ManagedApiDeclarationGenerator::usesNbtInput);

        Map<String, List<ApiSymbol>> membersByOwner = new TreeMap<>();
        symbols.stream()
                .filter(symbol -> "member".equals(symbol.id().kind()))
                .forEach(symbol -> {
                    String qualifiedName = symbol.id().qualifiedName();
                    int separator = qualifiedName.lastIndexOf('.');
                    if (separator > 0) {
                        membersByOwner.computeIfAbsent(qualifiedName.substring(0, separator), ignored -> new ArrayList<>())
                                .add(symbol);
                    }
                });

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated managed API declarations for ").append(scriptType.name).append(".\n");
        sb.append("// Do not edit; regenerate with /nekojs probe.\n\n");
        sb.append("export {};\n\n");
        sb.append("declare global {\n");

        if (usesJsonInput) {
            sb.append("    type JsonInput = null | boolean | number | string | $JsonValue")
                    .append(" | readonly JsonInput[] | { readonly [key: string]: JsonInput };\n\n");
        }
        if (usesNbtInput) {
            sb.append("    type NbtInput = string | number | $NbtValue")
                    .append(" | readonly NbtInput[] | { readonly [key: string]: NbtInput };\n\n");
        }

        for (Map.Entry<String, List<ApiSymbol>> entry : membersByOwner.entrySet()) {
            sb.append("    interface $").append(entry.getKey()).append(" {\n");
            entry.getValue().stream()
                    .sorted(Comparator.comparing(symbol -> symbol.id().qualifiedName()))
                    .forEach(symbol -> renderMember(sb, symbol));
            sb.append("    }\n");
        }

        if (!membersByOwner.isEmpty()) sb.append('\n');

        for (ApiSymbol symbol : globals) {
            String name = symbol.id().qualifiedName();
            List<ApiSignature> sigs = symbol.signatures();

            if (membersByOwner.containsKey(name)) {
                sb.append("    const ").append(name).append(": $").append(name).append(";\n");
                continue;
            }

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

    private static void renderMember(StringBuilder sb, ApiSymbol symbol) {
        String qualifiedName = symbol.id().qualifiedName();
        String memberName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        for (ApiSignature signature : symbol.signatures()) {
            sb.append("        ").append(memberName).append('(');
            renderParameters(sb, signature.parameters());
            sb.append("): ").append(renderTypeRef(signature.returnType())).append(";\n");
        }
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
                if (p.type().kind() == ApiTypeRef.Kind.UNION) sb.append('(');
                sb.append(renderTypeRef(p.type()));
                if (p.type().kind() == ApiTypeRef.Kind.UNION) sb.append(')');
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
            case ARRAY -> {
                ApiTypeRef element = type.arguments().getFirst();
                String rendered = renderTypeRef(element);
                yield element.kind() == ApiTypeRef.Kind.UNION ? "(" + rendered + ")[]" : rendered + "[]";
            }
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
            case TYPE_VARIABLE -> type.name();
        };
    }

    private static boolean usesJsonInput(ApiSymbol symbol) {
        return symbol.signatures().stream().anyMatch(ManagedApiDeclarationGenerator::usesJsonInput);
    }

    private static boolean usesJsonInput(ApiSignature signature) {
        return signature.parameters().stream().anyMatch(parameter -> usesJsonInput(parameter.type()))
                || usesJsonInput(signature.returnType());
    }

    private static boolean usesJsonInput(ApiTypeRef type) {
        if (type.kind() == ApiTypeRef.Kind.PRIMITIVE && "json".equals(type.name())) return true;
        if (type.arguments().stream().anyMatch(ManagedApiDeclarationGenerator::usesJsonInput)) return true;
        return type.callbackSignature() != null && usesJsonInput(type.callbackSignature());
    }

    private static boolean usesNbtInput(ApiSymbol symbol) {
        return symbol.signatures().stream().anyMatch(ManagedApiDeclarationGenerator::usesNbtInput);
    }

    private static boolean usesNbtInput(ApiSignature signature) {
        return signature.parameters().stream().anyMatch(parameter -> usesNbtInput(parameter.type()))
                || usesNbtInput(signature.returnType());
    }

    private static boolean usesNbtInput(ApiTypeRef type) {
        if (type.kind() == ApiTypeRef.Kind.PRIMITIVE && "nbt".equals(type.name())) return true;
        if (type.arguments().stream().anyMatch(ManagedApiDeclarationGenerator::usesNbtInput)) return true;
        return type.callbackSignature() != null && usesNbtInput(type.callbackSignature());
    }

    private static String mapPrimitive(String name) {
        return switch (name) {
            case "boolean" -> "boolean";
            case "string", "java.lang.String", "char" -> "string";
            case "number", "byte", "short", "int", "long", "float", "double" -> "number";
            case "null" -> "null";
            case "json" -> "JsonInput";
            case "nbt" -> "NbtInput";
            case "object", "java.lang.Object" -> "unknown";
            case "void" -> "void";
            default -> name;
        };
    }
}
