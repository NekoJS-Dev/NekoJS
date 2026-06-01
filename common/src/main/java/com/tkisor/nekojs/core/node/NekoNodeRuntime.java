package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.ScriptType;

/**
 * Per-context Node.js compatibility runtime.
 * Holds all Node-compatible subsystems and the module loader host.
 *
 * <p>Created once per {@link com.tkisor.nekojs.script.ScriptType} context
 * during {@link com.tkisor.nekojs.core.node.NekoNodeModuleInstaller#install install()}.
 * Exposed to JS as {@code __nekoNodeRuntime}.
 */
public final class NekoNodeRuntime implements AutoCloseable {
    private final ScriptType scriptType;
    private final NekoScriptModuleLoaderHost moduleLoaderHost;
    private final NekoNodeFS fs = new NekoNodeFS();
    private final NekoNodePath path = new NekoNodePath();
    private final NekoNodeOS os = new NekoNodeOS();
    private final NekoNodeTimers timers;
    private final NekoNodeProcess process = new NekoNodeProcess(fs);

    public NekoNodeRuntime(ScriptType scriptType, NekoScriptModuleLoaderHost moduleLoaderHost) {
        this.scriptType = scriptType;
        this.moduleLoaderHost = moduleLoaderHost;
        this.timers = new NekoNodeTimers(scriptType);
    }

    public ScriptType scriptType() {
        return scriptType;
    }

    public NekoScriptModuleLoaderHost moduleLoaderHost() {
        return moduleLoaderHost;
    }

    public NekoNodeFS fs() {
        return fs;
    }

    public NekoNodePath path() {
        return path;
    }

    public NekoNodeTimers timers() {
        return timers;
    }

    public NekoNodeOS os() {
        return os;
    }

    public NekoNodeProcess process() {
        return process;
    }

    public NekoNodeBuffer bufferFromString(String value, String encoding) {
        return NekoNodeBuffer.fromString(value, encoding);
    }

    public NekoNodeBuffer bufferAlloc(int size) {
        return NekoNodeBuffer.alloc(size);
    }

    public NekoNodeBuffer bufferConcat(NekoNodeBuffer[] buffers) {
        return NekoNodeBuffer.concat(buffers);
    }

    public int bufferByteLength(String value, String encoding) {
        return NekoNodeBuffer.byteLength(value, encoding);
    }

    public void flushReadyTimers() {
        timers.flushReadyCallbacks();
    }

    public boolean hasPendingTimers() {
        return timers.hasPendingCallbacks();
    }

    public MappedStackLine mapStackLine(String path, int line, int column) {
        String displayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
        if (displayPath == null) {
            displayPath = path;
        }
        SourceMapRegistry.OriginalPosition mapped = SourceMapRegistry.getMappedPosition(displayPath, line, column);
        String mappedPath = mapped.path != null && !mapped.path.isBlank() ? mapped.path : displayPath;
        return new MappedStackLine(mappedPath, mapped.line, mapped.column);
    }

    public record MappedStackLine(String path, int line, int column) {}

    @Override
    public void close() {
        timers.close();
    }
}
