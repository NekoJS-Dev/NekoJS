package com.tkisor.nekojs.bindings.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 24 platform adapter source trace (pure file reads, runs on every node).
 *
 * <p>Pins the platform wiring facts a bare JVM cannot exercise against real Minecraft
 * callbacks, mirroring ticket 23's {@code Ticket23RecipeDataPhaseTraceTest} shape:
 * <ol>
 *   <li><b>AC3 (representative caller path)</b> — every platform-dispatched bus of the eight
 *       gameplay families is bound to the NeoForge game bus exactly once through the single
 *       {@code EventBusForgeBridge} per family; posted-object buses
 *       ({@code ItemEvents.modification}/{@code BlockEvents.modification}) and the
 *       listener-posted {@code PlayerEvents.inventoryChanged} are deliberately NOT bridge-bound
 *       and have exactly one production poster each.</li>
 *   <li><b>AC5 (side)</b> — the both-logical-side events bound to SERVER buses carry the
 *       {@code !level().isClientSide()} filter, so client instances never enter SERVER script
 *       contexts (Render-thread Graal rejection).</li>
 *   <li><b>AC9 (per-node source trace)</b> — the fabric twins/adapters that carry the fabric
 *       subset of each family exist and post the neutral payloads, including the entity
 *       behavior mixins (death drops / finalize spawn / tick / damage); the 1.21.1
 *       {@code NeoForgeBlockEvents} twin keeps the same bind surface.</li>
 *   <li><b>AC10 (no second registration path)</b> — {@code EventGroup.of("<Family>")} for each
 *       of the eight families is declared only in the legal sites (shared tree declaration +
 *       fabric twin + fabric binding group merged by name); no fourth surface exists.</li>
 * </ol>
 * File reads see the raw shared tree (stonecutter guards included), so the same assertions
 * hold on every node; guards only affect evaluation, not the wiring recorded here.
 */
class Ticket24GameplayEventPhaseTraceTest {

    private static final String MAIN = "src/main/java/com/tkisor/nekojs";
    private static final String FABRIC = "src/fabric/java/com/tkisor/nekojs";

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
        assertEquals(8, count(code(MAIN + "/bindings/event/ItemEvents.java"), ".bind("),
                "ItemEvents: 8 platform buses bound (modification is posted-object, not bridged)");
        assertEquals(10, count(code(MAIN + "/bindings/event/LevelEvents.java"), ".bind("),
                "LevelEvents: 10 platform buses bound (aliases included, one bind per bus)");
        assertEquals(17, count(code(MAIN + "/bindings/event/PlayerEvents.java"), ".bind("),
                "PlayerEvents: 17 platform buses bound (inventoryChanged is listener-posted, not bridged)");
        assertEquals(2, count(code(MAIN + "/bindings/event/CommandEvents.java"), ".bind("),
                "CommandEvents: register + command both bridged");
        assertEquals(12, count(code(MAIN + "/bindings/event/EntityEvents.java"), ".bind("),
                "EntityEvents: 12 plain binds + DAMAGE_PRE via bindCancellable (13 wired buses)");
        assertEquals(1, count(code(MAIN + "/bindings/event/EntityEvents.java"), ".bindCancellable("),
                "DAMAGE_PRE is the one entity bus cancelled through an adapter cancel action"
                        + " (D4: Pre has no ICancellableEvent face; cancel maps to setNewDamage(0))");
        assertEquals(12, count(code(MAIN + "/bindings/event/NeoForgeBlockEvents.java"), ".bind("),
                "BlockEvents adapter: 12 plain binds + BROKEN via bindTransformed (13 wired buses)");
        assertEquals(1, count(code(MAIN + "/bindings/event/NeoForgeBlockEvents.java"), ".bindTransformed("),
                "BROKEN is the one transformed (neutral wrapper payload) block bus");
    }

    @Test
    void postedObjectAndListenerPostedBusesAreNotBridgeBound() {
        String item = code(MAIN + "/bindings/event/ItemEvents.java");
        assertFalse(item.contains(".bind(MODIFICATION"),
                "ItemEvents.modification is posted-object mode (ticket 39): it must stay off the platform bridge");
        String player = code(MAIN + "/bindings/event/PlayerEvents.java");
        assertFalse(player.contains(".bind(INVENTORY_CHANGED"),
                "PlayerEvents.inventoryChanged is posted by InventoryChangeListener, not bridge-bound");
        String blockAdapter = code(MAIN + "/bindings/event/NeoForgeBlockEvents.java");
        assertFalse(blockAdapter.contains(".bind(BlockEvents.MODIFICATION"),
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

        // modification families: only the ticket-39 domain owner posts (event-side wiring only;
        // transaction/snapshot acceptance belongs to ticket 39's evidence)
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
        assertEquals(2, count(code(MAIN + "/bindings/event/ItemEvents.java"), "isClientSide()"),
                "ItemEvents: rightClicked + entityInteracted filter client instances");
        assertEquals(3, count(code(MAIN + "/bindings/event/LevelEvents.java"), "isClientSide()"),
                "LevelEvents: tickPre/tickPost/tick alias filter client instances");
        assertEquals(3, count(code(MAIN + "/bindings/event/PlayerEvents.java"), "isClientSide()"),
                "PlayerEvents: tickPre/tickPost/entityInteract filter client instances");
        assertEquals(4, count(code(MAIN + "/bindings/event/EntityEvents.java"), "isClientSide()"),
                "EntityEvents: tick/tickPre/joinLevel/leaveLevel filter client instances");
        assertEquals(2, count(code(MAIN + "/bindings/event/NeoForgeBlockEvents.java"), "isClientSide()"),
                "BlockEvents adapter: rightClicked/leftClicked filter client instances");
        assertEquals(2, count(code("versions/1.21.1/src/main/java/com/tkisor/nekojs/bindings/event/NeoForgeBlockEvents.java"),
                        "isClientSide()"),
                "1.21.1 BlockEvents twin keeps the same client-instance filters");
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
        assertTrue(entityBindings.contains("DEATH.post(") && entityBindings.contains("DAMAGE_PRE.post(")
                        && entityBindings.contains("DAMAGE_POST.post(") && entityBindings.contains("JOIN_LEVEL.post("),
                "fabric death/damage/joinLevel post through ServerLivingEntityEvents/ServerEntityEvents");

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
        // shared-tree declaration (every loader compiles it) + fabric twin + fabric binding
        // group merged by name are the legal sites; anything else would be a second surface
        assertDeclarationCount("BlockEvents", 1);
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
