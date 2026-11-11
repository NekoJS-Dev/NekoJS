package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Offline-only experimental carrier CLI, invoked through the existing packTool task. */
public final class MixinToolMain {
    private MixinToolMain() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || !Set.of("build", "validate").contains(args[0])) throw MixinPlan.invalid("Expected mixins build|validate --source PATH --classes PATH --minecraft VERSION --loader neoforge|fabric --loader-version VERSION --side server|client [--output JAR]");
        var options = new LinkedHashMap<String, String>();
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length || !Set.of("--source", "--classes", "--minecraft", "--loader", "--loader-version", "--side", "--output").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw MixinPlan.invalid("Unknown, duplicated or incomplete option");
        }
        for (String name : List.of("--source", "--classes", "--minecraft", "--loader", "--loader-version", "--side")) if (!options.containsKey(name)) throw MixinPlan.invalid("Missing " + name);
        var profile = new MixinPlan.Profile(options.get("--minecraft"), options.get("--loader"), options.get("--loader-version"), "runtime", options.get("--side"));
        Path classes = Path.of(options.get("--classes")).toRealPath();
        var compilation = MixinCompiler.compile(Path.of(options.get("--source")), profile, name -> target(classes, name));
        var plan = compilation.plan();
        System.out.println("Experimental runtime-namespace injection plan: " + plan.hash() + " hooks=" + plan.hooks().size());
        if (args[0].equals("build")) {
            if (!options.containsKey("--output")) throw MixinPlan.invalid("Build requires --output; installed files are never overwritten");
            System.out.println(MixinCarrierWriter.write(Path.of(options.get("--output")), plan, compilation.sources()));
            System.out.println(MixinCarrierWriter.gameLibrary(profile)
                    ? "Generated an internal game library, not a carrier mod. Automatic workspace preparation is available in development. Restart is required."
                    : "Install the carrier mod and fully restart. NekoJS is a required dependency.");
        }
    }
    private static byte[] target(Path classes, String name) throws Exception {
        String entry = name.replace('.', '/') + ".class";
        if (Files.isDirectory(classes)) {
            Path file = classes.resolve(entry).toRealPath();
            if (!file.startsWith(classes)) throw MixinPlan.invalid("Target entry escapes class root");
            return Files.readAllBytes(file);
        }
        try (var jar = new JarFile(classes.toFile())) {
            var item = jar.getJarEntry(entry);
            if (item == null) throw MixinPlan.invalid("Target class is missing: " + name);
            try (var stream = jar.getInputStream(item)) { return stream.readAllBytes(); }
        }
    }
}
