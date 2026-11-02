package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;

/** Root-owned authoring documents, snapshot cache, listener and final shutdown cleanup. */
public final class AuthoringRuntime implements AutoCloseable {
    private final NekoJSPaths paths;
    private final AuthoringPaths outputPaths;
    private AutoCloseable probeBinding;
    private final AuthoringConfig config;
    private final AuthoringBundle bundle;
    private final JsonObject environment;
    private final AuthoringQuery query;
    private final AuthoringControl controls;
    private volatile com.tkisor.nekojs.api.authoring.AuthoringControlHost liveHost;
    private final Path snapshotDirectory;
    private final String instanceId = UUID.randomUUID().toString();
    private final Thread shutdownHook;
    private AuthoringHttpServer listener;
    private String loadedPointer;
    private volatile boolean closed;

    private AuthoringRuntime(NekoJSPaths paths, ProbeConfig probe) throws IOException {
        this.paths = paths;
        this.config = AuthoringConfig.load(paths.verifyInsideNekoRootForCreate(paths.config().resolve("ai.toml")));
        this.outputPaths = AuthoringPaths.resolve(paths.gameDir(), config.directory());
        this.bundle = AuthoringBundle.load();
        this.environment = AuthoringEnvironment.current();
        this.query = new AuthoringQuery(AuthoringEnvironment.id(environment));
        this.controls = new AuthoringControl(config.controls(), new LiveHost());
        this.snapshotDirectory = outputPaths.snapshots();
        this.shutdownHook = new Thread(this::close, "NekoJS authoring shutdown");
    }

