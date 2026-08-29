package com.tkisor.nekojs.api.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class NekoSnippetJson {
    private NekoSnippetJson() {}

    public static JsonObject vscodeSnippets() {
        JsonObject root = new JsonObject();
        for (SnippetCatalogEntry snippet : NekoScriptCatalog.snippets()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("prefix", snippet.prefix());
            entry.add("body", snippetBody(snippet.body()));
            entry.addProperty("description", snippet.description());
            root.add(snippet.name(), entry);
        }
        return root;
    }

    private static JsonArray snippetBody(String body) {
        JsonArray lines = new JsonArray();
        for (String line : body.split("\\R", -1)) {
            lines.add(line);
        }
        return lines;
    }
}
