package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

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
                    NekoJS.LOGGER.error("[NekoJS] Fallback JSON 解析失败: ", e);
                }
            } else {
                NekoJS.LOGGER.error("[NekoJS] 找不到 {}:{} 处理器，且参数非标准 JSON 对象。", namespace, recipeType);
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