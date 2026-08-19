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
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import com.tkisor.nekojs.wrapper.event.server.LootTableEventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.Reader;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    private static volatile boolean schemaAutoDiscovered;

    static {
        // loot table 加载（reload 流程）时应用脚本在 lootTables 事件中的修改。
        NeoForge.EVENT_BUS.addListener(LootTableEventJS::onLootTableLoad);
    }

    /**
     * 物品属性修改事件：每次服务器启动时 post 一次（datapack 装载完成、玩家加入前）。
     * 脚本 reload 时由 {@code /nekojs reload server}（NekoJSCommands）重放，走同一条
     * 快照恢复路径；物品单例与已修改组件跨 vanilla /reload 保留，无需在此重放。
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        activateWorldPacks(event.getServer());
        ItemModificationEventJS.fire(event.getServer());
    }

    /**
     * 激活存档侧世界脚本包（{@code <world>/nekojs_packs/}）。常规 SERVER reload 发生在
     * 资源装载期（{@link #onServerResourceReload}），早于本事件，因此有世界包被激活时
     * 补一次完整 SERVER reload 把包内脚本纳入；无世界包则零开销。
     */
    private static void activateWorldPacks(net.minecraft.server.MinecraftServer server) {
        var worldPacks = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get()
                .activateWorldPacks(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        if (worldPacks.isEmpty()) return;
        try {
            NekoJSMod.RUNTIME_ROOT.reload(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("SERVER reload after world pack activation failed: ", e);
        }
    }

    /**
     * 卸载世界脚本包：按包前缀反注册 SERVER 监听器与 timer；单人环境下 CLIENT 侧同样
     * 清理（客户端脚本会在下次进世界时整体重载，这里只清内存中的监听器，避免返回标题
     * 界面后世界包回调仍在菜单 tick 上触发）。
     */
    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        var removed = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().deactivateWorldPacks();
        if (removed.isEmpty()) return;
        var serverManager = NekoJSMod.RUNTIME_ROOT.scriptManagerOrNull(ScriptType.SERVER);
        if (serverManager != null) serverManager.clearWorldPackListeners(removed);
        if (com.tkisor.nekojs.platform.Platform.isClient()) {
            var clientManager = NekoJSMod.RUNTIME_ROOT.scriptManagerOrNull(ScriptType.CLIENT);
            if (clientManager != null) clientManager.clearWorldPackListeners(removed);
        }
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
