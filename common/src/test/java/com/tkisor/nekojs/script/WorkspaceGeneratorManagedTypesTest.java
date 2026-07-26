package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
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
}
