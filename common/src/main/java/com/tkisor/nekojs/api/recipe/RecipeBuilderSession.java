package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonObject;

/**
 * Borrows one native recipe's identity, JSON view, and preparation lifecycle from its event owner.
 * No session method publishes a new runtime or owns the registry view.
 */
public interface RecipeBuilderSession {
    String currentId();

    /** Returns a detached snapshot of the current recipe JSON. */
    JsonObject snapshot();

    /** Registers preparation with the native event's final encode lifecycle. */
    void beforeEncode(Runnable action);

}
