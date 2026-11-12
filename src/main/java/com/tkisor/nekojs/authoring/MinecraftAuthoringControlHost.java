package com.tkisor.nekojs.authoring;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.authoring.AuthoringControlHost;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.mixin.accessor.CommandsAccessor;
import com.tkisor.nekojs.resource.ScriptPackDataManager;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owner-thread bridge for the separately authorized live authoring controls. */
public final class MinecraftAuthoringControlHost implements AuthoringControlHost {
    private final NekoRuntimeRoot root;
    private final Supplier<MinecraftServer> currentServer;
    private final AuthoringClientExecutor client;
    private WeakReference<MinecraftServer> observedServer = new WeakReference<>(null);
    private long observedEpoch = -1;

    public MinecraftAuthoringControlHost(NekoRuntimeRoot root, Supplier<MinecraftServer> currentServer) {
        this(root, currentServer, null);
    }

    public MinecraftAuthoringControlHost(NekoRuntimeRoot root, Supplier<MinecraftServer> currentServer,
                                         AuthoringClientExecutor client) {
        this.root = Objects.requireNonNull(root);
        this.currentServer = Objects.requireNonNull(currentServer);
        this.client = client;
    }

    @Override
    public long epoch(ScriptType type) {
        return switch (Objects.requireNonNull(type)) {
            case SERVER, TEST -> epoch();
            case CLIENT -> client == null ? -1 : client.epoch();
            case STARTUP -> -1;
        };
    }

    @Override
    public void enqueue(ScriptType type, Runnable operation) {
        if (type == ScriptType.SERVER || type == ScriptType.TEST) {
            enqueue(operation);
        } else if (type == ScriptType.CLIENT && client != null) {
            client.execute(admitted(operation));
        } else {
            throw new UnsupportedOperationException("[NEKO-1019] The selected reload owner thread is unavailable.");
        }
    }

    @Override
    public CompletionStage<ReloadResult> reload(ScriptType type, Consumer<String> output) {
        if (type == ScriptType.SERVER) return reloadServer(output);
        ReloadResult result;
        if (type == ScriptType.CLIENT && client != null) {
            result = client.call(() -> reloadCandidate(ScriptType.CLIENT, output));
        } else if (type == ScriptType.TEST) {
            MinecraftServer server = requireOwnerServer(true);
            long expectedEpoch = epoch();
            requireCurrent(server, expectedEpoch);
            result = runTestCandidate(output);
            requireCurrent(server, expectedEpoch);
        } else {
            throw new UnsupportedOperationException("[NEKO-1019] The selected Script Type does not support live reload.");
        }
        return CompletableFuture.completedFuture(result);
    }

    private ReloadResult runTestCandidate(Consumer<String> output) {
        var previous = root.latestTestRunReport();
        try {
            root.scriptManagerOf(ScriptType.TEST);
            var result = root.runTests();
            output.accept(com.tkisor.nekojs.core.lifecycle.RuntimeCommandResultFormatter.testResult(result));
            boolean completed = result.isConfigured() && result.isCompleted();
            return new ReloadResult(completed, completed, result.testRunReport());
        } catch (RuntimeException failure) {
            NekoJS.LOGGER.warn("[NEKO-1008] TEST candidate did not commit", failure);
            output.accept("[NEKO-1008] TEST candidate did not commit; inspect structured diagnostics.");
            var report = root.latestTestRunReport();
            return new ReloadResult(false, false, report == previous ? null : report);
        }
    }

    private ReloadResult reloadCandidate(ScriptType type, Consumer<String> output) {
        NekoRuntimeRoot.ReloadResult candidate;
        try {
            candidate = root.reload(type);
        } catch (RuntimeException failure) {
            NekoJS.LOGGER.warn("[NEKO-1008] {} candidate did not commit", type, failure);
            output.accept("[NEKO-1008] " + type + " candidate did not commit; inspect local diagnostics.");
            return new ReloadResult(false, false);
        }
        if (candidate.success()) {
            output.accept("NekoJS " + type + " reload committed: generation=" + candidate.generation());
        } else {
            output.accept("[NEKO-1008] " + type + " candidate did not commit; inspect local diagnostics.");
        }
        // CLIENT has no SERVER follow-ups; TEST execution and assertion outcomes use runTestCandidate.
        return new ReloadResult(candidate.success(), candidate.success());
    }

