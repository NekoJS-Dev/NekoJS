package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.catalog.NodeModuleRegister;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.lifecycle.PluginLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.api.recipe.RecipeSchemaRegister;
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
