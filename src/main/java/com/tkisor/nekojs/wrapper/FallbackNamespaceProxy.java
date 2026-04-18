package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

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
                    Value jsGlobalJSON = Context.getCurrent().getBindings("js").getMember("JSON");
                    String jsonString = jsGlobalJSON.invokeMember("stringify", arguments[0]).asString();

                    JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

                    json.addProperty("type", namespace + ":" + recipeType);

                    event.custom(json);

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