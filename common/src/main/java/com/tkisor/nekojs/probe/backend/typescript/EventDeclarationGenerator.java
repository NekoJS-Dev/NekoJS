package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCallSignature;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventVariant;
import com.tkisor.nekojs.probe.EventPayloadDeclarations;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeScriptDeclarationFormatter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates TypeScript event declarations from EventCatalogEntry.
 *
 * <p>Output format follows ProbeJS:
 * <pre>
 * import { $RecipeEventJS } from "java:com/example";
 * import { $ResourceLocation_ } from "java:net/minecraft/resources";
 *
 * declare module "@side-only/server/events" {
 * }
 *
 * export {};
 *
 * declare global {
 *     export namespace ServerEvents {
 *         function recipes(handler: ((event: $RecipeEventJS) => void)): void;
 *         function recipes(extra: string, handler: ((event: $RecipeEventJS) => void)): void;
 *     }
 * }
 * </pre>
 */
public final class EventDeclarationGenerator {
    private final TypeAliasRegistry aliasRegistry;
    private final AdapterAliasGenerator adapterAliasGenerator;

    public EventDeclarationGenerator(TypeAliasRegistry aliasRegistry, AdapterAliasGenerator adapterAliasGenerator) {
        this.aliasRegistry = aliasRegistry;
        this.adapterAliasGenerator = adapterAliasGenerator;
    }

    /**
     * Generates event declarations for the given ScriptType.
     */
    public String generate(List<EventCatalogEntry> events, ScriptType scriptType) {
        return generate(events, scriptType, List.of());
    }

    public String generate(List<EventCatalogEntry> events, ScriptType scriptType,
                           List<RegistryBuilderSurfaceEntry> builders) {
        Map<Class<?>, EventPayload> payloads = EventPayloadDeclarations.resolve(events, builders);
        StringBuilder sb = new StringBuilder();
        if (!EventPayloadDeclarations.builderNames(payloads, builders).isEmpty()) {
            sb.append("/// <reference path=\"../../../@event-builders/index.d.ts\" />\n\n");
        }

        if (events.stream().flatMap(event -> callShapes(event, aliasRegistry).stream())
                .anyMatch(AdapterAliasGenerator.RenderedShape::usesRegistry)) {
            sb.append("import type { RegistryTypes } from \"@special/types\";\n\n");
        }
        Set<String> imports = referencedClasses(events, payloads);
        Set<TypeScriptImportNames.Symbol> symbols = new LinkedHashSet<>();
        for (String fqn : imports) {
            int dot = fqn.lastIndexOf('.');
            symbols.add(new TypeScriptImportNames.Symbol(fqn, "$" + (dot >= 0 ? fqn.substring(dot + 1) : fqn)));
            String adapterName = adapterAliasGenerator.aliasNameOf(fqn);
            if (adapterName != null) symbols.add(new TypeScriptImportNames.Symbol(fqn, adapterName));
        }
        Set<String> payloadNames = payloads.values().stream()
                .flatMap(payload -> payload.declarationNames().stream()).collect(Collectors.toSet());
        TypeScriptImportNames importNames = new TypeScriptImportNames(symbols, payloadNames, Set.of());
        // Imports and signatures share a module-local view; catalog identities and shared aliases stay unchanged.
        TypeAliasRegistry moduleAliases = importNames.hasAliases()
                ? aliasRegistry.withReferences(importNames::reference) : aliasRegistry;

        if (!imports.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (var binding : importNames.bindings().entrySet()) {
                String fqn = binding.getKey().fqn();
                int dot = fqn.lastIndexOf('.');
                String pkg = dot >= 0 ? fqn.substring(0, dot) : "";
                List<String> names = importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>());
                String exported = binding.getKey().exportedName();
                String local = binding.getValue();
                names.add(exported.equals(local) ? exported : exported + " as " + local);
            }

            for (var entry : importsByPackage.entrySet()) {
                String importPath = "java:" + entry.getKey().replace('.', '/');
                // Reflection order must not affect module output.
                List<String> names = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
                Collections.sort(names);
                sb.append("import { ").append(String.join(", ", names));
                sb.append(" } from \"").append(importPath).append("\";\n");
            }
            sb.append("\n");
        }

        appendPayloadInterfaces(sb, payloads, "\n");

        // Empty module declaration, to keep the structure consistent
        sb.append("declare module \"@side-only/").append(scriptType.name).append("/events\" {\n");
        sb.append("}\n\n");

        // export {} + declare global
        sb.append("export {};\n\n");
        sb.append("declare global {\n");

        // Group by group
        Map<String, List<EventCatalogEntry>> byGroup = events.stream()
                .collect(Collectors.groupingBy(EventCatalogEntry::group, LinkedHashMap::new, Collectors.toList()));

