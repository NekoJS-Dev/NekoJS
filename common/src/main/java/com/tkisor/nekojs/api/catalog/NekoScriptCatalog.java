package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.MemberVisibilityQuery;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;
import graal.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;

public final class NekoScriptCatalog {
    private static NekoCatalogPlatformProvider platformProvider = NekoCatalogPlatformProvider.EMPTY;

    private NekoScriptCatalog() {}

    public static void setPlatformProvider(NekoCatalogPlatformProvider provider) {
        platformProvider = provider == null ? NekoCatalogPlatformProvider.EMPTY : provider;
    }

    public static NekoScriptCatalogSnapshot snapshot() {
        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                bindings(),
                events(),
                adapters(),
                List.copyOf(platformProvider.recipeNamespaces()),
                hostExtensions(),
                List.copyOf(platformProvider.snippets()),
                NekoTypeDocs.typeDocs(),
                NekoTypeDocs.manualDeclarations(),
                platformProvider.outputLayout(),
                JavaModuleImportPolicy.nekoDefault()
        );
    }

    public static NekoScriptCatalogSnapshot snapshot(ScriptType scriptType) {
        return new NekoScriptCatalogSnapshot(
                List.of(scriptType),
                bindings(scriptType),
                events(scriptType),
                adapters(),
                List.copyOf(platformProvider.recipeNamespaces()),
                hostExtensions(scriptType),
                snippets(scriptType),
                NekoTypeDocs.typeDocs(scriptType),
                NekoTypeDocs.manualDeclarations(scriptType),
                platformProvider.outputLayout(),
                JavaModuleImportPolicy.nekoDefault()
        );
    }

    public static TypeOutputLayout outputLayout() {
        return platformProvider.outputLayout();
    }

    public static List<BindingCatalogEntry> bindings() {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(bindings(type));
        }
        return List.copyOf(entries);
    }

    public static List<BindingCatalogEntry> bindings(ScriptType scriptType) {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        List<TypeDocCatalogEntry> docs = NekoTypeDocs.typeDocs(scriptType).stream()
                .filter(doc -> doc.kind().equals("binding"))
                .toList();
        for (var binding : NekoPluginRuntime.current().bindings(scriptType).values()) {
            BindingCatalogEntry entry = BindingCatalogEntry.of(binding.name(), scriptType, binding.valueType(), binding.value() instanceof Class<?>);
            for (TypeDocCatalogEntry doc : docs) {
                if (doc.target().equals(binding.name())) {
                    entry = entry.withDoc(doc);
                }
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events() {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(events(type));
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events(ScriptType scriptType) {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (EventGroup group : NekoPluginRuntime.current().eventGroups().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                EventGroup.BusHolder holder = entry.getValue();
                if (!holder.canApplyOn(scriptType)) continue;

                var bus = holder.getBus(scriptType);
                if (bus == null) continue;

                Class<?> dispatchKeyType = bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus
                        ? dispatchBus.dispatchKey().keyType()
                        : null;

                entries.add(EventCatalogEntry.of(
                        group.name(),
                        entry.getKey(),
                        scriptType,
                        bus.bus().eventType(),
                        dispatchKeyType,
                        bus.canCancel(),
                        bus.canDispatch()
                ));
            }
        }
        return List.copyOf(entries);
    }

    public static List<AdapterCatalogEntry> adapters() {
        List<AdapterCatalogEntry> entries = new ArrayList<>();
        for (var adapter : NekoPluginRuntime.current().adapters()) {
            entries.add(adapterEntry(adapter));
        }
        return List.copyOf(entries);
    }

    private static AdapterCatalogEntry adapterEntry(JSTypeAdapter<?> adapter) {
        Class<?> targetType = adapter.getTargetClass();
        return new AdapterCatalogEntry(
                targetType,
                targetType.getSimpleName(),
                inputShapesFor(targetType),
                adapter.getPrecedence(),
                errorPolicyFor(adapter.getPrecedence()),
                examplesFor(targetType)
        );
    }

    private static List<String> inputShapesFor(Class<?> targetType) {
        String name = targetType.getSimpleName();
        return switch (name) {
            case "ItemStack" -> List.of("ItemStack", "Item", "item id string", "{ item|id, count?, components? }");
            case "Ingredient" -> List.of("Ingredient", "IngredientFactory", "ItemStack", "Item", "item id string", "tag id string", "array", "{ item|tag|ingredient }");
            case "SizedIngredient" -> List.of("SizedIngredient", "SizedIngredientJS", "Ingredient", "IngredientFactory", "item id string", "tag id string", "array", "{ ingredient|item|tag, count }");
            case "FluidStack" -> List.of("FluidStack", "fluid id string", "{ fluid|id, amount? }");
            case "FluidIngredient" -> List.of("FluidIngredient", "FluidIngredientJS", "fluid id string", "tag id string", "array", "{ fluid|tag|ingredient }");
            case "SizedFluidIngredient" -> List.of("SizedFluidIngredient", "FluidIngredient", "FluidIngredientJS", "fluid id string", "tag id string", "{ ingredient|fluid|tag, amount }");
            case "RecipeFilter" -> List.of("recipe id string", "array of filters", "{ mod?, type?, group?, id?, input?, output?, and?, or?, not?, idStartsWith?, idEndsWith?, idContains? }");
            case "RecipeJsonValue" -> List.of("primitive", "JS object", "JS array", "JsonElement", "IngredientFactory", "Ingredient", "SizedIngredientJS", "SizedIngredient", "ItemStack", "FluidIngredientJS", "FluidIngredient", "SizedFluidIngredient", "FluidStack");
            case "JsonObject" -> List.of("JS object");
            default -> List.of();
        };
    }

    private static String errorPolicyFor(HostAccess.TargetMappingPrecedence precedence) {
        return precedence == HostAccess.TargetMappingPrecedence.LOWEST
                ? "Prefer native Java overloads first; throw on unsupported shapes."
                : "Throw on unsupported shapes.";
    }

    private static List<String> examplesFor(Class<?> targetType) {
        String name = targetType.getSimpleName();
        return switch (name) {
            case "ItemStack" -> List.of("ItemJS.of('minecraft:stone')", "ItemJS.of('minecraft:stone').withCount(4)");
            case "Ingredient" -> List.of("Ingredient.of('minecraft:stone')", "Ingredient.tag('minecraft:planks')");
            case "SizedIngredient" -> List.of("Ingredient.of('minecraft:stone').withCount(3)", "{ ingredient: 'minecraft:stone', count: 3 }");
            case "FluidStack" -> List.of("Fluid.of('minecraft:water', 1000)");
            case "FluidIngredient" -> List.of("FluidIngredient.of('minecraft:water')", "FluidIngredient.tag('minecraft:water')");
            case "SizedFluidIngredient" -> List.of("FluidIngredient.of('minecraft:water').withAmount(1000)");
            case "RecipeFilter" -> List.of("{ output: 'minecraft:stick' }", "{ mod: 'minecraft', type: 'minecraft:crafting_shaped' }");
            case "RecipeJsonValue" -> List.of("builder.property('ingredients', [Ingredient.of('minecraft:stone')])", "event.custom({ type: 'minecraft:crafting_shapeless', ingredients: [Ingredient.of('minecraft:stone')], result: { id: 'minecraft:stone_button' } })");
            default -> List.of();
        };
    }

    public static List<HostExtensionCatalogEntry> hostExtensions() {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(hostExtensions(type));
        }
        return List.copyOf(entries);
    }

    public static List<HostExtensionCatalogEntry> hostExtensions(ScriptType scriptType) {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (HostExtensionSource source : platformProvider.hostExtensions()) {
            if (!source.canApplyOn(scriptType)) continue;

            for (var binding : MemberVisibilityQuery.getVisibleMethods(source.extensionInterface()).values()) {
                entries.add(new HostExtensionCatalogEntry(
                        source.targetClass(),
                        source.extensionInterface(),
                        binding.member().getName(),
                        binding.jsName(),
                        binding.member(),
                        source.scriptType(),
                        false
                ));
            }
        }
        return List.copyOf(entries);
    }

    public static List<SnippetCatalogEntry> snippets() {
        return List.copyOf(platformProvider.snippets());
    }

    public static List<SnippetCatalogEntry> snippets(ScriptType scriptType) {
        List<SnippetCatalogEntry> entries = new ArrayList<>();
        for (SnippetCatalogEntry snippet : platformProvider.snippets()) {
            if (snippet.canApplyOn(scriptType)) {
                entries.add(snippet);
            }
        }
        return List.copyOf(entries);
    }
}
