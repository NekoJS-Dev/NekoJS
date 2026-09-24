package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * Frozen one-frame measurement of one JSX UI root (ticket 45). A snapshot never holds
 * runtime state: every field is copied data, so a script or test can keep it while the
 * UI keeps changing. Fields come from the public host contract only — no Java object
 * identity, native widget, {@code GuiGraphics}, or reconciler internals cross this
 * boundary.
 *
 * @param rootId      id of the measured UI root
 * @param source      which environment collected the frame, e.g. {@code fake-host}
 * @param viewport    logical viewport and safe-area facts for this frame
 * @param nodes       measured node tree, roots first
 * @param diagnostics layout diagnostics of the frame, in produced order
 * @param errors      retained phase-tagged runtime failures, oldest first
 * @param screenshot  auxiliary capture metadata, null when no frame was captured
 */
public record InspectorSnapshot(
        String rootId,
        String source,
        InspectorViewport viewport,
        List<InspectorNode> nodes,
        List<String> diagnostics,
        List<PhaseError> errors,
        InspectorScreenshot screenshot) {
    public InspectorSnapshot {
        nodes = List.copyOf(nodes);
        diagnostics = List.copyOf(diagnostics);
        errors = List.copyOf(errors);
    }

    /** Current viewport profile (1-6) of the frame this snapshot measured. */
    public int profile() {
        return viewport.profile();
    }

    /** One phase-tagged runtime failure retained for the inspector ({@code render}, {@code layout}, ...). */
    public record PhaseError(String phase, String rootId, String message) {
    }
}