        for (var groupEntry : byGroup.entrySet()) {
            String group = groupEntry.getKey();
            List<EventCatalogEntry> groupEvents = groupEntry.getValue();

            sb.append("    namespace ").append(group).append(" {\n");

            for (EventCatalogEntry event : groupEvents) {
                sb.append(generateEventMethod(event, payloads.get(event.eventType()), moduleAliases));
            }

            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Gets the TypeScript identifier for a class (a nested class uses the Parent$Child form).
     */
    private static String getTsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return getTsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    /** Renders module namespaces without declaring legacy global event groups. */
    public String generateModuleNamespaces(List<EventCatalogEntry> events) {
        return generateModuleNamespaces(events, List.of());
    }

    /** Generates TypeScript namespace declarations for module-scoped events and registry builders. */
    public String generateModuleNamespaces(List<EventCatalogEntry> events, List<RegistryBuilderSurfaceEntry> builders) {
        Map<Class<?>, EventPayload> payloads = EventPayloadDeclarations.resolve(events, builders);
        TypeAliasRegistry inline = aliasRegistry.withReferences((fqn, symbol) -> {
            int dot = fqn.lastIndexOf('.');
            String pkg = dot >= 0 ? fqn.substring(0, dot) : "";
            return "import(\"java:" + pkg.replace('.', '/') + "\")." + symbol;
        });
        StringBuilder output = new StringBuilder();
        if (events.stream().flatMap(event -> callShapes(event, inline).stream())
                .anyMatch(AdapterAliasGenerator.RenderedShape::usesRegistry)) {
            output.append("    import type { RegistryTypes } from \"@special/types\";\n");
        }
        appendPayloadInterfaces(output, payloads, "");
        Map<String, List<EventCatalogEntry>> groups = events.stream().collect(Collectors.groupingBy(
                EventCatalogEntry::group, TreeMap::new, Collectors.toList()));
        groups.forEach((name, entries) -> {
            output.append("    export namespace ").append(name).append(" {\n");
            entries.stream().sorted(Comparator.comparing(EventCatalogEntry::name))
                    .forEach(event -> output.append(generateEventMethod(event, payloads.get(event.eventType()), inline)));
            output.append("    }\n");
        });
        return output.toString();
    }

    private static void appendPayloadInterfaces(StringBuilder output, Map<Class<?>, EventPayload> payloads,
                                                String separator) {
        for (EventPayload payload : payloads.values()) {
            if (payload.selectorScoped()) {
                for (EventVariant variant : payload.variants()) {
                    appendPayloadInterface(output, variant.typeName(), variant.members());
                    output.append(separator);
                }
            } else {
                appendPayloadInterface(output, payload.name(), payload.members());
                output.append(separator);
            }
        }
    }

    private static void appendPayloadInterface(StringBuilder output, String name,
                                               List<RegistryBuilderSurfaceEntry.Member> members) {
        output.append("interface ").append(name).append(" {\n");
        for (RegistryBuilderSurfaceEntry.Member member : members) {
            output.append("    ");
            switch (member.kind()) {
                case WRITABLE_PROPERTY -> output.append(member.name()).append(": ").append(member.tsType());
                case READ_ONLY_PROPERTY -> output.append("readonly ").append(member.name()).append(": ").append(member.tsType());
                case METHOD -> output.append(member.tsType());
            }
            output.append(";\n");
        }
        output.append("}\n");
    }

    private static void appendRequiredSelector(StringBuilder sb, String name, String keyType, String eventType) {
        String key = "selector: " + keyType;
        String handler = "handler: ((event: " + eventType + ") => void)";
        String priority = "priority: \"HIGHEST\" | \"HIGH\" | \"NORMAL\" | \"LOW\" | \"LOWEST\"";
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + name, List.of(key, handler), ": void;");
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + name, List.of(priority, key, handler), ": void;");
    }

    private String generateEventMethod(EventCatalogEntry event, EventPayload payload, TypeAliasRegistry moduleAliases) {
        StringBuilder sb = new StringBuilder();

        // Custom events declared by ScriptEvents: the payload is anything and the script may fire them itself.
        if (event.scriptDefined()) {
            sb.append("        namespace ").append(event.name()).append(" {\n");
            TypeScriptDeclarationFormatter.appendCallable(sb, 12, "function post", List.of("payload?: any"), ": void;");
            sb.append("        }\n");
            TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + event.name(),
                    List.of("handler: ((payload: any) => void)"), ": void;");
            return sb.toString();
        }

        if (!event.callSignatures().isEmpty()) {
            for (EventCallSignature signature : event.callSignatures()) {
                List<String> parameters = new ArrayList<>();
                for (EventCallSignature.Parameter input : signature.inputs()) {
                    parameters.add(input.name() + ": " + AdapterAliasGenerator
                            .renderShape(input.shape(), moduleAliases, true).type());
                }
                List<String> callbackParameters = new ArrayList<>();
                for (EventCallSignature.Parameter parameter : signature.callbackParameters()) {
                    callbackParameters.add(parameter.name() + ": " + AdapterAliasGenerator
                            .renderShape(parameter.shape(), moduleAliases, false).type());
                }
                parameters.add("callback: (" + String.join(", ", callbackParameters) + ") => void");
                TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + event.name(), parameters,
                        ": " + AdapterAliasGenerator.renderShape(signature.returns(), moduleAliases, false).type() + ";");
            }
            return sb.toString();
        }

        if (payload != null && payload.selectorScoped()) {
            if (payload.variants().isEmpty()) {
                appendRequiredSelector(sb, event.name(), "never", "never");
            }
            for (EventVariant variant : payload.variants()) {
                String keyType = variant.selectors().stream().map(EventPayloadDeclarations::quoteSelector)
                        .collect(Collectors.joining(" | "));
                appendRequiredSelector(sb, event.name(), keyType, variant.typeName());
            }
            return sb.toString();
        }

        // Event class type
        String eventType = "any";
        if (payload != null) {
            eventType = payload.name();
        } else if (event.eventType() != null) {
            eventType = moduleAliases.reference(event.eventType().getName(), "$" + getTsClassName(event.eventType()));
            // Handle generics
            List<TypeVariable<?>> typeParams = JavaGenericTypes.parameters(event.eventType());
            if (!typeParams.isEmpty()) {
                eventType += "<";
                List<String> args = new ArrayList<>();
                for (int i = 0; i < typeParams.size(); i++) {
                    args.add("any");
                }
                eventType += String.join(", ", args);
                eventType += ">";
            }
        }

        if (event.selectorRequired()) {
            String keyType = event.dispatchKeyType() == null ? "unknown" : renderKeyType(event, moduleAliases);
            appendRequiredSelector(sb, event.name(), keyType, eventType);
            return sb.toString();
        }

        // The version without a dispatch key
        TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + event.name(),
                List.of("handler: ((event: " + eventType + ") => void)"), ": void;");

        // Variant that includes a dispatch key.
        if (event.dispatchable() && event.dispatchKeyType() != null) {
            String keyType = renderKeyType(event, moduleAliases);
            TypeScriptDeclarationFormatter.appendCallable(sb, 8, "function " + event.name(),
                    List.of("extra: " + keyType, "handler: ((event: " + eventType + ") => void)"), ": void;");
        }

        return sb.toString();
    }

    private static String renderKeyType(EventCatalogEntry event, TypeAliasRegistry moduleAliases) {
        return TypeScriptClassRenderer.renderTypeRef(
                JavaTypeScriptProjection.project(event.dispatchKeyType(), TypeReflector.toRef(event.dispatchKeyType())),
                moduleAliases, true);
    }

    /** Includes emitted payload and dispatch imports without reflecting replaced builder payloads. */
    Set<String> referencedClasses(List<EventCatalogEntry> events, List<RegistryBuilderSurfaceEntry> builders) {
        return referencedClasses(events, EventPayloadDeclarations.resolve(events, builders));
    }

    private Set<String> referencedClasses(List<EventCatalogEntry> events, Map<Class<?>, EventPayload> payloads) {
        Set<String> imports = new LinkedHashSet<>();
        for (EventCatalogEntry event : events) {
            if (event.scriptDefined()) continue;
            if (!event.callSignatures().isEmpty()) {
                for (AdapterAliasGenerator.RenderedShape shape : callShapes(event, aliasRegistry)) {
                    imports.addAll(shape.referenceFqns());
                }
                continue;
            }
            if (event.eventType() != null && !payloads.containsKey(event.eventType())) collectImports(event.eventType(), imports);
            EventPayload payload = payloads.get(event.eventType());
            if (event.dispatchable() && event.dispatchKeyType() != null && (payload == null || !payload.selectorScoped())) {
                collectImports(event.dispatchKeyType(), imports);
            }
        }
        return imports;
    }

    private static List<AdapterAliasGenerator.RenderedShape> callShapes(
            EventCatalogEntry event, TypeAliasRegistry aliases) {
        List<AdapterAliasGenerator.RenderedShape> shapes = new ArrayList<>();
        if (event.scriptDefined()) return shapes;
        for (EventCallSignature signature : event.callSignatures()) {
            for (EventCallSignature.Parameter parameter : signature.inputs()) {
                shapes.add(AdapterAliasGenerator.renderShape(parameter.shape(), aliases, true));
            }
            for (EventCallSignature.Parameter parameter : signature.callbackParameters()) {
                shapes.add(AdapterAliasGenerator.renderShape(parameter.shape(), aliases, false));
            }
            shapes.add(AdapterAliasGenerator.renderShape(signature.returns(), aliases, false));
        }
        return shapes;
    }

    private void collectImports(Class<?> cls, Set<String> imports) {
        if (cls == null || cls.isPrimitive() || cls == Object.class) return;

        // The class is an array: collect the component type recursively.
        if (cls.isArray()) {
            collectImports(cls.getComponentType(), imports);
            return;
        }

        // Emitted signatures are dependency roots independently of the primary scan. The
        // declaration closure applies the current configuration's exclusion and hide policy.
        imports.add(cls.getName());
    }
}
