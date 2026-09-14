package com.tkisor.nekojs.core.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 派生证据导出：把 JSX 声明与 automatic runtime 模块声明写到
 * {@code docs/architecture-refactor/baseline/2026-09-13-jsx-ui-common-core/}。
 *
 * <p>声明是登记表的派生物（AC6）；本测试只导出，不做版本控制中的第二事实源——
 * 没有测试读取该目录做断言（不存在「声明有而 runtime 无」的静默缺口：
 * 一致性由 {@link UiContractChainTest} 直接对比登记表）。
 */
class UiDeclarationEvidenceTest {

    @Test
    void exportsGeneratedJsxDeclarationsAsEvidence() throws Exception {
        Path dir = Path.of("..", "docs", "architecture-refactor", "baseline",
                "2026-09-13-jsx-ui-common-core");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("jsx-ui.generated.d.ts"),
                UiJsxDeclarationGenerator.generate(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("jsx-runtime.generated.d.ts"),
                UiJsxDeclarationGenerator.generateRuntimeModule(), StandardCharsets.UTF_8);
        assertTrue(Files.size(dir.resolve("jsx-ui.generated.d.ts")) > 500);
    }
}
