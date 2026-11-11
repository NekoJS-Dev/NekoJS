package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.ScriptType;

import java.lang.reflect.*;
import java.util.*;

/**
 * 绑定声明生成器：从 BindingCatalogEntry 生成 TypeScript 全局绑定声明。
 *
 * <p>格式参考 ProbeJS：
 * <pre>
 * import { $Platform } from "java:dev/latvian/mods/kubejs/platform";
 * import { $ConsoleJS } from "java:dev/latvian/mods/kubejs/util";
 *
 * export {};
 *
 * declare global {
 *     let Platform: typeof $Platform;
 *     let console: $ConsoleJS;
 * }
 * </pre>
 */
public final class BindingDeclarationGenerator {
    /**
     * 为指定 ScriptType 生成绑定声明。
     */
    public String generate(List<BindingCatalogEntry> bindings, ScriptType scriptType) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Bindings for ").append(scriptType.name).append(" scripts\n");

        // 收集需要 import 的类
        Set<String> imports = referencedClasses(bindings);

        // 生成 import
        Map<String, List<String>> importsByPackage = new TreeMap<>();
        for (String fqn : imports) {
            String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add("$" + simple);
        }
        for (var entry : importsByPackage.entrySet()) {
            String importPath = "java:" + entry.getKey().replace('.', '/');
            // 包内符号名字典序排序：反射顺序无规范保证，跨 JVM 运行需确定性输出
            List<String> names = new ArrayList<>(new java.util.LinkedHashSet<>(entry.getValue()));
            java.util.Collections.sort(names);
            sb.append("import { ").append(String.join(", ", names));
            sb.append(" } from \"").append(importPath).append("\";\n");
        }

        // Emit type aliases for bindings that use a TypeDoc typeOverride (e.g. ItemJS -> NekoItemHelper).
        // Without this, `let ItemJS: NekoItemHelper` references an undefined name and the binding loses
        // all member completion. The alias forwards to the real $Class so the override resolves to members.
        // Inline type expressions already describe their shape and cannot be used as alias names.
        Set<String> emittedAlias = new LinkedHashSet<>();
        for (BindingCatalogEntry binding : bindings) {
            String override = binding.typeOverride();
            if (override != null && override.matches("[\\p{javaLetter}_$][\\p{javaLetterOrDigit}_$]*")
                    && binding.javaType() != null && emittedAlias.add(override)) {
                sb.append("type ").append(override).append(" = $")
                        .append(getTsClassName(binding.javaType())).append(";\n");
            }
        }

        sb.append("\nexport {};\n\n");
        sb.append("declare global {\n");

        for (BindingCatalogEntry binding : bindings) {
            sb.append(generateBinding(binding));
        }

        sb.append("}\n");
        return sb.toString();
    }

    /** Emits ambient ESM exports and matching literal CommonJS overloads from runtime registrations. */
    public String generateModules(Map<String, List<BindingCatalogEntry>> modules) {
        return generateModules(modules, Map.of());
    }

    public String generateModules(Map<String, List<BindingCatalogEntry>> modules, Map<String, String> events) {
        StringBuilder output = new StringBuilder("// API modules for the selected script environment.\n");
        for (var module : new TreeMap<>(modules).entrySet()) {
            output.append("declare module \"").append(module.getKey()).append("\" {\n");
            module.getValue().stream().sorted(Comparator.comparing(BindingCatalogEntry::name)).forEach(binding -> {
                output.append("    export const ").append(binding.name()).append(": ");
                Class<?> type = binding.javaType();
                if (type == null) output.append("unknown");
                else if (type == String.class) output.append("string");
                else if (type == Boolean.class || type == boolean.class) output.append("boolean");
                else if (type.isPrimitive() || Number.class.isAssignableFrom(type)) output.append("number");
                else {
                    if (binding.staticClass()) output.append("typeof ");
                    output.append("import(\"java:").append(type.getPackageName().replace('.', '/'))
                            .append("\").$").append(getTsClassName(type));
                }
                output.append(";\n");
            });
            output.append(events.getOrDefault(module.getKey(), ""));
            output.append("}\n");
            output.append("declare function require(id: \"").append(module.getKey())
                    .append("\"): typeof import(\"").append(module.getKey()).append("\");\n");
        }
        output.append("declare function require(id: string): unknown;\n");
        return output.toString();
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

    private String generateBinding(BindingCatalogEntry binding) {
        StringBuilder sb = new StringBuilder();

        // Javadoc
        if (binding.description() != null && !binding.description().isEmpty()) {
            sb.append("    /** ").append(binding.description()).append(" */\n");
        }

        sb.append("    let ").append(binding.name()).append(": ");

        if (binding.typeOverride() != null && !binding.typeOverride().isEmpty()) {
            sb.append(binding.typeOverride());
        } else if (binding.javaType() != null) {
            if (binding.staticClass()) {
                sb.append("typeof $").append(getTsClassName(binding.javaType()));
            } else {
                sb.append("$").append(getTsClassName(binding.javaType()));
            }
        } else {
            sb.append("any");
        }

        // DelegatingBinding 代理委托：把 targetClass 的静态成员（typeof $Extra）交叉合并进来。
        // 例如 Item: NekoItemHelper & typeof $Item —— of/empty 来自 ItemJS 实例，其余静态成员来自 MC Item。
        for (Class<?> extra : binding.extraDocTypes()) {
            sb.append(" & typeof $").append(getTsClassName(extra));
        }

        sb.append(";\n");
        return sb.toString();
    }

    /** Shares actual binding imports with dependency completion, including roots outside the primary allowlist. */
    Set<String> referencedClasses(List<BindingCatalogEntry> bindings) {
        Set<String> imports = new LinkedHashSet<>();
        for (BindingCatalogEntry binding : bindings) {
            collectImports(binding.javaType(), imports);
            for (Class<?> extra : binding.extraDocTypes()) collectImports(extra, imports);
        }
        return imports;
    }

    private void collectImports(Class<?> cls, Set<String> imports) {
        if (cls == null || cls.isPrimitive() || cls == Object.class) return;
        if (cls == String.class || cls == Boolean.class || Number.class.isAssignableFrom(cls)) return;
        // 数组类：递归收集组件类型，避免 "[Lnet/.../Foo;" 描述符泄漏到 import
        if (cls.isArray()) {
            collectImports(cls.getComponentType(), imports);
            return;
        }

        imports.add(cls.getName());
    }
}
