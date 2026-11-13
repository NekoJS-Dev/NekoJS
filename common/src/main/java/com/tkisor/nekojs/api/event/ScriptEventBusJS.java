package com.tkisor.nekojs.api.event;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

/**
 * Script-side handle for a custom script event. It is callable, which registers a listener that is
 * forwarded to the underlying {@link EventBusJS}, and it also carries a {@code post} member that fires
 * the event.
 *
 * <pre>
 * // startup_scripts: declare
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * // server_scripts: listen and fire
 * MyEvents.bossKilled(payload => console.log(payload.boss))
 * MyEvents.bossKilled.post({ boss: 'ender_dragon' })
 * </pre>
 *
 * <p>Built-in event groups do not go through this class. Only events declared through
 * {@code ScriptEvents} can be posted by a script.
 */
public final class ScriptEventBusJS implements ProxyExecutable, ProxyObject {

    private static final String POST = "post";

    private final String groupName;
    private final String eventName;
    private final EventBusJS<Object, ?> bus;
    /**
     * The post handle is cached per instance: creating a new one on each getMember would make
     * {@code a.post !== a.post}.
     */
    private final ProxyExecutable post;

    @SuppressWarnings("unchecked")
    public ScriptEventBusJS(String groupName, String eventName, EventBusJS<?, ?> bus) {
        this.groupName = groupName;
        this.eventName = eventName;
        this.bus = (EventBusJS<Object, ?>) bus;
        this.post = arguments -> {
            this.bus.post(arguments.length == 0 ? null : arguments[0].as(Object.class));
            // Custom event buses cannot be cancelled, so post returns nothing instead of a meaningless false.
            return null;
        };
    }

    /** Calling it registers a listener, the same shape as the built-in event buses (a priority name is allowed). */
    @Override
    public Object execute(Value... arguments) {
        return bus.execute(arguments);
    }

    @Override
    public Object getMember(String key) {
        if (POST.equals(key)) {
            return post;
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
