package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.ScriptTypeView;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.StagesFacade;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Default implementation of {@link StagesFacade} exposed as the global {@code Stages} script
 * binding.
 *
 * <p>Delegates server/player mutations and reads to {@link StageRuntimeBridge#store()}, client
 * stage queries to {@link ClientStageMirror#SHARED}, and item-stage lookups to
 * {@link StageRuntimeBridge#requiredStagesForItem(NekoId)}. When invoked from
 * {@link ScriptType#CLIENT}, {@link #player(StagePlayerRef)} and {@link #server()} return
 * {@link StageHandle#clientReadOnly(StageReadView)} views that reject all stage mutations with
 * {@code [NEKO-4070]}.
 */
@ScriptTypeView(StagesFacade.class)
public final class StagesBinding implements StagesFacade {

    private final @Nullable ScriptType boundScriptType;

    /** Creates a {@code Stages} binding that resolves the calling {@link ScriptType} dynamically. */
    public StagesBinding() {
        this(null);
    }

    /** Creates a {@code Stages} binding pinned to {@code boundScriptType} (when non-null). */
    public StagesBinding(@Nullable ScriptType boundScriptType) {
        this.boundScriptType = boundScriptType;
        StageRuntimeBridge.ensureInitialized();
    }

    @Override
    public StageHandle player(@Nullable StagePlayerRef player) {
        if (isClientScriptContext()) {
            return StageHandle.clientReadOnly(ClientStageMirror.SHARED.playerView());
        }
        if (player == null) {
            return StageHandle.EMPTY;
        }
        return StageRuntimeBridge.store().playerHandle(player);
    }

    @Override
    public StageHandle server() {
        if (isClientScriptContext()) {
            return StageHandle.clientReadOnly(ClientStageMirror.SHARED.serverView());
        }
        return StageRuntimeBridge.store().serverHandle();
    }

    @Override
    public StageReadView clientPlayer() {
        return ClientStageMirror.SHARED.playerView();
    }

    @Override
    public StageReadView clientServer() {
        return ClientStageMirror.SHARED.serverView();
    }

    @Override
    public List<String> requiredStagesForItem(@Nullable NekoId itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("[NEKO-4070] Item ID must not be null");
        }
        return StageRuntimeBridge.requiredStagesForItem(itemId);
    }

    private boolean isClientScriptContext() {
        if (boundScriptType != null) {
            return boundScriptType == ScriptType.CLIENT;
        }
        try {
            Context current = Context.getCurrent();
            return current != null && ScriptContextRegistry.scriptTypeOf(current) == ScriptType.CLIENT;
        } catch (IllegalStateException noCurrentContext) {
            return false;
        }
    }
}
