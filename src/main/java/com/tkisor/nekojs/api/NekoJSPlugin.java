package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.data.EventGroupRegistry;

/**
 * NekoJS 插件接口。<p>
 * 插件类必须实现此接口，才能在 NekoJS 初始化时被自动注册。<p>
 * 插件类可以在 {@link #registerEvents(EventGroupRegistry)} 方法中注册事件。
 */
public interface NekoJSPlugin {
    /**
     * 注册事件组。<p>
     * 插件类可以在这个方法中注册事件组。
     */
    default void registerEvents(EventGroupRegistry registry) {
    }

    /**
     * 注册全局静态对象绑定
     */
    default void registerBindings(BindingsRegister registry) {}

    /**
     * 暂时没做好
     * @param registry
     */
    default void registerAdapters(JSTypeAdapterRegister registry) {}
}
