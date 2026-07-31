package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public interface IPlatform {
    boolean isClient();

    boolean isDevelopment();

    default String getCurrentThreadName() {
        return Thread.currentThread().getName();
    }

    String getMcVersion();

    default int getMcVersionInt() {
        String versionStr = getMcVersion();
        if ("unknown".equals(versionStr)) return 0;

        try {
            String cleanVersion = versionStr.split("-")[0].split("\\+")[0];
            String[] parts = cleanVersion.split("\\.");

            int major = 0;
            int minor = 0;
            int patch = 0;

            if (parts.length >= 1) major = Integer.parseInt(parts[0]);
            if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
            if (parts.length >= 3) patch = Integer.parseInt(parts[2]);

            // 计算逻辑：
            // 1.21.1 -> 1 * 1000 + 21 * 10 + 1 = 1211
            // 26.1.2 -> 26 * 100 + 1 * 10 + 2 = 2612
            // 26.1   -> 26 * 100 + 1 * 10 + 0 = 2610

            if (major >= 10) {
                return major * 100 + minor * 10 + patch;
            } else {
                return major * 1000 + minor * 10 + patch;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    Path getGameDir();

    Map<String, IModInfo> getMods();

    IModInfo getInfo(String modID);

    default boolean isLoaded(String modId) {
        return getMods().containsKey(modId);
    }

    default Set<String> getList() {
        return getMods().keySet();
    }

    default Set<PlatformCapability> capabilities() {
        return Set.of();
    }

    default NbtBinaryCodec nbtBinaryCodec() {
        return NbtBinaryCodec.unsupported();
    }

    /** 只读注册表查询服务；平台不支持时返回 {@code null}（脚本侧 {@code Registry} 查询返回空结果）。 */
    default RegistryQueryService registryQueryService() {
        return null;
    }

    /** Loader identifier, e.g. "neoforge", "cleanroom", "forge". */
    String getLoaderId();

    /** Loader version string, e.g. "4.0.0". */
    String getLoaderVersion();
}
