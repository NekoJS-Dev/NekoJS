package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.resource.FabricDirectoryPackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Installs one directory source alongside native discovery, before the first repository reload. */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nekojs$addDirectorySource(RepositorySource[] original, CallbackInfo ci) {
        for (var source : original) {
            if (!(source instanceof FolderRepositorySource)) continue;
            var folder = (FolderRepositorySourceAccessor) source;
            PackType type = folder.nekojs$packType();
            var updated = new LinkedHashSet<>(sources);
            updated.add(new FabricDirectoryPackSource(type,
                    type == PackType.SERVER_DATA ? folder.nekojs$folder().toAbsolutePath().normalize().getParent() : null));
            sources = updated;
            return;
        }
    }
}
