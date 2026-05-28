package com.tkisor.nekojs.core.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record ModuleVersion(String moduleId, long sourceStamp, int generation) {
    ModuleVersion {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId must not be blank");
        }
        generation = Math.max(0, generation);
    }

    ModuleVersion nextGeneration(long newSourceStamp) {
        return new ModuleVersion(moduleId, newSourceStamp, generation + 1);
    }

    boolean sourceChanged(ModuleVersion other) {
        return other == null || sourceStamp != other.sourceStamp;
    }

    static long sourceStamp(Path path) throws IOException {
        return (Files.getLastModifiedTime(path).toMillis() << 16) ^ Files.size(path);
    }

    static ModuleVersion initial(String moduleId, Path path) throws IOException {
        return new ModuleVersion(moduleId, sourceStamp(path), 0);
    }

    static ModuleVersion initial(String moduleId, long stamp) {
        return new ModuleVersion(moduleId, stamp, 0);
    }
}
