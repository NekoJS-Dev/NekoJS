package com.tkisor.nekojs.fabric.resource;

import com.tkisor.nekojs.resource.NekoJSPathPackResources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** A repository group whose directories remain separate layers in the native resource manager. */
public final class FabricDirectoryPackResources extends NekoJSPathPackResources {
    private final PackType type;
    private final List<PackResources> layers;

    public FabricDirectoryPackResources(String name, PackType type, List<Path> roots) {
        super(name, roots.getFirst(), type);
        this.type = type;
        var layers = new ArrayList<PackResources>();
        for (int index = 0; index < roots.size(); index++) {
            layers.add(new DirectoryLayer(name + "/" + index, roots.get(index), type));
        }
        this.layers = List.copyOf(layers);
    }

    /** Preserve native resource stacks, including additive tags and translations across directories. */
    public static List<PackResources> expand(List<PackResources> packs) {
        if (packs.stream().noneMatch(FabricDirectoryPackResources.class::isInstance)) return packs;
        var expanded = new ArrayList<PackResources>();
        for (var pack : packs) {
            if (pack instanceof FabricDirectoryPackResources group) expanded.addAll(group.layers);
            else expanded.add(pack);
        }
        return List.copyOf(expanded);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        // Only synthetic metadata belongs to the aggregate; script files are never root resources.
        return path.length == 1 && path[0].equals("pack.mcmeta") ? super.getRootResource(path) : null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType requested, Identifier id) {
        if (requested != type) return null;
        for (var layer : layers.reversed()) {
            var resource = layer.getResource(requested, id);
            if (resource != null) return resource;
        }
        return null;
    }

    @Override
    public void listResources(PackType requested, String namespace, String prefix, ResourceOutput output) {
        if (requested != type) return;
        var resources = new TreeMap<Identifier, IoSupplier<InputStream>>();
        for (var layer : layers) layer.listResources(requested, namespace, prefix, resources::put);
        resources.forEach(output::accept);
    }

    @Override
    public Set<String> getNamespaces(PackType requested) {
        if (requested != type) return Set.of();
        var namespaces = new TreeSet<String>();
        for (var layer : layers) namespaces.addAll(layer.getNamespaces(requested));
        return Set.copyOf(namespaces);
    }

    @Override
    public void close() {
        layers.forEach(PackResources::close);
    }

    private static final class DirectoryLayer extends NekoJSPathPackResources {
        private final PackType type;

        private DirectoryLayer(String name, Path root, PackType type) {
            super(name, root, type);
            this.type = type;
        }

        @Override public IoSupplier<InputStream> getRootResource(String... path) {
            return path.length == 1 && path[0].equals("pack.mcmeta") ? super.getRootResource(path) : null;
        }

        @Override public IoSupplier<InputStream> getResource(PackType requested, Identifier id) {
            return requested == type ? super.getResource(requested, id) : null;
        }

        @Override public void listResources(PackType requested, String namespace, String prefix, ResourceOutput output) {
            if (requested == type) super.listResources(requested, namespace, prefix, output);
        }

        @Override public Set<String> getNamespaces(PackType requested) {
            return requested == type ? super.getNamespaces(requested) : Set.of();
        }
    }
}
