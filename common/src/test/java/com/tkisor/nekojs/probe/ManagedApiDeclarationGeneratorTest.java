package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.*;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManagedApiDeclarationGeneratorTest {

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
    void generatesGlobalFromSurfaceWithoutNativeTypes() {
        ApiSymbol stable = new ApiSymbol(
                ApiSymbolId.parse("global:Stable"),
                List.of(ApiSignature.function(
                        List.of(new ApiParameter("input", ApiTypeRef.primitive("string"), false, false)),
                        ApiTypeRef.primitive("string"))));

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(stable),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, emptyContractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        String output = generator.generate(managedApis, ScriptType.SERVER);

        assertTrue(output.contains("declare global"), output);
        assertTrue(output.contains("const Stable:"), output);
        assertFalse(output.contains("net.minecraft"), output);
    }

    @Test
    void rendersMultipleGlobalsSortedByName() {
        ApiSymbol alpha = new ApiSymbol(
                ApiSymbolId.parse("global:Alpha"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));
        ApiSymbol beta = new ApiSymbol(
                ApiSymbolId.parse("global:Beta"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(beta, alpha),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, emptyContractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        String output = generator.generate(managedApis, ScriptType.SERVER);

        int alphaIdx = output.indexOf("const Alpha:");
        int betaIdx = output.indexOf("const Beta:");
        assertTrue(alphaIdx >= 0, "Alpha not found");
        assertTrue(betaIdx >= 0, "Beta not found");
        assertTrue(alphaIdx < betaIdx, "Alpha should come before Beta");
    }

    @Test
    void skipsNonGlobalSymbols() {
        ApiSymbol event = new ApiSymbol(
                ApiSymbolId.parse("event:ServerEvents.tick"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));
        ApiSymbol global = new ApiSymbol(
                ApiSymbolId.parse("global:MyAPI"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(event, global),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, emptyContractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        String output = generator.generate(managedApis, ScriptType.SERVER);

        assertTrue(output.contains("const MyAPI:"), output);
        assertFalse(output.contains("ServerEvents"), output);
    }

    @Test
    void returnsEmptyForMissingScriptType() {
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of();
        String output = generator.generate(managedApis, ScriptType.SERVER);
        assertEquals("", output);
    }

    @Test
    void rendersOverloadsAsIntersection() {
        ApiSymbol overloaded = new ApiSymbol(
                ApiSymbolId.parse("global:Overloaded"),
                List.of(
                        ApiSignature.function(
                                List.of(new ApiParameter("x", ApiTypeRef.primitive("string"), false, false)),
                                ApiTypeRef.voidType()),
                        ApiSignature.function(
                                List.of(new ApiParameter("x", ApiTypeRef.primitive("int"), false, false)),
                                ApiTypeRef.voidType())));

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(overloaded),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, emptyContractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        String output = generator.generate(managedApis, ScriptType.SERVER);

        assertTrue(output.contains("&"), "Overloads should be rendered as intersection: " + output);
    }

    @Test
    void renderTypeRefHandlesAllKinds() {
        assertEquals("string", ManagedApiDeclarationGenerator.renderTypeRef(ApiTypeRef.primitive("java.lang.String")));
        assertEquals("number", ManagedApiDeclarationGenerator.renderTypeRef(ApiTypeRef.primitive("int")));
        assertEquals("boolean", ManagedApiDeclarationGenerator.renderTypeRef(ApiTypeRef.primitive("boolean")));
        assertEquals("$com.example.Foo", ManagedApiDeclarationGenerator.renderTypeRef(
                ApiTypeRef.symbol(ApiSymbolId.parse("type:com.example.Foo"))));
        assertEquals("string[]", ManagedApiDeclarationGenerator.renderTypeRef(
                ApiTypeRef.array(ApiTypeRef.primitive("string"))));
        assertEquals("void", ManagedApiDeclarationGenerator.renderTypeRef(ApiTypeRef.voidType()));
    }

    @Test
    void renderTypeRefHandlesUnion() {
        ApiTypeRef union = ApiTypeRef.union(List.of(
                ApiTypeRef.primitive("string"),
                ApiTypeRef.primitive("int")));
        String rendered = ManagedApiDeclarationGenerator.renderTypeRef(union);
        // Union members are sorted by compatibilityKey
        assertTrue(rendered.contains("|"), "Union should contain '|': " + rendered);
    }

    @Test
    void renderTypeRefHandlesCallback() {
        ApiSignature sig = ApiSignature.function(
                List.of(new ApiParameter("x", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.voidType());
        ApiTypeRef callback = ApiTypeRef.callback(sig);
        String rendered = ManagedApiDeclarationGenerator.renderTypeRef(callback);
        assertTrue(rendered.contains("=>"), "Callback should contain '=>': " + rendered);
    }

    @Test
    void renderSignatureHandlesConstructor() {
        ApiSignature ctor = ApiSignature.constructor(
                List.of(new ApiParameter("x", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.symbol(ApiSymbolId.parse("type:com.example.Foo")));
        String rendered = ManagedApiDeclarationGenerator.renderSignature(ctor);
        assertTrue(rendered.contains("new ("), "Constructor should contain 'new (': " + rendered);
    }
}
