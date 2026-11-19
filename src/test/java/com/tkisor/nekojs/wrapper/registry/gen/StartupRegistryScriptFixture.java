package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.TestGameDirs;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Binds the production module publication to a STARTUP test context without a game launch. */
final class StartupRegistryScriptFixture implements AutoCloseable {
    final Context context;
    final ModuleEventGroup publication;

    StartupRegistryScriptFixture(NekoJSPlugin... plugins) {
        this(bootstrap(plugins));
    }

    StartupRegistryScriptFixture(NekoPluginRuntime pluginRuntime) {
        publication = pluginRuntime.moduleEventGroups().stream()
                .filter(group -> group.moduleId().equals("nekojs:event/versioned")
                        && group.group().name().equals("StartupEvents"))
                .findFirst().orElseThrow();
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(context, ScriptType.STARTUP);
        try {
            context.getBindings("js").putMember("StartupEvents",
                    new ModuleEventGroupProxy(context, publication, ScriptType.STARTUP));
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    static NekoPluginRuntime bootstrap(NekoJSPlugin... plugins) {
        initializePlatform();
        List<OwnedPlugin> owned = Arrays.stream(plugins).map(plugin -> {
            String owner = plugin instanceof NekoRegistryPointsPlugin ? "nekojs" : "startup-test-addon";
            return new OwnedPlugin(new PluginIdentity(owner, plugin.getClass().getName(), URI.create("test:" + owner)), plugin);
        }).toList();
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0"));
        NormativeApiContract contract = new NormativeApiContract(2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0")),
                null, List.of(), List.of(), List.of());
        VerifiedContractSet contracts = VerifiedContractSet.of(VerifiedApiContract.create(identity, contract,
                URI.create("test:/nekojs-core"), "nekojs/api-contract/preview", "sha256:preview", "sha256:preview"));
        return NekoPluginBootstrap.bootstrapOwned(owned, new ScriptPropertyRegistry.Impl(), contracts);
    }

    static void initializePlatform() {
        try {
            Platform.init(new TestPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            if (Platform.instance() == null) throw alreadyInitialized;
        }
    }

    @Override
    public void close() {
        try {
            publication.group().clearListeners(ScriptType.STARTUP);
        } finally {
            ScriptContextRegistry.unbind(context);
            context.close();
        }
    }

    private static final class TestPlatform implements IPlatform {
        public boolean isClient() { return false; }
        public boolean isDevelopment() { return true; }
        public String getMcVersion() { return "0.0.0"; }
        public Path getGameDir() { return TestGameDirs.unique("nekojs-startup-registry-test"); }
        public Map<String, IModInfo> getMods() { return Map.of(); }
        public IModInfo getInfo(String modId) { return null; }
        public String getLoaderId() { return "test"; }
        public String getLoaderVersion() { return "0.0.0"; }
    }
}
