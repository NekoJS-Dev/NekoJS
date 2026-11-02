package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCallSignature;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;
import com.tkisor.nekojs.probe.EventPayloadDeclarations;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.reflect.JavaGenericTypes;

import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件声明生成器：从 EventCatalogEntry 生成 TypeScript 事件声明。
 *
 * <p>格式参考 ProbeJS：
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
     * 为指定 ScriptType 生成事件声明。
     */
    public String generate(List<EventCatalogEntry> events, ScriptType scriptType) {
        return generate(events, scriptType, List.of());
    }

    public String generate(List<EventCatalogEntry> events, ScriptType scriptType,
                           List<RegistryBuilderSurfaceEntry> builders) {
        Map<Class<?>, EventPayload> payloads = EventPayloadDeclarations.resolve(events, builders);
        StringBuilder sb = new StringBuilder();
        if (!payloads.isEmpty()) {
            sb.append("/// <reference path=\"../../../@event-builders/index.d.ts\" />\n\n");
        }

        if (events.stream().flatMap(event -> callShapes(event, aliasRegistry).stream())
                .anyMatch(AdapterAliasGenerator.RenderedShape::usesRegistry)) {
            sb.append("import type { RegistryTypes } from \"@special/types\";\n\n");
        }
        Set<String> imports = referencedClasses(events, payloads);
        Set<TypeScriptImportNames.Symbol> symbols = new LinkedHashSet<>();
        for (String fqn : imports) {
            symbols.add(new TypeScriptImportNames.Symbol(fqn, "$" + fqn.substring(fqn.lastIndexOf('.') + 1)));
            String adapterName = adapterAliasGenerator.aliasNameOf(fqn);
            if (adapterName != null) symbols.add(new TypeScriptImportNames.Symbol(fqn, adapterName));
        }
        Set<String> payloadNames = payloads.values().stream().map(EventPayload::name).collect(Collectors.toSet());
        TypeScriptImportNames importNames = new TypeScriptImportNames(symbols, payloadNames, Set.of());
        // Imports and signatures share a module-local view; catalog identities and shared aliases stay unchanged.
        TypeAliasRegistry moduleAliases = importNames.hasAliases()
                ? aliasRegistry.withReferences(importNames::reference) : aliasRegistry;

        if (!imports.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (var binding : importNames.bindings().entrySet()) {
                String fqn = binding.getKey().fqn();
                String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
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

        for (EventPayload payload : payloads.values()) {
            sb.append("interface ").append(payload.name()).append(" {\n");
            for (RegistryBuilderSurfaceEntry.Member member : payload.members()) {
                switch (member.kind()) {
                    case WRITABLE_PROPERTY -> sb.append("    ").append(member.name()).append(": ")
                            .append(member.tsType()).append(";\n");
                    case READ_ONLY_PROPERTY -> sb.append("    readonly ").append(member.name()).append(": ")
                            .append(member.tsType()).append(";\n");
                    case METHOD -> sb.append("    ").append(member.tsType()).append(";\n");
                }
            }
            sb.append("}\n\n");
        }

        // 空的 module 声明（保持结构一致）
        sb.append("declare module \"@side-only/").append(scriptType.name).append("/events\" {\n");
        sb.append("}\n\n");

        // export {} + declare global
        sb.append("export {};\n\n");
        sb.append("declare global {\n");

        // 按 group 分组
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
     * 获取类的 TypeScript 标识符名（内部类使用 Parent$Child 格式）。
     */
    private static String getTsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return getTsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private String generateEventMethod(EventCatalogEntry event, EventPayload payload, TypeAliasRegistry moduleAliases) {
        StringBuilder sb = new StringBuilder();

        // ScriptEvents 声明的自定义事件：载荷任意、且脚本可自己触发
        if (event.scriptDefined()) {
            sb.append("        namespace ").append(event.name()).append(" {\n");
            sb.append("            function post(payload?: any): void;\n");
            sb.append("        }\n");
            sb.append("        function ").append(event.name());
            sb.append("(handler: ((payload: any) => void)): void;\n");
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
                sb.append("        function ").append(event.name()).append("(")
                        .append(String.join(", ", parameters)).append("): ")
                        .append(AdapterAliasGenerator.renderShape(signature.returns(), moduleAliases, false).type())
                        .append(";\n");
            }
            return sb.toString();
        }

        // 事件类类型
        String eventType = "any";
        if (payload != null) {
            eventType = payload.name();
        } else if (event.eventType() != null) {
            eventType = moduleAliases.reference(event.eventType().getName(), "$" + getTsClassName(event.eventType()));
            // 处理泛型
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

        // 无 dispatch key 的版本
        sb.append("        function ").append(event.name());
        sb.append("(handler: ((event: ").append(eventType).append(") => void)): void;\n");

        // 有 dispatch key 的版本
        if (event.dispatchable() && event.dispatchKeyType() != null) {
            String keyType = TypeScriptClassRenderer.renderTypeRef(
                    JavaTypeScriptProjection.project(event.dispatchKeyType(), TypeReflector.toRef(event.dispatchKeyType())),
                    moduleAliases, true);
            sb.append("        function ").append(event.name());
            sb.append("(extra: ").append(keyType);
            sb.append(", handler: ((event: ").append(eventType).append(") => void)): void;\n");
        }

        return sb.toString();
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
            if (event.dispatchable() && event.dispatchKeyType() != null) collectImports(event.dispatchKeyType(), imports);
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

        // 数组类：递归收集组件类型
        if (cls.isArray()) {
            collectImports(cls.getComponentType(), imports);
            return;
        }

        // Emitted signatures are dependency roots independently of the primary scan. The
        // declaration closure applies the current configuration's exclusion and hide policy.
        imports.add(cls.getName());
    }
}
