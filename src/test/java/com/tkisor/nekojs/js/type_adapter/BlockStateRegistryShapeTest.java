package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockStateRegistryShapeTest {
    @Test
    void blockStateIdsUseTheBlockCatalogAndRetainPropertyStrings() {
        var shapes = new BlockStateAdapter().inputShapes();
        assertTrue(shapes.contains(AdapterInputShape.registry("Block")), shapes.toString());
        assertFalse(shapes.contains(AdapterInputShape.registry("BlockState")), shapes.toString());
        assertTrue(shapes.contains(AdapterInputShape.string()), shapes.toString());
    }
}
