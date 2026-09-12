package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.api.surface.LoaderVersion;

import java.util.Map;

/**
 * Managed surface 测试共享 fixture（ticket 09 review：6 份逐字相同的 environment()/platform
 * 复制超过抽取阈值）。只放真正的共享形状；测试专属差异留在各测试类内。
 */
final class ApiSurfaceTestSupport {

    private ApiSurfaceTestSupport() {}

    /** SERVER / DEDICATED_SERVER / loaderId=test / mc=1.21.1 的测试环境键。 */
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
