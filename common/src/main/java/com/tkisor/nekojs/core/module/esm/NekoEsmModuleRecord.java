package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoPreparedModule;
import graal.graalvm.polyglot.Value;

import java.nio.file.Path;

public final class NekoEsmModuleRecord {
    private final String id;
    private final Path path;
    private final NekoPreparedModule prepared;
    private NekoEsmModuleState state = NekoEsmModuleState.NEW;
    private NekoEsmLinkMetadata linkMetadata;
    private Value namespace;
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

    public void state(NekoEsmModuleState state) {
        this.state = state;
    }

    public NekoEsmLinkMetadata linkMetadata() {
        return linkMetadata;
    }

    public void linkMetadata(NekoEsmLinkMetadata linkMetadata) {
        this.linkMetadata = linkMetadata;
    }

    public Value namespace() {
        return namespace;
    }

    public void namespace(Value namespace) {
        this.namespace = namespace;
    }

    public Throwable failure() {
        return failure;
    }

    public void failure(Throwable failure) {
        this.failure = failure;
        this.state = NekoEsmModuleState.FAILED;
    }
}
