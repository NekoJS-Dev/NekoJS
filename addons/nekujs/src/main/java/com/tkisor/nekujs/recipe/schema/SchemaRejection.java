package com.tkisor.nekujs.recipe.schema;

/** A terminal profile rejection with the original resource and JSON location. */
public record SchemaRejection(
        String code, String schemaId, String source, String resourcePath, String jsonPath, String message
) {
    public String reason() {
        return "[" + code + "] " + message + " (" + source + ":" + resourcePath + jsonPath + ")";
    }
}
