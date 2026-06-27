package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import com.tkisor.nekojs.NekoJS;

public class FallbackNamespaceProxy implements ProxyObject {
    private final RecipeEventJS event;
    private final String namespace;

    public FallbackNamespaceProxy(RecipeEventJS event, String namespace) {
        this.event = event;
        this.namespace = namespace;
    }

    @Override
    public Object getMember(String recipeType) {
        return (ProxyExecutable) arguments -> {
            if (arguments.length == 1 && arguments[0].hasMembers()) {
                try {
                    JsonElement converted = RecipeJsonValueConverter.toJson(event, arguments[0]);
                    if (!converted.isJsonObject()) {
                        throw new IllegalArgumentException("Fallback recipe JSON must be an object");
                    }

                    JsonObject json = converted.getAsJsonObject();
                    json.addProperty("type", namespace + ":" + recipeType);

                    return event.custom(json);

                } catch (Exception e) {
                    NekoJS.LOGGER.debug("Failed to parse fallback JSON: ", e);
                }
            } else {
                NekoJS.LOGGER.debug("Handler {}:{} not found, and arguments are not a valid JSON object.", namespace, recipeType);
            }
            return null;
        };
    }

    @Override
    public Object getMemberKeys() { return new String[0]; }
    @Override
    public boolean hasMember(String key) { return true; }
    @Override
    public void putMember(String key, Value value) {}
}