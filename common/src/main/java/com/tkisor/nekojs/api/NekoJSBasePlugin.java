package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * NekoJS 基础插件接口（common 层）。
 * <p>
 * 只包含不依赖 Minecraft / NeoForge / Graal 运行时的方法。
 * 平台层通过 {@code NekoJSPlugin extends NekoJSBasePlugin} 标记可自动发现插件。
 * </p>
 */
public interface NekoJSBasePlugin {
    default void registerScriptCompilers(ScriptCompilerRegistry registry) {}

    default void registerScriptProperty(ScriptPropertyRegistry registry) {}

    default void registerBinding(BindingRegistry registry) {}

    default void registerAdapters(JSTypeAdapterRegistry registry) {}

    default void registerTypeDocs(TypeDocsRegister registry) {}
}
