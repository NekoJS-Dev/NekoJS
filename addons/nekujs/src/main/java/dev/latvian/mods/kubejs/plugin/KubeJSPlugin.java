package dev.latvian.mods.kubejs.plugin;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.kubejs.script.DataComponentTypeInfoRegistry;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;

/** The supported binary hook surface; all execution remains owned by NekoJS. */
public interface KubeJSPlugin {
    default void init() {}
    default void initStartup() {}
    default void afterInit() {}
    default void registerEvents(EventGroupRegistry registry) {}
    default void registerBindings(BindingRegistry registry) {}
    default void registerTypeWrappers(TypeWrapperRegistry registry) {}
    default void registerRecipeComponents(RecipeComponentTypeRegistry registry) {}
    default void registerRecipeFactories(dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry registry) {}
    default void registerRecipeSchemas(RecipeSchemaRegistry registry) {}
    default void registerBuilderTypes(BuilderTypeRegistry registry) {}
    default void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) {}
    default void generateData(KubeDataGenerator generator) {}
}
