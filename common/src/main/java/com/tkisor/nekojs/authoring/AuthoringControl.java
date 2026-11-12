package com.tkisor.nekojs.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.authoring.AuthoringControlHost;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Default-off live control policy and bounded operation lifecycle, independent of Minecraft and transport.
 * Only the requesting worker waits. Owner-thread callbacks atomically claim admitted work before starting
 * the host stage; a timed-out or closed queued operation cannot later acquire permission to execute.
 */
public final class AuthoringControl implements AutoCloseable {
    public static final String EXECUTE_COMMAND = "execute_game_command";
    public static final String RELOAD_SCRIPTS = "reload_scripts";
    public static final int DEFAULT_TIMEOUT_MS = 5_000;
    public static final int MAX_TIMEOUT_MS = 8_000;
    public static final int MAX_COMMAND_LENGTH = 2_048;
    public static final int MAX_PENDING = 32;
    public static final int MAX_AUDIT = 64;
    public static final int MAX_OUTPUT_LINES = 64;
    public static final int MAX_OUTPUT_CHARS = 8_192;
    private static final Pattern COMMAND_ROOT = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9_.-]+)?");

    /** Independent switches and an immutable, exact command-root allowlist. An allowlist is not a sandbox. */
    public record Policy(boolean commandsEnabled, boolean reloadEnabled, Set<String> commandAllowlist) {
        public Policy {
            if (commandAllowlist == null) throw AuthoringJson.invalid("/commandAllowlist", "Expected command root tokens.");
            if (commandAllowlist.size() > 128 || commandAllowlist.stream()
                    .anyMatch(root -> root == null || root.length() > 128 || !COMMAND_ROOT.matcher(root).matches())) {
                throw AuthoringJson.invalid("/commandAllowlist", "Expected at most 128 exact command root tokens.");
            }
            commandAllowlist = Set.copyOf(commandAllowlist);
        }

        public static Policy disabled() { return new Policy(false, false, Set.of()); }
    }

    private static final List<ScriptType> RELOAD_TYPES = List.of(ScriptType.SERVER, ScriptType.CLIENT, ScriptType.TEST);

    private final Object lock = new Object();
    private final Policy policy;
    private final AuthoringControlHost host;
    private final LongSupplier nanoTime;
    private final Map<String, Operation> pending = new LinkedHashMap<>();
    private final ArrayDeque<JsonObject> audit = new ArrayDeque<>();
    private boolean closed;
    private Operation active;

    /** A null host is a genuine offline adapter and always rejects live actions. */
    public AuthoringControl(Policy policy, AuthoringControlHost host) {
        this(policy, host, System::nanoTime);
    }

    AuthoringControl(Policy policy, AuthoringControlHost host, LongSupplier nanoTime) {
        this.policy = Objects.requireNonNull(policy);
        this.host = host;
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    /** Whether this operation may be advertised; forged requests still pass the full admission checks. */
    public boolean available(String operation) {
        synchronized (lock) {
            if (closed) return false;
            Map<ScriptType, Long> epochs = liveEpochs();
            retireStale(epochs);
            return switch (operation) {
                case EXECUTE_COMMAND -> policy.commandsEnabled() && !policy.commandAllowlist().isEmpty()
                        && epochs.get(ScriptType.SERVER) >= 0;
                case RELOAD_SCRIPTS -> !availableReloadTypes(epochs).isEmpty();
                default -> false;
            };
        }
    }

    /** Reload targets currently admitted by policy and the live owner-thread adapter. */
    public List<String> availableReloadTypes() {
        synchronized (lock) {
            if (closed) return List.of();
            Map<ScriptType, Long> epochs = liveEpochs();
            retireStale(epochs);
            return availableReloadTypes(epochs);
        }
    }

    private List<String> availableReloadTypes(Map<ScriptType, Long> epochs) {
        if (!policy.reloadEnabled()) return List.of();
        return RELOAD_TYPES.stream().filter(type -> epochs.get(type) >= 0).map(type -> type.name).toList();
    }

    /**
     * Validates, admits and waits for one live operation. Never call from a Minecraft owner thread:
     * asynchronous stages must leave that thread free. Rejections throw AuthoringException before
     * dispatch; admitted results report effects explicitly and never include host Throwable details.
     */
    public JsonObject execute(String operation, JsonObject request) {
        if (operation.equals("operation_status")) return operationStatus(request);
        Request validated = validate(operation, request);
        Operation admitted;
        synchronized (lock) {
            if (closed) throw rejected(503, "CONTROL_CLOSED", "Live controls are closed.");
            Map<ScriptType, Long> epochs = liveEpochs();
            retireStale(epochs);
            long epoch = epochs.get(validated.type());
            if (epoch < 0) {
                if (host != null && validated.type() == ScriptType.CLIENT) {
                    throw rejected(409, "RELOAD_UNAVAILABLE", "CLIENT reload requires a live client runtime.");
                }
                throw rejected(409, "LIVE_RUNTIME_REQUIRED", "A live runtime for the selected Script Type is required.");
            }
            if (EXECUTE_COMMAND.equals(operation)) {
                if (!policy.commandsEnabled()) throw rejected(403, "CONTROL_DISABLED", "Game commands are disabled.");
                String root = validated.command().split(" ", 2)[0];
                if (!policy.commandAllowlist().contains(root)) {
                    throw rejected(403, "COMMAND_NOT_ALLOWED", "The exact command root is not allowed.");
                }
            } else if (!policy.reloadEnabled()) {
                throw rejected(403, "CONTROL_DISABLED", "Script reload is disabled.");
            }
            if (active != null || pending.size() >= MAX_PENDING) {
                throw rejected(429, "CONTROL_BUSY", "Another live operation is running or admission is full.");
            }
            admitted = new Operation(operation, validated.command(), validated.type(), epoch,
                    nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(validated.timeoutMs()));
            pending.put(admitted.id, admitted);
        }
        try {
            host.enqueue(admitted.type, () -> claim(admitted));
        } catch (RuntimeException failure) {
            synchronized (lock) {
                // A conforming host throws before admission. Still report uncertainty if it already ran.
                report(admitted, "failed", "HOST_DISPATCH_FAILED", admitted.claimed ? "unknown" : "none", null);
                if (!admitted.claimed) {
                    admitted.finished = true;
                    pending.remove(admitted.id);
                }
            }
        }
        try {
            long remaining = admitted.deadline - nanoTime.getAsLong();
            return admitted.reply.get(Math.max(0, remaining), TimeUnit.NANOSECONDS).deepCopy();
        } catch (TimeoutException failure) {
            synchronized (lock) {
                report(admitted, "timed_out", "CONTROL_TIMEOUT", admitted.claimed ? "unknown" : "none", null);
                return admitted.reply.join().deepCopy();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            synchronized (lock) {
                report(admitted, "interrupted", "CONTROL_INTERRUPTED", admitted.claimed ? "unknown" : "none", null);
                return admitted.reply.join().deepCopy();
            }
        } catch (ExecutionException impossible) {
            throw new AssertionError("Control replies never complete exceptionally.", impossible);
        }
    }

    private Request validate(String operation, JsonObject request) {
        if (request == null) throw AuthoringJson.invalid("/", "Expected a request object.");
        String command = null;
        ScriptType target = ScriptType.SERVER;
        if (EXECUTE_COMMAND.equals(operation)) {
            AuthoringJson.keys(request, "", "command", "timeoutMs");
            command = AuthoringJson.string(request, "command", "");
            if (command.length() > MAX_COMMAND_LENGTH || !command.equals(command.strip())
                    || command.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) {
                throw AuthoringJson.invalid("/command", "Expected one bounded command line without control characters or surrounding whitespace.");
            }
            if (command.startsWith("/")) command = command.substring(1);
            String root = command.split(" ", 2)[0];
            if (!COMMAND_ROOT.matcher(root).matches()) {
                throw AuthoringJson.invalid("/command", "Expected an exact command root followed by optional space-separated arguments.");
            }
        } else if (RELOAD_SCRIPTS.equals(operation)) {
            AuthoringJson.keys(request, "", "type", "timeoutMs");
            String type = AuthoringJson.string(request, "type", "");
            target = switch (type) {
                case "server" -> ScriptType.SERVER;
                case "client" -> ScriptType.CLIENT;
                case "test" -> ScriptType.TEST;
                case "startup" -> throw rejected(409, "RESTART_REQUIRED", "STARTUP changes require a process restart.");
                default -> throw rejected(400, "UNSUPPORTED_RELOAD", "Only full SERVER, CLIENT and TEST reloads are supported.");
            };
        } else {
            throw rejected(400, "UNKNOWN_CONTROL", "Unknown live control operation.");
        }
        int timeout = request.has("timeoutMs")
                ? AuthoringJson.integer(request.get("timeoutMs"), "/timeoutMs", 1, MAX_TIMEOUT_MS)
                : DEFAULT_TIMEOUT_MS;
        return new Request(command, target, timeout);
    }

    private Map<ScriptType, Long> liveEpochs() {
        Map<ScriptType, Long> epochs = new EnumMap<>(ScriptType.class);
        RELOAD_TYPES.forEach(type -> epochs.put(type, liveEpoch(type)));
        return epochs;
    }

    private long liveEpoch(ScriptType type) {
        if (host == null) return -1;
        try {
            return host.epoch(type);
        } catch (RuntimeException unavailable) {
            return -1;
        }
    }

    /** Called under lock: only a retired target identity releases an unresolved operation's gate. */
    private void retireStale(Map<ScriptType, Long> epochs) {
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Operation operation = iterator.next();
            long epoch = epochs.get(operation.type);
            if (epoch >= 0 && operation.epoch == epoch) continue;
            // Fence late callbacks before releasing accounting for that target's replacement runtime.
            operation.finished = true;
            iterator.remove();
            if (active == operation) active = null;
            String effects = operation.claimed ? "unknown" : "none";
            if (operation.reply.isDone()) {
                recordAudit(operation, "rejected", "STALE_RUNTIME", effects, null);
            } else {
                report(operation, "rejected", "STALE_RUNTIME", effects, null);
            }
        }
    }

    private void claim(Operation operation) {
        synchronized (lock) {
            if (operation.claimed || operation.finished) return;
            if (!operation.reply.isDone()) {
                if (closed) report(operation, "closed", "CONTROL_CLOSED", "none", null);
                else if (operation.requester.isInterrupted()) {
                    report(operation, "interrupted", "CONTROL_INTERRUPTED", "none", null);
                }
                else if (nanoTime.getAsLong() - operation.deadline >= 0) {
                    report(operation, "timed_out", "CONTROL_TIMEOUT", "none", null);
                } else if (liveEpoch(operation.type) != operation.epoch) {
                    report(operation, "rejected", "STALE_RUNTIME", "none", null);
                } else if (active != null) {
                    report(operation, "rejected", "CONTROL_BUSY", "none", null);
                }
            }
            if (operation.reply.isDone()) {
                operation.finished = true;
                pending.remove(operation.id);
                return;
            }
            operation.claimed = true;
            // Resource reload may pump the owner queue; a claimed stage exclusively owns live controls.
            active = operation;
        }
        try {
            CompletionStage<?> stage = EXECUTE_COMMAND.equals(operation.kind)
                    ? host.executeCommand(operation.command, text -> appendOutput(operation, text))
                    : host.reload(operation.type, text -> appendOutput(operation, text));
            Objects.requireNonNull(stage).whenComplete((value, failure) -> finish(operation, value, failure));
        } catch (Exception failure) {
            finish(operation, null, failure);
        }
    }

    private void finish(Operation operation, Object value, Throwable failure) {
        synchronized (lock) {
            if (operation.finished) return;
            operation.finished = true;
            pending.remove(operation.id);
            if (active == operation) active = null;
            if (!operation.reply.isDone() && nanoTime.getAsLong() - operation.deadline >= 0) {
                report(operation, "timed_out", "CONTROL_TIMEOUT", "unknown", null);
            }
            JsonObject details = new JsonObject();
            String status = "failed";
            String code = "HOST_OPERATION_FAILED";
            String effects = "unknown";
            if (failure == null && EXECUTE_COMMAND.equals(operation.kind)
                    && value instanceof AuthoringControlHost.CommandResult command) {
                details.addProperty("commandReturnValue", command.returnValue());
                status = command.success() ? "succeeded" : "failed";
                code = command.success() ? "OK" : "COMMAND_FAILED";
                effects = command.success() ? "applied" : "unknown";
            } else if (failure == null && RELOAD_SCRIPTS.equals(operation.kind)
                    && value instanceof AuthoringControlHost.ReloadResult reload
                    && (reload.committed() || !reload.postprocessingSucceeded())) {
                details.addProperty("committed", reload.committed());
                details.addProperty("postprocessingSucceeded", reload.postprocessingSucceeded());
                if (reload.testRunReport() != null) {
                    details.add("testRunReport", testRunReport(reload.testRunReport()));
                }
                status = reload.committed() && reload.postprocessingSucceeded() ? "succeeded" : "failed";
                code = !reload.committed() ? "RELOAD_NOT_COMMITTED"
                        : reload.postprocessingSucceeded() ? "OK" : "RELOAD_POSTPROCESSING_FAILED";
                effects = reload.committed() ? "committed" : "unknown";
                if (operation.type == ScriptType.TEST && reload.testRunReport() != null) {
                    var report = reload.testRunReport();
                    if (!report.executionCompleted()) { status = "failed"; code = "TEST_EXECUTION_INCOMPLETE"; }
                    else if (!report.executionSucceeded()) { status = "failed"; code = "TEST_EXECUTION_FAILED"; }
                    else if (Boolean.FALSE.equals(report.assertionsPassed())) { status = "failed"; code = "TEST_ASSERTIONS_FAILED"; }
                }
            }
            if (operation.reply.isDone()) {
                // A timeout/close response cannot be rewritten; retain a bounded final audit transition.
                recordAudit(operation, status, code, effects, details);
            } else {
                report(operation, status, code, effects, details);
            }
        }
    }

    private void appendOutput(Operation operation, String text) {
        if (text == null) return;
        synchronized (lock) {
            if (operation.reply.isDone() || operation.finished) return;
            int remaining = MAX_OUTPUT_CHARS - operation.outputChars;
            if (operation.output.size() >= MAX_OUTPUT_LINES || remaining == 0) {
                operation.outputTruncated = true;
                return;
            }
            StringBuilder plain = new StringBuilder(Math.min(remaining, text.length()));
            // Cap scanned input too: a huge feedback string of discarded controls is still bounded work.
            for (int i = 0; i < text.length() && i < remaining; i++) {
                char c = text.charAt(i);
                if (!Character.isISOControl(c) && c != 0x2028 && c != 0x2029) plain.append(c);
                else if (c == '\n' || c == '\r' || c == '\t') plain.append(' ');
            }
            if (text.length() > remaining) operation.outputTruncated = true;
            operation.output.add(plain.toString());
            operation.outputChars += plain.length();
        }
    }

    private void report(Operation operation, String status, String code, String effects, JsonObject details) {
        if (operation.reply.isDone()) return;
        JsonObject result = AuthoringJson.obj("operationId", operation.id, "operation", operation.kind,
                "status", status, "code", code, "effects", effects, "retryable", !operation.claimed,
                "output", operation.output.deepCopy(), "outputTruncated", operation.outputTruncated);
        if (RELOAD_SCRIPTS.equals(operation.kind)) result.addProperty("type", operation.type.name);
        if (details != null) details.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        recordAudit(operation, status, code, effects, details);
        operation.reply.complete(result);
    }

    private void recordAudit(Operation operation, String status, String code, String effects, JsonObject details) {
        if (audit.size() == MAX_AUDIT) audit.removeFirst();
        JsonObject entry = AuthoringJson.obj("operationId", operation.id, "operation", operation.kind,
                "status", status, "code", code, "effects", effects, "retryable", !operation.claimed);
        if (RELOAD_SCRIPTS.equals(operation.kind)) entry.addProperty("type", operation.type.name);
        if (details != null) details.entrySet().forEach(detail -> entry.add(detail.getKey(), detail.getValue().deepCopy()));
        if (operation.reply.isDone()) entry.add("initialResult", operationMetadata(operation.reply.getNow(null)));
        audit.addLast(entry);
    }

    /** Reads existing admission/audit state only; never dispatches or retries a mutation. */
    private JsonObject operationStatus(JsonObject request) {
        AuthoringJson.keys(request, "", "operationId");
        String supplied = AuthoringJson.string(request, "operationId", "");
        String id;
        try {
            id = UUID.fromString(supplied).toString();
            if (!id.equalsIgnoreCase(supplied)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw AuthoringJson.invalid("/operationId", "Expected a complete operation UUID.");
        }
        synchronized (lock) {
            if (!closed) retireStale(liveEpochs());
            Operation operation = pending.get(id);
            JsonObject first = null;
            JsonObject latest = null;
            for (JsonObject entry : audit) {
                if (!id.equals(entry.get("operationId").getAsString())) continue;
                if (first == null) first = operationMetadata(entry);
                latest = entry;
            }
            if (operation == null && latest == null) {
                return AuthoringJson.obj("operationId", id, "found", false, "state", "unknown_or_expired",
                        "terminal", null, "retryable", false);
            }
            if (latest != null && latest.has("initialResult")) first = latest.getAsJsonObject("initialResult").deepCopy();
            if (operation != null && operation.reply.isDone()) first = operationMetadata(operation.reply.getNow(null));
            boolean claimed = operation != null ? operation.claimed : !latest.get("retryable").getAsBoolean();
            boolean terminal = operation == null || operation.finished || (!claimed && operation.reply.isDone());
            String state = terminal ? "terminal" : claimed ? "running" : "queued";
            if (!terminal && first != null && "timed_out".equals(first.get("status").getAsString())) state = "timed_out_pending";
            JsonObject result = AuthoringJson.obj("operationId", id, "found", true, "state", state,
                    "terminal", terminal, "claimed", claimed, "retryable", terminal && !claimed);
            if (first != null) result.add("initialResult", first);
            if (latest != null) result.add("latestResult", operationMetadata(latest));
            return result;
        }
    }

    static JsonObject testRunReport(com.tkisor.nekojs.api.testing.TestRunReport report) {
        if (report == null) return null;
        return AuthoringJson.obj("executionCompleted", report.executionCompleted(), "executionSucceeded", report.executionSucceeded(),
                "passed", report.passed(), "failed", report.failed(), "assertionsPassed", report.assertionsPassed(), "overallPassed", report.overallPassed());
    }

    private static JsonObject operationMetadata(JsonObject result) {
        JsonObject copy = new JsonObject();
        for (String key : List.of("operationId", "operation", "status", "code", "effects", "retryable",
                "type", "commandReturnValue", "committed", "postprocessingSucceeded", "testRunReport")) {
            if (result.has(key)) copy.add(key, result.get(key).deepCopy());
        }
        return copy;
    }

    /** Bounded metadata only: commands, arguments, feedback and exception details are never retained here. */
    public JsonArray audit() {
        synchronized (lock) {
            JsonArray copy = new JsonArray();
            audit.forEach(entry -> copy.add(entry.deepCopy()));
            return copy;
        }
    }

    /** Stops admission before root shutdown; does not interrupt owner threads or undo started effects. */
    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            for (Operation operation : pending.values()) {
                report(operation, "closed", "CONTROL_CLOSED", operation.claimed ? "unknown" : "none", null);
            }
        }
    }

    private static AuthoringException rejected(int status, String code, String message) {
        return new AuthoringException(status, code, "/", message);
    }

    private record Request(String command, ScriptType type, int timeoutMs) {}

    private static final class Operation {
        final String id = UUID.randomUUID().toString();
        final Thread requester = Thread.currentThread();
        final String kind;
        final String command;
        final ScriptType type;
        final long epoch;
        final long deadline;
        final CompletableFuture<JsonObject> reply = new CompletableFuture<>();
        final JsonArray output = new JsonArray();
        int outputChars;
        boolean outputTruncated;
        boolean claimed;
        boolean finished;

        Operation(String kind, String command, ScriptType type, long epoch, long deadline) {
            this.kind = kind;
            this.command = command;
            this.type = type;
            this.epoch = epoch;
            this.deadline = deadline;
        }
    }
}
