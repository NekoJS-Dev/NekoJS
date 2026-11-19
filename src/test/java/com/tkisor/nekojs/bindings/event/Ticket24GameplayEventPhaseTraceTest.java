package com.tkisor.nekojs.bindings.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traces the platform adapter sources (pure file reads, runs on every node).
 *
 * <p>Pins the platform wiring that a bare JVM cannot exercise against real Minecraft callbacks,
 * in the same shape as {@code Ticket23RecipeDataPhaseTraceTest}:
 * <ol>
 *   <li><b>Representative caller path</b>: every platform-dispatched bus of the eight gameplay
 *       families is bound to the NeoForge game bus exactly once through the single
 *       {@code EventBusForgeBridge} per family; posted-object buses
 *       ({@code ItemEvents.modification}/{@code BlockEvents.modification}) and the
 *       listener-posted {@code PlayerEvents.inventoryChanged} are deliberately NOT bridge-bound
 *       and have exactly one production poster each.</li>
 *   <li><b>Side</b>: the events that fire on both logical sides but are bound to SERVER buses
 *       carry the {@code !level().isClientSide()} filter, so client instances never enter SERVER
 *       script contexts (Graal rejects the render thread).</li>
 *   <li><b>Per-node source trace</b>: the fabric twins/adapters that carry the fabric subset of
 *       each family exist and post the neutral payloads, including the entity behavior mixins
 *       (death drops / finalize spawn / tick / damage); the 1.21.1 {@code BlockEvents} twin keeps
 *       the same bind surface.</li>
 *   <li><b>No second registration path</b>: {@code EventGroup.of("<Family>")} for each of the
 *       eight families is declared only in its legal platform/node source sites; mutually
 *       exclusive source twins do not create an additional runtime surface.</li>
 * </ol>
 * File reads see the raw shared tree (stonecutter guards included), so the same assertions hold
 * on every node; guards only affect evaluation, not the wiring recorded here.
 */
class Ticket24GameplayEventPhaseTraceTest {

