//? if fabric {
/*package com.tkisor.nekojs.fabric.resource;

import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import com.tkisor.nekojs.core.pack.PackDependencyPlan;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabricDirectoryPackResourcesTest {
    @TempDir Path temporary;
    private static final PackDependencyPlan.Environment ENVIRONMENT =
            new PackDependencyPlan.Environment("fabric", "26.1.2", java.util.Map.of());

    @BeforeAll static void version() { SharedConstants.tryDetectVersion(); }

    @Test void overlaysResolveAndEnumerateTheSameWinnerWithoutExposingTheOtherSide() throws Exception {
        Path base = temporary.resolve("base");
        Path global = temporary.resolve("global");
        Path world = temporary.resolve("world");
        Path remote = temporary.resolve("remote");
        for (Path root : List.of(base, global, world, remote)) {
            write(root, "assets/test/models/item/same.json", root.getFileName().toString());
            write(root, "data/private/recipe/hidden.json", "private");
        }
        write(base, "assets/base/lang/en_us.json", "{}");
        write(world, "assets/test/models/item/world_only.json", "world");
        write(remote, "manifest.json", "private manifest");
        var id = Identifier.parse("test:models/item/same.json");
        try (var resources = new FabricDirectoryPackResources("nekojs", PackType.CLIENT_RESOURCES,
                List.of(base, global, world, remote))) {
            assertEquals("remote", read(resources.getResource(PackType.CLIENT_RESOURCES, id)));
            var listed = new LinkedHashMap<Identifier, String>();
            resources.listResources(PackType.CLIENT_RESOURCES, "test", "models", (key, content) -> {
                try { listed.put(key, read(content)); }
                catch (Exception failure) { throw new AssertionError(failure); }
            });
            assertEquals("remote", listed.get(id));
            assertEquals("world", listed.get(Identifier.parse("test:models/item/world_only.json")));
            assertEquals(java.util.Set.of("base", "test"), resources.getNamespaces(PackType.CLIENT_RESOURCES));
            assertTrue(resources.getNamespaces(PackType.SERVER_DATA).isEmpty());
            assertNull(resources.getResource(PackType.SERVER_DATA, Identifier.parse("private:recipe/hidden.json")));
            assertNull(resources.getRootResource("manifest.json"));
            assertNotNull(resources.getRootResource("pack.mcmeta"));
        }
        try (var removed = new FabricDirectoryPackResources("nekojs", PackType.CLIENT_RESOURCES,
                List.of(base, global))) {
            assertEquals("global", read(removed.getResource(PackType.CLIENT_RESOURCES, id)));
            assertNull(removed.getResource(PackType.CLIENT_RESOURCES, Identifier.parse("test:models/item/world_only.json")));
        }
    }

    @Test void serverDataExcludesRemoteAndNativeModCopiesAndDisabledDirectories() throws Exception {
        var global = pack("global", ScriptPackScope.GLOBAL, true);
        var world = pack("world", ScriptPackScope.WORLD, true);
        var remote = pack("remote", ScriptPackScope.SERVER_CACHE, true);
        var mod = pack("mod", ScriptPackScope.MOD, true);
        var disabled = pack("disabled", ScriptPackScope.GLOBAL, false);
        var packs = List.of(global, mod, disabled, world, remote);
        Path base = temporary.resolve("base");
        assertEquals(List.of(base, global.root(), world.root()),
                FabricDirectoryPackSource.roots(base, packs, PackType.SERVER_DATA, ENVIRONMENT));
        assertEquals(List.of(base, global.root(), world.root(), remote.root()),
                FabricDirectoryPackSource.roots(base, packs, PackType.CLIENT_RESOURCES, ENVIRONMENT));
        try (var resources = new FabricDirectoryPackResources("nekojs", PackType.SERVER_DATA,
                FabricDirectoryPackSource.roots(base, packs, PackType.SERVER_DATA, ENVIRONMENT))) {
            assertEquals("world", read(resources.getResource(PackType.SERVER_DATA, Identifier.parse("test:recipe/same.json"))));
            assertNull(resources.getResource(PackType.SERVER_DATA, Identifier.parse("remote:recipe/only.json")));
            assertNull(resources.getResource(PackType.SERVER_DATA, Identifier.parse("mod:recipe/only.json")));
            assertTrue(resources.getNamespaces(PackType.CLIENT_RESOURCES).isEmpty());
        }
    }

    @Test void descriptorAdmissionAlsoConstrainsResourcesWithoutMountingNativeDependencyCopies() throws Exception {
        var mod = pack("mod", ScriptPackScope.MOD, true);
        var dependent = described("dependent", "\"dependencies\":{\"MOD:mod\":\"*\"}");
        var wrongLoader = described("wrong_loader", "\"environment\":{\"loaders\":[\"neoforge\"]}");
        var missingDependency = described("missing", "\"dependencies\":{\"absent\":\"*\"}");
        Path base = temporary.resolve("base");
        assertEquals(List.of(base, dependent.root()), FabricDirectoryPackSource.roots(base,
                List.of(dependent, wrongLoader, missingDependency, mod), PackType.CLIENT_RESOURCES, ENVIRONMENT));
    }

    private ScriptPack described(String id, String conditions) throws Exception {
        var pack = pack(id, ScriptPackScope.GLOBAL, true);
        write(pack.root(), "manifest.json", "{\"formatVersion\":1,\"id\":\"" + id
                + "\",\"version\":\"1.0.0\",\"entries\":{}," + conditions + "}");
        return new ScriptPack(id, id, "1.0.0", ScriptPackScope.GLOBAL, pack.root(), true,
                ScriptPackManifest.load(pack.root(), id));
    }

    @Test void nativeResourceStacksRetainEveryDirectoryForAdditiveTagsAndTranslations() throws Exception {
        Path base = temporary.resolve("base");
        Path world = temporary.resolve("world");
        for (PackType type : PackType.values()) {
            String path = type == PackType.CLIENT_RESOURCES ? "lang/en_us.json" : "tags/item/example.json";
            String baseContent = type == PackType.CLIENT_RESOURCES ? "{\"base\":\"base\"}" : "{\"values\":[\"test:base\"]}";
            String worldContent = type == PackType.CLIENT_RESOURCES ? "{\"world\":\"world\"}" : "{\"values\":[\"test:world\"]}";
            write(base, type.getDirectory() + "/test/" + path, baseContent);
            write(world, type.getDirectory() + "/test/" + path, worldContent);
            var group = new FabricDirectoryPackResources(FabricDirectoryPackSource.PACK_ID, type, List.of(base, world));
            List<PackResources> expanded = FabricDirectoryPackResources.expand(List.of(group));
            try (var manager = new MultiPackResourceManager(type, expanded)) {
                var stack = manager.getResourceStack(Identifier.parse("test:" + path));
                assertEquals(2, stack.size());
                try (var input = stack.getFirst().open()) {
                    assertEquals(baseContent, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                try (var input = stack.getLast().open()) {
                    assertEquals(worldContent, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                assertEquals(2, manager.listResourceStacks(path.substring(0, path.lastIndexOf('/')), id -> true)
                        .get(Identifier.parse("test:" + path)).size());
            }
        }
    }

    private ScriptPack pack(String id, ScriptPackScope scope, boolean enabled) throws Exception {
        Path root = temporary.resolve(id);
        write(root, "manifest.json", "{\"id\":\"" + id + "\"}");
        write(root, "data/test/recipe/same.json", id);
        write(root, "data/" + id + "/recipe/only.json", id);
        write(root, "assets/test/models/item/same.json", id);
        return new ScriptPack(id, id, "1.0.0", scope, root, enabled, ScriptPackManifest.load(root, id));
    }

    private static void write(Path root, String path, String content) throws Exception {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static String read(net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> resource) throws Exception {
        assertNotNull(resource);
        try (var input = resource.get()) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
*///?}
