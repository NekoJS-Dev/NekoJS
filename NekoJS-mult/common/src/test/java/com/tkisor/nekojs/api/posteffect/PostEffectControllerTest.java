package com.tkisor.nekojs.api.posteffect;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostEffectControllerTest {
    @Test
    void controllerDelegatesStateCommandsAndReportsUnsupportedRuntime() {
        FakeRendererAdapter adapter = new FakeRendererAdapter(PostEffectCapabilities.resourceOnly());
        PostEffectController controller = PostEffectController.backedBy(adapter);
        PostEffectDefinition resource = PostEffectDefinition.resource("minecraft:invert");
        PostEffectDefinition runtime = PostEffectDefinition.runtime("nekojs:runtime", "{\"passes\":[]}");
        PostEffectEventCatalogFixture fixture = new PostEffectEventCatalogFixture(resource, runtime);

        assertEquals(PostEffectCommandResult.Status.ACTIVATED, adapter.reconcile(fixture.catalog()).status());
        assertEquals("minecraft:invert", controller.current());
        assertTrue(controller.isActive());
        assertEquals(PostEffectCommandResult.Status.UNSUPPORTED,
                controller.activate("nekojs:runtime").status());
        assertEquals(PostEffectCommandResult.RUNTIME_PIPELINE_UNSUPPORTED,
                controller.activate("nekojs:runtime").code());
        assertEquals("minecraft:invert", controller.current(),
                "unsupported activation must not replace the active resource effect");

        assertEquals(PostEffectCommandResult.Status.CLEARED,
                controller.toggle("minecraft:invert").status());
        assertNull(controller.current());
        assertFalse(controller.isActive());
        assertEquals(PostEffectCommandResult.Status.ALREADY_CLEAR, controller.clear().status());
        assertEquals(PostEffectCapabilities.resourceOnly(), controller.capabilities());
    }

    @Test
    void reconcileInvalidatesCachesWhenDefinitionsDisappearOrCatalogIsCleared() {
        FakeRendererAdapter adapter = new FakeRendererAdapter(PostEffectCapabilities.resourceOnly());
        PostEffectDefinition resource = PostEffectDefinition.resource("minecraft:invert");
        PostEffectDefinition runtime = PostEffectDefinition.runtime("nekojs:runtime", "{\"passes\":[]}");
        PostEffectEventCatalogFixture fixture = new PostEffectEventCatalogFixture(resource, runtime);

        adapter.reconcile(fixture.catalog());
        int beforeRemoval = adapter.invalidations;
        adapter.reconcile(new PostEffectCatalog(Map.of("minecraft:invert", resource), null));
        assertTrue(adapter.invalidations > beforeRemoval,
                "removing a runtime definition must invalidate cached renderer resources");

        int beforeEmpty = adapter.invalidations;
        adapter.reconcile(PostEffectCatalog.empty());
        assertTrue(adapter.invalidations > beforeEmpty,
                "reconciling an empty catalog must invalidate cached resources");
        assertNull(adapter.current());
        assertFalse(adapter.isActive());
    }

    private record PostEffectEventCatalogFixture(
            PostEffectDefinition resource,
            PostEffectDefinition runtime
    ) {
        PostEffectCatalog catalog() {
            return new PostEffectCatalog(
                    Map.of(resource.id(), resource, runtime.id(), runtime), resource.id());
        }
    }

    private static final class FakeRendererAdapter implements PostEffectRendererAdapter {
        private final PostEffectCapabilities capabilities;
        private PostEffectCatalog catalog = PostEffectCatalog.empty();
        private String current;
        private int invalidations;
        private final List<String> calls = new ArrayList<>();

        private FakeRendererAdapter(PostEffectCapabilities capabilities) {
            this.capabilities = capabilities;
        }

        @Override
        public PostEffectCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public PostEffectCommandResult reconcile(PostEffectCatalog next) {
            PostEffectCatalog previous = catalog;
            boolean removedDefinition = previous.definitions().keySet().stream()
                    .anyMatch(id -> !next.definitions().containsKey(id));
            if (removedDefinition || next.isEmpty()) {
                invalidate();
            }
            catalog = next;
            if (current != null && !next.contains(current)) {
                return clear();
            }
            if (next.desiredActiveId() != null) {
                return activate(next.desiredActiveId());
            }
            return current == null ? PostEffectCommandResult.alreadyClear()
                    : PostEffectCommandResult.activated(current);
        }

        @Override
        public PostEffectCommandResult activate(String id) {
            PostEffectDefinition definition = catalog.definition(id);
            if (definition == null) return PostEffectCommandResult.notFound(id);
            if (!capabilities.supports(definition)) {
                return definition.isRuntime()
                        ? PostEffectCommandResult.unsupportedRuntime(definition.id())
                        : PostEffectCommandResult.unsupportedResource(definition.id());
            }
            current = definition.id();
            calls.add("activate:" + current);
            return PostEffectCommandResult.activated(current);
        }

        @Override
        public PostEffectCommandResult clear() {
            if (current == null) return PostEffectCommandResult.alreadyClear();
            calls.add("clear:" + current);
            current = null;
            return PostEffectCommandResult.cleared();
        }

        @Override
        public PostEffectCommandResult toggle(String id) {
            PostEffectDefinition definition = catalog.definition(id);
            if (definition == null) return PostEffectCommandResult.notFound(id);
            if (definition.id().equals(current)) return clear();
            return activate(definition.id());
        }

        @Override
        @Nullable
        public String current() {
            return current;
        }

        @Override
        public boolean isActive() {
            return current != null;
        }

        @Override
        public void invalidate() {
            invalidations++;
            calls.add("invalidate");
        }
    }
}
