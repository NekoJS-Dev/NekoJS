package com.tkisor.nekojs.listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.recipe.definition.MinecraftRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeSchemaAutoDiscovery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionJsonLoader;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    private static volatile boolean schemaAutoDiscovered;

    static {
        // loot table 加载（reload 流程）时应用脚本在 lootTables 事件中的修改。
        NeoForge.EVENT_BUS.addListener(LootTableEventJS::onLootTableLoad);
    }

    @SubscribeEvent
    public static void onServerResourceReload(AddReloadListenerEvent event) {
        if (!schemaAutoDiscovered) {
            try {
                RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered = MinecraftRecipeSchemaScanner.scan();
                RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeSchemaAutoDiscovery.discover(() -> discovered));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to auto-discover recipe schemas: {}", e.getMessage());
            }
            schemaAutoDiscovered = true;
        }
        event.addListener((ResourceManagerReloadListener) ServerEventListener::loadRecipeTypeDefinitions);
        try {
            NekoJSMod.RUNTIME_ROOT.reload(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Script overload failed: ", e);
        }
        // loot table JSON 管理（reload 流程先于 loot 解析，修改当次 reload 生效）
        ServerEvents.LOOT_TABLES.post(new LootTableEventJS());
        postGenerateData();
    }

    /**
     * 数据生成事件：脚本把 datapack JSON 写入 {@code <gameDir>/nekojs/data}（磁盘 datapack，
     * 懒读保证 reload 时序正确）。目前支持单一 {@code after_mods} 阶段。
     */
    private static void postGenerateData() {
        try {
            DataGeneratorJS generator = new DataGeneratorJS(NekoJSPaths.get().data(), "after_mods");
            PluginGenerationHooks.fireGenerateData(generator);
            ServerEvents.GENERATE_DATA.post(generator, "after_mods");
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("generateData event failed: ", e);
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
                NekoJS.LOGGER.error("Failed to load recipe type definition {}", resourceId, e);
            }
        }
        RecipeTypeDefinitionStorage.replace(builder.build());
    }
}
