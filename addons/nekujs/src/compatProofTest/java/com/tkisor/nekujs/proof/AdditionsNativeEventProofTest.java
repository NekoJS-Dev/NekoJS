package com.tkisor.nekujs.proof;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.NekuJSModListTestFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unchanged-jar evaluator/ABI proof only. The player is deliberately null: no
 * Minecraft world, real respawn hook, loader discovery, or gameplay is exercised.
 */
class AdditionsNativeEventProofTest {
    private static final String FIXTURE = "kubejsadditions-neoforge-1.21.1-6.0.0.jar";
    private static final String PLUGIN = "pie.ilikepiefoo.AdditionsPlugin";
    private static final String EVENTS = "pie.ilikepiefoo.events.AdditionalEvents";
    private static final String WRAPPER = "pie.ilikepiefoo.events.PlayerRespawnEventJS";

    @TempDir
    static Path gameDirectory;

    @BeforeAll
    static void initializeEvaluatorArrangement() {
        NekuJSModListTestFixture.initializeAbsentOptionalMods();
        assertFalse(ModList.get().isLoaded("architectury"));
        assertFalse(ModList.get().isLoaded("jei"));
        assertFalse(ModList.get().isLoaded("jade"));
        try {
            Platform.instance();
        } catch (IllegalStateException uninitialized) {
            Platform.init(new EvaluatorPlatform(gameDirectory));
        }
    }

    @Test
    void unchangedPluginRegistersAndItsRespawnFieldPostsIntoNativeGraalListeners() throws Exception {
        Path fixture = fixture();
        try (URLClassLoader addon = new URLClassLoader(new java.net.URL[]{fixture.toUri().toURL()},
                getClass().getClassLoader());
             Context context = Context.newBuilder("js").allowAllAccess(true)
                     .option("engine.WarnInterpreterOnly", "false").build()) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("dev.latvian.mods.rhino.ScriptRuntime", false, addon));
            assertTrue(KubeJSPlugin.class.isInterface());
            KubeJSPlugin plugin = (KubeJSPlugin) Class.forName(discoveredPlugin(fixture), true, addon)
                    .getConstructor().newInstance();
            assertSame(addon, plugin.getClass().getClassLoader());
            assertEquals(fixture.toUri(), plugin.getClass().getProtectionDomain().getCodeSource()
                    .getLocation().toURI(), "the supplied jar, not copied addon classes, defines the plugin");

