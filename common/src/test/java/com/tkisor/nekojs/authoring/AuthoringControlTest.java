package com.tkisor.nekojs.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.authoring.AuthoringControlHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringControlTest {
    private static final AuthoringControl.Policy ENABLED = new AuthoringControl.Policy(true, true, Set.of("say", "minecraft:say"));
    private static final long EXPIRED = TimeUnit.MILLISECONDS.toNanos(AuthoringControl.MAX_TIMEOUT_MS + 1L);

    private static JsonObject command(String text) { return AuthoringJson.obj("command", text); }
    private static JsonObject reload() { return AuthoringJson.obj("type", "server"); }
    private static String string(JsonObject object, String name) { return object.get(name).getAsString(); }

    private static void rejects(String code, Runnable action) {
        assertEquals(code, assertThrows(AuthoringException.class, action::run).code());
    }

    private static final class Host implements AuthoringControlHost {
        long epoch = 1;
        int commandCalls;
        int reloadCalls;
        String lastCommand;
        Consumer<Runnable> dispatch = Runnable::run;
        BiFunction<String, Consumer<String>, CompletionStage<CommandResult>> commands =
                (command, output) -> CompletableFuture.completedFuture(new CommandResult(1, true));
        Function<Consumer<String>, CompletionStage<ReloadResult>> reloads =
                output -> CompletableFuture.completedFuture(new ReloadResult(true, true));

        @Override public long epoch() { return epoch; }
        @Override public void enqueue(Runnable operation) { dispatch.accept(operation); }
        @Override public CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) {
            commandCalls++;
            lastCommand = command;
            return commands.apply(command, output);
        }
        @Override public CompletionStage<ReloadResult> reloadServer(Consumer<String> output) {
            reloadCalls++;
            return reloads.apply(output);
        }
    }

    private static final class TypedHost implements AuthoringControlHost {
        final Map<ScriptType, Long> epochs = new EnumMap<>(ScriptType.class);
        final Map<ScriptType, LinkedBlockingQueue<Runnable>> queues = new EnumMap<>(ScriptType.class);
        final List<ScriptType> dispatched = new ArrayList<>();
        final List<ScriptType> reloaded = new ArrayList<>();
        int commandCalls;
        Thread lastOwner;
        BiConsumer<ScriptType, Runnable> dispatch = (type, operation) -> operation.run();
        BiFunction<ScriptType, Consumer<String>, CompletionStage<ReloadResult>> reloads =
                (type, output) -> CompletableFuture.completedFuture(new ReloadResult(true, true));

        TypedHost() {
            for (ScriptType type : ScriptType.values()) {
                epochs.put(type, type == ScriptType.STARTUP ? -1L : type == ScriptType.CLIENT ? 7L : 41L);
                queues.put(type, new LinkedBlockingQueue<>());
            }
        }

        @Override public long epoch() { throw new AssertionError("Typed controls must select a target epoch."); }
        @Override public long epoch(ScriptType type) { return epochs.get(type); }
        @Override public void enqueue(Runnable operation) { throw new AssertionError("Typed controls must select an owner queue."); }
        @Override public void enqueue(ScriptType type, Runnable operation) {
            dispatched.add(type);
            dispatch.accept(type, operation);
        }
        @Override public CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) {
            commandCalls++;
            lastOwner = Thread.currentThread();
            return CompletableFuture.completedFuture(new CommandResult(1, true));
        }
        @Override public CompletionStage<ReloadResult> reloadServer(Consumer<String> output) {
            throw new AssertionError("Typed controls must select a reload target.");
        }
        @Override public CompletionStage<ReloadResult> reload(ScriptType type, Consumer<String> output) {
            reloaded.add(type);
            lastOwner = Thread.currentThread();
            return reloads.apply(type, output);
        }
    }

    @Test void reloadTypeAdvertisementIsDynamicPolicySensitiveAndLegacyCompatible() {
        Host legacy = new Host();
        TypedHost typed = new TypedHost();
        try (AuthoringControl legacyControl = new AuthoringControl(ENABLED, legacy);
             AuthoringControl control = new AuthoringControl(ENABLED, typed);
             AuthoringControl disabled = new AuthoringControl(AuthoringControl.Policy.disabled(), typed)) {
            assertEquals(List.of("server"), legacyControl.availableReloadTypes());
            assertEquals(List.of("server", "client", "test"), control.availableReloadTypes());
            assertEquals(List.of(), disabled.availableReloadTypes());
            assertFalse(disabled.available(AuthoringControl.RELOAD_SCRIPTS));
            typed.epochs.put(ScriptType.SERVER, -1L);
            typed.epochs.put(ScriptType.TEST, -1L);
            assertEquals(List.of("client"), control.availableReloadTypes());
            assertTrue(control.available(AuthoringControl.RELOAD_SCRIPTS));
            assertFalse(control.available(AuthoringControl.EXECUTE_COMMAND));
            typed.epochs.put(ScriptType.CLIENT, -1L);
            assertEquals(List.of(), control.availableReloadTypes());
            assertFalse(control.available(AuthoringControl.RELOAD_SCRIPTS));
            typed.epochs.put(ScriptType.TEST, 82L);
            assertEquals(List.of("test"), control.availableReloadTypes());
            legacy.epoch = -1;
            assertEquals(List.of(), legacyControl.availableReloadTypes());
            control.close();
            assertEquals(List.of(), control.availableReloadTypes());
        }
    }

    @Test void fullReloadsUseSelectedOwnerQueueAndReturnTargetWhileCommandsStayServerBound() throws Exception {
        for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.CLIENT, ScriptType.TEST)) {
            TypedHost host = new TypedHost();
            host.dispatch = (target, operation) -> host.queues.get(target).add(operation);
            try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
                CompletableFuture<JsonObject> reply = new CompletableFuture<>();
                Thread requester = reloadWorker(control, type, reply);
                requester.start();
                try {
                    Runnable callback = host.queues.get(type).poll(2, TimeUnit.SECONDS);
                    assertNotNull(callback);
                    assertTrue(host.reloaded.isEmpty());
                    for (ScriptType other : ScriptType.values()) assertTrue(host.queues.get(other).isEmpty());
                    Thread owner = new Thread(callback, "authoring-test-owner-" + type.name);
                    owner.start();
                    owner.join(2_000);
                    assertFalse(owner.isAlive());
                    JsonObject result = reply.get(2, TimeUnit.SECONDS);
                    assertEquals("succeeded", string(result, "status"));
                    assertEquals(type.name, string(result, "type"));
                    assertTrue(result.get("committed").getAsBoolean());
                    assertTrue(result.get("postprocessingSucceeded").getAsBoolean());
                    assertEquals(List.of(type), host.dispatched);
                    assertEquals(List.of(type), host.reloaded);
                    assertSame(owner, host.lastOwner);
                    assertEquals(type.name, string(control.audit().get(0).getAsJsonObject(), "type"));
                } finally {
                    control.close();
                    requester.join(2_000);
                    assertFalse(requester.isAlive());
                }
            }
        }
        TypedHost host = new TypedHost();
        host.dispatch = (type, operation) -> host.queues.get(type).add(operation);
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            CompletableFuture<JsonObject> reply = new CompletableFuture<>();
            Thread requester = worker(control, reply);
            requester.start();
            try {
                Runnable callback = host.queues.get(ScriptType.SERVER).poll(2, TimeUnit.SECONDS);
                assertNotNull(callback);
                callback.run();
                JsonObject result = reply.get(2, TimeUnit.SECONDS);
                assertFalse(result.has("type"));
                assertFalse(control.audit().get(0).getAsJsonObject().has("type"));
                assertEquals(List.of(ScriptType.SERVER), host.dispatched);
                assertEquals(1, host.commandCalls);
                assertSame(Thread.currentThread(), host.lastOwner);
                assertTrue(host.reloaded.isEmpty());
            } finally {
                control.close();
                requester.join(2_000);
                assertFalse(requester.isAlive());
            }
        }
    }

    @Test void clientCanReloadWithoutServerAndDedicatedClientAndStartupRejectBeforeEnqueue() {
        TypedHost host = new TypedHost();
        host.epochs.put(ScriptType.SERVER, -1L);
        host.epochs.put(ScriptType.TEST, -1L);
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            JsonObject result = control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "client"));
            assertEquals("succeeded", string(result, "status"));
            assertEquals("client", string(result, "type"));
            rejects("LIVE_RUNTIME_REQUIRED", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            rejects("LIVE_RUNTIME_REQUIRED", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            host.epochs.put(ScriptType.CLIENT, -1L);
            host.epochs.put(ScriptType.SERVER, 42L);
            rejects("RELOAD_UNAVAILABLE", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "client")));
            rejects("RESTART_REQUIRED", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "startup")));
            assertEquals(List.of(ScriptType.CLIENT), host.dispatched);
            assertEquals(List.of(ScriptType.CLIENT), host.reloaded);
            assertEquals(0, host.commandCalls);
        }
    }

    private static Thread reloadWorker(AuthoringControl control, ScriptType type, CompletableFuture<JsonObject> reply) {
        return new Thread(() -> {
            try { reply.complete(control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", type.name))); }
            catch (Throwable failure) { reply.completeExceptionally(failure); }
        });
    }

    @Test void crossTargetEpochsNeverRetireEachOthersLiveGateAndLateOldCompletionCannotClearReplacement() {
        TypedHost host = new TypedHost();
        AtomicLong clock = new AtomicLong();
        CompletableFuture<AuthoringControlHost.ReloadResult> oldClient = new CompletableFuture<>();
        CompletableFuture<AuthoringControlHost.ReloadResult> newServer = new CompletableFuture<>();
        host.reloads = (type, output) -> {
            clock.addAndGet(EXPIRED);
            return type == ScriptType.CLIENT ? oldClient : newServer;
        };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            JsonObject old = control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "client"));
            assertEquals("timed_out", string(old, "status"));
            assertEquals("unknown", string(old, "effects"));
            assertEquals("client", string(old, "type"));
            assertFalse(old.get("retryable").getAsBoolean());
            // Server lifetime changes are unrelated to the active client's independent identity.
            host.epochs.put(ScriptType.SERVER, 42L);
            host.epochs.put(ScriptType.TEST, 42L);
            assertEquals(List.of("server", "client", "test"), control.availableReloadTypes());
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            assertEquals(1, control.audit().size());
            host.epochs.put(ScriptType.CLIENT, 8L);
            JsonObject replacement = control.execute(AuthoringControl.RELOAD_SCRIPTS, reload());
            assertEquals("timed_out", string(replacement, "status"));
            JsonObject retirement = control.audit().get(1).getAsJsonObject();
            assertEquals("STALE_RUNTIME", string(retirement, "code"));
            assertEquals("client", string(retirement, "type"));
            assertEquals("unknown", string(retirement, "effects"));
            int auditSize = control.audit().size();
            oldClient.complete(new AuthoringControlHost.ReloadResult(true, true));
            assertEquals(auditSize, control.audit().size());
            // Retiring another client lifetime must not release a live server stage.
            host.epochs.put(ScriptType.CLIENT, 9L);
            control.availableReloadTypes();
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "client")));
            newServer.complete(new AuthoringControlHost.ReloadResult(true, true));
            host.reloads = (type, output) -> CompletableFuture.completedFuture(new AuthoringControlHost.ReloadResult(true, true));
            assertEquals("succeeded", string(control.execute(AuthoringControl.RELOAD_SCRIPTS,
                    AuthoringJson.obj("type", "test")), "status"));
        }
    }

    @Test void queuedReloadsNeverExecuteAfterDeadlineCloseOrTheirOwnEpochRetirement() {
        for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.CLIENT, ScriptType.TEST)) {
            for (String reason : List.of("deadline", "close", "epoch")) {
                TypedHost host = new TypedHost();
                AtomicLong clock = new AtomicLong();
                ArrayDeque<Runnable> queue = new ArrayDeque<>();
                try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
                    host.dispatch = (target, operation) -> {
                        queue.add(operation);
                        if (reason.equals("close")) control.close();
                        else if (reason.equals("epoch")) {
                            host.epochs.put(type, host.epochs.get(type) + 1L);
                            control.availableReloadTypes();
                        } else clock.addAndGet(EXPIRED);
                    };
                    JsonObject result = control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", type.name));
                    assertEquals(type.name, string(result, "type"));
                    assertEquals(switch (reason) {
                        case "deadline" -> "CONTROL_TIMEOUT";
                        case "close" -> "CONTROL_CLOSED";
                        default -> "STALE_RUNTIME";
                    }, string(result, "code"));
                    assertEquals("none", string(result, "effects"));
                    assertTrue(result.get("retryable").getAsBoolean());
                    queue.remove().run();
                    assertTrue(host.reloaded.isEmpty());
                    assertEquals(type.name, string(control.audit().get(0).getAsJsonObject(), "type"));
                }
            }
        }
    }

    @Test void queuedClientUsesItsOwnEpochEvenWhenServerHasDifferentOrChangingIdentity() throws Exception {
        TypedHost host = new TypedHost();
        host.dispatch = (target, operation) -> host.queues.get(target).add(operation);
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            CompletableFuture<JsonObject> reply = new CompletableFuture<>();
            Thread requester = reloadWorker(control, ScriptType.CLIENT, reply);
            requester.start();
            try {
                Runnable callback = host.queues.get(ScriptType.CLIENT).poll(2, TimeUnit.SECONDS);
                assertNotNull(callback);
                host.epochs.put(ScriptType.SERVER, 43L);
                host.epochs.put(ScriptType.TEST, 43L);
                control.availableReloadTypes();
                assertFalse(reply.isDone());
                assertEquals(0, control.audit().size());
                callback.run();
                JsonObject result = reply.get(2, TimeUnit.SECONDS);
                assertEquals("succeeded", string(result, "status"));
                assertEquals("client", string(result, "type"));
            } finally {
                control.close();
                requester.join(2_000);
                assertFalse(requester.isAlive());
            }
        }
    }

    @Test void controlsAreIndependentlyDefaultOffAndOfflineCannotRelay() {
        Host host = new Host();
        try (AuthoringControl disabled = new AuthoringControl(AuthoringControl.Policy.disabled(), host);
             AuthoringControl offline = new AuthoringControl(ENABLED, null);
             AuthoringControl commands = new AuthoringControl(new AuthoringControl.Policy(true, false, Set.of("say")), host);
             AuthoringControl reloads = new AuthoringControl(new AuthoringControl.Policy(false, true, Set.of()), host);
             AuthoringControl empty = new AuthoringControl(new AuthoringControl.Policy(true, true, Set.of()), host)) {
            assertFalse(disabled.available(AuthoringControl.EXECUTE_COMMAND));
            assertFalse(disabled.available(AuthoringControl.RELOAD_SCRIPTS));
            rejects("CONTROL_DISABLED", () -> disabled.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            rejects("CONTROL_DISABLED", () -> disabled.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            rejects("LIVE_RUNTIME_REQUIRED", () -> offline.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            rejects("LIVE_RUNTIME_REQUIRED", () -> offline.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            assertFalse(offline.available(AuthoringControl.RELOAD_SCRIPTS));
            assertFalse(empty.available(AuthoringControl.EXECUTE_COMMAND));
            rejects("COMMAND_NOT_ALLOWED", () -> empty.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            assertTrue(commands.available(AuthoringControl.EXECUTE_COMMAND));
            rejects("CONTROL_DISABLED", () -> commands.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            assertTrue(reloads.available(AuthoringControl.RELOAD_SCRIPTS));
            rejects("CONTROL_DISABLED", () -> reloads.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            assertEquals(0, host.commandCalls);
            assertEquals(0, host.reloadCalls);
        }
    }

    @Test void commandPolicyChecksActualExactRootAndNeverAcceptsImpersonationFields() {
        Host host = new Host();
        Set<String> roots = new HashSet<>(Set.of("say"));
        AuthoringControl.Policy policy = new AuthoringControl.Policy(true, false, roots);
        roots.add("op");
        try (AuthoringControl control = new AuthoringControl(policy, host)) {
            assertEquals("succeeded", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("/say hello")), "status"));
            assertEquals("say hello", host.lastCommand);
            for (String denied : new String[]{"sayevil hello", "minecraft:say hello", "op someone", "execute run say hi", "Say hi"}) {
                if (denied.startsWith("Say")) {
                    rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command(denied)));
                } else {
                    rejects("COMMAND_NOT_ALLOWED", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command(denied)));
                }
            }
            for (String field : new String[]{"player", "source", "permissionLevel"}) {
                JsonObject request = command("say hi");
                request.addProperty(field, "someone");
                rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, request));
            }
            assertEquals(1, host.commandCalls);
        }
        rejects("INVALID_REQUEST", () -> new AuthoringControl.Policy(true, false, Set.of("say hi")));
        rejects("INVALID_REQUEST", () -> new AuthoringControl.Policy(true, false, Set.of("/say")));
        Set<String> nullRoot = new HashSet<>();
        nullRoot.add(null);
        rejects("INVALID_REQUEST", () -> new AuthoringControl.Policy(true, false, nullRoot));
        rejects("INVALID_REQUEST", () -> new AuthoringControl.Policy(true, false, Set.of("Say")));
    }

    @Test void strictValidationRejectsMultilineOversizeWrongTypesAndUnsupportedReloadShapes() {
        Host host = new Host();
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            for (String invalid : new String[]{" say hi", "say hi ", "say\nhi", "say\rhi", "say\thi", "say" + (char) 0x2028 + "hi", "say" + (char) 0x2029 + "hi", "//say hi", "/", "say " + "x".repeat(AuthoringControl.MAX_COMMAND_LENGTH)}) {
                rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command(invalid)));
            }
            for (Object invalid : new Object[]{0, -1, 0.5, "3", true, 8_001}) {
                JsonObject request = command("say hi");
                request.add("timeoutMs", AuthoringJson.GSON.toJsonTree(invalid));
                rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, request));
            }
            rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, AuthoringJson.obj("command", 42)));
            rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, null));
            rejects("RESTART_REQUIRED", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", "startup")));
            for (String type : new String[]{"SERVER", "package", "file"}) {
                rejects("UNSUPPORTED_RELOAD", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, AuthoringJson.obj("type", type)));
            }
            for (String field : new String[]{"file", "package", "path"}) {
                JsonObject request = reload();
                request.addProperty(field, "main.js");
                rejects("INVALID_REQUEST", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, request));
            }
            rejects("UNKNOWN_CONTROL", () -> control.execute("batch", new JsonObject()));
            assertEquals(0, host.commandCalls);
            assertEquals(0, host.reloadCalls);
        }
    }

    @Test void timeoutBeforeQueueDrainCannotExecuteAndDuplicateCallbackCannotRunTwice() {
        Host host = new Host();
        AtomicLong clock = new AtomicLong();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        host.dispatch = task -> { queue.add(task); clock.addAndGet(EXPIRED); };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
            assertEquals("timed_out", string(result, "status"));
            assertEquals("none", string(result, "effects"));
            Runnable callback = queue.remove();
            callback.run();
            callback.run();
            assertEquals(0, host.commandCalls);
            assertEquals(1, control.audit().size());
        }
        host.dispatch = task -> { task.run(); task.run(); };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            control.execute(AuthoringControl.EXECUTE_COMMAND, command("say once"));
            assertEquals(1, host.commandCalls);
        }
    }

    @Test void ownerClaimRejectsDeadlineEpochUnavailableAndCloseBeforeStarting() {
        for (String scenario : new String[]{"deadline", "epoch", "unavailable", "close"}) {
            Host host = new Host();
            AtomicLong clock = new AtomicLong();
            try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
                host.dispatch = task -> {
                    switch (scenario) {
                        case "deadline" -> clock.set(EXPIRED);
                        case "epoch" -> host.epoch++;
                        case "unavailable" -> host.epoch = -1;
                        case "close" -> control.close();
                    }
                    task.run();
                };
                JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
                assertEquals("none", string(result, "effects"));
                assertEquals(switch (scenario) {
                    case "deadline" -> "CONTROL_TIMEOUT";
                    case "close" -> "CONTROL_CLOSED";
                    default -> "STALE_RUNTIME";
                }, string(result, "code"));
                assertEquals(0, host.commandCalls);
            }
        }
        Host host = new Host();
        host.epoch = -1;
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            assertFalse(control.available(AuthoringControl.RELOAD_SCRIPTS));
            rejects("LIVE_RUNTIME_REQUIRED", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
        }
    }

    @Test void runningTimeoutReportsUnknownWithoutCancellingAndLateCompletionOnlyUpdatesAudit() {
        Host host = new Host();
        AtomicLong clock = new AtomicLong();
        CompletableFuture<AuthoringControlHost.CommandResult> stage = new CompletableFuture<>();
        host.commands = (command, output) -> { output.accept("before timeout"); clock.set(EXPIRED); return stage; };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
            assertEquals("timed_out", string(result, "status"));
            assertEquals("unknown", string(result, "effects"));
            assertFalse(result.get("retryable").getAsBoolean());
            assertFalse(stage.isCancelled());
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.RELOAD_SCRIPTS, reload()));
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say concurrent")));
            stage.complete(new AuthoringControlHost.CommandResult(1, true));
            assertEquals("timed_out", string(result, "status"));
            assertEquals(2, control.audit().size());
            assertEquals("succeeded", string(control.audit().get(1).getAsJsonObject(), "status"));
            host.commands = (command, output) -> CompletableFuture.completedFuture(new AuthoringControlHost.CommandResult(1, true));
            assertEquals("succeeded", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say released")), "status"));
        }
    }

    @Test void changedEpochRetiresUnresolvedOperationWithoutReleasingReplacementGate() {
        Host host = new Host();
        AtomicLong clock = new AtomicLong();
        CompletableFuture<AuthoringControlHost.CommandResult> oldStage = new CompletableFuture<>();
        CompletableFuture<AuthoringControlHost.CommandResult> replacementStage = new CompletableFuture<>();
        host.commands = (command, output) -> {
            clock.addAndGet(EXPIRED);
            return host.epoch == 1 ? oldStage : replacementStage;
        };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            JsonObject oldReply = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say old"));
            assertEquals("timed_out", string(oldReply, "status"));
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say same epoch")));
            host.epoch = 2;
            JsonObject replacementReply = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say replacement"));
            assertEquals("timed_out", string(replacementReply, "status"));
            assertEquals(2, host.commandCalls);
            JsonObject retirement = control.audit().get(1).getAsJsonObject();
            assertEquals(string(oldReply, "operationId"), string(retirement, "operationId"));
            assertEquals("STALE_RUNTIME", string(retirement, "code"));
            assertEquals("unknown", string(retirement, "effects"));
            assertFalse(retirement.get("retryable").getAsBoolean());
            assertFalse(oldStage.isCancelled());
            int auditSize = control.audit().size();
            oldStage.complete(new AuthoringControlHost.CommandResult(1, true));
            assertEquals(auditSize, control.audit().size());
            assertEquals("timed_out", string(oldReply, "status"));
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say still blocked")));
            replacementStage.complete(new AuthoringControlHost.CommandResult(1, true));
            host.commands = (command, output) -> CompletableFuture.completedFuture(new AuthoringControlHost.CommandResult(1, true));
            assertEquals("succeeded", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say released")), "status"));
        }
    }

    @Test void unavailableEpochRetiresClaimedOperationOnAvailabilityAndAdmission() {
        for (boolean availabilityCheck : new boolean[]{false, true}) {
            Host host = new Host();
            AtomicLong clock = new AtomicLong();
            CompletableFuture<AuthoringControlHost.CommandResult> oldStage = new CompletableFuture<>();
            host.commands = (command, output) -> { clock.addAndGet(EXPIRED); return oldStage; };
            try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
                control.execute(AuthoringControl.EXECUTE_COMMAND, command("say old"));
                host.epoch = -1;
                if (availabilityCheck) assertFalse(control.available(AuthoringControl.EXECUTE_COMMAND));
                else rejects("LIVE_RUNTIME_REQUIRED", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say offline")));
                JsonObject retirement = control.audit().get(control.audit().size() - 1).getAsJsonObject();
                assertEquals("STALE_RUNTIME", string(retirement, "code"));
                assertEquals("unknown", string(retirement, "effects"));
                assertFalse(retirement.get("retryable").getAsBoolean());
                int auditSize = control.audit().size();
                assertFalse(control.available(AuthoringControl.EXECUTE_COMMAND));
                assertEquals(auditSize, control.audit().size());
                host.epoch = 2;
                host.commands = (command, output) -> CompletableFuture.completedFuture(new AuthoringControlHost.CommandResult(1, true));
                assertEquals("succeeded", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say replacement")), "status"));
            }
        }
    }

    @Test void epochRetirementCompletesWaitingQueuedAndClaimedRepliesOnce() throws Exception {
        for (boolean claimed : new boolean[]{false, true}) {
            for (long replacementEpoch : new long[]{-1, 2}) {
                Host host = new Host();
                LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
                CompletableFuture<AuthoringControlHost.CommandResult> oldStage = new CompletableFuture<>();
                host.dispatch = queue::add;
                host.commands = (command, output) -> oldStage;
                try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
                    CompletableFuture<JsonObject> reply = new CompletableFuture<>();
                    Thread worker = worker(control, reply);
                    worker.start();
                    try {
                        Runnable oldCallback = queue.poll(2, TimeUnit.SECONDS);
                        assertNotNull(oldCallback);
                        if (claimed) oldCallback.run();
                        assertFalse(reply.isDone());
                        host.epoch = replacementEpoch;
                        assertEquals(replacementEpoch >= 0, control.available(AuthoringControl.EXECUTE_COMMAND));
                        JsonObject retired = reply.get(2, TimeUnit.SECONDS);
                        assertEquals("STALE_RUNTIME", string(retired, "code"));
                        assertEquals(claimed ? "unknown" : "none", string(retired, "effects"));
                        assertEquals(!claimed, retired.get("retryable").getAsBoolean());
                        oldCallback.run();
                        oldCallback.run();
                        oldStage.complete(new AuthoringControlHost.CommandResult(1, true));
                        assertEquals(claimed ? 1 : 0, host.commandCalls);
                        assertEquals(1, control.audit().size());
                    } finally {
                        control.close();
                        worker.join(2_000);
                        assertFalse(worker.isAlive());
                    }
                }
            }
        }
    }

    @Test void changedEpochRetiresQueuedTombstonesWithoutWaitingForOldQueueDrain() {
        Host host = new Host();
        AtomicLong clock = new AtomicLong();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        host.dispatch = task -> { queue.add(task); clock.addAndGet(EXPIRED); };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            for (int i = 0; i < AuthoringControl.MAX_PENDING; i++) {
                assertEquals("timed_out", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say old")), "status"));
            }
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say full")));
            host.epoch = 2;
            assertTrue(control.available(AuthoringControl.EXECUTE_COMMAND));
            JsonArray retired = control.audit();
            assertEquals(AuthoringControl.MAX_AUDIT, retired.size());
            for (int i = AuthoringControl.MAX_PENDING; i < retired.size(); i++) {
                JsonObject retirement = retired.get(i).getAsJsonObject();
                assertEquals("STALE_RUNTIME", string(retirement, "code"));
                assertEquals("none", string(retirement, "effects"));
                assertTrue(retirement.get("retryable").getAsBoolean());
            }
            host.dispatch = task -> {
                while (!queue.isEmpty()) {
                    Runnable oldCallback = queue.remove();
                    oldCallback.run();
                    oldCallback.run();
                }
                assertEquals(0, host.commandCalls);
                task.run();
            };
            assertEquals("succeeded", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say replacement")), "status"));
            assertEquals(1, host.commandCalls);
            int auditSize = control.audit().size();
            assertTrue(control.available(AuthoringControl.EXECUTE_COMMAND));
            assertEquals(auditSize, control.audit().size());
        }
    }

    @Test void asynchronousReloadDoesNotWaitOnOwnerCallbackAndPreservesCommitVersusPostprocessing() throws Exception {
        Host host = new Host();
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        CompletableFuture<AuthoringControlHost.ReloadResult> stage = new CompletableFuture<>();
        host.dispatch = queue::add;
        host.reloads = output -> stage;
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            CompletableFuture<JsonObject> reply = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try { reply.complete(control.execute(AuthoringControl.RELOAD_SCRIPTS, reload())); }
                catch (Throwable failure) { reply.completeExceptionally(failure); }
            });
            worker.start();
            try {
                Runnable ownerCallback = queue.poll(2, TimeUnit.SECONDS);
                assertNotNull(ownerCallback);
                ownerCallback.run();
                assertEquals(1, host.reloadCalls);
                assertFalse(reply.isDone());
                stage.complete(new AuthoringControlHost.ReloadResult(true, false));
                JsonObject result = reply.get(2, TimeUnit.SECONDS);
                assertEquals("RELOAD_POSTPROCESSING_FAILED", string(result, "code"));
                assertTrue(result.get("committed").getAsBoolean());
                assertFalse(result.get("postprocessingSucceeded").getAsBoolean());
                assertEquals("committed", string(result, "effects"));
                assertFalse(result.get("retryable").getAsBoolean());
            } finally {
                control.close();
                worker.join(2_000);
                assertFalse(worker.isAlive());
            }
        }
        host.dispatch = Runnable::run;
        host.reloads = output -> CompletableFuture.completedFuture(new AuthoringControlHost.ReloadResult(false, false));
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            JsonObject result = control.execute(AuthoringControl.RELOAD_SCRIPTS, reload());
            assertEquals("RELOAD_NOT_COMMITTED", string(result, "code"));
            assertFalse(result.get("committed").getAsBoolean());
            assertEquals("unknown", string(result, "effects"));
        }
    }

    @Test void closeDuringRunningWorkAndInterruptedWaitNeverPromiseRollbackOrRetry() {
        Host host = new Host();
        CompletableFuture<AuthoringControlHost.CommandResult> stage = new CompletableFuture<>();
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            host.commands = (command, output) -> { control.close(); return stage; };
            JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
            assertEquals("closed", string(result, "status"));
            assertEquals("unknown", string(result, "effects"));
            assertFalse(result.get("retryable").getAsBoolean());
            assertFalse(stage.isCancelled());
            rejects("CONTROL_CLOSED", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
        }
        host.commands = (command, output) -> { Thread.currentThread().interrupt(); return new CompletableFuture<>(); };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            try {
                JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
                assertEquals("interrupted", string(result, "status"));
                assertEquals("unknown", string(result, "effects"));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test void preexistingInterruptPreventsOwnerClaimAndPreservesInterruptFlag() {
        Host host = new Host();
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            Thread.currentThread().interrupt();
            try {
                JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say cancelled"));
                assertEquals("interrupted", string(result, "status"));
                assertEquals("none", string(result, "effects"));
                assertEquals(0, host.commandCalls);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test void ownerQueuePumpingCannotStartTwoPreviouslyAdmittedOperations() throws Exception {
        Host host = new Host();
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        CompletableFuture<AuthoringControlHost.CommandResult> stage = new CompletableFuture<>();
        host.dispatch = queue::add;
        host.commands = (command, output) -> stage;
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            CompletableFuture<JsonObject> firstReply = new CompletableFuture<>();
            CompletableFuture<JsonObject> secondReply = new CompletableFuture<>();
            Thread first = worker(control, firstReply);
            Thread second = worker(control, secondReply);
            first.start();
            try {
                Runnable firstCallback = queue.poll(2, TimeUnit.SECONDS);
                assertNotNull(firstCallback);
                second.start();
                Runnable secondCallback = queue.poll(2, TimeUnit.SECONDS);
                assertNotNull(secondCallback);
                firstCallback.run();
                secondCallback.run();
                JsonObject rejected = secondReply.get(2, TimeUnit.SECONDS);
                assertEquals("CONTROL_BUSY", string(rejected, "code"));
                assertEquals("none", string(rejected, "effects"));
                assertEquals(1, host.commandCalls);
                assertFalse(firstReply.isDone());
                stage.complete(new AuthoringControlHost.CommandResult(1, true));
                assertEquals("succeeded", string(firstReply.get(2, TimeUnit.SECONDS), "status"));
            } finally {
                control.close();
                first.join(2_000);
                second.join(2_000);
                assertFalse(first.isAlive());
                assertFalse(second.isAlive());
            }
        }
    }

    private static Thread worker(AuthoringControl control, CompletableFuture<JsonObject> reply) {
        return new Thread(() -> {
            try { reply.complete(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"))); }
            catch (Throwable failure) { reply.completeExceptionally(failure); }
        });
    }

    @Test void staleQueuedTombstonesStillConsumeAdmissionUntilDrained() {
        Host host = new Host();
        AtomicLong clock = new AtomicLong();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        host.dispatch = task -> { queue.add(task); clock.addAndGet(EXPIRED); };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host, clock::get)) {
            for (int i = 0; i < AuthoringControl.MAX_PENDING; i++) {
                assertEquals("timed_out", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")), "status"));
            }
            rejects("CONTROL_BUSY", () -> control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")));
            assertEquals(AuthoringControl.MAX_PENDING, queue.size());
            queue.remove().run();
            control.execute(AuthoringControl.EXECUTE_COMMAND, command("say admitted"));
            assertEquals(AuthoringControl.MAX_PENDING, queue.size());
            while (!queue.isEmpty()) queue.remove().run();
            assertEquals(0, host.commandCalls);
        }
    }

    @Test void feedbackAndAuditAreBoundedDetachedAndNeverSerializeHostExceptions() {
        Host host = new Host();
        host.commands = (command, output) -> {
            for (int i = 0; i < 100; i++) output.accept("x".repeat(200));
            return CompletableFuture.completedFuture(new AuthoringControlHost.CommandResult(0, false));
        };
        try (AuthoringControl control = new AuthoringControl(ENABLED, host)) {
            JsonObject result = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say secret-argument"));
            assertEquals("COMMAND_FAILED", string(result, "code"));
            assertTrue(result.get("outputTruncated").getAsBoolean());
            JsonArray output = result.getAsJsonArray("output");
            assertTrue(output.size() <= AuthoringControl.MAX_OUTPUT_LINES);
            int chars = 0;
            for (JsonElement line : output) chars += line.getAsString().length();
            assertEquals(AuthoringControl.MAX_OUTPUT_CHARS, chars);
            host.commands = (command, sink) -> { throw new IllegalStateException("secret-exception"); };
            assertEquals("HOST_OPERATION_FAILED", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")), "code"));
            host.commands = (command, sink) -> CompletableFuture.failedFuture(new IllegalStateException("secret-stage"));
            assertEquals("HOST_OPERATION_FAILED", string(control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi")), "code"));
            for (int i = 0; i < AuthoringControl.MAX_AUDIT + 5; i++) control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
            JsonArray audit = control.audit();
            assertEquals(AuthoringControl.MAX_AUDIT, audit.size());
            assertFalse(audit.toString().contains("secret"));
            audit.get(0).getAsJsonObject().addProperty("status", "forged");
            assertNotEquals("forged", string(control.audit().get(0).getAsJsonObject(), "status"));
            host.dispatch = task -> { throw new IllegalStateException("secret-dispatch"); };
            JsonObject dispatch = control.execute(AuthoringControl.EXECUTE_COMMAND, command("say hi"));
            assertEquals("HOST_DISPATCH_FAILED", string(dispatch, "code"));
            assertEquals("none", string(dispatch, "effects"));
            assertFalse(dispatch.toString().contains("secret"));
        }
    }
}
