package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.IngredientJS;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.js.type_adapter.*;

@RegisterNekoJSPlugin
public class NekoJSCorePlugin implements NekoJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(ItemEvents.GROUP);
        registry.register(EntityEvents.GROUP);
        registry.register(CommandEvents.GROUP);
        registry.register(RegistryEvents.GROUP);
        registry.register(LevelEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingsRegister registry) {
        registry.register(Binding.of("Item", new ItemJS()));
        registry.register(Binding.of("Ingredient", new IngredientJS()));

        registry.register(Binding.of("NativeEvents", new NativeEventsJS()));
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegister registry) {
        registry.register(new ItemStackAdapter());
        registry.register(new ItemStackWrapperAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new IdentifierAdapter());
        registry.register(new RecipeFilterAdapter());
        registry.register(new JsonObjectAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new BlockAdapter());
    }

    @Override
    public void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
        registry.register("minecraft", MinecraftRecipeHandler::new);
    }
}