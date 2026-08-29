package com.tkisor.nekojs.api.event;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

/**
 * 自定义脚本事件的脚本侧句柄：既可调用（注册监听，转发给底层 {@link EventBusJS}），
 * 又带一个 {@code post} 成员（触发事件）。
 *
 * <pre>
 * // startup_scripts：声明
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * // server_scripts：监听与触发
 * MyEvents.bossKilled(payload => console.log(payload.boss))
 * MyEvents.bossKilled.post({ boss: 'ender_dragon' })
 * </pre>
 *
 * <p>内置事件组不走本类——只有 {@code ScriptEvents} 声明出来的事件才允许脚本自己 post。
 */
public final class ScriptEventBusJS implements ProxyExecutable, ProxyObject {

    private static final String POST = "post";

    private final String groupName;
    private final String eventName;
    private final EventBusJS<Object, ?> bus;

    @SuppressWarnings("unchecked")
    public ScriptEventBusJS(String groupName, String eventName, EventBusJS<?, ?> bus) {
        this.groupName = groupName;
        this.eventName = eventName;
        this.bus = (EventBusJS<Object, ?>) bus;
    }

    /** 调用即注册监听：形态与内置事件总线一致（可带优先级名）。 */
    @Override
    public Object execute(Value... arguments) {
        return bus.execute(arguments);
    }

    @Override
    public Object getMember(String key) {
        if (POST.equals(key)) {
            return (ProxyExecutable) arguments -> {
                Object payload = arguments.length == 0 ? null : arguments[0].as(Object.class);
                return bus.post(payload);
            };
        }
        return null;
    }

    @Override
    public Object getMemberKeys() {
        return new String[] {POST};
    }

    @Override
    public boolean hasMember(String key) {
        return POST.equals(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException(
                "Script event " + groupName + "." + eventName + " has no writable members");
    }
}
