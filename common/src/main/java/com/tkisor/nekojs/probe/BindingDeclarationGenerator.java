package com.tkisor.nekojs.probe;

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
        Set<String> imports = new LinkedHashSet<>();
        for (BindingCatalogEntry binding : bindings) {
            if (binding.javaType() != null) {
                collectImports(binding.javaType(), imports);
            }
            for (Class<?> extra : binding.extraDocTypes()) {
                collectImports(extra, imports);
            }
        }

        // 生成 import
        Map<String, List<String>> importsByPackage = new TreeMap<>();
        for (String fqn : imports) {
            String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add("$" + simple);
        }
        for (var entry : importsByPackage.entrySet()) {
            String importPath = "java:" + entry.getKey().replace('.', '/');
            sb.append("import { ").append(String.join(", ", entry.getValue()));
            sb.append(" } from \"").append(importPath).append("\";\n");
        }

        // Emit type aliases for bindings that use a TypeDoc typeOverride (e.g. ItemJS -> NekoItemHelper).
        // Without this, `let ItemJS: NekoItemHelper` references an undefined name and the binding loses
        // all member completion. The alias forwards to the real $Class so the override resolves to members.
        Set<String> emittedAlias = new LinkedHashSet<>();
        for (BindingCatalogEntry binding : bindings) {
            String override = binding.typeOverride();
            if (override != null && !override.isEmpty()
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
