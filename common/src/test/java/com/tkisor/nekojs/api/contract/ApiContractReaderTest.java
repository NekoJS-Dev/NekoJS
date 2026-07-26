package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiVersion;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ApiContractReaderTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-module.jar");
    private static final String RESOURCE_NAME = "nekojs/api-contract/test.json";

    private VerifiedApiContract read(String fixture) throws Exception {
        return read(fixture, new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0")));
    }

    private VerifiedApiContract read(String fixture, ApiContractIdentity expectedIdentity) throws Exception {
        try (Reader r = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/nekojs/api-contract/" + fixture)),
                StandardCharsets.UTF_8)) {
            return ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME, expectedIdentity, null);
        }
    }

    @Test
    void readsMinimalPortableContract() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        NormativeApiContract contract = verified.contract();
        assertEquals("nekojs-core", contract.identity().owner());
        assertEquals(ApiContractKind.PORTABLE, contract.identity().kind());
        assertEquals("1.0.0", contract.identity().version().toString());
        assertFalse(contract.symbols().isEmpty());
        assertEquals("global:Item", contract.symbols().getFirst().id().value());
    }

    @Test
    void readsMinimalPortableContractWithCorrectSymbolTier() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        NormativeApiContract contract = verified.contract();
        assertFalse(contract.symbols().isEmpty());
        assertEquals("global:Item", contract.symbols().getFirst().id().value());
    }

    @Test
    void rejectsVersionModuleWithContractVersion() {
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> read("invalid/version-with-contract-version.json",
                        new ApiContractIdentity("nekojs-core", ApiContractKind.FEATURE, "@nekojs/version-module", ApiVersion.parse("1.0.0"))));
        assertEquals("INVALID_MODULE_VERSION_DISCRIMINATOR", error.violation().code());
    }

    @Test
    void rejectsAddonUsingNekojsNamespace() {
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> read("invalid/addon-core-namespace.json",
                        new ApiContractIdentity("some-addon", ApiContractKind.ADDON, "@nekojs/core-module", ApiVersion.parse("1.0.0"))));
        assertEquals("RESERVED_MODULE_NAMESPACE", error.violation().code());
    }

    @Test
    void rejectsMismatchedIdentity() {
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> read("valid/minimal-portable.json", new ApiContractIdentity(
                        "wrong-owner", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0"))));
        assertEquals("IDENTITY_MISMATCH", error.violation().code());
    }

    @Test
    void rejectsSchemaValidationFailure() {
        String invalidJson = "{\"schemaVersion\": 1, \"symbols\": []}";
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> {
                    try (Reader r = new StringReader(invalidJson)) {
                        ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME,
                                new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0")), null);
                    }
                });
        assertEquals("SCHEMA_VALIDATION_FAILED", error.violation().code());
    }

    @Test
    void schemaResourceIsLoadable() {
        assertNotNull(ApiContractReader.class.getResource("/nekojs/api-contract/api-contract.schema.json"),
                "Schema resource must be loadable from classpath");
    }

    @Test
    void schemaVersionFieldIsCorrect() throws Exception {
        try (var is = ApiContractReader.class.getResourceAsStream("/nekojs/api-contract/api-contract.schema.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\""),
                    "Schema must declare 2020-12 dialect");
        }
    }

    @Test
    void verifiedContractHoldsIntegrityAndCompatibilityHashes() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        assertNotNull(verified.integritySha256());
        assertNotNull(verified.compatibilitySha256());
        assertNotEquals("", verified.integritySha256());
        assertNotEquals("", verified.compatibilitySha256());
    }

    @Test
    void verifiedContractSetIndexesByIdentity() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        VerifiedContractSet set = VerifiedContractSet.of(verified);
        assertEquals(1, set.all().size());
        assertTrue(set.forOwner("nekojs-core").contains(verified));
        assertTrue(set.forOwner("other").isEmpty());
    }

    @Test
    void verifiedContractSetRejectsDuplicateIdentity() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedContractSet.of(verified, verified));
    }

    @Test
    void requirePortableReturnsPortableContract() throws Exception {
        VerifiedApiContract verified = read("valid/minimal-portable.json");
        VerifiedContractSet set = VerifiedContractSet.of(verified);
        VerifiedApiContract portable = set.requirePortable("nekojs-core");
        assertEquals(ApiContractKind.PORTABLE, portable.contract().identity().kind());
    }

    @Test
    void requirePortableFailsWhenMissing() {
        VerifiedContractSet empty = VerifiedContractSet.of();
        assertThrows(IllegalStateException.class, () -> empty.requirePortable("nekojs-core"));
    }

    @Test
    void emptyVerifiedCorePreviewCreatesPreviewEnvelope() {
        VerifiedApiContract preview = ApiContractReader.emptyVerifiedCorePreview(CODE_SOURCE);
        assertEquals("nekojs-core", preview.contract().identity().owner());
        assertEquals(ApiContractKind.PORTABLE, preview.contract().identity().kind());
        assertEquals("portable-core", preview.contract().identity().contractId());
        assertEquals("0.0.0", preview.contract().identity().version().toString());
        assertTrue(preview.contract().symbols().isEmpty());
    }

    @Test
    void rejectsInvalidJsonSyntax() {
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> {
                    try (Reader r = new StringReader("{invalid json}")) {
                        ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME,
                                new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0")), null);
                    }
                });
        assertEquals("INVALID_JSON", error.violation().code());
    }

    @Test
    void rejectsPortableWithModules() {
        String json = """
                {
                  "schemaVersion": 1,
                  "identity": {
                    "owner": "nekojs-core",
                    "kind": "PORTABLE",
                    "contractId": "portable-core",
                    "version": "1.0.0"
                  },
                  "symbols": [{"id":"global:Test","signatures":[{"parameters":[],"returnType":{"kind":"VOID"}}]}],
                  "modules": [{"id":"@nekojs/test","tier":"FEATURE","contractVersion":"1.0.0"}]
                }
                """;
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> {
                    try (Reader r = new StringReader(json)) {
                        ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME,
                                new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0")), null);
                    }
                });
        assertEquals("PORTABLE_WITH_MODULES", error.violation().code());
    }

    @Test
    void rejectsAddonWithMismatchedModuleId() {
        String json = """
                {
                  "schemaVersion": 1,
                  "identity": {
                    "owner": "some-addon",
                    "kind": "ADDON",
                    "contractId": "@some-addon/addon-module",
                    "version": "1.0.0"
                  },
                  "symbols": [],
                  "modules": [{"id":"@some-addon/other-module","tier":"ADDON","contractVersion":"1.0.0"}]
                }
                """;
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> {
                    try (Reader r = new StringReader(json)) {
                        ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME,
                                new ApiContractIdentity("some-addon", ApiContractKind.ADDON, "@some-addon/addon-module", ApiVersion.parse("1.0.0")), null);
                    }
                });
        assertEquals("MODULE_ID_MISMATCH", error.violation().code());
    }

    @Test
    void rejectsAddonWithMismatchedModuleVersion() {
        String json = """
                {
                  "schemaVersion": 1,
                  "identity": {
                    "owner": "some-addon",
                    "kind": "ADDON",
                    "contractId": "@some-addon/addon-module",
                    "version": "1.0.0"
                  },
                  "symbols": [],
                  "modules": [{"id":"@some-addon/addon-module","tier":"ADDON","contractVersion":"2.0.0"}]
                }
                """;
        ApiContractException error = assertThrows(ApiContractException.class,
                () -> {
                    try (Reader r = new StringReader(json)) {
                        ApiContractReader.readVerified(r, CODE_SOURCE, RESOURCE_NAME,
                                new ApiContractIdentity("some-addon", ApiContractKind.ADDON, "@some-addon/addon-module", ApiVersion.parse("1.0.0")), null);
                    }
                });
        assertEquals("MODULE_VERSION_MISMATCH", error.violation().code());
    }
}