    public static AuthoringRuntime open(NekoJSPaths paths, ProbeConfig probe) throws IOException {
        AuthoringRuntime runtime = new AuthoringRuntime(paths, probe);
        try {
            if (runtime.config.documentation()) {
                AuthoringBundleExporter.export(runtime.bundle, runtime.outputPaths, runtime.config.agents());
                com.tkisor.nekojs.probe.AgentTemplateGenerator.generate(runtime.outputPaths.agents(), runtime.outputPaths.root());
                if (runtime.config.editorAgents()) {
                    Path agents = paths.verifyInsideGameDirForCreate(paths.gameDir().resolve(".github/agents"));
                    com.tkisor.nekojs.probe.AgentTemplateGenerator.generate(agents, runtime.outputPaths.root());
                }
            }
            runtime.writeEnvironment();
            runtime.writeLocator();
            runtime.probeBinding = com.tkisor.nekojs.probe.ProbeCoordinator.bindAuthoringPaths(runtime.outputPaths);
            if (runtime.config.http() || runtime.config.mcp()) {
                Path tokenFile = paths.verifyInsideNekoRootForCreate(paths.config().resolve("ai.token"));
                String token = token(tokenFile);
                runtime.listener = new AuthoringHttpServer(runtime.config.port(), token, runtime.config.http(), runtime.config.mcp(), runtime.bundle, runtime::execute, runtime.controls::available);
            }
            runtime.writeConnection(runtime.listener != null);
            Runtime.getRuntime().addShutdownHook(runtime.shutdownHook);
            return runtime;
        } catch (IOException | RuntimeException | Error failure) {
            try { runtime.close(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public JsonObject execute(String operation, JsonObject request) {
        if (closed) throw new AuthoringException(503, "SERVICE_CLOSED", "/", "Authoring service is closed.");
        if (operation.equals(AuthoringControl.EXECUTE_COMMAND) || operation.equals(AuthoringControl.RELOAD_SCRIPTS)) {
            return controls.execute(operation, request);
        }
        if (operation.equals("context")) AuthoringJson.keys(request, "");
        boolean apiOperation = List.of("resolve", "members", "query", "batch").contains(operation);
        if (!config.api() && apiOperation) throw new AuthoringException(503, "INDEX_DISABLED", "/", "API snapshot queries are disabled in ai.toml.");
        if (config.api() && (apiOperation || operation.equals("context"))) {
            try { refresh(); }
            catch (IOException | AuthoringException e) {
                if (apiOperation) throw new AuthoringException(503, "INDEX_UNAVAILABLE", "/", "The current authoring snapshot could not be verified.");
                JsonObject state = context();
                state.addProperty("indexState", "unavailable");
                return state;
            }
        }
        if (operation.equals("context")) return context();
        JsonObject result = query.execute(operation, request);
        if (operation.equals("capabilities")) result.add("liveControls", controlCapabilities());
        return result;
    }

    private JsonObject context() {
        JsonObject state = query.context();
        state.addProperty("instanceId", instanceId);
        state.add("environment", environment.deepCopy());
        state.add("documentation", AuthoringJson.obj("bundleId", bundle.id(), "version", bundle.manifest().get("version"), "language", "en"));
        if (!config.api()) state.addProperty("indexState", "disabled");
        state.add("liveControls", controlCapabilities());
        return state;
    }

    private synchronized void refresh() throws IOException {
        if (closed) return;
        Path directory = outputPaths.snapshots();
        Path pointer = outputPaths.verify(directory.resolve("current.json"));
        if (!Files.exists(pointer)) {
            if (loadedPointer != null) throw new IOException("[NEKO-1019] The current authoring snapshot pointer is missing.");
            return;
        }
        if (Files.size(pointer) > 4096) throw new IOException("[NEKO-1019] Authoring snapshot pointer exceeds the size limit.");
        String identity = AuthoringSnapshotFiles.readBounded(pointer, 4096);
        if (identity.equals(loadedPointer)) return;
        JsonObject document = AuthoringSnapshotFiles.load(directory, AuthoringEnvironment.id(environment), identity);
        if (document == null) throw new IOException("[NEKO-1019] Authoring snapshot belongs to another environment.");
        query.publish(new AuthoringSnapshot(document));
        loadedPointer = identity;
    }

    private void writeEnvironment() throws IOException {
        AuthoringSnapshotFiles.atomicWrite(outputPaths.environment(), AuthoringJson.obj(
                "schemaVersion", "1", "instanceId", instanceId, "environmentId", AuthoringEnvironment.id(environment), "environment", environment,
                "bundleId", bundle.id(), "apiEnabled", config.api(), "apiSnapshotDirectory", outputPaths.gameDirectory().relativize(snapshotDirectory).toString().replace('\\', '/'),
                "refresh", "after-successful-typescript-probe", "languages", List.of("typescript"), "memberViews", List.of("declared")).toString());
    }
    private void writeLocator() throws IOException {
        Path locator = paths.verifyInsideNekoRootForCreate(paths.config().resolve("ai-location.json"));
        AuthoringSnapshotFiles.atomicWrite(locator, AuthoringJson.obj("schemaVersion", 1, "directory", config.directory()).toString());
    }

    private void writeConnection(boolean listening) throws IOException {
        String address = listening ? "http://127.0.0.1:" + listener.port() : null;
        AuthoringSnapshotFiles.atomicWrite(outputPaths.connection(), AuthoringJson.obj(
                "schemaVersion", "1", "instanceId", instanceId, "environmentId", AuthoringEnvironment.id(environment), "listening", listening,
                "http", listening && config.http() ? address + "/api/v1/authoring" : null,
                "mcp", listening && config.mcp() ? address + "/mcp" : null, "mcpProtocolVersion", AuthoringMcp.VERSION,
                "authentication", "bearer-token-file", "tokenFile", "nekojs/config/ai.token", "tokenFileBase", "game-directory", "readOnly",
                 !(config.controls().reloadEnabled() || (config.controls().commandsEnabled() && !config.controls().commandAllowlist().isEmpty()))).toString());
    }
    private static String token(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        boolean created = false;
        try { Files.createFile(file); created = true; }
        catch (FileAlreadyExistsException ignored) { }
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
        else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl == null) throw new IOException("[NEKO-1019] Token permissions cannot be restricted on this filesystem.");
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
        if (created) {
            // Restrict the empty file before writing any secret, including on ACL-based filesystems.
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            Files.writeString(file, Base64.getUrlEncoder().withoutPadding().encodeToString(random), StandardOpenOption.WRITE);
        }
        if (Files.size(file) > 256) throw new IOException("[NEKO-1019] Invalid local authoring token file.");
        String token = Files.readString(file).strip();
        if (!token.matches("[A-Za-z0-9_-]{43,128}")) throw new IOException("[NEKO-1019] Invalid local authoring token file.");
        return token;
    }

    /** Installs the root-owned platform adapter once; ordinary script reloads do not replace it. */
    public synchronized void installHost(com.tkisor.nekojs.api.authoring.AuthoringControlHost host) {
        if (closed || liveHost != null) throw new IllegalStateException("Authoring host is closed or already installed.");
        liveHost = Objects.requireNonNull(host);
    }

    /** Stops mutations before script managers are torn down, without waiting for owner-thread work. */
    public void stopControls() { controls.close(); }

    private JsonObject controlCapabilities() {
        return AuthoringJson.obj("commandsEnabled", config.controls().commandsEnabled(),
                "reloadEnabled", config.controls().reloadEnabled(), "commandAllowlist", config.controls().commandAllowlist(),
                "executeGameCommandAvailable", controls.available(AuthoringControl.EXECUTE_COMMAND),
                "reloadScriptsAvailable", controls.available(AuthoringControl.RELOAD_SCRIPTS),
                "reloadTypes", controls.availableReloadTypes(), "defaultTimeoutMs", AuthoringControl.DEFAULT_TIMEOUT_MS,
                "maxTimeoutMs", AuthoringControl.MAX_TIMEOUT_MS);
    }

    private final class LiveHost implements com.tkisor.nekojs.api.authoring.AuthoringControlHost {
        @Override public long epoch() {
            var host = liveHost;
            return closed || host == null ? -1 : host.epoch();
        }
        private com.tkisor.nekojs.api.authoring.AuthoringControlHost required() {
            var host = liveHost;
            if (host == null) throw new IllegalStateException("A live authoring host is required.");
            return host;
        }
        @Override public long epoch(com.tkisor.nekojs.api.ScriptType type) {
            var host = liveHost;
            return closed || host == null ? -1 : host.epoch(type);
        }
        @Override public void enqueue(Runnable operation) { required().enqueue(operation); }
        @Override public void enqueue(com.tkisor.nekojs.api.ScriptType type, Runnable operation) {
            required().enqueue(type, operation);
        }
        @Override public java.util.concurrent.CompletionStage<ReloadResult> reload(
                com.tkisor.nekojs.api.ScriptType type, java.util.function.Consumer<String> output) throws Exception {
            return required().reload(type, output);
        }
        @Override public java.util.concurrent.CompletionStage<CommandResult> executeCommand(
                String command, java.util.function.Consumer<String> output) throws Exception {
            return required().executeCommand(command, output);
        }
        @Override public java.util.concurrent.CompletionStage<ReloadResult> reloadServer(
                java.util.function.Consumer<String> output) throws Exception {
            return required().reloadServer(output);
        }
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        controls.close();
        if (probeBinding != null) {
            try { probeBinding.close(); }
            catch (Exception failure) { NekoJS.LOGGER.warn("[NEKO-1019] Authoring service closed; Probe path binding could not be released", failure); }
        }
        // Listener callbacks may refresh under this monitor, so release it before waiting for transport cleanup.
        try { if (listener != null) listener.close(); }
        finally {
            try { query.close(); }
            finally { finishClose(); }
        }
    }

    private void finishClose() {
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { }
        catch (SecurityException e) { NekoJS.LOGGER.warn("[NEKO-1019] Authoring service closed; shutdown hook could not be removed", e); }
        try {
            Path descriptor = outputPaths.connection();
            if (Files.exists(descriptor)) {
                JsonObject saved = AuthoringJson.parse(Files.readString(descriptor));
                if (saved.has("instanceId") && instanceId.equals(saved.get("instanceId").getAsString())) writeConnection(false);
            }
        } catch (IOException | RuntimeException e) { NekoJS.LOGGER.warn("[NEKO-1019] Authoring listener closed; connection descriptor could not be updated", e); }
    }
}
