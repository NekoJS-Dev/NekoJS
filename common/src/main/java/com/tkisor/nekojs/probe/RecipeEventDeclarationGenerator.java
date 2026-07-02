package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.script.ScriptType;

import java.util.*;

/**
 * 生成 recipe 事件的 TypeScript 声明。
 *
 * <p>参考 ProbeJS：在 @side-only/server/events/recipes/index.d.ts 中定义：
 * <ul>
 *   <li>每个 recipe 类型的 builder 类（如 Minecraft$Smelting extends $RecipeJsonBuilder）</li>
 *   <li>DocumentedRecipes 类 — 把所有命名空间组织成嵌套对象</li>
 * </ul>
 *
 * <p>然后 ClassDeclGenerator 通过 skipGetter+overrideGetter 机制，
 * 让 $RecipeEventJS.recipes getter 返回 DocumentedRecipes 而不是 $RecipeRegistryProxy。
 */
public final class RecipeEventDeclarationGenerator {
    private final TypeAliasRegistry aliasRegistry;

    public RecipeEventDeclarationGenerator(TypeAliasRegistry aliasRegistry) {
        this.aliasRegistry = aliasRegistry;
    }

    /**
     * 解析某个 Java 类型的输入别名名（如 ItemStack → $ItemStack_）。
     * 优先取适配器驱动的注册别名，缺失时回退到 {@code $simple_}。
     */
    private String resolveAlias(String pkg, String simple) {
        String fqn = pkg + "." + simple;
        if (aliasRegistry.hasAlias(fqn)) return aliasRegistry.getAlias(fqn);
        return "$" + simple + "_";
    }

