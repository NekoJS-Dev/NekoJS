package com.tkisor.nekojs.api.ui;

/**
 * Read-only measurement port for one JSX UI root (ticket 45). Implementations return a
 * frozen {@link InspectorSnapshot}; fake hosts and the NeoForge host collect through the
 * same {@link InspectorSnapshots} code, so their records are isomorphic by construction.
 */
public interface UiInspector {
    /** Latest frozen measurement, or null when the root has not rendered a frame yet. */
    InspectorSnapshot inspect();
}
