package com.tkisor.nekojs.api.surface;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentScopeVersionTest {
    private EnvironmentKey environment(String minecraft, String loader) {
        return new EnvironmentKey(ScriptTypeId.SERVER, RuntimeDist.DEDICATED_SERVER, loader,
                null, null, minecraft, Map.of());
    }
    @Test void minecraftRangeUsesTheExistingPackRangeSemantics() {
        var scope = new EnvironmentScope(null, null, Set.of(), Set.of(), null, ">=1.21.1 <26.2.0");
        assertTrue(scope.matches(environment("1.21.1", "fabric")));
        assertTrue(scope.matches(environment("26.1.2", "neoforge")));
        assertFalse(scope.matches(environment("26.2.0", "fabric")));
        assertFalse(scope.matches(environment(null, "fabric")));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentScope(null, null, Set.of(), Set.of(), null, "[1,2)"));
    }
    @Test void missingLoaderCannotSatisfyAnAllowlist() {
        var scope = new EnvironmentScope(null, null, Set.of(), Set.of("fabric"), null, null);
        assertFalse(scope.matches(environment("26.1.2", null)));
        assertFalse(scope.matches(environment("26.1.2", "neoforge")));
        assertTrue(scope.matches(environment("26.1.2", "fabric")));
    }
}
