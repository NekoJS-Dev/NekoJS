package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.data.BindingExposure.Category;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ApiModuleBaselineTest {
    private static final String KEY = "SERVER:nekojs/api/stable:ID";
    private static ApiModuleBaseline baseline(Category category, boolean bridge, String... facets) {
        return new ApiModuleBaseline("test", Map.of(KEY, new ApiModuleBaseline.Entry(category, bridge, Set.of(facets))));
    }

    @Test void stableDeletionsSignatureAndWritableContractionsFail() {
        var old = baseline(Category.STABLE, false, "call:of(string):Id", "read:count:int", "write:count:int");
        assertFalse(old.compareTo(new ApiModuleBaseline("next", Map.of())).compatible());
        assertFalse(old.compareTo(baseline(Category.STABLE, false, "call:of(int):Id", "read:count:int")).compatible());
        assertFalse(old.compareTo(baseline(Category.VERSION, false, "call:of(string):Id", "read:count:int", "write:count:int")).compatible());
        assertFalse(old.compareTo(new ApiModuleBaseline("next", Map.of("CLIENT:nekojs/api/stable:ID", old.exports().get(KEY)))).compatible());
    }

    @Test void additionsAndVersionChangesAreReportedSeparately() {
        var old = baseline(Category.STABLE, false, "call:of(string):Id");
        var added = old.compareTo(baseline(Category.STABLE, false, "call:of(string):Id", "call:resource(string):Id"));
        assertTrue(added.compatible());
        assertEquals(1, added.additions().size());
        var version = baseline(Category.VERSION, false, "old").compareTo(baseline(Category.VERSION, false, "new"));
        assertTrue(version.compatible());
        assertEquals(1, version.versionChanges().size());
    }

    @Test void bridgesKeepStableShapeAndScopeButReportNativeProfileChanges() {
        var old = baseline(Category.STABLE, true, "call:toNative(Id):<native>", "native:Identifier");
        var report = old.compareTo(baseline(Category.STABLE, true, "call:toNative(Id):<native>", "native:ResourceLocation"));
        assertTrue(report.compatible());
        assertEquals(1, report.versionChanges().size());
        assertFalse(old.compareTo(baseline(Category.STABLE, true, "native:Identifier")).compatible());
        assertFalse(old.compareTo(new ApiModuleBaseline("next", Map.of())).compatible());
    }

    @Test void deterministicJsonRoundTripsWithoutVerifiedContractClaims() {
        var baseline = baseline(Category.STABLE, false, "z", "a");
        assertEquals(baseline, ApiModuleBaseline.fromJson(baseline.toJson()));
        assertEquals(baseline.toJson(), ApiModuleBaseline.fromJson(baseline.toJson()).toJson());
    }
}
