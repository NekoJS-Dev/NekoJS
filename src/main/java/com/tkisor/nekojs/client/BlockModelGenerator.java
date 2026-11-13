// Fabric has no equivalent implementation yet; this whole-file guard goes away once the port is done
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.resources.Identifier;

/**
 * Generates default block models for 26.x during the client asset generation phase.
 *
 * <p>26.x (1.21.5+) renders through models: translucent rendering of a block depends on
 * {@code "force_translucent": true} in the texture reference of the model JSON (or on the texture
 * itself carrying alpha). After a script declares {@code BlockBuilder.renderType('translucent')},
 * this class generates a default model, after {@code ClientEvents.generateAssets}, for every block
 * that has no model of its own:
 * <ul>
 *   <li>{@code models/block/<path>.json}: cube_all plus a texture (object form for translucent)</li>
 *   <li>{@code blockstates/<path>.json}: variants pointing at the model</li>
 *   <li>{@code items/<path>.json}: inventory model definition (model-driven since 1.21.2)</li>
 * </ul>
 * Files that already exist (written by the script in generateAssets) are not overwritten; existence
 * is checked with {@link DataGeneratorJS#getJson(String)}.
 */
public final class BlockModelGenerator {
    private BlockModelGenerator() {}

    /** Generates a default model for every block that declared a renderType, only when the script wrote none. */
    public static void generateDefaultModels(DataGeneratorJS generator) {
        for (var entry : com.tkisor.nekojs.wrapper.registry.gen.BlockBuilder.RENDER_TYPES.entrySet()) {
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
     * Builds the cube_all model JSON. When {@code translucent} is set, the texture reference uses the
     * object form {@code {"sprite": ..., "force_translucent": true}} (26.x model-driven rendering);
     * otherwise it is a plain string, cutout is derived from the texture alpha, and solid is the default.
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

    /**
     * Generates a default spawn egg model for entities that requested {@code spawnEgg()}, only when
     * the script wrote none.
     *
     * <p>Spawn egg rendering in 26.x is data-driven with no runtime tinting: this generates a new-style
     * item model definition pointing at an old-style model, and the old-style model references the egg
     * texture as a placeholder. A custom look needs a resource pack that overrides the model JSON or
     * provides {@code <ns>:textures/item/<path>_spawn_egg.png}.
     */
    public static void generateSpawnEggModels(DataGeneratorJS generator) {
        for (Identifier id : EntityTypeBuilder.registeredSpawnEggs()) {
            String namespace = id.getNamespace();
            String path = id.getPath();

            String itemPath = namespace + "/items/" + path + ".json";
            String modelPath = namespace + "/models/item/" + path + ".json";

            if (generator.getJson(itemPath) == null) {
                JsonObject item = new JsonObject();
                JsonObject model = new JsonObject();
                model.addProperty("type", "minecraft:model");
                model.addProperty("model", namespace + ":item/" + path);
                item.add("model", model);
                generator.json(itemPath, item);
            }
            if (generator.getJson(modelPath) == null) {
                JsonObject model = new JsonObject();
                model.addProperty("parent", "minecraft:item/generated");
                JsonObject textures = new JsonObject();
                textures.addProperty("layer0", "minecraft:item/egg");
                model.add("textures", textures);
                generator.json(modelPath, model);
            }
        }
    }
}
//?}
//?}
