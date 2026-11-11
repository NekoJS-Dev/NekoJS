package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventGroupPublicationTest {
    public record Payload(int count) {}
    public record View(int count) {}

    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void oneSourceDeclaresBothTargetsWithoutBindingAnOwnerAtDefinitionTime() {
        var group = EventGroup.of("OwnedEvents");
        var bus = group.server("changed", Payload.class, api -> {
            api.versioned();
            api.stable(View.class, event -> new View(event.count())).scriptTypes(ScriptType.SERVER);
        });
        group.server("legacyOnly", Payload.class);
        group.freeze();
        var first = new EventGroupRegistry.Impl().scopedTo("first");
        var second = new EventGroupRegistry.Impl().scopedTo("second");
        first.register(group);
        second.register(group);
        assertEquals(2, first.moduleGroups().size());
        var stable = publication(first, BindingExposure.Category.STABLE);
        var version = publication(first, BindingExposure.Category.VERSION);
        assertEquals("nekojs/api/stable/first", stable.moduleId());
        assertEquals("nekojs/api/stable/second", publication(second, BindingExposure.Category.STABLE).moduleId());
        assertEquals(Set.of("changed"), stable.events());
        assertEquals(Set.of("changed"), version.events());
        assertSame(bus, stable.group().getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(bus, version.group().getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(bus, first.view().get("OwnedEvents").getBusHolder("changed").getBus(ScriptType.SERVER));
        assertTrue(version.supports("changed", ScriptType.STARTUP));
        assertFalse(stable.supports("changed", ScriptType.STARTUP));
        assertEquals(new View(7), stable.views().get("changed").converter().apply(new Payload(7)));
    }

    @Test void separateSourcesKeepPerEventScopesWhenTheirModuleExportIsMerged() {
        var first = EventGroup.of("SplitEvents");
        var serverBus = first.server("serverChanged", Payload.class,
                api -> api.stable().scriptTypes(ScriptType.SERVER));
        var second = EventGroup.of("SplitEvents");
        var clientBus = second.client("clientChanged", Payload.class,
                api -> api.stable().scriptTypes(ScriptType.CLIENT));
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(first);
        registry.register(second);
        var publication = publication(registry, BindingExposure.Category.STABLE);
        assertEquals(1, registry.moduleGroups().size());
        assertTrue(publication.supports("serverChanged", ScriptType.SERVER));
        assertFalse(publication.supports("serverChanged", ScriptType.STARTUP));
        assertTrue(publication.supports("clientChanged", ScriptType.CLIENT));
        assertFalse(publication.supports("clientChanged", ScriptType.SERVER));
        assertSame(serverBus, publication.group().getBusHolder("serverChanged").getBus(ScriptType.SERVER));
        assertSame(clientBus, publication.group().getBusHolder("clientChanged").getBus(ScriptType.CLIENT));
        assertEquals(2, registry.sources().size());
    }

    @Test void mergingGroupsDoesNotExpandASourcesWildcardPublication() {
        var source = EventGroup.of("MergedEvents").expose(api -> api.versioned());
        var bus = source.server("published", Payload.class);
        var target = EventGroup.of("MergedEvents");
        target.server("legacyOnly", Payload.class);
        target.merge(source);
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(target);
        assertEquals(Set.of("published"), publication(registry, BindingExposure.Category.VERSION).events());
        assertSame(bus, target.getBusHolder("published").getBus(ScriptType.SERVER));
        var inherited = new EventGroupRegistry.Impl().scopedTo("example");
        inherited.inherit(registry);
        var additional = EventGroup.of("MergedEvents").expose(api -> api.versioned());
        additional.server("additional", Payload.class);
        inherited.register(additional);
        assertEquals(Set.of("published", "additional"), publication(inherited, BindingExposure.Category.VERSION).events());
        assertTrue(inherited.view().get("MergedEvents").viewBuses().containsKey("legacyOnly"));
    }

    @Test void anEmptySourceWildcardStaysEmptyAfterMerging() {
        var empty = EventGroup.of("EmptySourceEvents").expose(api -> api.versioned());
        var group = EventGroup.of("EmptySourceEvents");
        group.server("legacyOnly", Payload.class);
        group.merge(empty);
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertTrue(publication(registry, BindingExposure.Category.VERSION).events().isEmpty());
    }

    @Test void duplicateTargetsFailBeforeAddingTheBusAndRetainedBuildersAreFrozen() {
        var group = EventGroup.of("FrozenEvents");
        assertThrows(IllegalArgumentException.class, () -> group.server("empty", Payload.class, api -> {}));
        assertNull(group.getBusHolder("empty"));
        assertThrows(IllegalArgumentException.class, () -> group.server("invalid", Payload.class, api -> {
            api.stable();
            api.stable();
        }));
        assertNull(group.getBusHolder("invalid"));
        var retained = new AtomicReference<EventPublication<Payload>>();
        var scope = new AtomicReference<EventPublication<Payload>.Scope>();
        group.server("changed", Payload.class, api -> {
            retained.set(api);
            scope.set(api.stable());
        });
        assertThrows(IllegalArgumentException.class, () -> retained.get().versioned());
        assertThrows(IllegalArgumentException.class, () -> scope.get().scriptTypes(ScriptType.SERVER));
        group.freeze();
        assertThrows(IllegalStateException.class, () -> group.expose(api -> api.versioned()));
        assertThrows(IllegalStateException.class, () -> group.server("late", Payload.class, api -> api.stable()));
    }

    @Test void conflictingDeclarationsDoNotPublishHalfAGroup() {
        var group = EventGroup.of("DuplicateEvents");
        group.server("changed", Payload.class, api -> api.stable());
        group.expose(api -> api.stable().events("changed"));
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        assertThrows(IllegalArgumentException.class, () -> registry.register(group));
        assertTrue(registry.view().isEmpty());
        assertTrue(registry.moduleGroups().isEmpty());
        assertTrue(registry.sources().isEmpty());
    }

    private static ModuleEventGroup publication(EventGroupRegistry registry, BindingExposure.Category category) {
        return registry.moduleGroups().stream().filter(module -> module.category() == category).findFirst().orElseThrow();
    }
}
