package com.tkisor.nekojs.script;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

public enum ScriptType implements ScriptTypeFilter {
    COMMON("common", "NekoJS Common", NekoJSPaths.COMMON_SCRIPTS),
    STARTUP("startup", "NekoJS Startup", NekoJSPaths.STARTUP_SCRIPTS),
    SERVER("server", "NekoJS Server", NekoJSPaths.SERVER_SCRIPTS),
    CLIENT("client", "NekoJS Client", NekoJSPaths.CLIENT_SCRIPTS);

    public final String name;
    public final String cname;
    public final Path path;

    ScriptType(String name, String cname, Path path) {
        this.name = name;
        this.cname = cname;
        this.path = path;
    }

    public Logger logger() {
        return NekoJSLoggers.get(name);
    }

    public Path getLogFile() {
        var dir = FMLPaths.GAMEDIR.get().resolve("logs/nekojs");
        var file = dir.resolve(name + ".log");

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            if (!Files.exists(file)) {
                var oldFile = dir.resolve(name + ".txt");

                if (Files.exists(oldFile)) {
                    Files.move(oldFile, file);
                } else {
                    Files.createFile(file);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return file;
    }

    /**
     * 根据脚本文件路径创建资源定位符
     * @param file 脚本文件路径
     * @return 对应的资源定位符
     */
    public Identifier makeId(Path file) {
        String fileName = file.getFileName().toString();
        return Identifier.fromNamespaceAndPath("nekojs", name + "/" + fileName);
    }

    public String defaultMainScript() {
        return """
                // %s example script
                console.info('Hello, World! (Loaded %s example script)');
                """.formatted(name, name);
    }

    public boolean isCommon() {
        return this == COMMON;
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }

    public boolean isStartup() {
        return this == STARTUP;
    }

    @Override
    public boolean test(ScriptType scriptType) {
        return scriptType == this;
    }
}