package com.tkisor.nekojs.fabric.client;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Supplies vanilla egg models only for registered eggs without a resource-pack item definition. */
public final class FabricSpawnEggModels {
    private static final Identifier VANILLA_EGG = Identifier.fromNamespaceAndPath("minecraft", "egg");

    private FabricSpawnEggModels() {}

    public static Set<Identifier> missingDefinitions(Set<Identifier> registeredEggs, ResourceManager resources) {
        return registeredEggs.stream()
                .filter(id -> resources.getResource(itemDefinition(id)).isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static ItemModel resolve(Identifier id, ItemModel loaded, Set<Identifier> missingDefinitions,
            Function<Identifier, ItemModel> models) {
        if (VANILLA_EGG.equals(id) || !(loaded instanceof MissingItemModel) || !missingDefinitions.contains(id)) {
            return loaded;
        }
        return models.apply(VANILLA_EGG);
    }

    static Identifier itemDefinition(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json");
    }
}
