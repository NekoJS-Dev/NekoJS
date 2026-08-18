package com.tkisor.nekojs.api.recipe.definition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTypeDefinitionStorageTest {

    private static RecipeTypeDefinition def(String ns, String name) {
        return new RecipeTypeDefinition(ns, name, ns + ":" + name, ns + "_" + name,
                List.of(), Map.of(), List.of());
    }

    @Test
    void scriptLayerWinsOverAllOthers() {
        try {
            RecipeTypeDefinitionStorage.setAutoDiscovered(
                    RecipeTypeDefinitionRegistry.builder().add(def("mod", "type")).build());
            RecipeTypeDefinitionStorage.replace(
                    RecipeTypeDefinitionRegistry.builder().add(def("mod", "type")).build());
            RecipeTypeDefinitionStorage.setPluginOverrides(
                    RecipeTypeDefinitionRegistry.builder().add(def("mod", "type")).build());
            RecipeTypeDefinitionStorage.replaceScript(
                    RecipeTypeDefinitionRegistry.builder().add(
                            new RecipeTypeDefinition("mod", "type", "mod:type", "mod_type",
                                    List.of(), Map.of(), List.of())).build());

            var current = RecipeTypeDefinitionStorage.current().get("mod", "type");
            assertNotNull(current);
            assertEquals("mod_type", current.prefix(), "script 层应覆盖同 key 的 data/plugin/auto 层");
        } finally {
            RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeTypeDefinitionRegistry.EMPTY);
            RecipeTypeDefinitionStorage.replace(RecipeTypeDefinitionRegistry.EMPTY);
            RecipeTypeDefinitionStorage.setPluginOverrides(RecipeTypeDefinitionRegistry.EMPTY);
            RecipeTypeDefinitionStorage.replaceScript(RecipeTypeDefinitionRegistry.EMPTY);
        }
    }

    @Test
    void emptyScriptLayerDoesNotHideLowerLayers() {
        try {
            RecipeTypeDefinitionStorage.setAutoDiscovered(
                    RecipeTypeDefinitionRegistry.builder().add(def("mod", "auto")).build());
            RecipeTypeDefinitionStorage.replaceScript(RecipeTypeDefinitionRegistry.EMPTY);
            assertNotNull(RecipeTypeDefinitionStorage.current().get("mod", "auto"),
                    "script 层为空时不应遮蔽 auto 层");
        } finally {
            RecipeTypeDefinitionStorage.setAutoDiscovered(RecipeTypeDefinitionRegistry.EMPTY);
            RecipeTypeDefinitionStorage.replaceScript(RecipeTypeDefinitionRegistry.EMPTY);
        }
    }
}
