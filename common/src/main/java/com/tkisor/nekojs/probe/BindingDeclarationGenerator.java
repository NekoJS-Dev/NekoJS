package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import com.tkisor.nekojs.script.ScriptType;

import java.lang.reflect.*;
import java.util.*;

/**
 * 绑定声明生成器：从 BindingCatalogEntry 生成 TypeScript 全局绑定声明。
 *
 * <p>格式参考 ProbeJS：
 * <pre>
 * import { $Platform } from "@package/dev/latvian/mods/kubejs/platform";
 * import { $ConsoleJS } from "@package/dev/latvian/mods/kubejs/util";
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
    private final TypeConverter typeConverter;

    public BindingDeclarationGenerator(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

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
        }

        // 生成 import
        Map<String, List<String>> importsByPackage = new TreeMap<>();
        for (String fqn : imports) {
            String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add("$" + simple);
        }
        for (var entry : importsByPackage.entrySet()) {
            String importPath = "@package/" + entry.getKey().replace('.', '/');
            sb.append("import { ").append(String.join(", ", entry.getValue()));
            sb.append(" } from \"").append(importPath).append("\";\n");
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

        sb.append(";\n");
        return sb.toString();
    }

    private void collectImports(Class<?> cls, Set<String> imports) {
        if (cls == null || cls.isPrimitive() || cls == Object.class) return;
        if (cls == String.class || cls == Boolean.class || Number.class.isAssignableFrom(cls)) return;

        imports.add(cls.getName());
    }
}
