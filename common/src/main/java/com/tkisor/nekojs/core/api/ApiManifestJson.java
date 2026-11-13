package com.tkisor.nekojs.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.api.surface.ApiManifest;

/** JSON codec for ApiManifest (gson, sorted fields and indentation give deterministic output). */
public final class ApiManifestJson {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ApiManifestJson() {}

    public static String toJson(ApiManifest manifest) {
        return GSON.toJson(manifest);
    }

    public static ApiManifest fromJson(String json) {
        return GSON.fromJson(json, ApiManifest.class);
    }
}
