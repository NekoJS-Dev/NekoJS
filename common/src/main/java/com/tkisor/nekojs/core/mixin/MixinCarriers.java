package com.tkisor.nekojs.core.mixin;

import com.google.gson.Gson;
import com.tkisor.nekojs.platform.Platform;
import java.nio.file.*;
import java.net.URI;
import java.util.*;

/** Reads only native-loader-admitted plans. Pending source declarations never become runtime slots. */
public final class MixinCarriers {
    private MixinCarriers() {}
    public static List<MixinPlan> discover() { return discover(Platform.instance()); }
    static List<MixinPlan> discover(com.tkisor.nekojs.platform.IPlatform platform) {
        var result = readAdmitted(platform);
        validate(platform, result);
        return result;
    }

    /** Loader-admitted plans before profile validation, so preparation can replace a stale cache. */
    public static List<MixinPlan> readAdmitted() { return readAdmitted(Platform.instance()); }
    private static List<MixinPlan> readAdmitted(com.tkisor.nekojs.platform.IPlatform platform) {
        var result = new LinkedHashMap<URI, MixinPlan>();
        platform.visitLoadedMixinPlans((mod, uri) -> {
            if (result.containsKey(uri)) return;
            FileSystem opened = null;
            try {
                Path path;
                try { path = Path.of(uri); }
                catch (FileSystemNotFoundException missing) {
                    String value = uri.toString(); int separator = value.indexOf("!/");
                    if (separator < 0) throw MixinPlan.invalid("Carrier URI has no archive boundary");
                    try { opened = FileSystems.newFileSystem(URI.create(value.substring(0, separator)), Map.of()); }
                    catch (FileSystemAlreadyExistsException existing) { }
                    path = Path.of(uri);
                }
                if (Files.size(path) > 1_048_576) throw MixinPlan.invalid("Carrier plan exceeds the size limit");
                MixinPlan plan = new Gson().fromJson(Files.readString(path), MixinPlan.class);
                if (plan == null || !mod.equals("nekojs_mixins_" + plan.hash().substring(0, 16))) throw MixinPlan.invalid("Carrier owner/hash identity mismatch");
                result.put(uri, plan);
            } catch (Exception failure) {
                throw new IllegalArgumentException("[NEKO-4090] Installed mixin carrier rejected: " + mod, failure);
            } finally {
                if (opened != null) try { opened.close(); }
                catch (java.io.IOException failure) { throw new IllegalStateException("[NEKO-4090] Carrier filesystem close failed", failure); }
            }
        });
        return List.copyOf(result.values());
    }

    private static void validate(com.tkisor.nekojs.platform.IPlatform platform, List<MixinPlan> result) {
        if (!result.isEmpty() && !platform.isDevelopment()) {
            throw MixinPlan.invalid("Experimental MixinJS carriers are development-only until production mapping acceptance is completed");
        }
        if (!result.isEmpty()) {
            var profile = currentProfile(platform);
            var hooks = new HashSet<String>();
            for (var plan : result) {
                if (!profile.equals(plan.profile())) throw MixinPlan.invalid("Installed carrier profile mismatch: " + plan.hash());
                for (var hook : plan.hooks()) if (!hooks.add(hook.id())) throw MixinPlan.invalid("Duplicate installed hook: " + hook.id());
            }
        }
    }
    public static MixinPlan.Profile currentProfile() { return currentProfile(Platform.instance()); }
    private static MixinPlan.Profile currentProfile(com.tkisor.nekojs.platform.IPlatform platform) {
        return new MixinPlan.Profile(platform.getMcVersion(), platform.getLoaderId(), platform.getLoaderVersion(),
                "runtime", platform.isClient() ? "client" : "server");
    }
}
