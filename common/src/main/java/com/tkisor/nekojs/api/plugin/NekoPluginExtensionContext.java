package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

public interface NekoPluginExtensionContext {
    boolean client();

    ScriptCompilerRegistry scriptCompilers();

    ScriptPropertyRegistry scriptProperties();

    ScriptTypedValue<BindingRegistry> bindings();

    JSTypeAdapterRegistry adapters();

    EventGroupRegistry events();

    TypeDocsRegister typeDocs();

    RecipeNamespaceRegister recipeNamespaces();

    RecipeLifecycleRegister recipeLifecycle();
}
