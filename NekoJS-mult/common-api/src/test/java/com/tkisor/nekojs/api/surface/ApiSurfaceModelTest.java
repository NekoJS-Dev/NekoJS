package com.tkisor.nekojs.api.surface;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiSurfaceModelTest {

    @Test
    void symbolIdRequiresKindAndQualifiedName() {
        assertEquals("global:Item", ApiSymbolId.parse("global:Item").value());
        assertEquals("module-member:@nekojs/feature/tags.add",
                ApiSymbolId.parse("module-member:@nekojs/feature/tags.add").value());
        assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse("Item"));
        assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse("global:"));
    }

    @Test
    void symbolIdRejectsBlankKind() {
        assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse(":Item"));
    }

    @Test
    void symbolIdRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse("  "));
    }

    @Test
    void symbolIdPreservesCase() {
        ApiSymbolId id = ApiSymbolId.parse("global:MyClass.myMethod");
        assertEquals("global:MyClass.myMethod", id.value());
        assertEquals("global", id.kind());
        assertEquals("MyClass.myMethod", id.qualifiedName());
    }

    @Test
    void memberSymbolIdDoesNotIncludeParameters() {
        ApiSymbolId id = ApiSymbolId.parse("member:nekojs.api.PlayerRef.give");
        assertEquals("member", id.kind());
        assertEquals("nekojs.api.PlayerRef.give", id.qualifiedName());
    }

    @Test
    void signatureKeyPreservesOverloads() {
        ApiSignature byId = ApiSignature.function(List.of(
                new ApiParameter("id", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiSignature byIndex = ApiSignature.function(List.of(
                new ApiParameter("index", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
        assertNotEquals(byId.compatibilityKey(), byIndex.compatibilityKey());
    }

    @Test
    void signatureCallKeyExcludesReturnType() {
        ApiSignature sig1 = ApiSignature.function(List.of(
                new ApiParameter("x", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiSignature sig2 = ApiSignature.function(List.of(
                new ApiParameter("x", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("number"));
        assertEquals(sig1.callKey(), sig2.callKey());
        assertNotEquals(sig1.compatibilityKey(), sig2.compatibilityKey());
    }

    @Test
    void symbolRejectsDuplicateCallKey() {
        ApiSignature sig1 = ApiSignature.function(List.of(
                new ApiParameter("x", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiSignature sig2 = ApiSignature.function(List.of(
                new ApiParameter("y", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("number"));
        // Same call key (param type is number, different name doesn't matter) but different return type
        // This should be rejected as illegal JS overload
        assertThrows(IllegalArgumentException.class, () ->
                new ApiSymbol(ApiSymbolId.parse("global:test"), List.of(sig1, sig2)));
    }

    @Test
    void typeRefPrimitiveFactory() {
        ApiTypeRef ref = ApiTypeRef.primitive("string");
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, ref.kind());
        assertEquals("string", ref.name());
        assertTrue(ref.arguments().isEmpty());
        assertNull(ref.callbackSignature());
    }

    @Test
    void typeRefSymbolFactory() {
        ApiSymbolId id = ApiSymbolId.parse("global:Item");
        ApiTypeRef ref = ApiTypeRef.symbol(id);
        assertEquals(ApiTypeRef.Kind.SYMBOL, ref.kind());
        assertEquals("global:Item", ref.name());
    }

    @Test
    void typeRefArrayFactory() {
        ApiTypeRef element = ApiTypeRef.primitive("string");
        ApiTypeRef array = ApiTypeRef.array(element);
        assertEquals(ApiTypeRef.Kind.ARRAY, array.kind());
        assertEquals(1, array.arguments().size());
        assertEquals(element, array.arguments().get(0));
    }

    @Test
    void typeRefUnionFactory() {
        ApiTypeRef a = ApiTypeRef.primitive("string");
        ApiTypeRef b = ApiTypeRef.primitive("number");
        ApiTypeRef union = ApiTypeRef.union(List.of(a, b));
        assertEquals(ApiTypeRef.Kind.UNION, union.kind());
        assertEquals(2, union.arguments().size());
    }

    @Test
    void typeRefUnionRequiresAtLeastTwo() {
        ApiTypeRef a = ApiTypeRef.primitive("string");
        assertThrows(IllegalArgumentException.class, () -> ApiTypeRef.union(List.of(a)));
    }

    @Test
    void typeRefUnionDeduplicatesMembers() {
        ApiTypeRef a = ApiTypeRef.primitive("string");
        ApiTypeRef b = ApiTypeRef.primitive("string");
        ApiTypeRef c = ApiTypeRef.primitive("number");
        ApiTypeRef union = ApiTypeRef.union(List.of(a, b, c));
        assertEquals(2, union.arguments().size());
    }

    @Test
    void typeRefCallbackCarriesSignature() {
        ApiSignature sig = ApiSignature.function(List.of(
                new ApiParameter("x", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiTypeRef cb = ApiTypeRef.callback(sig);
        assertEquals(ApiTypeRef.Kind.CALLBACK, cb.kind());
        assertNotNull(cb.callbackSignature());
        assertEquals(sig, cb.callbackSignature());
    }

    @Test
    void apiVersionSemVer() {
        ApiVersion v = ApiVersion.parse("1.2.3");
        assertEquals(1, v.major());
        assertEquals(2, v.minor());
        assertEquals(3, v.patch());
        assertNull(v.prerelease());
        assertNull(v.build());
    }

    @Test
    void apiVersionSemVerWithPrereleaseAndBuild() {
        ApiVersion v = ApiVersion.parse("1.0.0-alpha.1+build.123");
        assertEquals(1, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
        assertEquals("alpha.1", v.prerelease());
        assertEquals("build.123", v.build());
    }

    @Test
    void apiVersionComparison() {
        ApiVersion v1 = ApiVersion.parse("1.0.0");
        ApiVersion v2 = ApiVersion.parse("1.0.1");
        assertTrue(v1.compareTo(v2) < 0);
    }

    @Test
    void apiVersionRangeExact() {
        ApiVersionRange range = ApiVersionRange.exact(ApiVersion.parse("1.0.0"));
        assertTrue(range.matches(ApiVersion.parse("1.0.0")));
        assertFalse(range.matches(ApiVersion.parse("1.0.1")));
    }

    @Test
    void apiVersionRangeMinMax() {
        ApiVersionRange range = ApiVersionRange.range(
                ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0"));
        assertTrue(range.matches(ApiVersion.parse("1.5.0")));
        assertFalse(range.matches(ApiVersion.parse("2.0.0")));
        assertFalse(range.matches(ApiVersion.parse("0.9.0")));
    }

    @Test
    void apiTierHasExpectedValues() {
        assertEquals(9, ApiTier.values().length);
        assertNotNull(ApiTier.GLOBAL);
        assertNotNull(ApiTier.MEMBER);
        assertNotNull(ApiTier.MODULE_MEMBER);
        assertNotNull(ApiTier.FEATURE);
        assertNotNull(ApiTier.PLATFORM);
        assertNotNull(ApiTier.ADDON);
        assertNotNull(ApiTier.VERSION);
        assertNotNull(ApiTier.UNSAFE_NATIVE);
    }

    @Test
    void apiSymbolRejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                new ApiSymbol(null, List.of()));
    }

    @Test
    void apiSymbolRejectsEmptySignatures() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiSymbol(ApiSymbolId.parse("global:test"), List.of()));
    }

}
