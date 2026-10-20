//? if neoforge {
package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeManualTypeReferencesTest {
    @Test
    void coreContributorAssociatesAuthoredTypesWithThisNodesActualClassesAndInputAdapter() {
        var entries = new ArrayList<ManualDeclarationCatalogEntry>();
        new NekoJSCorePlugin().registerTypeDocs(new TypeDocsRegister() {
            @Override public void register(TypeDocCatalogEntry entry) {}
            @Override public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) { entries.add(entry); }
        });
        var references = new TreeMap<String, ManualDeclarationCatalogEntry.TypeReference>();
        entries.forEach(entry -> references.putAll(entry.references()));
        assertEquals(List.of("CompoundTag", "EntityType", "FluidIngredientJS", "FluidStack", "Ingredient",
                "IngredientInput", "ItemStack", "JsonArray", "JsonObject", "SizedFluidIngredient", "SizedIngredientJS"),
                List.copyOf(references.keySet()));
        Map<String, Class<?>> expected = Map.of(
                "CompoundTag", CompoundTag.class, "EntityType", EntityType.class, "ItemStack", ItemStack.class,
                "Ingredient", Ingredient.class, "IngredientInput", Ingredient.class,
                "FluidIngredientJS", com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS.class);
        expected.forEach((name, type) -> assertEquals(type, references.get(name).targetType(), name));
        assertTrue(references.get("IngredientInput").input());
        assertFalse(references.get("Ingredient").input());
        assertTrue(entries.stream().filter(entry -> entry.id().equals("nekojs.entity-goal"))
                .findFirst().orElseThrow().references().containsKey("EntityType"));
        assertFalse(entries.stream().filter(entry -> entry.id().equals("nekojs.recipe-filter"))
                .findFirst().orElseThrow().references().containsKey("EntityType"), "Startup dependencies stay scoped");
    }
}
//?}
