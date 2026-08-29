package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

/**
 * Last-resort namespace proxy used when no handler method and no schema definition
 * matches a member name. Every member resolves to the same executable: convert the
 * single object argument to JSON, stamp it with {@code namespace:recipeType} as the
 * recipe type, and hand it to {@link RecipeSchemaHost#custom(JsonObject)}.
 *
 * <p>Parse failures are swallowed (debug-logged) and the executable returns null, so
 * an unknown namespace member degrades to a no-op instead of a hard script error.
 *
 * <p>All methods are invoked by GraalVM via {@link ProxyObject} dispatch, not by
 * direct Java callers.
 */
@Doc("Recipe namespace fallback: any member call becomes a raw JSON recipe typed 'namespace:member'.")
public class FallbackNamespaceProxy implements ProxyObject {
    private final RecipeSchemaHost host;
    private final String namespace;

    /** Wraps fallback dispatch for one namespace. */
    public FallbackNamespaceProxy(RecipeSchemaHost host, String namespace) {
        this.host = host;
        this.namespace = namespace;
    }

    @Override
    @Doc("Returns a function building a raw JSON recipe typed 'namespace:recipeType'.")
    @Param(name = "recipeType", value = "member name used as the type's path, e.g. 'smelting' in 'neko:smelting'")
    @Return("an executable taking one JSON-like object, or null if the call fails to convert")
    public Object getMember(String recipeType) {
        return (ProxyExecutable) arguments -> {
            if (arguments.length == 1 && arguments[0].hasMembers()) {
                try {
                    JsonElement converted = host.toJson(arguments[0]);
                    if (!converted.isJsonObject()) {
                        throw new IllegalArgumentException("Fallback recipe JSON must be an object");
                    }

                    JsonObject json = converted.getAsJsonObject();
                    json.addProperty("type", namespace + ":" + recipeType);

                    return host.custom(json);

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
    @Doc("Always empty: fallback members are unbounded and not enumerable.")
    public Object getMemberKeys() { return new String[0]; }
    @Override
    @Doc("Always true: every name is accepted as a potential raw JSON recipe type.")
    public boolean hasMember(String key) { return true; }
    @Override
    @Doc("Ignored: the fallback namespace proxy is read-only.")
    public void putMember(String key, Value value) {}
}
