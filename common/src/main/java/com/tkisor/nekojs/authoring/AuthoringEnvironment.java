package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.platform.Platform;

import java.util.TreeMap;

/** Captures process environment identity without retaining platform objects. */
public final class AuthoringEnvironment {
    private AuthoringEnvironment() {}

    public static JsonObject current() {
        TreeMap<String, String> mods = new TreeMap<>();
        Platform.getMods().forEach((id, info) -> mods.put(id, info.getVersion()));
        return AuthoringJson.obj("minecraft", Platform.getMcVersion(), "loader", Platform.getLoaderId(),
                "loaderVersion", Platform.getLoaderVersion(), "dist", Platform.isClient() ? "client" : "dedicated-server", "mods", mods);
    }
    public static String id(JsonObject environment) { return "environment:" + AuthoringJson.hash(AuthoringJson.canonical(environment)); }
}