    @Override
    public synchronized long epoch() {
        MinecraftServer server = currentServer.get();
        if (!root.isAuthoringControlActive() || root.globalState().isClosed() || server == null || server.isStopped()) {
            observedServer.clear();
            observedEpoch = -1;
        } else if (observedServer.get() != server) {
            observedServer = new WeakReference<>(server);
            observedEpoch = AuthoringClientExecutor.nextEpoch();
        }
        return observedEpoch;
    }

    @Override
    public void enqueue(Runnable operation) {
        Objects.requireNonNull(operation);
        MinecraftServer server = requireOwnerServer(false);
        // Core rechecks the admission epoch inside this callback, including callbacks queued on an old server.
        Runnable admitted = admitted(operation);
        if (server.isSameThread()) admitted.run();
        else server.execute(admitted);
    }

    private static Runnable admitted(Runnable operation) {
        Objects.requireNonNull(operation);
        return () -> {
            try {
                operation.run();
            } catch (RuntimeException failure) {
                // Never turn an already admitted or inline callback into a second dispatch failure.
                NekoJS.LOGGER.error("[NEKO-1020] Admitted owner callback failed", failure);
            }
        };
    }

    @Override
    public CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) {
        MinecraftServer server = requireOwnerServer(true);
        long expectedEpoch = epoch();
        requireCurrent(server, expectedEpoch);
        BooleanSupplier stillCurrent = () -> isCurrent(server, expectedEpoch);
        // Nested dispatch only appends work, so its return cannot establish command completion.
        if (CommandsAccessor.nekojs$currentExecutionContext().get() != null) {
            output.accept("[NEKO-1021] Live command rejected because another command context is active.");
            return CompletableFuture.completedFuture(new CommandResult(0, false));
        }
        class CommandOutcome {
            private int returnValue;
            private boolean succeeded;

            void record(boolean success, int value) {
                returnValue += value;
                succeeded |= success;
            }

            CommandResult result() {
                return new CommandResult(returnValue, succeeded);
            }
        }
        CommandOutcome outcome = new CommandOutcome();
        CommandSource feedback = new RequestFeedback(output, stillCurrent);
        var source = AuthoringCommandPlatform.createSource(server, feedback).withCallback(outcome::record);
        var parsed = server.getCommands().getDispatcher().parse(command, source);
        try {
            Commands.validateParseResults(parsed);
        } catch (CommandSyntaxException failure) {
            feedback.sendSystemMessage(Component.literal(failure.getRawMessage().getString()));
            return CompletableFuture.completedFuture(new CommandResult(0, false));
        }
        requireCurrent(server, expectedEpoch);
        // Callbacks can report multiple branches before dispatch finishes; none is completion by itself.
        server.getCommands().performCommand(parsed, command);
        requireCurrent(server, expectedEpoch);
        // Rejected or incomplete commands may return without a callback and must complete as failures.
        return CompletableFuture.completedFuture(outcome.result());
    }

    @Override
    public CompletionStage<ReloadResult> reloadServer(Consumer<String> output) {
        MinecraftServer server = requireOwnerServer(true);
        long expectedEpoch = epoch();
        requireCurrent(server, expectedEpoch);
        NekoRuntimeRoot.ReloadResult candidate;
        try {
            candidate = root.reload(ScriptType.SERVER);
        } catch (RuntimeException failure) {
            NekoJS.LOGGER.warn("[NEKO-1008] SERVER candidate did not commit", failure);
            output.accept("[NEKO-1008] SERVER candidate did not commit; inspect local server diagnostics.");
            return CompletableFuture.completedFuture(new ReloadResult(false, false));
        }
        if (!candidate.success()) {
            output.accept("[NEKO-1008] SERVER candidate did not commit.");
            return CompletableFuture.completedFuture(new ReloadResult(false, false));
        }
        output.accept("NekoJS SERVER reload committed: generation=" + candidate.generation());
        try {
            requireCurrent(server, expectedEpoch);
            boolean recipesApplied = applyRecipes(server);
            if (!recipesApplied) {
                output.accept("[NEKO-1022] SERVER committed, but recipe replay is unavailable on this runtime.");
            }
            requireCurrent(server, expectedEpoch);
            // Always refresh the selection: the last previously mounted pack may have been removed.
            var activation = ScriptPackDataManager.activateForServerChecked(
                    server, ScriptPackRegistry.get().enabledPacks());
            if (!activation.success()) {
                output.accept("[NEKO-1022] SERVER committed, but script pack activation failed.");
                return CompletableFuture.completedFuture(new ReloadResult(true, false));
            }
            requireCurrent(server, expectedEpoch);
            if (!activation.reloadRequired()) {
                return CompletableFuture.completedFuture(new ReloadResult(true, recipesApplied));
            }
            BooleanSupplier stillCurrent = () -> isCurrent(server, expectedEpoch);
            // A resource reload replays recipes again; its future alone does not report swallowed script failures.
            var previousRecipeManager = server.getRecipeManager();
            IRecipeManagerExtension.ReplayObservation replay = previousRecipeManager instanceof IRecipeManagerExtension extension
                    ? extension.nekojs$observeScriptReplays() : () -> false;
            return ScriptPackDataManager.reloadServerResourcesAsync(server, stillCurrent)
                    .handle((reloaded, failure) -> {
                        boolean completed = failure == null && Boolean.TRUE.equals(reloaded)
                                && stillCurrent.getAsBoolean();
                        if (completed) {
                            // Successful resource completion runs on the owner and may have installed a new manager.
                            var currentRecipeManager = server.getRecipeManager();
                            completed = currentRecipeManager == previousRecipeManager ? replay.succeeded()
                                    : currentRecipeManager instanceof IRecipeManagerExtension extension
                                    && extension.nekojs$scriptReplaysSucceededSinceCreation();
                        }
                        if (!completed) {
                            output.accept("[NEKO-1022] SERVER committed, but resource follow-up did not complete.");
                        }
                        return new ReloadResult(true, recipesApplied && completed);
                    });
        } catch (RuntimeException failure) {
            NekoJS.LOGGER.warn("[NEKO-1022] SERVER committed, but postprocessing failed", failure);
            output.accept("[NEKO-1022] SERVER committed, but postprocessing failed; inspect local server diagnostics.");
            return CompletableFuture.completedFuture(new ReloadResult(true, false));
        }
    }

    private boolean applyRecipes(MinecraftServer server) {
        if (server.getRecipeManager() instanceof IRecipeManagerExtension extension) {
            return extension.nekojs$applyScriptsChecked();
        }
        return false;
    }

    private MinecraftServer requireOwnerServer(boolean ownerRequired) {
        long expectedEpoch = epoch();
        MinecraftServer server = currentServer.get();
        if (!isCurrent(server, expectedEpoch)) throw staleRuntime();
        if (ownerRequired && !server.isSameThread()) {
            throw new IllegalStateException("[NEKO-1023] Live controls require the server owner thread.");
        }
        return server;
    }

    private synchronized boolean isCurrent(MinecraftServer server, long expectedEpoch) {
        return expectedEpoch >= 0 && epoch() == expectedEpoch && observedServer.get() == server;
    }

    private void requireCurrent(MinecraftServer server, long expectedEpoch) {
        if (!isCurrent(server, expectedEpoch)) throw staleRuntime();
    }

    private static IllegalStateException staleRuntime() {
        return new IllegalStateException("[NEKO-1024] Live server runtime is no longer current.");
    }

    private record RequestFeedback(Consumer<String> output, BooleanSupplier stillCurrent) implements CommandSource {
        @Override
        public void sendSystemMessage(Component message) {
            if (stillCurrent.getAsBoolean()) output.accept(message.getString());
        }

        @Override
        public boolean acceptsSuccess() { return true; }

        @Override
        public boolean acceptsFailure() { return true; }

        @Override
        public boolean shouldInformAdmins() { return false; }
    }
}
