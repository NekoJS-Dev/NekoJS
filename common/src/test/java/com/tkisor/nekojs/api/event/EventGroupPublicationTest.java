package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class EventGroupPublicationTest {
    public record Payload(int count) {}
    public record View(int count) {}

    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void explicitEventsUseDedicatedOfficialAndPluginModuleNamespaces() {
        var group = EventGroup.of("OwnedEvents");
        group.server("changed", Payload.class, api -> {
            api.versioned();
            api.stable();
        });
        var official = new EventGroupRegistry.Impl().scopedTo("nekojs");
        official.register(group);
        assertEquals("nekojs:event/stable", publication(official, BindingExposure.Category.STABLE).moduleId());
        assertEquals("nekojs:event/versioned", publication(official, BindingExposure.Category.VERSION).moduleId());
        var plugin = new EventGroupRegistry.Impl().scopedTo("events");
        plugin.register(group);
        assertEquals("nekojs:event/stable/events", publication(plugin, BindingExposure.Category.STABLE).moduleId());
        assertEquals("nekojs:event/versioned/events", publication(plugin, BindingExposure.Category.VERSION).moduleId());
    }

    @Test void differentOwnersCanPublishTheSameGroupAndEventNames() {
        var registry = new EventGroupRegistry.Impl();
        var official = EventGroup.of("SharedEvents");
        var officialBus = official.server("changed", Payload.class, api -> api.versioned());
        var plugin = EventGroup.of("SharedEvents");
        var pluginBus = plugin.server("changed", View.class, api -> api.versioned());
        registry.scopedTo("nekojs").register(official);
        assertDoesNotThrow(() -> registry.scopedTo("example").register(plugin));
        assertEquals(2, registry.view().size());
        assertEquals(2, registry.moduleGroups().size());
        assertSame(officialBus, registry.view().get("nekojs:SharedEvents").getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(pluginBus, registry.view().get("example:SharedEvents").getBusHolder("changed").getBus(ScriptType.SERVER));
    }

    @Test void oneSourceDeclaresBothTargetsWithoutBindingAnOwnerAtDefinitionTime() {
        var group = EventGroup.of("OwnedEvents");
        var bus = group.server("changed", Payload.class, api -> {
            api.versioned();
            api.stable().view(View.class, event -> new View(event.count())).scriptTypes(ScriptType.SERVER);
        });
        group.add("legacyOnly", ScriptType.SERVER, EventBusJS.of(Payload.class));
        group.freeze();
        var first = new EventGroupRegistry.Impl().scopedTo("first");
        var second = new EventGroupRegistry.Impl().scopedTo("second");
        first.register(group);
        second.register(group);
        assertEquals(2, first.moduleGroups().size());
        var stable = publication(first, BindingExposure.Category.STABLE);
        var version = publication(first, BindingExposure.Category.VERSION);
        assertEquals("nekojs:event/stable/first", stable.moduleId());
        assertEquals("nekojs:event/stable/second", publication(second, BindingExposure.Category.STABLE).moduleId());
        assertEquals(Set.of("changed"), stable.events());
        assertEquals(Set.of("changed"), version.events());
        assertSame(bus, stable.group().getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(bus, version.group().getBusHolder("changed").getBus(ScriptType.SERVER));
        assertSame(bus, first.view().get("first:OwnedEvents").getBusHolder("changed").getBus(ScriptType.SERVER));
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

    @Test void mergingGroupsOnlyPublishesExplicitlyDeclaredEvents() {
        var source = EventGroup.of("MergedEvents");
        var bus = source.server("published", Payload.class, api -> api.versioned());
        var target = EventGroup.of("MergedEvents");
        target.add("legacyOnly", ScriptType.SERVER, EventBusJS.of(Payload.class));
        target.merge(source);
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(target);
        assertEquals(Set.of("published"), publication(registry, BindingExposure.Category.VERSION).events());
        assertSame(bus, target.getBusHolder("published").getBus(ScriptType.SERVER));
        var inherited = new EventGroupRegistry.Impl().scopedTo("example");
        inherited.inherit(registry);
        var additional = EventGroup.of("MergedEvents");
        additional.server("additional", Payload.class, api -> api.versioned());
        inherited.register(additional);
        assertEquals(Set.of("published", "additional"), publication(inherited, BindingExposure.Category.VERSION).events());
        assertTrue(inherited.view().get("example:MergedEvents").viewBuses().containsKey("legacyOnly"));
    }

    @Test void sourceOnlyGroupsDoNotCreateModuleExports() {
        var empty = EventGroup.of("EmptySourceEvents");
        var group = EventGroup.of("EmptySourceEvents");
        group.add("legacyOnly", ScriptType.SERVER, EventBusJS.of(Payload.class));
        group.merge(empty);
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertTrue(registry.moduleGroups().isEmpty());
    }

    @Test void addWithoutConfigurationKeepsPlainAndKeyedBusesPrivateWithoutAnOwner() {
        var group = EventGroup.of("PrivateEvents");
        for (ScriptType type : new ScriptType[]{ScriptType.SERVER, ScriptType.CLIENT, ScriptType.STARTUP}) {
            var plain = EventBusJS.of(Payload.class);
            var keyed = EventBusJS.of(Payload.class, false, DispatchKey.<Payload>string());
            assertSame(plain, group.add(type.name() + "Plain", type, plain));
            assertSame(keyed, group.add(type.name() + "Keyed", type, keyed));
            assertSame(plain, group.getBusHolder(type.name() + "Plain").getBus(type));
            assertSame(keyed, group.getBusHolder(type.name() + "Keyed").getBus(type));
        }
        var registry = new EventGroupRegistry.Impl();
        registry.register(group);
        assertTrue(registry.moduleGroups().isEmpty());
        assertEquals(6, registry.view().get("PrivateEvents").viewBuses().size());
        assertEquals(java.util.List.of(group), registry.sources());
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
        assertThrows(IllegalStateException.class, () -> group.server("late", Payload.class, api -> api.stable()));
    }

    @Test void conflictingDeclarationsDoNotPublishHalfAGroup() {
        var group = EventGroup.of("DuplicateEvents");
        group.server("changed", Payload.class, api -> api.stable());
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        var conflicting = EventGroup.of("DuplicateEvents");
        conflicting.server("changed", Payload.class, api -> api.versioned());
        conflicting.server("additional", Payload.class, api -> api.stable());
        assertThrows(IllegalArgumentException.class, () -> registry.register(conflicting));
        assertEquals(1, registry.view().size());
        assertEquals(1, registry.moduleGroups().size());
        assertEquals(Set.of("changed"), publication(registry, BindingExposure.Category.STABLE).events());
        assertFalse(registry.view().get("example:DuplicateEvents").viewBuses().containsKey("additional"));
        assertEquals(1, registry.sources().size());
    }

    @Test void allUnconfiguredShortcutsPublishVersionedWithoutChangingTheirSourceBuses() {
        var group = EventGroup.of("DefaultEvents");
        Map<String, EventBusJS<?, ?>> buses = Map.of(
                "serverPlain", group.server("serverPlain", Payload.class),
                "serverKeyed", group.server("serverKeyed", Payload.class, DispatchKey.string()),
                "clientPlain", group.client("clientPlain", Payload.class),
                "clientKeyed", group.client("clientKeyed", Payload.class, DispatchKey.string()),
                "startupPlain", group.startup("startupPlain", Payload.class),
                "startupKeyed", group.startup("startupKeyed", Payload.class, DispatchKey.string()));
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertEquals(1, registry.moduleGroups().size());
        var versioned = publication(registry, BindingExposure.Category.VERSION);
        assertEquals(buses.keySet(), versioned.events());
        assertTrue(versioned.views().isEmpty(), "default versioned publications keep native payloads");
        for (var entry : buses.entrySet()) {
            ScriptType sourceType = entry.getKey().startsWith("server") ? ScriptType.SERVER
                    : entry.getKey().startsWith("client") ? ScriptType.CLIENT : ScriptType.STARTUP;
            assertSame(entry.getValue(), versioned.group().getBusHolder(entry.getKey()).getBus(sourceType));
            assertEquals(Payload.class, entry.getValue().eventType());
            assertEquals(entry.getKey().endsWith("Keyed"), entry.getValue().canDispatch());
            for (ScriptType target : ScriptType.all()) {
                assertEquals(group.getBusHolder(entry.getKey()).canApplyOn(target), versioned.supports(entry.getKey(), target));
            }
        }
    }

    @Test void configuredShortcutsPublishOnlyTheSelectedStableTarget() {
        var group = EventGroup.of("ConfiguredEvents");
        group.server("serverPlain", Payload.class, api -> api.stable());
        group.server("serverKeyed", Payload.class, DispatchKey.string(), api -> api.stable());
        group.client("clientPlain", Payload.class, api -> api.stable());
        group.client("clientKeyed", Payload.class, DispatchKey.string(), api -> api.stable());
        group.startup("startupPlain", Payload.class, api -> api.stable());
        group.startup("startupKeyed", Payload.class, DispatchKey.string(), api -> api.stable());
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertEquals(1, registry.moduleGroups().size(), "configured calls must not add a default versioned publication");
        var stable = publication(registry, BindingExposure.Category.STABLE);
        assertEquals(group.viewBuses().keySet(), stable.events());
        assertTrue(stable.views().isEmpty(), "stable identity publication does not need a converter");
    }

    public interface CountView { int getCount(); }
    public record CovariantView(int count) implements CountView {
        @Override public int getCount() { return count; }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eitherTargetAcceptsContravariantConvertersAndCovariantViews(boolean stable) {
        Function<Object, CovariantView> converter = value -> new CovariantView(((Payload) value).count());
        var group = EventGroup.of("VarianceEvents");
        group.server("changed", Payload.class, api -> {
            var target = stable ? api.stable() : api.versioned();
            target.scriptTypes(ScriptType.SERVER).view(CountView.class, converter);
        });
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertEquals(1, registry.moduleGroups().size());
        var module = registry.moduleGroups().getFirst();
        assertEquals(stable ? BindingExposure.Category.STABLE : BindingExposure.Category.VERSION, module.category());
        assertEquals(Set.of(ScriptType.SERVER), module.scriptTypes());
        var view = module.views().get("changed");
        assertEquals(CountView.class, view.type());
        assertEquals(new CovariantView(13), view.converter().apply(new Payload(13)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void viewsRejectDuplicateNullAndFrozenConfigurationBeforeAddingABus(boolean stable) {
        var group = EventGroup.of("InvalidViewEvents");
        assertThrows(IllegalArgumentException.class, () -> group.server("duplicate", Payload.class, api -> {
            var target = stable ? api.stable() : api.versioned();
            target.view(View.class, event -> new View(event.count()));
            target.view(View.class, event -> new View(event.count()));
        }));
        assertNull(group.getBusHolder("duplicate"));
        assertThrows(NullPointerException.class, () -> group.server("nullType", Payload.class, api -> {
            var target = stable ? api.stable() : api.versioned();
            target.view(null, event -> new View(event.count()));
        }));
        assertNull(group.getBusHolder("nullType"));
        assertThrows(NullPointerException.class, () -> group.server("nullConverter", Payload.class, api -> {
            var target = stable ? api.stable() : api.versioned();
            target.view(View.class, null);
        }));
        assertNull(group.getBusHolder("nullConverter"));
        var retained = new AtomicReference<EventPublication<Payload>.Scope>();
        group.server("changed", Payload.class, api -> retained.set(stable ? api.stable() : api.versioned()));
        assertThrows(IllegalArgumentException.class,
                () -> retained.get().view(View.class, event -> new View(event.count())));
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        registry.register(group);
        assertEquals(Set.of("changed"), registry.moduleGroups().getFirst().events());
        assertTrue(registry.moduleGroups().getFirst().views().isEmpty());
    }

    private static ModuleEventGroup publication(EventGroupRegistry registry, BindingExposure.Category category) {
        return registry.moduleGroups().stream().filter(module -> module.category() == category).findFirst().orElseThrow();
    }
}
