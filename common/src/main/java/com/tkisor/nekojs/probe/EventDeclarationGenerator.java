package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import com.tkisor.nekojs.script.ScriptType;

import java.lang.reflect.*;
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
    private final TypeConverter typeConverter;
    private final AdapterAliasGenerator adapterAliasGenerator;

    public EventDeclarationGenerator(TypeConverter typeConverter, AdapterAliasGenerator adapterAliasGenerator) {
        this.typeConverter = typeConverter;
        this.adapterAliasGenerator = adapterAliasGenerator;
    }

    /**
     * 为指定 ScriptType 生成事件声明。
     */
    public String generate(List<EventCatalogEntry> events, ScriptType scriptType) {
        StringBuilder sb = new StringBuilder();

        // 收集所有需要 import 的类型
        Set<String> imports = new LinkedHashSet<>();
        for (EventCatalogEntry event : events) {
            if (event.eventType() != null) {
                collectImports(event.eventType(), imports);
            }
            if (event.dispatchKeyType() != null) {
                collectImports(event.dispatchKeyType(), imports);
            }
        }

        // 生成 import 语句
        if (!imports.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (String fqn : imports) {
                String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                List<String> names = importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>());
                names.add("$" + simple);
                // dispatch key 参数放宽后可能引用 $Foo_，需一并导入
                String aliasName = adapterAliasGenerator.aliasNameOf(fqn);
                if (aliasName != null) {
                    names.add(aliasName);
                }
            }

            for (var entry : importsByPackage.entrySet()) {
                String importPath = "java:" + entry.getKey().replace('.', '/');
                sb.append("import { ").append(String.join(", ", entry.getValue()));
                sb.append(" } from \"").append(importPath).append("\";\n");
            }
            sb.append("\n");
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
                sb.append(generateEventMethod(event));
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

    private String generateEventMethod(EventCatalogEntry event) {
        StringBuilder sb = new StringBuilder();

        // 事件类类型
        String eventType = "any";
        if (event.eventType() != null) {
            eventType = "$" + getTsClassName(event.eventType());
            // 处理泛型
            TypeVariable<?>[] typeParams = event.eventType().getTypeParameters();
            if (typeParams.length > 0) {
                eventType += "<";
                List<String> args = new ArrayList<>();
                for (int i = 0; i < typeParams.length; i++) {
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
            String keyType = typeConverter.toTypeScript(event.dispatchKeyType(), true);
            sb.append("        function ").append(event.name());
            sb.append("(extra: ").append(keyType);
            sb.append(", handler: ((event: ").append(eventType).append(") => void)): void;\n");
        }

        return sb.toString();
    }

    private void collectImports(Class<?> cls, Set<String> imports) {
        if (cls == null || cls.isPrimitive() || cls == Object.class) return;

        // 数组类：递归收集组件类型
        if (cls.isArray()) {
            collectImports(cls.getComponentType(), imports);
            return;
        }

        String name = cls.getName();
        if (!isRelevantClass(name)) return;

        // 防止循环引用导致无限递归（StackOverflowError）
        if (!imports.add(name)) return;

        // 收集父类
        if (cls.getSuperclass() != null) {
            collectImports(cls.getSuperclass(), imports);
        }

        // 收集接口
        for (Class<?> iface : cls.getInterfaces()) {
            collectImports(iface, imports);
        }

        // 收集公共方法参数和返回值类型
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                collectTypeImports(method.getGenericReturnType(), imports);
                for (Type paramType : method.getGenericParameterTypes()) {
                    collectTypeImports(paramType, imports);
                }
            }
        }

        // 收集公共字段类型
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                collectTypeImports(field.getGenericType(), imports);
            }
        }
    }

    private void collectTypeImports(Type type, Set<String> imports) {
        if (type instanceof Class<?> cls) {
            collectImports(cls, imports);
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) {
                collectImports(rawCls, imports);
            }
            for (Type arg : pt.getActualTypeArguments()) {
                collectTypeImports(arg, imports);
            }
        } else if (type instanceof GenericArrayType gat) {
            collectTypeImports(gat.getGenericComponentType(), imports);
        }
    }

    private boolean isRelevantClass(String name) {
        return name.startsWith("java.") ||
               name.startsWith("net.minecraft.") ||
               name.startsWith("net.neoforged.") ||
               name.startsWith("com.tkisor.nekojs.");
    }
}
