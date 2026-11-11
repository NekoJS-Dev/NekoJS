package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonObject;

import java.util.Objects;

/** One graph document; source and resourcePath identify its original resource for diagnostics. */
public record SchemaDocument(String source, String id, String resourcePath, JsonObject json) {
    public SchemaDocument {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resourcePath, "resourcePath");
        json = Objects.requireNonNull(json, "json").deepCopy();
    }
}
