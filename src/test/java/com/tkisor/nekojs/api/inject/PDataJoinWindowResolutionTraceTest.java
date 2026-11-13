package com.tkisor.nekojs.api.inject;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source trace for the joinLevel window fix, following the pattern of
 * NetworkRegistrationSourceTraceTest. While an {@code EntityJoinLevelEvent} callback runs, the
 * entity is not in the level entity index yet, so a reverse lookup by entity id
 * ({@code level.getEntity(id)}) always comes back empty and a script write to pdata is dropped
 * silently.
 *
 * <p>The fix: {@link EntityPDataStore.Access} gains an entity-reference face whose platform
 * implementations dereference the persistent container directly instead of looking the entity up
 * by id, and the script entry point {@code EntityExtension#neko$pdata()} goes through
 * {@link EntityPDataStore#getPDataTag}/{@code setPDataTag} on that face. A real joinLevel event is
 * platform behavior and cannot be built on a bare JVM without FML (see EntityPDataStoreTest), so
 * this test pins "writing does not depend on an id lookup" to the source structure: the
 * entity-reference methods contain no lookup call, and the lookup call count stays confined to the
 * id-based face.
 */
class PDataJoinWindowResolutionTraceTest {

    // ---- Locate the repository root; the node test CWD is versions/<node>, so walk up to the
    // controller script ----

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
        Path path = repoRoot().resolve(relative);
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    // ---- The bridge: the entity-reference face exists and the static entry points use it ----

    @Test
    void bridgeExposesEntityReferenceOverloadsAndScriptEntriesPreferThem() {
        String bridge = read("src/main/java/com/tkisor/nekojs/api/inject/EntityPDataStore.java");
        assertTrue(bridge.contains("default CompoundTag get(Entity entity, String key)"),
                "Access must declare the entity-reference get overload (default delegates to id face)");
        assertTrue(bridge.contains("default void set(Entity entity, String key, CompoundTag tag)"),
                "Access must declare the entity-reference set overload (default delegates to id face)");
        assertTrue(bridge.contains("return current.get(entity, key);"),
                "getPDataTag must route through the entity-reference face, not entity.getId()");
        assertTrue(bridge.contains("current.set(entity, key, tag);"),
                "setPDataTag must route through the entity-reference face, not entity.getId()");

        // The script entry point (EntityExtension#neko$pdata) goes through the bridge's static entry
        // points and resolves no container itself. The setter needle omits the closing parenthesis; the
        // source reads setPDataTag(self(), KEY, tag).
        String extension = read("src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java");
        assertTrue(extension.contains("EntityPDataStore.getPDataTag(self(), NEKO_PDATA_KEY)"),
                "the script-facing pdata getter must go through EntityPDataStore.getPDataTag");
        assertTrue(extension.contains("EntityPDataStore.setPDataTag(self(), NEKO_PDATA_KEY"),
                "the script-facing pdata setter must go through EntityPDataStore.setPDataTag");
    }

    // ---- NeoForge: the entity-reference face dereferences getPersistentData() directly, and the
    // reverse lookup stays on the id-based face ----

    @Test
    void neoforgeInstallOverridesEntityFaceWithDirectContainerDereference() {
        String mod = read("src/main/java/com/tkisor/nekojs/NekoJSMod.java");

        // The entity-reference overrides exist and dereference the NeoForge platform container directly.
        // Both version branches begin with the same text.
        assertTrue(mod.contains("public net.minecraft.nbt.CompoundTag get(net.minecraft.world.entity.Entity entity, String key)"),
                "NeoForge install must override the entity-reference get face");
        assertTrue(mod.contains("public void set(net.minecraft.world.entity.Entity entity, String key, net.minecraft.nbt.CompoundTag tag)"),
                "NeoForge install must override the entity-reference set face");
        assertTrue(mod.contains("entity.getPersistentData().getCompound(key)"),
                "entity-reference get must dereference Entity#getPersistentData() directly");
        assertTrue(mod.contains("var container = entity.getPersistentData();"),
                "entity-reference set must dereference Entity#getPersistentData() directly");

        // The id lookup (pdataContainer) stays confined to the id-based face: exactly one call site for
        // get and one for set. The method definition whose signature is pdataContainer(int entityId) does
        // not count. If the entity-reference face went back to an id lookup, the silent drop in the join
        // window would return and this count would grow.
        assertTrue(countOccurrences(mod, "pdataContainer(entityId)") == 2,
                "reverse lookup must stay confined to the id-keyed face (exactly the id get/set call sites)");
    }

    // ---- Fabric: the entity-reference face dereferences the mixin duck interface directly, and the
    // reverse lookup stays on the id-based face ----

    @Test
    void fabricInstallOverridesEntityFaceWithDirectDuckDereference() {
        String sync = read("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java");

        assertTrue(sync.contains("public CompoundTag get(Entity entity, String key)"),
                "fabric install must override the entity-reference get face");
        assertTrue(sync.contains("public void set(Entity entity, String key, CompoundTag tag)"),
                "fabric install must override the entity-reference set face");
        assertTrue(countOccurrences(sync, "((NekoEntityPData) entity).neko$getPDataRoot()") >= 4,
                "both faces must dereference the mixin duck root directly (id face 2 + entity face 2)");

        // The findEntity lookup stays confined to the id-based face: exactly one call for get and one for
        // set. If the entity-reference face went back to a lookup, the silent drop in the join window
        // would return and the count would grow to 4.
        assertTrue(countOccurrences(sync, "findEntity(entityId)") == 2,
                "reverse lookup must stay confined to the id-keyed face");
    }

    // ---- Both platform faces read and write NBT the same way: defensive copy and removal of a
    // subkey on an empty tag ----

    @Test
    void bothLoaderFacesKeepCopySemanticsAndEmptyTagRemoval() {
        // The same "copy on read, remove on empty write" semantics appear in three places: the NeoForge
        // install, the fabric install and the in-memory implementation in EntityPDataStoreTest. That is
        // the domain contract of the bridge.
        try (Stream<Path> files = Stream.of(
                Path.of("src/main/java/com/tkisor/nekojs/NekoJSMod.java"),
                Path.of("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java"))) {
            for (Path relative : files.toList()) {
                String source = read(relative.toString().replace('\\', '/'));
                assertTrue(source.contains("if (tag.isEmpty())"),
                        relative + " must keep the empty-tag-removes-key semantics");
                assertTrue(source.contains("tag.copy()"),
                        relative + " must keep the defensive copy on write");
            }
        }
    }
}
