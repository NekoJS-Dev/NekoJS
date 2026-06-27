package com.tkisor.nekojs.listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.recipe.definition.MinecraftRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeSchemaAutoDiscovery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionJsonLoader;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.io.Reader;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    private static volatile boolean schemaAutoDiscovered;

    @SubscribeEvent
    public static void onServerResourceReload(AddServerReloadListenersEvent event) {
        if (!schemaAutoDiscovered) {
            try {
                RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered = MinecraftRecipeSchemaScanner.scan();
                RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeSchemaAutoDiscovery.discover(() -> discovered));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to auto-discover recipe schemas: {}", e.getMessage());
            }
            schemaAutoDiscovered = true;
        }
        event.addListener(Identifier.fromNamespaceAndPath(NekoJS.MODID, "recipe_type_definitions"), (ResourceManagerReloadListener) ServerEventListener::loadRecipeTypeDefinitions);
        try {
            NekoJSMod.RUNTIME_ROOT.reload(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Script overload failed: ", e);
        }
    }

    private static void loadRecipeTypeDefinitions(ResourceManager manager) {
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();
        for (Map.Entry<Identifier, Resource> entry : manager.listResources("nekojs/recipe_types", path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier resourceId = entry.getKey();
            String path = resourceId.getPath();
            String prefix = "nekojs/recipe_types/";
            String typeName = path.substring(prefix.length(), path.length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!json.isJsonObject()) {
                    throw new IllegalArgumentException("Recipe type definition must be an object");
                }
                builder.add(RecipeTypeDefinitionJsonLoader.parse(resourceId.getNamespace(), typeName, json.getAsJsonObject()));
            } catch (Exception e) {
                NekoJS.LOGGER.error("Failed to load recipe type definition {}", resourceId, e);
            }
        }
        RecipeTypeDefinitionStorage.replace(builder.build());
    }
}
