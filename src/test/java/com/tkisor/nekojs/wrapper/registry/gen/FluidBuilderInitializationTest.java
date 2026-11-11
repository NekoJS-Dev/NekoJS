//? if neoforge && >=26.1 {
package com.tkisor.nekojs.wrapper.registry.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidBuilderInitializationTest {
    private static final class CustomFluidBuilder extends FluidBuilder {
        private final Identifier flowingId = Identifier.parse("nekojs:custom_flowing");
        private final Identifier bucketId = Identifier.parse("nekojs:custom_bucket");
        private int idReads;

        private CustomFluidBuilder() {
            super(Identifier.parse("nekojs:fluid_initialization"));
        }

        @Override
        public Identifier getFlowingId() {
            assertNotNull(flowingId, "Derived IDs must not be read before subclass initialization");
            idReads++;
            return flowingId;
        }

        @Override
        public Identifier getBucketId() {
            assertNotNull(bucketId, "Derived IDs must not be read before subclass initialization");
            idReads++;
            return bucketId;
        }
    }

    @Test
    void additionalBuildersUseInitializedOverridesAndRetainTheirSuppliers() {
        var builder = new CustomFluidBuilder();
        assertEquals(0, builder.idReads);
        List<String> ids = new ArrayList<>();
        List<Supplier<?>> first = new ArrayList<>();
        builder.handleAdditionalObjects((registry, id, supplier) -> {
            ids.add(id.toString());
            first.add(supplier);
        });
        assertEquals(List.of("nekojs:custom_flowing", "nekojs:fluid_initialization",
                "nekojs:custom_bucket", "nekojs:fluid_initialization"), ids);
        assertEquals(2, builder.idReads);

        List<Supplier<?>> second = new ArrayList<>();
        builder.handleAdditionalObjects((registry, id, supplier) -> second.add(supplier));
        for (int index = 0; index < 3; index++) assertSame(first.get(index), second.get(index));
        assertEquals(2, builder.idReads);
        builder.noBlock();
        builder.noBucket();
        List<String> reduced = new ArrayList<>();
        builder.handleAdditionalObjects((registry, id, supplier) -> reduced.add(id.toString()));
        assertEquals(List.of("nekojs:custom_flowing", "nekojs:fluid_initialization"), reduced);
    }
}
//?}
