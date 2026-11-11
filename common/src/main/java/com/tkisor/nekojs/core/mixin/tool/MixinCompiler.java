package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Compiles declarations against exact target bytes without loading target classes. */
public final class MixinCompiler {
    private MixinCompiler() {}

    @FunctionalInterface
    public interface TargetBytes { byte[] read(String name) throws Exception; }

    public record Compilation(MixinPlan plan, Map<String, byte[]> sources) {
        public Compilation { sources = Map.copyOf(sources); }
    }

    public static Compilation compile(Path source, MixinPlan.Profile profile, TargetBytes targets) throws Exception {
        try (var declarations = new MixinDeclarations(source)) {
            declarations.loadAll();
            declarations.hooks.sort(Comparator.comparing(MixinPlan.Hook::id));
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(MixinCarrierWriter.JSON.toJson(profile).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(MixinCarrierWriter.JSON.toJson(declarations.hooks).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (var entry : declarations.sources.entrySet()) {
                digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(entry.getValue());
            }
            for (var hook : declarations.hooks) {
                byte[] bytes = targets.read(hook.target());
                MixinCarrierWriter.validate(hook, bytes);
                digest.update(bytes);
            }
            return new Compilation(new MixinPlan(MixinPlan.ABI, profile,
                    HexFormat.of().formatHex(digest.digest()), declarations.hooks), declarations.sources);
        }
    }
}
