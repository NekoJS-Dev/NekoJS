package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class ApiRuntimeVersionReader {

    private static final String RESOURCE_PATH = "nekojs/api-runtime.properties";

    /**
     * Reads the version information from api-runtime.properties.
     *
     * <p>{@code spi.version} and {@code runtime.contract.version} are currently {@code 0.0.0},
     * meaning "unspecified, so no version gate is enforced". Any plugin compatibility check
     * driven by these versions (such as {@code runtime.spiVersion >= plugin.requires}) lets
     * every plugin through while they are 0.0.0. Bump them to 0.1.0 once the SPI is stable.
     */

    private ApiRuntimeVersionReader() {}

    public static ApiRuntimeVersions read() {
        return read(ApiRuntimeVersionReader.class.getClassLoader());
    }

    public static ApiRuntimeVersions read(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try (InputStream is = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("api-runtime.properties not found on classpath");
            }
            Properties props = new Properties();
            props.load(is);

            String nekojsVersion = requireProperty(props, "nekojs.version");
            ApiVersion apiVersion = ApiVersion.parse(requireProperty(props, "api.version"));
            ApiVersion spiVersion = ApiVersion.parse(requireProperty(props, "spi.version"));
            ApiVersion runtimeContractVersion = ApiVersion.parse(requireProperty(props, "runtime.contract.version"));
            int catalogSchemaVersion = Integer.parseInt(requireProperty(props, "catalog.schema.version"));

            return new ApiRuntimeVersions(
                    nekojsVersion,
                    apiVersion,
                    spiVersion,
                    runtimeContractVersion,
                    catalogSchemaVersion);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read api-runtime.properties", e);
        }
    }

    private static String requireProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value.trim();
    }
}
