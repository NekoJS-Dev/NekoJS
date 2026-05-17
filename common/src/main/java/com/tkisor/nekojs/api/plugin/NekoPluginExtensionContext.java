package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

public interface NekoPluginExtensionContext {
    boolean client();

    ScriptCompilerRegistry scriptCompilers();

    ScriptPropertyRegistry scriptProperties();

    BindingsRegister bindings();

    default BindingsRegister bindings(ScriptType type) {
        return binding -> bindings().register(copyBinding(type, binding));
    }

    JSTypeAdapterRegister adapters();

    EventGroupRegistry events();

    TypeDocsRegister typeDocs();

    RecipeNamespaceRegister recipeNamespaces();

    private static Binding copyBinding(ScriptType type, Binding binding) {
        if (binding.isStaticClass()) {
            return Binding.of(type, binding.getName(), binding.getType());
        }
        return Binding.of(type, binding.getName(), binding.getObject());
    }
}
