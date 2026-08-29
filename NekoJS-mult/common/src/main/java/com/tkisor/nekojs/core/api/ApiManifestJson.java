package com.tkisor.nekojs.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.api.surface.ApiManifest;

/** ApiManifest 的 JSON 编解码（gson；字段排序 + 缩进，输出确定性）。 */
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
