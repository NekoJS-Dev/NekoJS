package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 在这里注册 JS -> Java 类型适配器
 */
public final class RegisterJSTypeAdaptersEvent extends Event implements IModBusEvent {
    private final List<JSTypeAdapter<?>> adapters = new ArrayList<>();

    /** JS→Java 类型适配器注册 */
    public <T> void register(JSTypeAdapter<T> adapter) {
        adapters.add(adapter);
    }

    public List<JSTypeAdapter<?>> getAdapters() {
        return adapters;
    }
}
