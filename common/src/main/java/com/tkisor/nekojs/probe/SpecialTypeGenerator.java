package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 生成 @special/types/index.d.ts — 注册表字面量联合类型。
 *
 * <p>参考 ProbeJS 的 SpecialDocuments，生成：
 * <pre>
 * declare module "@special/types" {
 *     export namespace RegistryTypes {
 *         type Block = "minecraft:stone" | "minecraft:dirt" | ...;
 *         type Fluid = "minecraft:water" | ...;
 *     }
 * }
 * </pre>
 */
public final class SpecialTypeGenerator {

    /**
     * 生成 @special/types/index.d.ts。
     */
    public void generate(List<RegistryTypeCatalogEntry> registries, Path specialDir) throws IOException {
        Path typesDir = specialDir.resolve("types");
        Files.createDirectories(typesDir);

        StringBuilder sb = new StringBuilder();
        sb.append("declare module \"@special/types\" {\n");
        sb.append("    export namespace RegistryTypes {\n");

        for (RegistryTypeCatalogEntry registry : registries) {
            generateRegistryType(sb, registry);
        }

        sb.append("    }\n");
        sb.append("}\n");

        // 根 index.d.ts re-export
        sb.append("\nexport * as types from \"@special/types\";\n");

        Files.writeString(specialDir.resolve("index.d.ts"), "export * as types from \"@special/types\";\n");
        Files.writeString(typesDir.resolve("index.d.ts"), sb.toString());
    }

    /**
     * 生成单个注册表的字面量联合类型。
     * 例如: type Block = "minecraft:stone" | "minecraft:dirt" | ...;
     */
    private void generateRegistryType(StringBuilder sb, RegistryTypeCatalogEntry registry) {
        String typeName = registry.typeName();

        if (registry.entries().isEmpty()) {
            sb.append("        type ").append(typeName).append(" = string;\n");
            return;
        }

        // 字面量联合，每行最多 8 个以保持可读性
        sb.append("        type ").append(typeName).append(" = ");
        List<String> entries = registry.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(" | ");
            // 每 8 个换行对齐
            if (i > 0 && i % 8 == 0) sb.append("\n            ");
            sb.append("\"").append(escapeQuote(entries.get(i))).append("\"");
        }
        sb.append(";\n");
    }

    private static String escapeQuote(String s) {
        return s.replace("\"", "\\\"");
    }
}
