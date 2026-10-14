package com.tkisor.nekojs.core.pack.tool;

import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.pack.PackDescriptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Repository CLI; arguments are passed as a JSON array by the packTool task. */
public final class PackToolMain {
    private PackToolMain() {}
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0) throw new IllegalArgumentException("[NEKO-1018] Expected create, validate, archive, install, jar or schema");
        switch (arguments[0]) {
            case "create" -> {
                count(arguments, 4);
                var types = Arrays.stream(arguments[3].split(",")).map(name -> ScriptType.valueOf(name.toUpperCase(java.util.Locale.ROOT))).toList();
                System.out.println(PackTools.create(Path.of(arguments[1]), arguments[2], types));
            }
            case "validate" -> {
                count(arguments, 2); var content = PackTools.validate(Path.of(arguments[1]));
                System.out.println(content.manifest().id() + "@" + content.manifest().version() + " " + content.hash() + " files=" + content.files().size());
            }
            case "archive" -> { count(arguments, 3); System.out.println(PackTools.archive(Path.of(arguments[1]), Path.of(arguments[2]))); }
            case "install" -> {
                if (arguments.length != 3 && !(arguments.length == 4 && arguments[3].equals("replace"))) {
                    throw new IllegalArgumentException("[NEKO-1018] Expected install <archive> <packs-root> [replace]");
                }
                System.out.println(PackTools.install(Path.of(arguments[1]), Path.of(arguments[2]), arguments.length == 4));
            }
            case "jar" -> { count(arguments, 5); System.out.println(PackTools.modJar(Path.of(arguments[1]), Path.of(arguments[2]), arguments[3], arguments[4])); }
            case "schema" -> {
                count(arguments, 2); Path output = Path.of(arguments[1]).toAbsolutePath().normalize(); Files.createDirectories(output.getParent());
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(PackDescriptor.schema()) + "\n", StandardCharsets.UTF_8);
                System.out.println(output);
            }
            default -> throw new IllegalArgumentException("[NEKO-1018] Unknown package tool command: " + arguments[0]);
        }
    }
    private static void count(String[] arguments, int expected) {
        if (arguments.length != expected) throw new IllegalArgumentException("[NEKO-1018] Invalid argument count for " + arguments[0]);
    }
}
