package com.tkisor.nekojs.js.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.NekoJSEventBus; // ⚡ 确保导入的是这个带 J 的总线
import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * 严格事件代理器 (Strict Event Proxy)
 * 负责拦截 JS 侧的事件订阅请求，并将其安全地存入 NekoJSEventBus
 */
public class StrictEventProxy implements ProxyObject {

    private final EventGroup group;
    private final ScriptType currentEnv;

    public StrictEventProxy(EventGroup group, ScriptType currentEnv) {
        this.group = group;
        this.currentEnv = currentEnv;
    }

    @Override
    public Object getMember(String key) {
        if (!group.hasHandler(key)) {
            throw new IllegalArgumentException(
                    "[NekoJS 语法错误] 事件组 '" + group.name() + "' 中不存在名为 '" + key + "' 的事件！"
            );
        }

        if (!group.isHandlerValidFor(key, currentEnv)) {
            throw new IllegalStateException(
                    "[NekoJS 越权拦截] 当前环境 " + currentEnv.name() + " 无法监听 " + group.name() + "." + key
            );
        }

        String fullEventName = group.name() + "." + key;

        return (ProxyExecutable) arguments -> {
            if (arguments.length == 0) {
                throw new IllegalArgumentException("[NekoJS] 必须提供回调函数！");
            }

            Value callback = arguments[arguments.length - 1];
            if (!callback.canExecute()) {
                throw new IllegalArgumentException("[NekoJS] 最后一个参数必须是函数！");
            }

            String target = null;
            if (arguments.length > 1) {
                target = arguments[0].isString() ? arguments[0].asString() : arguments[0].toString();
            }

            NekoJSEventBus.register(
                    fullEventName,
                    this.currentEnv,
                    target,
                    callback
            );

            return null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return group.getHandlerKeys().toArray(new String[0]);
    }

    @Override
    public boolean hasMember(String key) {
        return group.hasHandler(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("[NekoJS] 禁止篡改事件组对象");
    }
}