            ScriptContextRegistry.bind(context, com.tkisor.nekojs.api.ScriptType.SERVER);
            try (NativeEventProofScope scope = new NativeEventProofScope()) {
                ScriptContextRegistry.switchCurrentScriptId(context, "nekujs-proof/respawn.js");
                plugin.registerEvents(scope);
                assertEquals(Set.of("CommonAddedEvents"), scope.groups().keySet());
                EventGroup group = scope.groups().get("CommonAddedEvents");
                assertEquals(Set.of("entityEnterChunk", "entityTame", "playerChangeDimension", "playerClone", "playerRespawn"),
                        group.getHandlers().keySet());
                assertTrue(group.getHandlers().get("entityTame").proofHasResult());
                assertFalse(group.getHandlers().get("playerRespawn").proofHasResult());
                assertThrows(IllegalStateException.class,
                        () -> group.server("late", () -> KubeEvent.class));

                Class<?> definitions = Class.forName(EVENTS, true, addon);
                EventHandler respawn = (EventHandler) definitions.getField("PLAYER_RESPAWN").get(null);
                assertSame(group.getHandlers().get("playerRespawn"), respawn);
                EventGroup arch = (EventGroup) definitions.getField("ARCH_EVENTS").get(null);
                assertEquals(4, arch.getHandlers().size());
                TargetedEventHandler<?> target = (TargetedEventHandler<?>)
                        definitions.getField("ARCH_SERVER_EVENT_HANDLER").get(null);
                assertSame(target, arch.getHandlers().get("handleServer"));
                assertTrue(target.targetRequired);
                assertSame(EventTargetType.STRING, target.target);
                assertEquals(String.class, target.target.type());
                assertThrows(UnsupportedOperationException.class, () -> scope.register(arch));

                Class<?> wrapperClass = Class.forName(WRAPPER, true, addon);
                KubeEvent death = (KubeEvent) wrapperClass.getConstructor(ServerPlayer.class, boolean.class)
                        .newInstance(null, false);
                KubeEvent portal = (KubeEvent) wrapperClass.getConstructor(ServerPlayer.class, boolean.class)
                        .newInstance(null, true);
                assertSame(addon, death.getClass().getClassLoader());
                assertSame(wrapperClass, respawn.eventType.get());
                context.getBindings("js").putMember("CommonAddedEvents", scope.binding());
                context.eval("js", """
                        globalThis.deliveries = [];
                        globalThis.savedRespawn = CommonAddedEvents.playerRespawn;
                        CommonAddedEvents.playerRespawn(event => {
                            deliveries.push([
                                event.causedByDeath(), event.leavingEnd(),
                                event.returningFromEnd(), event.causedByPortal()
                            ].join(':'));
                        });
                        """);
                assertTrue(scope.hasListeners());
                assertSame(EventResult.PASS, respawn.post(ScriptType.SERVER, death));
                assertSame(EventResult.PASS, respawn.post(ScriptType.SERVER, portal));
                assertEquals("true:false:false:false|false:true:true:true",
                        context.eval("js", "deliveries.join('|')").asString());
                assertThrows(UnsupportedOperationException.class, () -> respawn.post(ScriptType.CLIENT, death));
                assertThrows(UnsupportedOperationException.class,
                        () -> group.getHandlers().get("entityTame").post(ScriptType.SERVER, death));
                assertThrows(IllegalArgumentException.class,
                        () -> respawn.post(ScriptType.SERVER, new KubeEvent() {}));
                assertThrows(UnsupportedOperationException.class, () -> death.afterPosted(EventResult.PASS));

                scope.close();
                assertFalse(scope.hasListeners(), "scope close removes the native bus listener tokens");
                assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                        () -> context.eval("js", "savedRespawn(event => {})"));
                assertFalse(scope.hasListeners(), "a retained binding cannot recreate listeners after close");
                assertThrows(IllegalStateException.class, () -> respawn.post(ScriptType.SERVER, death));
                assertEquals(2, context.eval("js", "deliveries.length").asInt());
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void lazyDefinitionsRecordTargetsAndNeverResolveAbsentEventClasses() {
        EventGroup group = EventGroup.of("CommonAddedEvents");
        EventHandler handler = group.server("playerRespawn", () -> {
            throw new AssertionError("The event class must stay lazy until a supported post");
        });
        TargetedEventHandler<String> targeted = group.server("targeted", () -> KubeEvent.class)
                .hasResult().requiredTarget(EventTargetType.STRING);
        assertTrue(targeted.proofHasResult());
        assertTrue(targeted.targetRequired);
        assertSame(targeted, group.getHandlers().get("targeted"));
        try (NativeEventProofScope scope = new NativeEventProofScope()) {
            scope.register(group);
            scope.binding();
            assertThrows(IllegalArgumentException.class, () -> scope.register(group));
            assertThrows(UnsupportedOperationException.class,
                    () -> targeted.post(ScriptType.SERVER, new KubeEvent() {}));
            assertFalse(scope.hasListeners());
        }
        assertThrows(IllegalStateException.class,
                () -> handler.post(ScriptType.SERVER, new KubeEvent() {}));
    }

    private static Path fixture() {
        String directory = System.getProperty("nekujs.fixtures.dir");
        assertNotNull(directory, "Run the explicit compatProofTest task with nekujs.fixtures.dir");
        Path fixture = Path.of(directory).resolve(FIXTURE).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(fixture), "Missing unchanged Additions fixture: " + fixture);
        return fixture;
    }

    private static String discoveredPlugin(Path fixture) throws Exception {
        try (JarFile jar = new JarFile(fixture.toFile())) {
            var entry = jar.getJarEntry("kubejs.plugins.txt");
            assertNotNull(entry, "The unchanged fixture must carry its plugin discovery resource");
            try (var input = jar.getInputStream(entry)) {
                List<String> plugins = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
                assertTrue(plugins.contains(PLUGIN));
                return PLUGIN;
            }
        }
    }

    /** Host arrangement for an isolated server-phase evaluator, not a loader adapter. */
    private record EvaluatorPlatform(Path gameDir) implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return gameDir; }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "evaluator-proof"; }
        @Override public String getLoaderVersion() { return "not-a-loader-lifecycle"; }
    }
}
