package com.tkisor.nekojs.fabric.mixin;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

/** Identifies the native repository side and its save-owned datapack directory. */
@Mixin(FolderRepositorySource.class)
public interface FolderRepositorySourceAccessor {
    @Accessor("packType") PackType nekojs$packType();
    @Accessor("folder") Path nekojs$folder();
}
