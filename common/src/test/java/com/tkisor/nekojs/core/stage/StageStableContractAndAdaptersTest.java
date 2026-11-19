package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.api.stage.StableStageChangeEvent;
import com.tkisor.nekojs.api.stage.StableStageGatesEvent;
import com.tkisor.nekojs.api.stage.StageCustomizeEvent;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageDefinitionBuilder;
import com.tkisor.nekojs.api.stage.StageEvents;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.api.stage.StageTickContext;
import com.tkisor.nekojs.api.stage.StagesFacade;
import com.tkisor.nekojs.api.stage.gate.BlockGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ContainerGateBuilder;
import com.tkisor.nekojs.api.stage.gate.DimensionGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EffectGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EnchantGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EntityGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ItemGateBuilder;
import com.tkisor.nekojs.api.stage.gate.LootGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RecipeGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RegionGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RegionShape;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.api.stage.gate.StageGateKind;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-common contract, portability, and GraalJS type-adapter tests for {@code com.tkisor.nekojs.api.stage},
 * {@link ModulePublicTypes}, {@link NekoIdAdapter}, {@link StagePlayerRefAdapter}, and
 * {@link StageNotifyOptionsAdapter}.
 */
class StageStableContractAndAdaptersTest {

    private static final List<String> FORBIDDEN_NATIVE_PREFIXES = List.of(
            "net.minecraft.",
            "net.neoforged.",
            "net.fabricmc."
    );

    private static final List<Class<?>> ALL_API_STAGE_CLASSES = List.of(
            StableStageChangeEvent.class,
            StableStageGatesEvent.class,
            StageCustomizeEvent.class,
            StageDefinition.class,
            StageDefinitionBuilder.class,
            StageEvents.class,
            StageHandle.class,
            StageHandleResolver.class,
            StageNotifyOptions.class,
            StagePlayerRef.class,
            StageReadView.class,
            StageScope.class,
            StageTickContext.class,
            StagesFacade.class,
            BlockGateBuilder.class,
            ContainerGateBuilder.class,
            DimensionGateBuilder.class,
            EffectGateBuilder.class,
            EnchantGateBuilder.class,
            EntityGateBuilder.class,
            ItemGateBuilder.class,
            LootGateBuilder.class,
            RecipeGateBuilder.class,
            RegionGateBuilder.class,
            RegionShape.class,
            RegionShape.Mode.class,
            StageGateDeclaration.class,
            StageGateDeclaration.ItemGate.class,
            StageGateDeclaration.BlockGate.class,
            StageGateDeclaration.RecipeGate.class,
            StageGateDeclaration.ContainerGate.class,
            StageGateDeclaration.DimensionGate.class,
            StageGateDeclaration.RegionGate.class,
            StageGateDeclaration.EntityGate.class,
            StageGateDeclaration.EffectGate.class,
            StageGateDeclaration.EnchantGate.class,
            StageGateDeclaration.LootGate.class,
            StageGateKind.class
    );

    private record MockPlatformPlayer(String uuid, String name, boolean fake, boolean clientSide) {
    }

    @BeforeAll
    static void installMockPlatformPlayerResolver() {
        StagePlayerRefAdapter.registerHostResolver(host -> {
            if (!(host instanceof MockPlatformPlayer mock)) {
                return null;
            }
            if (mock.fake()) {
                return StagePlayerRef.fake(mock.name());
            }
            return mock.clientSide()
                    ? StagePlayerRef.client(mock.uuid(), mock.name())
                    : StagePlayerRef.of(mock.uuid(), mock.name());
        });
    }

    @Test
    void stagesFacadeAndStageHandlePassModulePublicTypesStableValidation() {
        assertDoesNotThrow(() -> ModulePublicTypes.validateStable(StagesFacade.class, false));
        assertDoesNotThrow(() -> ModulePublicTypes.validateStable(StageHandle.class, false));

        for (Class<?> apiType : ALL_API_STAGE_CLASSES) {
            assertDoesNotThrow(
                    () -> ModulePublicTypes.validateStable(apiType, false),
                    () -> "Instance surface must be portable for " + apiType.getName()
            );
            assertDoesNotThrow(
                    () -> ModulePublicTypes.validateStable(apiType, true),
                    () -> "Static surface must be portable for " + apiType.getName()
            );
        }
    }