    private static final String MAIN = "src/main/java/com/tkisor/nekojs";
    private static final String FABRIC = "src/fabric/java/com/tkisor/nekojs";
    private static final List<String> NEOFORGE_BLOCK_SOURCES = List.of(
            MAIN + "/bindings/event/BlockEvents.java",
            "versions/1.21.1/" + MAIN + "/bindings/event/BlockEvents.java");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(String relative) {
        try {
            return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + relative, e);
        }
    }

    /** Code lines only (drop javadoc/line comments): a symbol inside a comment is not wiring. */
    private static String code(String relative) {
        StringBuilder out = new StringBuilder();
        for (String line : read(relative).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    // ---- 1. bridge wiring: every platform bus bound exactly once per family ----

    @Test
    void forgeBridgeBindsEveryPlatformBusOfEachFamilyExactlyOnce() {
        assertBridgeBuses(MAIN + "/bindings/event/ItemEvents.java", "bind",
                "RIGHT_CLICKED", "TOOLTIP", "PICKED_UP_PRE", "PICKED_UP", "DROPPED", "ENTITY_INTERACTED", "FOOD_EATEN");
        assertBridgeBuses(MAIN + "/bindings/event/ItemEvents.java", "bindCancellable", "CAN_PICK_UP");
        assertBridgeBuses(MAIN + "/bindings/event/LevelEvents.java", "bind",
                "LOADED", "UNLOADED", "SAVED", "TICK_PRE", "TICK_POST", "TICK", "EXPLOSION_START", "BEFORE_EXPLOSION",
                "EXPLOSION_DETONATE", "AFTER_EXPLOSION");
        assertBridgeBuses(MAIN + "/bindings/event/PlayerEvents.java", "bind",
                "LOGGED_IN", "LOGGED_OUT", "CHAT", "TICK_POST", "TICK_PRE", "CLONED", "RESPAWNED", "CHANGED_DIMENSION",
                "ADVANCEMENT", "CONTAINER_OPENED", "INVENTORY_OPENED", "CONTAINER_CLOSED", "INVENTORY_CLOSED",
                "ENTITY_INTERACT", "CRAFTED", "SMELTED", "DESTROYED");
        assertBridgeBuses(MAIN + "/bindings/event/CommandEvents.java", "bind", "REGISTER", "COMMAND");
        assertBridgeBuses(MAIN + "/bindings/event/EntityEvents.java", "bind",
                "DAMAGE_POST", "DEATH", "DROPS", "FINALIZE_SPAWN", "TICK_Pre", "TICK_Post", "JOIN_LEVEL", "LEAVE_LEVEL",
                "USE_START", "USE_STOP", "USE_FINISHED", "USE_TICK");
        assertBridgeBuses(MAIN + "/bindings/event/EntityEvents.java", "bindCancellable", "DAMAGE_PRE");
        for (String blockSource : NEOFORGE_BLOCK_SOURCES) {
            assertBridgeBuses(blockSource, "bind", "ENTITY_PLACED", "ENTITY_MULTI_PLACED", "NEIGHBOR_NOTIFY",
                    "FARMLAND_TRAMPLE", "PORTAL_SPAWN", "TOOL_MODIFICATION", "RIGHT_CLICKED", "PLACED", "LEFT_CLICKED",
                    "RANDOM_TICK", "BLOCK_ENTITY_TICK");
            assertBridgeBuses(blockSource, "bindTransformed", "BROKEN");
            assertEquals(1, count(code(blockSource), "NeoForgeSupplementalFluidPlacement::onNative"),
                    "The native fluid placement hook must be registered once in " + blockSource);
        }
        String fluidPlacement = code(MAIN + "/bindings/event/NeoForgeSupplementalFluidPlacement.java");
        assertTrue(fluidPlacement.contains("!event.getLevel().isClientSide() && BlockEvents.FLUID_PLACED.post(event)"),
                "The native fluid placement hook posts its event on the logical server");
    }

    private static void assertBridgeBuses(String source, String method, String... expectedBuses) {
        List<String> actual = Pattern.compile("\\." + method + "\\(\\s*(\\w+)").matcher(code(source)).results()
                .map(match -> match.group(1))
                .sorted()
                .toList();
        assertEquals(List.of(expectedBuses).stream().sorted().toList(), actual, method + " buses in " + source);
    }

    @Test
    void postedObjectAndListenerPostedBusesAreNotBridgeBound() {
        String item = code(MAIN + "/bindings/event/ItemEvents.java");
        assertFalse(item.contains(".bind(MODIFICATION"),
                "ItemEvents.modification is posted-object mode (ticket 39): it must stay off the platform bridge");
        String player = code(MAIN + "/bindings/event/PlayerEvents.java");
        assertFalse(player.contains(".bind(INVENTORY_CHANGED"),
                "PlayerEvents.inventoryChanged is posted by InventoryChangeListener, not bridge-bound");
        String blockAdapter = code(MAIN + "/bindings/event/BlockEvents.java");
        assertFalse(blockAdapter.contains(".bind(MODIFICATION")
                        || blockAdapter.contains(".bind(BlockEvents.MODIFICATION"),
                "BlockEvents.modification is posted-object mode: the adapter must not bridge it");
    }

    @Test
    void manualPostingSitesEachHaveExactlyOneProductionPoster() {
        // capability family: only the mod-bus RegisterCapabilitiesEvent callback posts the payload
        assertEquals(1, count(code(MAIN + "/NekoJSMod.java"), "CapabilityEvents.REGISTER.postForCollection("),
                "CapabilityEvents.register has exactly one production poster (onRegisterCapabilities)");

        // goal family: the production posting site is invoked once per loader entry after
        // STARTUP scripts load (declaration inside GoalEvents.java is not a call site)
        assertEquals(1, count(code(MAIN + "/NekoJSMod.java"), "GoalEvents.postRegister();"),
                "NeoForge entry calls the goal posting site exactly once");
        assertEquals(1, count(code(FABRIC + "/fabric/NekoJSFabricMod.java"), "GoalEvents.postRegister();"),
                "fabric entry calls the goal posting site exactly once");

        // inventoryChanged: only the inventory listener posts it
        List.of(MAIN + "/listener/InventoryChangeListener.java").forEach(path ->
                assertEquals(1, count(code(path), "PlayerEvents.INVENTORY_CHANGED.post("),
                        "inventoryChanged has exactly one poster (the inventory listener)"));

        // The modification families: only the domain owner posts them. This checks the event-side
        // wiring only; the transaction/snapshot acceptance is covered elsewhere.
        String owner = code(MAIN + "/wrapper/event/server/ModificationDomainOwner.java");
        assertEquals(1, count(owner, "ItemEvents.MODIFICATION.postForCollection("),
                "ItemEvents.modification is posted only by the modification domain owner");
        assertEquals(1, count(owner, "BlockEvents.MODIFICATION.postForCollection("),
                "BlockEvents.modification is posted only by the modification domain owner");
        assertEquals(0, count(owner, "ItemEvents.MODIFICATION.post("));
        assertEquals(0, count(owner, "BlockEvents.MODIFICATION.post("));

        // block broken on fabric: the fabric adapter posts the neutral payload and honors cancel
        assertEquals(1, count(code(FABRIC + "/fabric/event/FabricBlockEventBindings.java"),
                "BlockEvents.BROKEN"),
                "fabric BlockEvents.broken has exactly one bridge binding (PlayerBlockBreakEvents.BEFORE)");
        assertEquals(1, count(code(FABRIC + "/fabric/event/FabricBlockEventBindings.java"),
                "bindCancellable("),
                "fabric BlockEvents.broken uses one cancellable bridge binding");
    }

    // ---- 2. side filters on both-logical-side events bound to SERVER buses ----

    @Test
    void serverBoundBothSideEventsCarryTheClientInstanceFilter() {
        assertServerBindings(MAIN + "/bindings/event/ItemEvents.java", "!e.getLevel().isClientSide()",
                "RIGHT_CLICKED", "ENTITY_INTERACTED");
        assertServerBindings(MAIN + "/bindings/event/ItemEvents.java", "e.getEntity().level() instanceof ServerLevel",
                "FOOD_EATEN");
        assertServerBindings(MAIN + "/bindings/event/LevelEvents.java", "!e.getLevel().isClientSide()",
                "LOADED", "UNLOADED", "TICK_PRE", "TICK_POST", "TICK", "EXPLOSION_START", "BEFORE_EXPLOSION",
                "EXPLOSION_DETONATE", "AFTER_EXPLOSION");
        assertServerBindings(MAIN + "/bindings/event/PlayerEvents.java", "!e.getEntity().level().isClientSide()",
                "TICK_PRE", "TICK_POST");
        assertServerBindings(MAIN + "/bindings/event/PlayerEvents.java", "!e.getLevel().isClientSide()", "ENTITY_INTERACT");
        String entitySource = MAIN + "/bindings/event/EntityEvents.java";
        assertServerBindings(entitySource, "!e.getEntity().level().isClientSide()", "TICK_Pre", "TICK_Post");
        assertServerBindings(entitySource, "!e.getEntity().level().isClientSide() || e.getLevel() instanceof ServerLevel",
                "JOIN_LEVEL", "LEAVE_LEVEL");
        assertEquals(2, count(code(entitySource), "api.versioned().when(event -> !event.getEntity().level().isClientSide())"),
                "Versioned join and leave must filter client entities separately from stable delivery");
        for (String blockSource : NEOFORGE_BLOCK_SOURCES) {
            assertServerBindings(blockSource, "!e.getLevel().isClientSide()", "ENTITY_PLACED", "ENTITY_MULTI_PLACED",
                    "NEIGHBOR_NOTIFY", "FARMLAND_TRAMPLE", "PORTAL_SPAWN", "TOOL_MODIFICATION", "RIGHT_CLICKED",
                    "PLACED", "LEFT_CLICKED");
        }
    }

    private static void assertServerBindings(String source, String predicate, String... buses) {
        String text = code(source);
        for (String bus : buses) {
            assertEquals(1, count(text, ".bind(" + bus + ", e -> " + predicate + ")"),
                    bus + " must filter client instances in " + source);
        }
    }

    // ---- 3. fabric adapters and entity behavior mixins exist and carry the families ----

    @Test
    void fabricAdaptersCarryTheFamilySubsetsThroughNeutralPayloads() {
        // entity behavior representatives on fabric: the V2 bindings post the twin buses and
        // the mixins feed them (death drops / finalize spawn / tick / join-leave)
        String entityV2 = code(FABRIC + "/fabric/event/FabricEntityEventBindingsV2.java");
        assertTrue(entityV2.contains("EntityEvents.DROPS.post("),
                "fabric drops route through the V2 bindings posting the twin bus");
        assertTrue(entityV2.contains("EntityEvents.FINALIZE_SPAWN.post("),
                "fabric finalizeSpawn posts the twin bus (spawn behavior representative)");
        assertTrue(entityV2.contains("EntityEvents.TICK_PRE.post(") && entityV2.contains("EntityEvents.TICK_POST.post("),
                "fabric entity tick posts both twin buses");
        assertTrue(entityV2.contains("EntityEvents.LEAVE_LEVEL.post("),
                "fabric leaveLevel posts the twin bus (join/leave lifecycle representative)");
        String entityBindings = code(FABRIC + "/fabric/event/FabricEntityEventBindings.java");
        assertTrue(entityBindings.contains("ServerLivingEntityEvents.ALLOW_DEATH.register(")
                        && entityBindings.contains("!DEATH.post("),
                "Fabric's native death callback posts its versioned event and honors cancellation");
        assertTrue(entityBindings.contains("DEATH.postStableModuleCancellable(")
                        && code(FABRIC + "/fabric/mixin/MixinLivingEntityDeath.java")
                                .contains("FabricEntityEventBindings.postStableDeath("),
                "Fabric's stable death uses its cancellable pre-death mixin route");
        assertTrue(entityBindings.contains("DAMAGE_PRE.post(")
                        && code(FABRIC + "/fabric/mixin/MixinLivingDamageEvents.java")
                                .contains("FabricEntityEventBindings.filterDamage("),
                "Fabric's damage pre hook posts via the adapter");
        assertTrue(entityBindings.contains("DAMAGE_POST.post(")
                        && code(FABRIC + "/fabric/mixin/MixinLivingDamagePost.java")
                                .contains("FabricEntityEventBindings.withDamagePost("),
                "Fabric's accepted damage posts via the adapter");
        assertTrue(entityBindings.contains("JOIN_LEVEL.postStableModuleCancellable(")
                        && code(FABRIC + "/fabric/mixin/MixinPersistentEntityJoin.java")
                                .contains("FabricEntityEventBindings.postJoinLevelPre("),
                "Fabric's stable join runs before insertion and can cancel");
        assertTrue(entityBindings.contains("JOIN_LEVEL.postIgnoringCancellation(")
                        && code(FABRIC + "/fabric/mixin/ServerLevelMixin.java")
                                .contains("FabricEntityEventBindings.postVersionedJoinLevel("),
                "Fabric's versioned join uses its notification route");

        assertTrue(Files.isRegularFile(repoRoot().resolve(FABRIC + "/fabric/mixin/MixinLivingEntityDeathDrops.java")),
                "the fabric death-drops mixin exists (drops family source trace)");
        assertTrue(Files.isRegularFile(repoRoot().resolve(FABRIC + "/fabric/mixin/MixinMobFinalizeSpawn.java")),
                "the fabric finalize-spawn mixin exists");
        assertTrue(Files.isRegularFile(repoRoot().resolve(FABRIC + "/fabric/mixin/MixinEntityTick.java")),
                "the fabric entity tick mixin exists");
        assertTrue(Files.isRegularFile(repoRoot().resolve(FABRIC + "/fabric/mixin/MixinEntity.java")),
                "the fabric join/leave level mixin carrier exists");

        // player / level / command / item families have their fabric bindings classes
        for (String adapter : List.of(
                "FabricServerEventBindings", "FabricPlayerEventBindings", "FabricLevelEventBindings",
                "FabricLevelEventBindingsV2", "FabricCommandEventBindings", "FabricItemEventBindings",
                "FabricItemEventBindingsV2", "FabricBlockEventBindings")) {
            assertTrue(Files.isRegularFile(repoRoot().resolve(FABRIC + "/fabric/event/" + adapter + ".java")),
                    "fabric adapter missing: " + adapter);
        }

        assertEquals(1, count(code(FABRIC + "/fabric/FabricCorePlugin.java"), "CapabilityEvents.GROUP"),
                "Fabric registers its native lookup capability family exactly once");
        assertEquals(1, count(code(FABRIC + "/fabric/NekoJSFabricMod.java"), "CapabilityEvents.postAndApply();"),
                "Fabric commits capability providers once after startup registries drain");
    }

    // ---- 4. no second registration path ----

    @Test
    void familyGroupsAreDeclaredOnlyInTheirLegalSites() {
        // Count physical source owners, including mutually exclusive platform/node twins.
        // Exact block source paths below prevent a duplicate owner from replacing a legal site.
        assertDeclarationCount("BlockEvents", 3);
        for (String site : List.of(MAIN + "/bindings/event/BlockEvents.java",
                FABRIC + "/bindings/event/BlockEvents.java",
                "versions/1.21.1/src/main/java/com/tkisor/nekojs/bindings/event/BlockEvents.java")) {
            assertEquals(1, count(code(site), "EventGroup.of(\"BlockEvents\")"),
                    "Each mutually exclusive platform source owns exactly one BlockEvents group: " + site);
        }
        assertDeclarationCount("CapabilityEvents", 2);
        assertDeclarationCount("GoalEvents", 1);
        assertDeclarationCount("LevelEvents", 2);
        assertDeclarationCount("CommandEvents", 2);
        assertDeclarationCount("ItemEvents", 3);
        assertDeclarationCount("PlayerEvents", 3);
        assertDeclarationCount("EntityEvents", 3);
    }

    private void assertDeclarationCount(String family, int expected) {
        int found = 0;
        StringBuilder sites = new StringBuilder();
        for (String root : List.of("src/main/java", "src/fabric/java", "versions")) {
            try (var stream = Files.walk(repoRoot().resolve(root))) {
                for (Path path : stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("build"))
                        .toList()) {
                    String text = code(repoRoot().relativize(path).toString().replace('\\', '/'));
                    for (int i = count(text, "EventGroup.of(\"" + family + "\")"); i > 0; i--) {
                        found++;
                        sites.append(repoRoot().relativize(path).toString().replace('\\', '/')).append(' ');
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot scan " + root, e);
            }
        }
        assertEquals(expected, found,
                "EventGroup.of(\"" + family + "\") must appear in exactly the legal declaration sites,"
                        + " found: " + sites);
    }
}
