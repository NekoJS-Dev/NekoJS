package com.tkisor.nekojs.listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionJsonLoader;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    @SubscribeEvent
    public static void onServerResourceReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) ServerEventListener::loadRecipeTypeDefinitions);
        try {
            NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Script overload failed: ", e);
        }
    }

    private static void loadRecipeTypeDefinitions(ResourceManager manager) {
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("nekojs/recipe_types", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation resourceId = entry.getKey();
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
                NekoJSCommon.LOGGER.error("[NekoJS] Failed to load recipe type definition {}", resourceId, e);
            }
        }
        RecipeTypeDefinitionStorage.replace(builder.build());
    }
}
