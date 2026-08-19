package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.bindings.static_access.ClientDataJS;
import com.tkisor.nekojs.bindings.static_access.OnceJS;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NodeModuleTypeDocs;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * Self-registering built-in plugin for the pure-common registrations that used
 * to be duplicated verbatim in every platform {@code NekoJSCorePlugin}
 * (neoforge-26-shared / neoforge-1.21.1 / cleanroom-1.12.2): the TypeScript and
 * JSX language plugins, the four built-in script properties, the built-in
 * node module type declarations, and the platform-agnostic global bindings.
 *
 * <p>Platform core plugins keep only platform-tailored registrations
 * (bindings / adapters / event groups / platform-specific type docs).
 *
 * <p>Lives under {@code com.tkisor.nekojs.*} so it is auto-discovered on every
 * platform. Uses the default priority (1000): compilers are looked up
 * post-bootstrap (registry reverse iteration, last-wins — third-party
 * same-extension overrides behave the same as before), script properties and
 * type docs are consumed after bootstrap as well.
 */
@RegisterNekoJSPlugin
public final class NekoCommonBuiltinPlugin implements NekoJSPlugin {

    /**
     * 平台无关的全局绑定：run-once 守卫（{@code once}/{@code clearOnce}，ProxyExecutable
     * 函数绑定，标记进程级存活）与客户端数据只读视图（{@code clientData}，存储在 common，
     * 由平台网络层写入）。server 端推送用的 {@code ClientData} 绑定依赖平台网络层，
     * 由各平台 core plugin 注册。
     */
    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("once", OnceJS.ONCE);
        registry.register("clearOnce", OnceJS.CLEAR_ONCE);
        registry.register("clientData", new ClientDataJS());
    }

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        registry.register(NekoJsxLanguagePlugin.INSTANCE);
    }

    @Override
    public void registerScriptProperty(ScriptPropertyRegistry registry) {
        registry.register(ScriptProperty.AFTER);
        registry.register(ScriptProperty.MODLOADED);
        registry.register(ScriptProperty.DISABLE);
        registry.register(ScriptProperty.PRIORITY);
    }

    @Override
    public void registerNodeTypeDocs(TypeDocsRegister registry) {
        NodeModuleTypeDocs.registerBuiltin(registry);
    }
}
