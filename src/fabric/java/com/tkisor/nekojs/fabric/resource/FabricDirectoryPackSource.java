package com.tkisor.nekojs.fabric.resource;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import com.tkisor.nekojs.core.pack.PackDependencyPlan;
import com.tkisor.nekojs.fabric.FabricRegistryAdapter;
import com.tkisor.nekojs.resource.NekoJSPackSource;
import com.tkisor.nekojs.resource.NekoJSPathPackResources;
import com.tkisor.nekojs.wrapper.RegisteredItemAssets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Repository-owned directory discovery; enumerating a save never activates its script session. */
public final class FabricDirectoryPackSource implements RepositorySource {
    // Fabric's native mod pack already owns "nekojs"; directory content must have its own id.
    public static final String PACK_ID = "nekojs:directory";
    private final PackType type;
    private final Path world;

    public FabricDirectoryPackSource(PackType type, Path world) {
        this.type = type;
        this.world = world;
    }

    @Override
    public void loadPacks(Consumer<Pack> output) {
        List<Path> roots = roots();
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(PACK_ID, Component.literal("NekoJS"),
                        NekoJSPackSource.PACK_SOURCE_NEKO, Optional.empty()),
                new Pack.ResourcesSupplier() {
                    @Override public PackResources openPrimary(PackLocationInfo location) {
                        return new FabricDirectoryPackResources(location.id(), type, roots);
                    }

                    @Override public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                        // Native registration must succeed before any generated item assets are published.
                        if (type == PackType.CLIENT_RESOURCES) prepareItemAssets();
                        return openPrimary(location);
                    }
                }, type, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) output.accept(pack);
    }

    private List<Path> roots() {
        ScriptPackRegistry registry;
        if (type == PackType.SERVER_DATA) {
            // A repository can outlive a preview and can precede the MinecraftServer instance.
            registry = new ScriptPackRegistry();
            registry.refreshGlobalPacks(NekoJSPaths.get().packsRoot());
            registry.activateWorldPacks(world);
        } else {
            registry = ScriptPackRegistry.get();
            registry.refreshLocalPacks();
        }
        List<ScriptPack> packs;
        if (type == PackType.CLIENT_RESOURCES) {
            packs = FabricClientPackResources.packs();
        } else {
            packs = new ArrayList<>(registry.allPacks());
            // Native MOD resources are not mounted again, but can satisfy declared dependencies.
            ScriptPackRegistry.get().allPacks().stream().filter(pack -> pack.scope() == ScriptPackScope.MOD)
                    .forEach(packs::add);
        }
        return roots(NekoJSPaths.get().root(), packs, type, PackDependencyPlan.Environment.current());
    }

    /** Registry order is GLOBAL, MOD, WORLD, SERVER_CACHE; MOD uses the loader's native pack. */
    public static List<Path> roots(Path root, List<ScriptPack> packs, PackType type,
                                   PackDependencyPlan.Environment environment) {
        var roots = new ArrayList<Path>();
        roots.add(root);
        var blocked = PackDependencyPlan.resolve(packs, environment).blocked();
        for (var pack : packs) {
            if (blocked.containsKey(pack.selector()) || pack.scope() == ScriptPackScope.MOD) continue;
            if (type == PackType.SERVER_DATA && pack.scope() == ScriptPackScope.SERVER_CACHE) continue;
            if (Files.isDirectory(pack.root().resolve(type.getDirectory()))) roots.add(pack.root());
        }
        return List.copyOf(roots);
    }

    /** Called by openFull before the initial client resource enumeration, and again on F3+T. */
    public static void prepareItemAssets() {
        var runtime = FabricRegistryAdapter.runtime();
        if (!runtime.itemRegistrationComplete()) return;
        new RegisteredItemAssets().generate(NekoJSPaths.get().assets(), runtime.itemAssetDefinitions(),
                NekoJSPathPackResources.usesClientItemDefinitions(), NekoJS.LOGGER::warn);
    }
}
