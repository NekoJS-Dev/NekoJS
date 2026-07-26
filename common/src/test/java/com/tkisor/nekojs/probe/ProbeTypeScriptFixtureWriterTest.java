package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.*;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates managed API declarations from a fixed test fixture and asserts that
 * the output is byte-identical to the checked-in golden file
 * {@code common/src/test/probe-ts/generated/index.d.ts}.
 *
 * <p>The golden file is consumed by the TypeScript probe gate ({@code npm run test:probe-types}).
 * This test writes its actual output to {@code common/build/probe-ts-actual/index.d.ts}
 * for inspection but never mutates the source tree.
 */
class ProbeTypeScriptFixtureWriterTest {

    private static final Path GOLDEN_PATH = Path.of(
            "src", "test", "probe-ts", "generated", "index.d.ts");
    private static final Path ACTUAL_PATH = Path.of(
            "build", "probe-ts-actual", "index.d.ts");

    private final ManagedApiDeclarationGenerator generator = new ManagedApiDeclarationGenerator();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static EnvironmentKey serverEnv() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static ApiContractHashes emptyContractHashes() {
        return new ApiContractHashes("0.0.0", "sha256:" + "0".repeat(64), Map.of());
    }

    @Test
    void generatedDeclarationMatchesGolden() throws IOException {
        // Build fixture surface with overloaded globals and callback payload
        ApiParameter stringInput = new ApiParameter("input", ApiTypeRef.primitive("string"), false, false);
        ApiParameter intInput = new ApiParameter("input", ApiTypeRef.primitive("int"), false, false);

        ApiSignature overloadedString = ApiSignature.function(List.of(stringInput), ApiTypeRef.primitive("string"));
        ApiSignature overloadedInt = ApiSignature.function(List.of(intInput), ApiTypeRef.voidType());

        ApiSymbol overloaded = new ApiSymbol(
                ApiSymbolId.parse("global:Overloaded"),
                List.of(overloadedString, overloadedInt));

        ApiSignature callbackSig = ApiSignature.function(
                List.of(new ApiParameter("name", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.voidType());
        ApiTypeRef callbackType = ApiTypeRef.callback(callbackSig);
        ApiParameter handlerParam = new ApiParameter("handler", callbackType, false, false);
        ApiSignature withCallbackSig = ApiSignature.function(List.of(handlerParam), ApiTypeRef.voidType());

        ApiSymbol withCallback = new ApiSymbol(
                ApiSymbolId.parse("global:WithCallback"),
                List.of(withCallbackSig));

        ApiSymbol stable = new ApiSymbol(
                ApiSymbolId.parse("global:Stable"),
                List.of(ApiSignature.function(
                        List.of(new ApiParameter("input", ApiTypeRef.primitive("string"), false, false)),
                        ApiTypeRef.primitive("string"))));

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(overloaded, withCallback, stable),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, emptyContractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        String actual = generator.generate(managedApis, ScriptType.SERVER);

        assertFalse(actual.isEmpty(), "Generated declaration should not be empty");
        assertFalse(actual.contains("net.minecraft"), "Should not contain net.minecraft: " + actual);
        assertFalse(actual.contains("net.neoforged"), "Should not contain net.neoforged: " + actual);
        assertFalse(actual.contains("net.minecraftforge"), "Should not contain net.minecraftforge: " + actual);

        // Write actual output for inspection
        Files.createDirectories(ACTUAL_PATH.getParent());
        Files.writeString(ACTUAL_PATH, actual, StandardCharsets.UTF_8);

        // Compare with golden
        assertTrue(Files.exists(GOLDEN_PATH),
                "Golden file missing: " + GOLDEN_PATH.toAbsolutePath()
                        + "\nRun the test once, review the actual output at " + ACTUAL_PATH
                        + ", then copy it to " + GOLDEN_PATH);

        String golden = Files.readString(GOLDEN_PATH, StandardCharsets.UTF_8);
        assertEquals(golden, actual,
                "Generated declaration does not match golden. "
                        + "If intentional, update golden by copying:\n"
                        + "  " + ACTUAL_PATH + "\n  -> " + GOLDEN_PATH);
    }
}
