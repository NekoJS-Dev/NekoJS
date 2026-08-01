package com.tkisor.nekojs.client;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;

/**
 * 26.x 方块默认模型生成（客户端资产生成阶段）。
 *
 * <p>26.x（1.21.5+）渲染为模型驱动：方块的 translucent 渲染依赖模型 JSON 贴图引用
 * 的 {@code "force_translucent": true}（或贴图本身带 alpha）。脚本通过
 * {@code BlockBuilder.renderType('translucent')} 声明后，本类在
 * {@code ClientEvents.generateAssets} 之后为**没有自写模型**的方块生成默认模型：
 * <ul>
 *   <li>{@code models/block/<path>.json}：cube_all + 贴图（translucent 用对象形式）</li>
 *   <li>{@code blockstates/<path>.json}：variants 指向模型</li>
 *   <li>{@code items/<path>.json}：物品栏模型定义（1.21.2+ 模型驱动）</li>
 * </ul>
 * 已存在的文件（脚本在 generateAssets 中写的）不会被覆盖——用
 * {@link DataGeneratorJS#getJson(String)} 判存在。
 */
public final class BlockModelGenerator {
    private BlockModelGenerator() {}

    /** 为所有声明过 renderType 的方块生成默认模型（仅当脚本未自写）。 */
    public static void generateDefaultModels(DataGeneratorJS generator) {
        for (var entry : BlockRegistryEventJS.RENDER_TYPES.entrySet()) {
            String namespace = entry.getKey().getNamespace();
            String path = entry.getKey().getPath();
            boolean translucent = "translucent".equals(entry.getValue());

            String modelPath = namespace + "/models/block/" + path + ".json";
            String blockstatePath = namespace + "/blockstates/" + path + ".json";
            String itemPath = namespace + "/items/" + path + ".json";

            if (generator.getJson(modelPath) == null) {
                generator.json(modelPath, cubeAllModel(namespace, path, translucent));
            }
            if (generator.getJson(blockstatePath) == null) {
                JsonObject blockstate = new JsonObject();
                JsonObject variants = new JsonObject();
                JsonObject variant = new JsonObject();
                variant.addProperty("model", namespace + ":block/" + path);
                variants.add("", variant);
                blockstate.add("variants", variants);
                generator.json(blockstatePath, blockstate);
            }
            if (generator.getJson(itemPath) == null) {
                JsonObject item = new JsonObject();
                JsonObject model = new JsonObject();
                model.addProperty("type", "minecraft:model");
                model.addProperty("model", namespace + ":block/" + path);
                item.add("model", model);
                generator.json(itemPath, item);
            }
        }
    }

    /**
     * cube_all 模型 JSON。{@code translucent} 时贴图引用用
     * {@code {"sprite": ..., "force_translucent": true}} 对象形式（26.x 模型驱动渲染），
     * 否则用普通字符串（cutout 由贴图 alpha 自动推导，solid 默认）。
     */
    private static JsonObject cubeAllModel(String namespace, String path, boolean translucent) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        String sprite = namespace + ":block/" + path;
        if (translucent) {
            JsonObject ref = new JsonObject();
            ref.addProperty("sprite", sprite);
            ref.addProperty("force_translucent", true);
            textures.add("all", ref);
        } else {
            textures.addProperty("all", sprite);
        }
        model.add("textures", textures);
        return model;
    }
}
