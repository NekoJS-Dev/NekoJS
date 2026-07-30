package com.tkisor.nekojs.api.surface;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiSurfaceSnapshotTest {

    @Test
    void surfaceSnapshotIsImmutable() {
        ApiSurfaceSnapshot snapshot = new ApiSurfaceSnapshot(
                List.of(), Set.of(), List.of(), List.of(),
                new EnvironmentKey(null, null, null, null, null, null, Map.of()));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.symbols().add(null));
    }
}
