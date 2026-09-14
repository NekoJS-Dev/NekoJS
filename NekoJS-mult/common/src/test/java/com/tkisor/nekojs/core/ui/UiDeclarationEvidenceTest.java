package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC6：JSX 声明经<b>既有 TypeDocsRegister 链</b>可达（生产入口
 * {@code NekoCommonBuiltinPlugin.registerNodeTypeDocs}），并可在测试临时目录里落盘审阅。
 *
 * <p>测试不写生产目录（普通测试只读基线；证据产物由仓库基线目录单独承载）。
 */
class UiDeclarationEvidenceTest {

    @TempDir
    Path tempDir;

    @Test
    void jsxDeclarationsAreReachableThroughTheExistingTypeDocsRegisterChain() throws Exception {
        List<ManualDeclarationCatalogEntry> entries = new ArrayList<>();
        TypeDocsRegister registry = new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
                entries.add(entry);
            }
        };

        new com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin().registerNodeTypeDocs(registry);

        ManualDeclarationCatalogEntry elements = entries.stream()
                .filter(entry -> "nekojs.jsx-ui.elements".equals(entry.id()))
                .findFirst().orElseThrow();
        ManualDeclarationCatalogEntry runtime = entries.stream()
                .filter(entry -> "nekojs.jsx-ui.runtime".equals(entry.id()))
                .findFirst().orElseThrow();

        assertTrue(elements.declaration().contains("interface IntrinsicElements"),
                "intrinsic elements 声明必须经 TypeDocsRegister 可达");
        assertTrue(elements.declaration().contains("const Ui: NekoJsx.UiFacade"),
                "facade 全局声明必须经 TypeDocsRegister 可达");
        assertTrue(runtime.declaration().contains("declare module 'nekojs/jsx-runtime'"));

        // 声明内容与生成器直接输出一致（同源，无第二份手写声明）
        assertTrue(elements.declaration().equals(UiJsxDeclarationGenerator.generate()));
        assertTrue(runtime.declaration().equals(UiJsxDeclarationGenerator.generateRuntimeModule()));

        // 落盘到测试临时目录供人工审阅（普通测试不写生产目录）
        Path out = tempDir.resolve("jsx-ui.generated.d.ts");
        Files.writeString(out, elements.declaration(), StandardCharsets.UTF_8);
        assertTrue(Files.size(out) > 500);
    }
}