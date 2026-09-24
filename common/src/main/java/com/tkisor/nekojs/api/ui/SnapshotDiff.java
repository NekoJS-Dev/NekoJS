package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Optional;

/**
 * Structured difference report between a reference inspector snapshot and an actual one
 * (ticket 45). Entries are ranked by deviation, largest first, so the first entry names
 * the node/property that drifted the most; structural breaks (a node missing or added)
 * outrank any measurement drift. Provenance fields retain the inputs (both snapshot
 * sources), the environments, both profiles and viewports, the reference image id, and
 * the actual capture metadata.
 *
 * @param matches          true when no entry was produced
 * @param entries          ranked differences, largest deviation first
 * @param referenceSource  source of the reference snapshot
 * @param actualSource     source of the actual snapshot
 * @param referenceProfile profile the reference was measured under
 * @param actualProfile    profile the actual was measured under
 * @param referenceViewport reference viewport (reference sizes for the comparison)
 * @param actualViewport   actual viewport
 * @param referenceImage   reference image identifier, null when none was supplied
 * @param actualScreenshot actual capture metadata, null when none was captured
 */
public record SnapshotDiff(
        boolean matches,
        List<Entry> entries,
        String referenceSource,
        String actualSource,
        int referenceProfile,
        int actualProfile,
        InspectorViewport referenceViewport,
        InspectorViewport actualViewport,
        String referenceImage,
        InspectorScreenshot actualScreenshot) {
    public SnapshotDiff {
        entries = List.copyOf(entries);
    }

    /** Entry with the largest deviation, when anything differed. */
    public Optional<Entry> largest() {
        return entries.stream().findFirst();
    }

    /**
     * One measured difference.
     *
     * @param nodePath path of the node the field belongs to, empty for frame-level fields
     * @param field    field name, e.g. {@code rect.width} or {@code node}
     * @param expected value on the reference side, in canonical text form
     * @param actual   value on the actual side, in canonical text form
     * @param deviation absolute distance for numbers, 1 for discrete changes,
     *                  {@link Double#MAX_VALUE} for structural breaks
     */
    public record Entry(String nodePath, String field, String expected, String actual, double deviation) {
    }
}