    /**
     * 为指定 ScriptType 生成 recipe 事件声明。
     */
    public String generate(List<RecipeNamespaceCatalogEntry> namespaces, ScriptType scriptType) {
        // 第一遍：收集所有需要的 import（基于 field kind 和 handler param type）
        Map<String, Set<String>> importsByPkg = new TreeMap<>();
        addImport(importsByPkg, "com.tkisor.nekojs.api.recipe", "RecipeJsonBuilder");
        collectImports(namespaces, importsByPkg);

        StringBuilder sb = new StringBuilder();
        // 生成 import 语句
        for (var entry : importsByPkg.entrySet()) {
            String importPath = "java:" + entry.getKey().replace('.', '/');
            sb.append("import { ").append(String.join(", ", entry.getValue()));
            sb.append(" } from \"").append(importPath).append("\";\n");
        }
        sb.append("\n");

        // declare module
        sb.append("declare module \"@side-only/").append(scriptType.name).append("/events/recipes\" {\n");

        // 1. 为每个 schema 类型生成单独的 builder 类（链式 setter）
        for (RecipeNamespaceCatalogEntry ns : namespaces) {
            for (RecipeSchemaTypeEntry schemaType : ns.schemaTypes()) {
                if (schemaType.fields().isEmpty()) continue;
                generateSchemaClass(sb, schemaType, ns.namespace());
            }
        }

        // 2. 生成 DocumentedRecipes 类 — 关键：将所有命名空间组织为嵌套对象
        sb.append("\n    /**\n");
        sb.append("     * Top-level recipe registry. Access via event.recipes.<namespace>.<type>(...)\n");
        sb.append("     */\n");
        sb.append("    export class DocumentedRecipes {\n");

        for (RecipeNamespaceCatalogEntry ns : namespaces) {
            if (ns.handlerMethods().isEmpty() && ns.schemaTypes().isEmpty()) continue;
            generateNamespaceMember(sb, ns);
        }

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成命名空间成员（DocumentedRecipes 内的属性）。
     * 例如:
     * minecraft: {
     *     smelting(result: $ItemStack_, ingredient: $Ingredient_, xp?: number, time?: number): Minecraft$Smelting;
     *     crafting_shaped(...): Minecraft$CraftingShaped;
     * }
     */
    private void generateNamespaceMember(StringBuilder sb, RecipeNamespaceCatalogEntry ns) {
        sb.append("        ").append(ns.namespace()).append(": {\n");

        Set<String> emittedMethods = new HashSet<>();

        // Handler 方法（如 shaped, smelting 等有 Java 实现的方法）
        for (RecipeHandlerMethodEntry method : ns.handlerMethods()) {
            generateHandlerMethod(sb, method, ns.namespace());
            emittedMethods.add(method.methodName());
        }

        // Schema 类型方法（自动发现的 recipe 类型）
        for (RecipeSchemaTypeEntry schemaType : ns.schemaTypes()) {
            if (emittedMethods.contains(schemaType.type())) continue;
            generateSchemaMethod(sb, schemaType, ns.namespace());
            emittedMethods.add(schemaType.type());
        }

        sb.append("        };\n");
    }

    /**
     * 生成 handler 方法（有 Java 实现）。
     */
    private void generateHandlerMethod(StringBuilder sb, RecipeHandlerMethodEntry method, String namespace) {
        sb.append("            ").append(safeMethodName(method.methodName())).append("(");

        List<RecipeHandlerMethodEntry.HandlerParam> params = method.params();
        for (int i = 0; i < params.size(); i++) {
            RecipeHandlerMethodEntry.HandlerParam param = params.get(i);
            if (i > 0) sb.append(", ");
            sb.append(safeParamName(param.name(), i));
            if (i >= method.minArgs() || param.optional()) sb.append("?");
            sb.append(": ").append(mapTypeToTs(param.type()));
        }

        // 返回类型：对应的 builder 类，或 $RecipeJsonBuilder
        sb.append("): ").append(builderClassName(namespace, method.methodName())).append(";\n");
    }

    /**
     * 生成 schema 类型方法（自动发现的 recipe 类型）。
     */
    private void generateSchemaMethod(StringBuilder sb, RecipeSchemaTypeEntry schemaType, String namespace) {
        String returnType = !schemaType.fields().isEmpty()
                ? capitalize(namespace) + "$" + capitalize(schemaType.type())
                : "$RecipeJsonBuilder";

        // 命名对象重载
        if (!schemaType.fields().isEmpty()) {
            sb.append("            ").append(safeMethodName(schemaType.type())).append("(options: { ");
            boolean first = true;
            for (RecipeSchemaTypeEntry.SchemaField field : schemaType.fields()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(field.name());
                if (!field.required()) sb.append("?");
                sb.append(": ");
                sb.append(mapFieldKindToTs(field.kind()));
                if (field.array()) sb.append("[]");
            }
            sb.append(" }): ").append(returnType).append(";\n");
        }

        // 构造器位置参数重载
        for (List<String> ctor : schemaType.constructors()) {
            sb.append("            ").append(safeMethodName(schemaType.type())).append("(");
            for (int i = 0; i < ctor.size(); i++) {
                if (i > 0) sb.append(", ");
                String fieldName = ctor.get(i);
                sb.append(safeParamName(fieldName, i)).append(": ");
                sb.append(getFieldTsType(schemaType, fieldName));
            }
            sb.append("): ").append(returnType).append(";\n");
        }
    }

    /**
     * 为 schema 类型生成 builder 类。
     */
    private void generateSchemaClass(StringBuilder sb, RecipeSchemaTypeEntry schemaType, String namespace) {
        String className = capitalize(namespace) + "$" + capitalize(schemaType.type());

        sb.append("    export class ").append(className).append(" extends $RecipeJsonBuilder {\n");

        for (RecipeSchemaTypeEntry.SchemaField field : schemaType.fields()) {
            sb.append("        ").append(safeMethodName(field.name())).append("(");
            sb.append(safeParamName(field.name(), 0)).append(": ");
            sb.append(mapFieldKindToTs(field.kind()));
            if (field.array()) sb.append("[]");
            sb.append("): this;\n");
        }

        sb.append("    }\n");
    }

    /**
     * 推断 builder 类名 — 用于 handler 方法的返回类型。
     * 如果对应名称的 schema 类型存在，返回 builder 类；否则返回 $RecipeJsonBuilder。
     */
    private String builderClassName(String namespace, String methodName) {
        return "$RecipeJsonBuilder";
    }

    private String getFieldTsType(RecipeSchemaTypeEntry schemaType, String fieldName) {
        for (RecipeSchemaTypeEntry.SchemaField field : schemaType.fields()) {
            if (field.name().equals(fieldName)) {
                String type = mapFieldKindToTs(field.kind());
                if (field.array()) return type + "[]";
                return type;
            }
        }
        return "any";
    }

    private String mapFieldKindToTs(String kind) {
        String[] pkgType = kindToImport(kind);
        if (pkgType != null) return resolveAlias(pkgType[0], pkgType[1]);
        return switch (kind) {
            case "STRING" -> "string";
            case "INT", "NUMBER" -> "number";
            case "BOOLEAN" -> "boolean";
            case "JSON" -> "object";
            default -> "any";
        };
    }

    /**
     * 收集所有命名空间用到的输入别名类型，生成 import 映射。
     */
    private void collectImports(List<RecipeNamespaceCatalogEntry> namespaces, Map<String, Set<String>> importsByPkg) {
        Set<String> neededKinds = new HashSet<>();
        Set<String> neededHandlerTypes = new HashSet<>();

        for (RecipeNamespaceCatalogEntry ns : namespaces) {
            // schema field kinds
            for (RecipeSchemaTypeEntry schemaType : ns.schemaTypes()) {
                for (RecipeSchemaTypeEntry.SchemaField field : schemaType.fields()) {
                    neededKinds.add(field.kind());
                }
            }
            // handler param types
            for (RecipeHandlerMethodEntry method : ns.handlerMethods()) {
                for (RecipeHandlerMethodEntry.HandlerParam param : method.params()) {
                    neededHandlerTypes.add(param.type());
                }
            }
        }

        // 从 kind 推导 import (pkg -> kindName)
        for (String kind : neededKinds) {
            String[] pkgType = kindToImport(kind);
            if (pkgType != null) addImport(importsByPkg, pkgType[0], pkgType[1] + "_");
        }
        // 从 handler param type 推导 import
        for (String type : neededHandlerTypes) {
            String[] pkgType = typeToImport(type);
            if (pkgType != null) addImport(importsByPkg, pkgType[0], pkgType[1] + "_");
        }
    }

    /**
     * field kind → (package, className)。返回的 className 需加 _ 后缀作为输入别名。
     */
    private static String[] kindToImport(String kind) {
        return switch (kind) {
            case "INGREDIENT" -> new String[]{"net.minecraft.world.item.crafting", "Ingredient"};
            case "ITEM_STACK" -> new String[]{"net.minecraft.world.item", "ItemStack"};
            case "FLUID_STACK" -> new String[]{"net.neoforged.neoforge.fluids", "FluidStack"};
            case "FLUID_INGREDIENT" -> new String[]{"net.neoforged.neoforge.fluids.crafting", "FluidIngredient"};
            case "SIZED_FLUID_INGREDIENT" -> new String[]{"net.neoforged.neoforge.fluids.crafting", "SizedFluidIngredient"};
            default -> null;
        };
    }

    /**
     * handler param type name → (package, className)。
     */
    private static String[] typeToImport(String type) {
        return switch (type) {
            case "ItemStack" -> new String[]{"net.minecraft.world.item", "ItemStack"};
            case "Ingredient" -> new String[]{"net.minecraft.world.item.crafting", "Ingredient"};
            case "FluidStack" -> new String[]{"net.neoforged.neoforge.fluids", "FluidStack"};
            case "FluidIngredient" -> new String[]{"net.neoforged.neoforge.fluids.crafting", "FluidIngredient"};
            case "SizedFluidIngredient" -> new String[]{"net.neoforged.neoforge.fluids.crafting", "SizedFluidIngredient"};
            default -> null;
        };
    }

    private static void addImport(Map<String, Set<String>> importsByPkg, String pkg, String className) {
        importsByPkg.computeIfAbsent(pkg, k -> new TreeSet<>()).add("$" + className);
    }

    private String mapTypeToTs(String typeName) {
        String[] pkgType = typeToImport(typeName);
        if (pkgType != null) return resolveAlias(pkgType[0], pkgType[1]);
        return switch (typeName) {
            case "string" -> "string";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "RecipeJsonValue", "json" -> "object";
            case "any" -> "any";
            default -> "$" + typeName + "_";
        };
    }

    /**
     * 修复参数名 — 不能是 TS 保留字，不能与类型名冲突。
     */
    private static String safeParamName(String name, int index) {
        if (name == null || name.isEmpty()) return "arg" + index;
        // TS 保留字
        return switch (name) {
            case "default", "function", "class", "let", "const", "var", "if", "else",
                 "return", "for", "while", "do", "switch", "case", "break", "continue",
                 "throw", "try", "catch", "finally", "new", "delete", "typeof", "instanceof",
                 "in", "of", "this", "super", "extends", "implements", "interface",
                 "package", "import", "export", "from", "as" -> "_" + name;
            default -> name;
        };
    }

    /**
     * 修复方法名 — recipe 类型名可能是不合法标识符（含特殊字符）时引号包围。
     */
    private static String safeMethodName(String name) {
        if (name == null || name.isEmpty()) return "_";
        // 检查是否是合法 JS 标识符
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return "\"" + name + "\"";
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return "\"" + name + "\"";
        }
        return name;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.contains("_")) {
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : s.toCharArray()) {
                if (c == '_') {
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