    @Test
    void everyApiStageClassAndSourceHasZeroMinecraftNeoForgeOrFabricReferences() throws IOException {
        List<String> reflectionViolations = new ArrayList<>();
        for (Class<?> cls : ALL_API_STAGE_CLASSES) {
            inspectClassSignatures(cls, reflectionViolations);
        }
        assertEquals(List.of(), reflectionViolations,
                "Reflected API stage classes must not reference Minecraft or loader packages");

        Path stageApiDir = locateStageApiSourceDir();
        if (stageApiDir != null) {
            List<Path> sourceFiles;
            try (Stream<Path> walk = Files.walk(stageApiDir)) {
                sourceFiles = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            assertFalse(sourceFiles.isEmpty(), "Expected stage API Java sources under " + stageApiDir);

            List<String> sourceViolations = new ArrayList<>();
            for (Path file : sourceFiles) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                        continue;
                    }
                    for (String prefix : FORBIDDEN_NATIVE_PREFIXES) {
                        if (trimmed.contains(prefix)) {
                            sourceViolations.add(file.getFileName() + ":" + (i + 1) + ": " + trimmed);
                        }
                    }
                }
            }
            assertEquals(List.of(), sourceViolations,
                    "Stage API source files must contain zero Minecraft, NeoForge, or Fabric references");
        }
    }

    @Test
    void nekoIdAdapterConvertsQualifiedUnqualifiedAndTagIdsAndRejectsMalformedSyntax() {
        NekoIdAdapter adapter = new NekoIdAdapter();
        assertEquals(NekoId.class, adapter.getTargetClass());
        assertFalse(adapter.inputShapes().isEmpty());

        Value qualified = Value.asValue("minecraft:diamond_sword");
        assertTrue(adapter.test(qualified));
        assertEquals(NekoId.of("minecraft", "diamond_sword"), adapter.apply(qualified));

        Value unqualified = Value.asValue("diamond");
        assertTrue(adapter.test(unqualified));
        assertEquals(NekoId.of(NekoId.DEFAULT_NAMESPACE, "diamond"), adapter.apply(unqualified));

        Value prefixedTag = Value.asValue("#c:ingots/iron");
        assertTrue(adapter.test(prefixedTag));
        assertEquals(NekoId.of("c", "ingots/iron"), adapter.apply(prefixedTag));

        NekoId hostId = NekoId.of("mekanism", "configurator");
        Value hostVal = Value.asValue(hostId);
        assertTrue(adapter.test(hostVal));
        assertSame(hostId, adapter.apply(hostVal));

        for (String invalid : new String[]{"", "minecraft:", ":stone", "##c:ingots/iron", "Minecraft:Diamond", "a:b:c"}) {
            Value badVal = Value.asValue(invalid);
            assertFalse(adapter.test(badVal), "Adapter test() must reject invalid resource ID: " + invalid);
            ValueConversionException ex = assertThrows(
                    ValueConversionException.class,
                    () -> adapter.apply(badVal)
            );
            assertTrue(ex.getMessage().contains("[NEKO-4056]"), ex.getMessage());
        }

        assertFalse(adapter.test(null));
        assertThrows(ValueConversionException.class, () -> adapter.apply(null));
    }

    @Test
    void stagePlayerRefAdapterResolvesUuidStringsStablePlayerEventsAndCustomHostPlayers() {
        StagePlayerRefAdapter adapter = new StagePlayerRefAdapter();
        assertEquals(StagePlayerRef.class, adapter.getTargetClass());

        String uuid = "00000000-0000-0000-0000-000000000007";
        Value uuidValue = Value.asValue("  " + uuid + "  ");
        assertTrue(adapter.test(uuidValue));
        assertEquals(new StagePlayerRef(uuid, "", false, false), adapter.apply(uuidValue));

        Value blankUuid = Value.asValue("   ");
        assertFalse(adapter.test(blankUuid));
        ValueConversionException blankEx = assertThrows(
                ValueConversionException.class,
                () -> adapter.apply(blankUuid)
        );
        assertTrue(blankEx.getMessage().contains("[NEKO-4070]"), blankEx.getMessage());

        StablePlayerEvent playerEvent = new StablePlayerEvent("Alex", uuid, "minecraft:overworld", 10.0, 64.0, -5.0);
        Value eventVal = Value.asValue(playerEvent);
        assertTrue(adapter.test(eventVal));
        assertEquals(StagePlayerRef.of(uuid, "Alex"), adapter.apply(eventVal));

        StablePlayerItemEvent itemEvent = new StablePlayerItemEvent("Alex", uuid, "minecraft:diamond", 1);
        assertEquals(StagePlayerRef.of(uuid, "Alex"), adapter.apply(Value.asValue(itemEvent)));

        StablePlayerChatEvent chatEvent = new StablePlayerChatEvent("Alex", uuid, "hello");
        assertEquals(StagePlayerRef.of(uuid, "Alex"), adapter.apply(Value.asValue(chatEvent)));

        StablePlayerAdvancementEvent advEvent =
                new StablePlayerAdvancementEvent("Alex", uuid, "minecraft:story/root");
        assertEquals(StagePlayerRef.of(uuid, "Alex"), adapter.apply(Value.asValue(advEvent)));

        StablePlayerDimensionChangeEvent dimEvent =
                new StablePlayerDimensionChangeEvent("Alex", uuid, "minecraft:overworld", "minecraft:the_nether");
        assertEquals(StagePlayerRef.of(uuid, "Alex"), adapter.apply(Value.asValue(dimEvent)));

        StablePlayerEvent missingUuidEvent = new StablePlayerEvent("Ghost", "", "minecraft:overworld", 0.0, 64.0, 0.0);
        Value missingUuidVal = Value.asValue(missingUuidEvent);
        assertFalse(adapter.test(missingUuidVal));
        ValueConversionException missingUuidEx = assertThrows(
                ValueConversionException.class,
                () -> adapter.apply(missingUuidVal)
        );
        assertTrue(missingUuidEx.getMessage().contains("[NEKO-4070]"), missingUuidEx.getMessage());

        // Custom host resolver registered in @BeforeAll
        Value serverHostPlayer = Value.asValue(new MockPlatformPlayer(uuid, "ServerSteve", false, false));
        assertTrue(adapter.test(serverHostPlayer));
        assertEquals(StagePlayerRef.of(uuid, "ServerSteve"), adapter.apply(serverHostPlayer));

        Value clientHostPlayer = Value.asValue(new MockPlatformPlayer(uuid, "ClientSteve", false, true));
        assertTrue(adapter.test(clientHostPlayer));
        assertEquals(StagePlayerRef.client(uuid, "ClientSteve"), adapter.apply(clientHostPlayer));

        Value fakeHostPlayer = Value.asValue(new MockPlatformPlayer("", "Deployer", true, false));
        assertTrue(adapter.test(fakeHostPlayer));
        StagePlayerRef resolvedFake = adapter.apply(fakeHostPlayer);
        assertTrue(resolvedFake.fake());
        assertEquals("Deployer", resolvedFake.name());

        Value unrelatedHost = Value.asValue(new Object());
        assertFalse(adapter.test(unrelatedHost));
        assertThrows(ValueConversionException.class, () -> adapter.apply(unrelatedHost));
    }

    @Test
    void stageNotifyOptionsAdapterConvertsJsObjectsMapsAndHostInstancesAndRejectsInvalidShapes() {
        StageNotifyOptionsAdapter adapter = new StageNotifyOptionsAdapter();
        assertEquals(StageNotifyOptions.class, adapter.getTargetClass());

        Value hostPreset = Value.asValue(StageNotifyOptions.SILENT);
        assertTrue(adapter.test(hostPreset));
        assertSame(StageNotifyOptions.SILENT, adapter.apply(hostPreset));

        Map<String, Object> hostMap = new LinkedHashMap<>();
        hostMap.put("title", true);
        hostMap.put("chat", false);
        Value hostMapVal = Value.asValue(hostMap);
        assertTrue(adapter.test(hostMapVal));
        StageNotifyOptions fromHostMap = adapter.apply(hostMapVal);
        assertNull(fromHostMap.silent());
        assertEquals(Boolean.TRUE, fromHostMap.title());
        assertEquals(Boolean.FALSE, fromHostMap.chat());
        assertNull(fromHostMap.actionBar());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value jsObj = context.eval("js", "({ silent: false, title: true, actionBar: true })");
            assertTrue(adapter.test(jsObj));
            StageNotifyOptions parsed = adapter.apply(jsObj);
            assertEquals(Boolean.FALSE, parsed.silent());
            assertEquals(Boolean.TRUE, parsed.title());
            assertNull(parsed.chat());
            assertEquals(Boolean.TRUE, parsed.actionBar());

            StageDefinition defWithChat = new StageCustomizeEvent()
                    .stage("tech_age")
                    .showTitle(false)
                    .showChat(true)
                    .showActionBar(false)
                    .toDefinition();
            assertTrue(parsed.showTitle(defWithChat));
            assertTrue(parsed.showChat(defWithChat), "Null chat override must inherit StageDefinition.showChat()");
            assertTrue(parsed.showActionBar(defWithChat));

            Value emptyObj = context.eval("js", "({})");
            assertTrue(adapter.test(emptyObj));
            assertEquals(StageNotifyOptions.DEFAULT, adapter.apply(emptyObj));

            Value proxyObj = context.asValue(ProxyObject.fromMap(Map.of("silent", true)));
            assertTrue(adapter.test(proxyObj));
            assertEquals(Boolean.TRUE, adapter.apply(proxyObj).silent());
            assertFalse(adapter.apply(proxyObj).showChat(defWithChat));

            Value unknownKey = context.eval("js", "({ title: true, unknownFlag: false })");
            assertFalse(adapter.test(unknownKey));
            assertThrows(ValueConversionException.class, () -> adapter.apply(unknownKey));

            Value nonBooleanProp = context.eval("js", "({ chat: 'loud' })");
            assertFalse(adapter.test(nonBooleanProp));
            assertThrows(ValueConversionException.class, () -> adapter.apply(nonBooleanProp));

            Value jsArray = context.eval("js", "[true, false]");
            assertFalse(adapter.test(jsArray));
            assertThrows(ValueConversionException.class, () -> adapter.apply(jsArray));
        }
    }

    private static void inspectClassSignatures(Class<?> cls, List<String> violations) {
        Set<Type> visited = new HashSet<>();
        checkTypeTree(cls.getName() + " (superclass)", cls.getGenericSuperclass(), visited, violations);
        for (Type iface : cls.getGenericInterfaces()) {
            checkTypeTree(cls.getName() + " (interface)", iface, visited, violations);
        }
        for (Field field : cls.getDeclaredFields()) {
            checkTypeTree(cls.getName() + "#" + field.getName(), field.getGenericType(), visited, violations);
        }
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            for (Type param : ctor.getGenericParameterTypes()) {
                checkTypeTree(cls.getName() + ".<init>", param, visited, violations);
            }
        }
        for (Method method : cls.getDeclaredMethods()) {
            checkTypeTree(cls.getName() + "#" + method.getName() + " return",
                    method.getGenericReturnType(), visited, violations);
            for (Type param : method.getGenericParameterTypes()) {
                checkTypeTree(cls.getName() + "#" + method.getName() + " param", param, visited, violations);
            }
        }
        if (cls.isRecord()) {
            for (RecordComponent component : cls.getRecordComponents()) {
                checkTypeTree(cls.getName() + "." + component.getName(),
                        component.getGenericType(), visited, violations);
            }
        }
    }

    private static void checkTypeTree(String site, Type type, Set<Type> visited, List<String> violations) {
        if (type == null || !visited.add(type)) {
            return;
        }
        String typeName = type.getTypeName();
        for (String forbidden : FORBIDDEN_NATIVE_PREFIXES) {
            if (typeName.contains(forbidden)) {
                violations.add(site + " -> " + typeName);
            }
        }
        if (type instanceof Class<?> concrete && concrete.isArray()) {
            checkTypeTree(site, concrete.getComponentType(), visited, violations);
        } else if (type instanceof ParameterizedType parameterized) {
            checkTypeTree(site, parameterized.getRawType(), visited, violations);
            for (Type arg : parameterized.getActualTypeArguments()) {
                checkTypeTree(site, arg, visited, violations);
            }
        } else if (type instanceof GenericArrayType arrayType) {
            checkTypeTree(site, arrayType.getGenericComponentType(), visited, violations);
        } else if (type instanceof WildcardType wildcard) {
            for (Type upper : wildcard.getUpperBounds()) {
                checkTypeTree(site, upper, visited, violations);
            }
            for (Type lower : wildcard.getLowerBounds()) {
                checkTypeTree(site, lower, visited, violations);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                checkTypeTree(site, bound, visited, violations);
            }
        }
    }

    private static Path locateStageApiSourceDir() {
        List<Path> candidates = List.of(
                Path.of("src/main/java/com/tkisor/nekojs/api/stage"),
                Path.of("common/src/main/java/com/tkisor/nekojs/api/stage"),
                Path.of("../../common/src/main/java/com/tkisor/nekojs/api/stage")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
