package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.api.surface.LoaderVersion;

import java.util.Map;

/**
 * Shared fixture for managed-surface tests. Only the genuinely shared shapes live here;
 * differences specific to one test stay in that test class.
 */
final class ApiSurfaceTestSupport {

    private ApiSurfaceTestSupport() {}

    /** Test environment key: SERVER / DEDICATED_SERVER / loaderId=test / mc=1.21.1. */
    static EnvironmentKey serverEnvironment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "test",
                "0.0.0",
                LoaderVersion.parse("0.0.0"),
                "1.21.1",
                Map.of());
    }
}
