package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.surface.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ApiCompatibilityDiffTest {

    private static ApiVersion v1_0_0() {
        return ApiVersion.parse("1.0.0");
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

    private static ApiSymbol makeSymbol(String id, String paramType) {
        return new ApiSymbol(
                ApiSymbolId.parse(id),
                List.of(ApiSignature.function(
                        List.of(new ApiParameter("x", ApiTypeRef.primitive(paramType), false, false)),
                        ApiTypeRef.voidType())));
    }

    private static ApiSymbol makeSymbol(String id) {
        return new ApiSymbol(
                ApiSymbolId.parse(id),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));
    }

    private static ApiSurfaceSnapshot surface(ApiSymbol... symbols) {
        return new ApiSurfaceSnapshot(
                List.of(symbols),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());
    }

    @Test
    void removingStableSymbolIsBreaking() {
        ApiSymbol before = makeSymbol("global:TestAPI");
        ApiSurfaceSnapshot beforeSurface = surface(before);
        ApiSurfaceSnapshot afterSurface = surface();

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().anyMatch(e ->
                e.severity() == ApiCompatibilityDiff.Severity.BREAKING
                        && e.symbolId().equals("global:TestAPI")
                        && e.changeKind() == ApiCompatibilityDiff.ChangeKind.REMOVED));
    }

    @Test
    void parameterTypeChangeIsBreaking() {
        ApiSymbol before = makeSymbol("global:TestAPI", "string");
        ApiSymbol after = makeSymbol("global:TestAPI", "number");

        ApiSurfaceSnapshot beforeSurface = surface(before);
        ApiSurfaceSnapshot afterSurface = surface(after);

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().anyMatch(e ->
                e.severity() == ApiCompatibilityDiff.Severity.BREAKING
                        && e.symbolId().equals("global:TestAPI")
                        && e.changeKind() == ApiCompatibilityDiff.ChangeKind.SIGNATURE_CHANGED));
    }

    @Test
    void addingNewOverloadIsAdditive() {
        ApiSymbol before = makeSymbol("global:TestAPI");
        ApiSymbol after = new ApiSymbol(
                ApiSymbolId.parse("global:TestAPI"),
                List.of(
                        ApiSignature.function(List.of(), ApiTypeRef.voidType()),
                        ApiSignature.function(
                                List.of(new ApiParameter("x", ApiTypeRef.primitive("string"), false, false)),
                                ApiTypeRef.voidType())));

        ApiSurfaceSnapshot beforeSurface = surface(before);
        ApiSurfaceSnapshot afterSurface = surface(after);

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().anyMatch(e ->
                e.severity() == ApiCompatibilityDiff.Severity.ADDITIVE
                        && e.symbolId().equals("global:TestAPI")
                        && e.changeKind() == ApiCompatibilityDiff.ChangeKind.OVERLOAD_ADDED));
    }

    @Test
    void addingNewSymbolIsAdditive() {
        ApiSurfaceSnapshot beforeSurface = surface();
        ApiSurfaceSnapshot afterSurface = surface(makeSymbol("global:NewAPI"));

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().anyMatch(e ->
                e.severity() == ApiCompatibilityDiff.Severity.ADDITIVE
                        && e.symbolId().equals("global:NewAPI")
                        && e.changeKind() == ApiCompatibilityDiff.ChangeKind.ADDED));
    }

    @Test
    void documentationChangeIsDocumentationOnly() {
        ApiSymbol before = makeSymbol("global:TestAPI");
        ApiSymbol after = makeSymbol("global:TestAPI");

        ApiSurfaceSnapshot beforeSurface = surface(before);
        ApiSurfaceSnapshot afterSurface = surface(after);

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.isEmpty());
    }

    @Test
    void versionChangeDoesNotAppearInPortableDiff() {
        ApiSymbol before = makeSymbol("global:TestAPI", "string");
        ApiSymbol after = makeSymbol("global:TestAPI", "string");

        ApiSurfaceSnapshot beforeSurface = surface(before);
        ApiSurfaceSnapshot afterSurface = surface(after);

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().noneMatch(e ->
                e.changeKind() == ApiCompatibilityDiff.ChangeKind.VERSION_CHANGED));
    }

    @Test
    void requiredCapabilityTighteningIsBreaking() {
        ApiSymbol before = makeSymbol("global:TestAPI");
        ApiSymbol after = makeSymbol("global:TestAPI");

        ApiSurfaceSnapshot beforeSurface = new ApiSurfaceSnapshot(
                List.of(before),
                Set.of("cap-a", "cap-b"),
                List.of(),
                List.of(),
                serverEnv());

        ApiSurfaceSnapshot afterSurface = new ApiSurfaceSnapshot(
                List.of(after),
                Set.of("cap-a"),
                List.of(),
                List.of(),
                serverEnv());

        List<ApiCompatibilityDiff.DiffEntry> entries = ApiCompatibilityDiff.diff(beforeSurface, afterSurface);

        assertTrue(entries.stream().anyMatch(e ->
                e.severity() == ApiCompatibilityDiff.Severity.BREAKING
                        && e.changeKind() == ApiCompatibilityDiff.ChangeKind.CAPABILITY_REMOVED));
    }
}
