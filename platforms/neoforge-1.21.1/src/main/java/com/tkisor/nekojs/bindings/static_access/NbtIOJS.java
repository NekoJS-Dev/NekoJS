package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NbtIOJS {

    private NbtIOJS() {}

    public static CompoundTag read(String path) throws IOException {
        Path p = NekoJSPaths.get().root().resolve(path);
        if (!Files.isRegularFile(p)) return null;
        return NbtIo.readCompressed(Files.newInputStream(p), NbtAccounter.unlimitedHeap());
    }

    public static void write(String path, CompoundTag tag) throws IOException {
        Path p = NekoJSPaths.get().root().resolve(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        NbtIo.writeCompressed(tag, Files.newOutputStream(p));
    }
}
