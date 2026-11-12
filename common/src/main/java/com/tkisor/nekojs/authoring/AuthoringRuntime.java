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
    private volatile java.util.function.Supplier<List<com.tkisor.nekojs.core.error.ScriptDiagnosticRecord>> diagnostics;
    private volatile java.util.function.Supplier<com.tkisor.nekojs.api.testing.TestRunReport> latestTest;
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
            runtime.probeBinding = com.tkisor.nekojs.probe.ProbeCoordinator.bindAuthoringPaths(runtime.outputPaths);
            if (runtime.config.http() || runtime.config.mcp()) {
                Path tokenFile = paths.verifyInsideNekoRootForCreate(paths.config().resolve("ai.token"));
                String token = token(tokenFile);
                runtime.listener = new AuthoringHttpServer(runtime.config.port(), token, runtime.config.http(), runtime.config.mcp(), runtime.bundle, runtime::execute, runtime.controls::available, runtime::liveQueryAvailable);
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
        if (operation.equals("errors")) return errors(request);
        if (operation.equals("operation_status") || operation.equals(AuthoringControl.EXECUTE_COMMAND) || operation.equals(AuthoringControl.RELOAD_SCRIPTS)) {
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
        if (operation.equals("capabilities")) {
            result.add("liveControls", controlCapabilities());
            result.add("liveQueries", liveQueryCapabilities());
            for (String name : List.of("errors", "operation_status")) {
                if (liveQueryAvailable(name)) result.getAsJsonArray("operations").add(name);
            }
        }
        if (operation.equals("schema")) {
            for (String name : List.of("errors", "operation_status")) result.getAsJsonObject("operations").add(name, AuthoringSchema.input(name));
        }
        return result;
    }

    private JsonObject context() {
        JsonObject state = query.context();
        state.addProperty("instanceId", instanceId);
        state.add("environment", environment.deepCopy());
        state.add("documentation", AuthoringJson.obj("bundleId", bundle.id(), "version", bundle.manifest().get("version"), "language", "en"));
        if (!config.api()) state.addProperty("indexState", "disabled");
        state.add("liveControls", controlCapabilities());
        state.add("liveQueries", liveQueryCapabilities());
        var supplier = latestTest;
        state.add("testRunReport", AuthoringControl.testRunReport(supplier == null ? null : supplier.get()));
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

    /** Installs the root-owned frozen diagnostic view once; no owner-thread work is dispatched by queries. */
    public void installDiagnostics(java.util.function.Supplier<List<com.tkisor.nekojs.core.error.ScriptDiagnosticRecord>> supplier) {
        installDiagnostics(supplier, null);
    }

    public synchronized void installDiagnostics(java.util.function.Supplier<List<com.tkisor.nekojs.core.error.ScriptDiagnosticRecord>> supplier,
                                                java.util.function.Supplier<com.tkisor.nekojs.api.testing.TestRunReport> latestTestSupplier) {
        if (closed || diagnostics != null) throw new IllegalStateException("[NEKO-1019] Authoring diagnostics are closed or already installed.");
        diagnostics = Objects.requireNonNull(supplier);
        latestTest = latestTestSupplier;
    }

    private boolean liveQueryAvailable(String operation) {
        return !closed && (operation.equals("operation_status") || operation.equals("errors") && diagnostics != null);
    }

    private JsonObject liveQueryCapabilities() {
        return AuthoringJson.obj("errors", AuthoringJson.obj("available", !closed && diagnostics != null,
                        "readOnly", true, "maxLimit", 100, "defaultLimit", 25, "maxResponseBytes", 20 * 1024),
                "operation_status", AuthoringJson.obj("available", !closed, "readOnly", true),
                "testRunReport", AuthoringJson.obj("available", !closed && latestTest != null, "readOnly", true, "query", "context"));
    }

    private JsonObject errors(JsonObject request) {
        AuthoringJson.keys(request, "", "scriptType", "limit");
        com.tkisor.nekojs.api.ScriptType type = null;
        if (request.has("scriptType")) {
            try { type = com.tkisor.nekojs.api.ScriptType.valueOf(AuthoringJson.string(request, "scriptType", "")); }
            catch (IllegalArgumentException invalid) { throw AuthoringJson.invalid("/scriptType", "Expected STARTUP, SERVER, CLIENT or TEST."); }
        }
        int limit = request.has("limit") ? AuthoringJson.integer(request.get("limit"), "/limit", 1, 100) : 25;
        var supplier = diagnostics;
        if (supplier == null) return AuthoringJson.obj("available", false, "reason", "DIAGNOSTICS_UNAVAILABLE");
        List<com.tkisor.nekojs.core.error.ScriptDiagnosticRecord> records;
        int sourceSize;
        try {
            var supplied = Objects.requireNonNull(supplier.get());
            sourceSize = supplied.size();
            records = List.copyOf(supplied.subList(0, Math.min(sourceSize, 10000)));
        } catch (RuntimeException unavailable) {
            NekoJS.LOGGER.warn("[NEKO-1019] Authoring diagnostics unavailable; frozen records could not be read (failureType={})", unavailable.getClass().getSimpleName());
            return AuthoringJson.obj("available", false, "reason", "DIAGNOSTICS_UNAVAILABLE");
        }
        if (closed) throw new AuthoringException(503, "SERVICE_CLOSED", "/", "Authoring service is closed.");
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        int matches = 0;
        int bytes = 2;
        for (var record : records) {
            if (type != null && record.scriptType() != type) continue;
            matches++;
            if (rows.size() >= limit) continue;
            String message = bounded(record.message(), 1024);
            JsonObject row = AuthoringJson.obj("errorId", bounded(record.errorId(), 512), "scriptType", record.scriptType().name(),
                    "phase", record.phase().name(), "owner", bounded(record.owner(), 128), "generation", record.generation(),
                    "recordedDuringCandidate", record.recordedDuringCandidate(), "sourcePath", relativeDiagnosticPath(record.sourcePath()),
                    "line", record.line(), "column", record.column(), "message", message,
                    "messageTruncated", !message.equals(record.message()));
            int size = row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
            if (bytes + size > 19 * 1024) continue;
            rows.add(row);
            bytes += size;
        }
        boolean complete = sourceSize == records.size();
        return AuthoringJson.obj("available", true, "errors", rows, "returned", rows.size(),
                "total", complete ? matches : null, "totalIsExact", complete, "scanned", records.size(),
                "truncated", !complete || matches > rows.size(), "messagesAreUntrusted", true);
    }

    private static String bounded(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String relativeDiagnosticPath(String source) {
        if (source == null || source.length() > 2048) return null;
        try {
            Path path = source.startsWith("file:") ? Path.of(java.net.URI.create(source)) : Path.of(source);
            Path game = paths.gameDir().toAbsolutePath().normalize();
            if (!path.isAbsolute()) {
                path = path.startsWith(paths.root().getFileName()) ? game.resolve(path) : paths.root().resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (!path.startsWith(game)) return null;
            String relative = game.relativize(path).toString().replace('\\', '/');
            return relative.length() <= 1024 ? relative : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
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
            diagnostics = null;
            latestTest = null;
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
