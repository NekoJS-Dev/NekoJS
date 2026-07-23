package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.plugin.PluginLifecycleRegister;
import com.tkisor.nekojs.core.plugin.RecipeLifecycleRegister;
import com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister;
import com.tkisor.nekojs.core.plugin.RecipeSchemaRegister;
import com.tkisor.nekojs.script.ScriptTypedValue;

public interface NekoPluginExtensionContext {
    boolean client();

    ScriptCompilerRegistry scriptCompilers();

    ScriptTypedValue<BindingRegistry> bindings();

    JSTypeAdapterRegistry adapters();

    EventGroupRegistry events();

    TypeDocsRegister typeDocs();

    /** 插件 JS 模块注册器（registerNodeModules 扩展点用）。 */
    NodeModuleRegister nodeModules();

    RecipeNamespaceRegister recipeNamespaces();

    RecipeSchemaRegister recipeSchemas();

    RecipeLifecycleRegister recipeLifecycle();

    /** 插件生命周期钩子注册器（registerLifecycleHooks 扩展点用）。 */
    PluginLifecycleRegister lifecycle();
}
