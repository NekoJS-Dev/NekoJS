package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceGeneratorManagedTypesTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void buildConfigForEnvIncludesManagedTypes() {
        Path scriptDir = Path.of("/game/server_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.SERVER, scriptDir, probeDir);

        String managedPattern = probeDir.relativize(scriptDir).toString().replace('\\', '/')
                + "/@nekojs/managed/server/**/*.d.ts";
        // The relative path from scriptDir to probeDir is "../.neko_probe"
        // So the include should be "../.neko_probe/@nekojs/managed/server/**/*.d.ts"
        String expected = "../.neko_probe/@nekojs/managed/server/**/*.d.ts";

        assertTrue(model.include.contains(expected),
                "include should contain managed types pattern. Actual includes: " + model.include);
    }

    @Test
    void buildConfigForEnvIncludesManagedTypesForClient() {
        Path scriptDir = Path.of("/game/client_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.CLIENT, scriptDir, probeDir);

        String expected = "../.neko_probe/@nekojs/managed/client/**/*.d.ts";

        assertTrue(model.include.contains(expected),
                "include should contain client managed types pattern. Actual includes: " + model.include);
    }

    @Test
    void buildConfigForEnvIncludesManagedTypesForStartup() {
        Path scriptDir = Path.of("/game/startup_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.STARTUP, scriptDir, probeDir);

        String expected = "../.neko_probe/@nekojs/managed/startup/**/*.d.ts";

        assertTrue(model.include.contains(expected),
                "include should contain startup managed types pattern. Actual includes: " + model.include);
    }

    @Test
    void buildConfigForEnvIncludesManagedTypesForTest() {
        Path scriptDir = Path.of("/game/test_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.TEST, scriptDir, probeDir);

        String expected = "../.neko_probe/@nekojs/managed/test/**/*.d.ts";

        assertTrue(model.include.contains(expected),
                "include should contain test managed types pattern. Actual includes: " + model.include);
    }

    @Test
    void buildConfigForEnvPreservesExistingIncludes() {
        Path scriptDir = Path.of("/game/server_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.SERVER, scriptDir, probeDir);

        assertTrue(model.include.contains("../.neko_probe/@package/**/*.d.ts"),
                "Should preserve @package include");
        assertTrue(model.include.contains("../.neko_probe/@manual/**/*.d.ts"),
                "Should preserve @manual include");
        assertTrue(model.include.contains("../.neko_probe/@side-only/server/**/*.d.ts"),
                "Should preserve @side-only include");
    }

    @Test
    void buildConfigForEnvPreservesTypeRoots() {
        Path scriptDir = Path.of("/game/server_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(ScriptType.SERVER, scriptDir, probeDir);

        assertTrue(model.compilerOptions.typeRoots.contains("../.neko_probe/@package"),
                "Should preserve @package typeRoot");
    }

    @Test
    void buildConfigForEnvFollowsEngineJsxRuntimeMode() {
        Path scriptDir = Path.of("/game/server_scripts");
        Path probeDir = Path.of("/game/.neko_probe");

        // 默认（jsxAutomaticRuntime=false）：经典全局工厂写法
        JSConfigModel classic = WorkspaceGenerator.buildConfigForEnv(ScriptType.SERVER, scriptDir, probeDir);
        assertEquals("react", classic.compilerOptions.jsx);
        assertEquals("__nekoJsxFactory", classic.compilerOptions.jsxFactory);
        assertEquals("__nekoJsxFragment", classic.compilerOptions.jsxFragmentFactory);
        assertNull(classic.compilerOptions.jsxImportSource);

        // 引擎开启自动运行时：jsconfig 同步切到 react-jsx + nekojs/jsx-runtime
        SandboxConfig prev = ClassFilter.INSTANCE.config();
        try {
            ClassFilter.INSTANCE.updateConfig(new SandboxConfig(
                    prev.allowThreads(), prev.allowReflection(), prev.allowAsm(),
                    prev.allowFsWriteOutsideNekojs(), prev.enableEsmAuthoring(),
                    prev.conciseScriptErrorLogs(), true, prev.scriptMemberValidation(),
                    prev.scriptEvaluationTimeoutSeconds(), prev.scriptStatementLimit()));
            JSConfigModel automatic = WorkspaceGenerator.buildConfigForEnv(ScriptType.SERVER, scriptDir, probeDir);
            assertEquals("react-jsx", automatic.compilerOptions.jsx);
            assertEquals("nekojs", automatic.compilerOptions.jsxImportSource);
            assertNull(automatic.compilerOptions.jsxFactory);
            assertNull(automatic.compilerOptions.jsxFragmentFactory);
        } finally {
            ClassFilter.INSTANCE.updateConfig(prev);
        }
    }
}
