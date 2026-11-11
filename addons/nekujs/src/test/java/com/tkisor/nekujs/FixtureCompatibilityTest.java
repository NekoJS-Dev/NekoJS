package com.tkisor.nekujs;

import com.tkisor.nekujs.audit.AddonCompatibilityAudit;
import com.tkisor.nekujs.recipe.MekanismCrushingSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Requires user-provided binaries; no addon class is defined by this static inspection. */
@Tag("fixtures")
class FixtureCompatibilityTest {
    @Test
    void inspectsAllSixSuppliedJarsAndReadsTheActualMekanismSchema(@TempDir Path directory) throws Exception {
        Path supplied = Path.of(System.getProperty("nekujs.fixtures.dir"));
        // Keep the original six-binary audit independent of additional user-supplied fixture profiles.
        for (String file : List.of(
                "architectury-13.0.11-neoforge.jar",
                "create-1.21.1-6.0.10.jar",
                "kubejs-create-neoforge-2101.3.1-build.18.jar",
                "Mekanism-1.21.1-10.7.19.85.jar",
                "kubejs-mekanism-neoforge-2101.1.7-build.18.jar",
                "kubejsadditions-neoforge-1.21.1-6.0.0.jar")) {
            Path source = supplied.resolve(file);
            assertTrue(Files.isRegularFile(source), "Missing baseline fixture: " + source);
            Files.copy(source, directory.resolve(file));
        }
        var report = AddonCompatibilityAudit.inspect(directory);
        assertEquals(6, report.jars().size(), "This fixture profile expects the six supplied 1.21.1 JARs");
        assertFalse(report.addonClassesExecuted());
        assertEquals("NOT_VERIFIED", report.runtimeCompatibility());
        int addonCount = 0;
        for (var jar : report.jars()) {
            if (jar.pluginEntrypoints().isEmpty()) continue;
            addonCount++;
            assertFalse(jar.legacyTypesNotProvidedByFixtures().isEmpty(), jar.file());
            if (jar.file().startsWith("kubejs-create-")) {
                assertEquals(21, jar.recipeSchemas().size());
            } else if (jar.file().startsWith("kubejs-mekanism-")) {
                assertEquals(41, jar.recipeSchemas().size());
                var schema = MekanismCrushingSchema.read(directory.resolve(jar.file()));
                assertEquals("mekanism:crushing", schema.key());
            } else if (jar.file().startsWith("kubejsadditions-")) {
                assertTrue(jar.recipeSchemas().isEmpty());
            } else {
                fail("Unexpected addon in fixture profile: " + jar.file());
            }
        }
        assertEquals(3, addonCount);
    }
}
