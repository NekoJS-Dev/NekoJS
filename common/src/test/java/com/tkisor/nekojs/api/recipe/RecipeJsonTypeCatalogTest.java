package com.tkisor.nekojs.api.recipe;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeJsonTypeCatalogTest {

    @Test
    void defaultCatalogIsEmpty() {
        assertTrue(RecipeJsonTypeCatalog.types("anything").isEmpty());
    }

    @Test
    void setCatalogThenQueryByNamespace() {
        RecipeJsonTypeCatalog.setCatalog(Map.of(
                "thermalfoundation", Set.of("machine", "alloy"),
                "forestry", Set.of("carpenter")));
        assertEquals(Set.of("machine", "alloy"), RecipeJsonTypeCatalog.types("thermalfoundation"));
        assertEquals(Set.of("carpenter"), RecipeJsonTypeCatalog.types("forestry"));
        assertTrue(RecipeJsonTypeCatalog.types("unknown").isEmpty());
    }

    @Test
    void replaceCatalogClearsOldEntries() {
        RecipeJsonTypeCatalog.setCatalog(Map.of("moda", Set.of("x")));
        RecipeJsonTypeCatalog.setCatalog(Map.of("modb", Set.of("y")));
        assertTrue(RecipeJsonTypeCatalog.types("moda").isEmpty());
        assertEquals(Set.of("y"), RecipeJsonTypeCatalog.types("modb"));
    }
}
