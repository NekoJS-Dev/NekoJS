package com.tkisor.nekojs.core.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 46: the conversion reports shipped with the web conversion cookbook follow the
 * machine-readable conversion report contract (authoring contract section 10): input
 * checklist keys and completeness values, notes for gapped input, checklistKey tracing
 * of every missing or partial key by an uncertainty or needs-human item, item severities
 * and locations, and the verification record of every representative fixture.
 */
class WebConversionReportContractTest {

    private static final Set<String> CHECKLIST_KEYS =
            Set.of("structure", "styles", "resources", "fonts", "interactions", "profiles");
    private static final Set<String> COMPLETENESS =
            Set.of("complete", "partial", "missing", "not-applicable");
    private static final Set<String> EQUIVALENCE_KEYS =
            Set.of("structural", "interaction", "reactive", "visual");
    private static final Set<String> EQUIVALENCE_VALUES =
            Set.of("achieved", "partial", "downgraded");
    private static final Set<String> SEVERITIES =
            Set.of("unsupported", "downgraded", "uncertainty", "needs-human");

    @Test
    void fixtureReportsFollowTheConversionReportContract() throws IOException {
        List<Path> reports = reportFiles();
        assertTrue(reports.size() >= 2, "the cookbook ships a representative report per fixture");
        Set<String> severitiesSeen = new HashSet<>();
        for (Path report : reports) {
            JsonObject root = parsed(report);
            assertEquals(1, root.get("reportVersion").getAsInt(),
                    "report format version is frozen at 1: " + report);

            JsonObject conversion = requiredObject(root, "conversion", report);
            String source = requiredString(conversion, "source", report);
            requiredString(conversion, "output", report);
            requiredString(conversion, "tool", report);
            assertTrue(source.endsWith(".html"), "conversion.source names the web input: " + report);

            JsonObject checklist = requiredObject(root, "inputChecklist", report);
            assertTrue(checklist.keySet().containsAll(CHECKLIST_KEYS),
                    "input checklist covers the contract keys: " + report);
            boolean requiresNotes = false;
            Set<String> gapKeys = new HashSet<>();
            for (String key : CHECKLIST_KEYS) {
                String value = requiredString(checklist, key, report);
                assertTrue(COMPLETENESS.contains(value),
                        key + " carries a completeness value in " + report);
                if (value.equals("missing") || value.equals("partial")) {
                    requiresNotes = true;
                    gapKeys.add(key);
                } else if (value.equals("not-applicable")) {
                    requiresNotes = true;
                }
            }
            JsonArray notes = requiredArray(checklist, "notes", report);
            if (requiresNotes) {
                assertFalse(notes.isEmpty(),
                        "missing, partial, or not-applicable input requires explanatory notes: " + report);
            }

            JsonObject equivalence = requiredObject(root, "equivalence", report);
            assertEquals(EQUIVALENCE_KEYS, equivalence.keySet(),
                    "equivalence covers the four priorities: " + report);
            for (String key : EQUIVALENCE_KEYS) {
                String value = requiredString(equivalence, key, report);
                assertTrue(EQUIVALENCE_VALUES.contains(value),
                        key + " carries an equivalence value in " + report);
            }

            JsonObject verification = requiredObject(root, "verification", report);
            requiredString(verification, "fixture", report);
            requiredString(verification, "executor", report);
            requiredString(verification, "method", report);
            JsonArray profiles = requiredArray(verification, "profilesExercised", report);
            assertFalse(profiles.isEmpty(),
                    "the verification record names the exercised profiles: " + report);
            for (JsonElement profile : profiles) {
                int value = profile.getAsInt();
                assertTrue(value >= 1 && value <= 6,
                        "profiles come from the six viewport profiles: " + report);
            }
            assertFalse(requiredArray(verification, "assertions", report).isEmpty(),
                    "the verification record keeps its assertions: " + report);
            assertEquals("pass", requiredString(verification, "result", report),
                    "published fixtures record a passing verification: " + report);

            JsonArray items = requiredArray(root, "items", report);
            assertFalse(items.isEmpty(), "every fixture records its mapping decisions: " + report);
            Set<String> ids = new HashSet<>();
            Set<String> tracedGapKeys = new HashSet<>();
            for (JsonElement entry : items) {
                JsonObject item = entry.getAsJsonObject();
                String id = requiredString(item, "id", report);
                assertTrue(ids.add(id), "item ids are unique in " + report);
                String severity = requiredString(item, "severity", report);
                assertTrue(SEVERITIES.contains(severity),
                        "severity comes from the contract enum: " + report);
                severitiesSeen.add(severity);
                JsonElement checklistKey = item.get("checklistKey");
                if (checklistKey != null) {
                    assertTrue(checklistKey.isJsonPrimitive(),
                            "checklistKey is a string key name in " + report);
                    String key = checklistKey.getAsString();
                    assertTrue(CHECKLIST_KEYS.contains(key),
                            "checklistKey names an inputChecklist key: " + report);
                    if (severity.equals("uncertainty") || severity.equals("needs-human")) {
                        tracedGapKeys.add(key);
                    }
                }
                requiredString(item, "web", report);
                requiredString(item, "reason", report);
                requiredString(item, "replacement", report);
                String locationSource = requiredString(requiredObject(item, "location", report), "source", report);
                assertTrue(locationSource.startsWith(source),
                        "item locations point into the declared web input: " + report);
            }
            assertTrue(tracedGapKeys.containsAll(gapKeys),
                    "every missing or partial checklist key is traced by an uncertainty or needs-human item: "
                            + report);
        }
        assertEquals(SEVERITIES, severitiesSeen,
                "the shipped fixture pair demonstrates every report severity family");
    }

    private static List<Path> reportFiles() throws IOException {
        Path directory = Path.of("..", "docs", "ui-conversion", "fixtures");
        if (!Files.isDirectory(directory)) directory = Path.of("docs", "ui-conversion", "fixtures");
        assertTrue(Files.isDirectory(directory), "missing docs/ui-conversion/fixtures directory");
        List<Path> reports = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".conversion-report.json"))
                    .sorted()
                    .forEach(reports::add);
        }
        return reports;
    }

    private static JsonObject parsed(Path report) throws IOException {
        return JsonParser.parseString(Files.readString(report, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject requiredObject(JsonObject parent, String member, Path report) {
        JsonElement value = parent.get(member);
        assertTrue(value != null && value.isJsonObject(), member + " is a required object in " + report);
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject parent, String member, Path report) {
        JsonElement value = parent.get(member);
        assertTrue(value != null && value.isJsonArray(), member + " is a required array in " + report);
        return value.getAsJsonArray();
    }

    private static String requiredString(JsonObject parent, String member, Path report) {
        JsonElement value = parent.get(member);
        assertTrue(value != null && value.isJsonPrimitive(), member + " is a required string in " + report);
        return value.getAsString();
    }
}
