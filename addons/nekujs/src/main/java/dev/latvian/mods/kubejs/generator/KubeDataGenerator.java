package dev.latvian.mods.kubejs.generator;

import dev.latvian.mods.kubejs.script.data.VirtualDataMapFile;
import java.util.function.Consumer;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

public interface KubeDataGenerator {
    default <DT, RT> void dataMap(DataMapType<RT, DT> type, Consumer<VirtualDataMapFile<DT, RT>> consumer) {
        throw new UnsupportedOperationException("[NEKO-4287] Native datapack generation sink is not wired for legacy data maps");
    }
}
