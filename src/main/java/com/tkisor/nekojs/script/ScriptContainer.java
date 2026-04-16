package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.prop.ScriptProperties;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ScriptContainer {
    public final Identifier id;
    public final ScriptType type;
    public final Path path;
    public final ScriptProperties properties;

    public boolean disabled = false;
    public Throwable lastError;

    public ScriptContainer(Identifier id, ScriptType type, Path path, ScriptPropertyRegistry propertyRegistry) {
        this.id = id;
        this.type = type;
        this.path = path;
        this.properties = new ScriptProperties(propertyRegistry);
    }

    public boolean isType(ScriptType type) {
        return this.type == type;
    }

    public boolean shouldRun() {
        return !disabled
            && !properties.getOrDefault(ScriptProperty.DISABLE)
            && properties.getOrDefault(ScriptProperty.MODLOADED).stream().allMatch(ModList.get()::isLoaded);
    }

    public void preload() {
        var propertyMap = properties.registry.view();

        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (!line.startsWith("//")) {
                    break;
                }
                line = line.substring("//".length()).trim();

                var parts = line.split(":", 2);
                if (parts.length < 2) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                var prop = (ScriptProperty<Object>) propertyMap.get(parts[0].trim());
                if (prop != null) {
                    properties.put(prop, prop.read(parts[1].trim()));
                }
            }
        } catch (Exception e) {
            disabled = true;
            lastError = e;
        }
    }
}
