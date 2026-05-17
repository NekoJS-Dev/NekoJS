package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoPreparedModule;
import graal.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class NekoEsmModuleRecord {
    private final String id;
    private final Path path;
    private final NekoPreparedModule prepared;
    private NekoEsmModuleState state = NekoEsmModuleState.NEW;
    private NekoEsmLinkMetadata linkMetadata;
    private Value namespace;
    private CompletableFuture<Value> evaluation;
    private boolean asyncEvaluation;
    private Throwable failure;

    public NekoEsmModuleRecord(String id, Path path, NekoPreparedModule prepared) {
        this.id = id;
        this.path = path;
        this.prepared = prepared;
    }

    public String id() {
        return id;
    }

    public Path path() {
        return path;
    }

    public NekoPreparedModule prepared() {
        return prepared;
    }

    public NekoEsmModuleState state() {
        return state;
    }

    public void beginLinking() {
        this.state = NekoEsmModuleState.LINKING;
    }

    public void linked(NekoEsmLinkMetadata linkMetadata) {
        this.linkMetadata = linkMetadata;
        this.state = NekoEsmModuleState.LINKED;
    }

    public NekoEsmLinkMetadata linkMetadata() {
        return linkMetadata;
    }

    public void linkMetadata(NekoEsmLinkMetadata linkMetadata) {
        this.linkMetadata = linkMetadata;
    }

    public boolean topLevelAwait() {
        return prepared.esmAst() != null && prepared.esmAst().topLevelAwait();
    }

    public Value namespace() {
        return namespace;
    }

    public void namespace(Value namespace) {
        this.namespace = namespace;
    }

    public CompletableFuture<Value> evaluation() {
        return evaluation;
    }

    public void beginEvaluation(CompletableFuture<Value> evaluation, boolean asyncEvaluation) {
        this.evaluation = evaluation;
        this.asyncEvaluation = this.asyncEvaluation || asyncEvaluation;
        if (evaluation != null) {
            this.state = this.asyncEvaluation ? NekoEsmModuleState.EVALUATING_ASYNC : NekoEsmModuleState.EVALUATING;
        }
    }

    public void markAsyncEvaluation() {
        this.asyncEvaluation = true;
        if (state == NekoEsmModuleState.EVALUATING) {
            state = NekoEsmModuleState.EVALUATING_ASYNC;
        } else if (state == NekoEsmModuleState.EVALUATED) {
            state = NekoEsmModuleState.EVALUATED_ASYNC;
        }
    }

    public boolean asyncEvaluation() {
        return asyncEvaluation;
    }

    public boolean evaluating() {
        return state == NekoEsmModuleState.EVALUATING || state == NekoEsmModuleState.EVALUATING_ASYNC;
    }

    public boolean evaluated() {
        return state == NekoEsmModuleState.EVALUATED || state == NekoEsmModuleState.EVALUATED_ASYNC;
    }

    public Throwable failure() {
        return failure;
    }

    public void evaluated(Value namespace) {
        this.namespace = namespace;
        if (evaluation != null) {
            if (!evaluation.isDone()) {
                evaluation.complete(namespace);
            }
        } else {
            evaluation = CompletableFuture.completedFuture(namespace);
        }
        this.state = asyncEvaluation ? NekoEsmModuleState.EVALUATED_ASYNC : NekoEsmModuleState.EVALUATED;
    }

    public void failure(Throwable failure) {
        this.failure = failure;
        this.state = NekoEsmModuleState.FAILED;
        if (evaluation != null && !evaluation.isDone()) {
            evaluation.completeExceptionally(failure);
        }
    }
}
