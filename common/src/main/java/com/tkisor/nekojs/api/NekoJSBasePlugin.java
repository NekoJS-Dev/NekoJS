package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * NekoJS 基础插件接口（common 层）。
 * <p>
 * 只包含不依赖 Minecraft / NeoForge / Graal 运行时的方法。
 * 平台层通过 {@code NekoJSPlugin extends NekoJSBasePlugin} 扩展更多注册方法。
 * </p>
 */
public interface NekoJSBasePlugin {

    /**
     * 注册 JS 全局类型适配器
     */
    default void registerAdapters(JSTypeAdapterRegister registry) {}

    /**
     * 注册脚本属性（如 priority, after, disable 等）
     */
    default void registerScriptProperty(ScriptPropertyRegistry registry) {}
}