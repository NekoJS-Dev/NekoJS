package com.tkisor.nekojs.api.lifecycle;

import com.tkisor.nekojs.script.ScriptType;

import java.util.function.Consumer;

/**
 * 插件生命周期钩子注册器。
 *
 * <p>由 {@link com.tkisor.nekojs.api.NekoJSPlugin} 的 {@code registerLifecycleHooks} 接收，
 * 插件通常无需直接调用本接口 —— 直接 override {@code NekoJSPlugin} 上的便捷方法
 *（{@code init} / {@code initStartup} / {@code afterInit} /
 * {@code beforeScriptsLoaded} / {@code afterScriptsLoaded}）即可，
 * 默认实现会把对应方法引用注册进来。
 *
 * <p>仅在需要注册多个回调、或精细控制注册时机时才直接使用本注册器。
 */
public interface PluginLifecycleRegister {

    /** 最早触发：runtime bootstrap 完成后、startup 脚本加载前。 */
    void onInit(Runnable hook);

    /** startup 脚本加载完成后触发。 */
    void onInitStartup(Runnable hook);

    /** 所有 mod 初始化完成（FMLLoadCompleteEvent）后触发。 */
    void onAfterInit(Runnable hook);

    /** 每次某个类型的脚本加载前触发（含首次加载与完整 reload，不含单文件热重载）。 */
    void onBeforeScriptsLoaded(Consumer<ScriptType> hook);

    /** 每次某个类型的脚本加载后触发（含首次加载与完整 reload，不含单文件热重载）。 */
    void onAfterScriptsLoaded(Consumer<ScriptType> hook);
}
