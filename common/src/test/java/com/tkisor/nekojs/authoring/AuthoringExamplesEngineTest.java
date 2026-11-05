package com.tkisor.nekojs.authoring;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes bundled source unchanged through the real module pipeline, not a documentation-only parser. */
class AuthoringExamplesEngineTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Context context;
    private NekoNodeRuntime runtime;
    private NekoScriptModuleLoaderHost loader;
    private boolean ownsTestLogger;

    @AfterEach
    void close() throws Exception {
        try {
            if (runtime != null) runtime.close();
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (ownsTestLogger) closeTestLogger();
            }
        }
    }

    @Test
    void bundledCommonJsEntryResolvesItsPureHelperAndReusesExports() throws Exception {
        boot(ScriptType.SERVER);
        Path entry = copyExample("modules/hello.cjs", paths.serverScripts().resolve("examples/modules/hello.cjs"));
        copyExample("modules/lib/message.cjs", entry.getParent().resolve("lib/message.cjs"));

        Value exports = asValue(loader.loadEntry(entry.toString()));
        assertEquals("Hello, NekoJS!", exports.getMember("message").asString());
        Value helper = asValue(loader.requireFrom(entry.toString(), "./lib/message.cjs"));
        Value helperAgain = asValue(loader.requireFrom(entry.toString(), "./lib/message.cjs"));
        context.getBindings("js").putMember("helperFirst", helper);
        context.getBindings("js").putMember("helperAgain", helperAgain);
        assertTrue(context.eval("js", "helperFirst === helperAgain").asBoolean(),
                "repeated require must return the same cached helper exports");
        assertEquals("Hello, Reader!", helper.getMember("formatGreeting").execute("Reader").asString());
        assertEquals(1, helper.getMemberKeys().size(), "the discovered helper only exports its pure function");
    }

    @Test
    void bundledTestScriptUsesTheRealAssertionBinding() throws Exception {
        Object cfg = log4jConfiguration();
        ownsTestLogger = cfg.getClass().getMethod("getAppender", String.class)
                .invoke(cfg, "NekoJS-File-test") == null;
        boot(ScriptType.TEST);
        TestJS assertions = new TestJS();
        context.getBindings("js").putMember("Test", assertions);
        Path entry = copyExample("test/assertions.js", paths.testScripts().resolve("examples/assertions.js"));

        loader.loadEntry(entry.toString());

        assertEquals(3, assertions.passed());
        assertEquals(0, assertions.failed());
    }

    @Test
    void bundledCounterUsesRealTsxAndSignalsWithOnlyTheClientHostReplaced() throws Exception {
        boot(ScriptType.CLIENT);
        // This adapter proves common engine behavior, not NeoForge rendering, input routing or GPU resources.
        context.eval("js", CLIENT_UI_HOST);
        Path entry = copyExample("client/counter.tsx", paths.clientScripts().resolve("examples/counter.tsx"));

        loader.loadEntry(entry.toString());

        Value host = context.getBindings("js").getMember("exampleUiHost");
        Value root = host.getMember("root");
        assertNotNull(root);
        assertTrue(host.getMember("opened").asBoolean());
        assertEquals("Authoring counter", host.getMember("title").asString());
        assertFalse(host.getMember("pausesGame").asBoolean());
        assertEquals("screen", host.invokeMember("snapshot").getArrayElement(0).getMember("type").asString());
        assertEquals("0", host.invokeMember("text", "count").asString());
        assertEquals(0, host.getMember("diagnostics").getArraySize());

        assertTrue(root.invokeMember("dispatch", "increment", "click", context.eval("js", "({button: 0})")).asBoolean());
        assertEquals("1", host.invokeMember("text", "count").asString());
        assertEquals(0, host.getMember("diagnostics").getArraySize());

        assertTrue(root.invokeMember("close").asBoolean());
        assertTrue(root.invokeMember("isDisposed").asBoolean());
        assertEquals(0, host.invokeMember("snapshot").getArraySize());
        assertEquals(0, host.invokeMember("nodeCount").asInt());
        assertFalse(root.invokeMember("dispatch", "increment", "click", context.eval("js", "({})")).asBoolean(),
                "closing the root detaches its event handlers");
        assertEquals(0, host.getMember("diagnostics").getArraySize());
    }

    @Test
    void bundledControlsUseRealTsxControlledStateAndDisabledGuards() throws Exception {
        boot(ScriptType.CLIENT);
        // The fake host proves compiler, module and control contracts, not native clipboard or rendering.
        context.eval("js", CLIENT_UI_HOST);
        Path entry = copyExample("client/controls.tsx", paths.clientScripts().resolve("examples/controls.tsx"));
        loader.loadEntry(entry.toString());

        Value host = context.getBindings("js").getMember("exampleUiHost");
        Value root = host.getMember("root");
        assertTrue(host.getMember("opened").asBoolean());
        assertEquals("Authoring controls", host.getMember("title").asString());
        assertEquals("enabled=true; volume=40; quality=balanced", host.invokeMember("text", "controls-summary").asString());
        assertEquals(40, host.invokeMember("props", "controls-volume").getMember("value").asInt());
        assertEquals(0, host.getMember("diagnostics").getArraySize());

        assertTrue(root.invokeMember("dispatch", "controls-text", "change",
                Map.of("value", "Clipboard 中文 🙂")).asBoolean());
        assertEquals("Clipboard 中文 🙂", host.invokeMember("props", "controls-text").getMember("value").asString());
        assertTrue(root.invokeMember("dispatch", "controls-volume", "change",
                Map.of("value", "65")).asBoolean());
        assertEquals("enabled=true; volume=65; quality=balanced", host.invokeMember("text", "controls-summary").asString());

        root.invokeMember("dispatch", "controls-enabled", "click", context.eval("js", "({button: 0})"));
        assertTrue(host.invokeMember("props", "controls-volume").getMember("disabled").asBoolean());
        root.invokeMember("dispatch", "controls-volume", "change", context.eval("js", "({value: '90'})"));
        assertEquals("enabled=false; volume=65; quality=balanced", host.invokeMember("text", "controls-summary").asString(),
                "direct dispatch must not bypass a disabled controlled Slider");
        root.invokeMember("dispatch", "controls-enabled", "click", context.eval("js", "({button: 0})"));

        root.invokeMember("dispatch", "controls-quality", "click", context.eval("js", "({button: 0})"));
        assertTrue(host.invokeMember("has", "controls-quality:option:fast").asBoolean());
        root.invokeMember("dispatch", "controls-quality:option:unavailable", "click", context.eval("js", "({button: 0})"));
        assertEquals("enabled=true; volume=65; quality=balanced", host.invokeMember("text", "controls-summary").asString());
        assertTrue(host.invokeMember("has", "controls-quality:option:fast").asBoolean());
        root.invokeMember("dispatch", "controls-quality:option:fast", "click", context.eval("js", "({button: 0})"));
        assertEquals("enabled=true; volume=65; quality=fast", host.invokeMember("text", "controls-summary").asString());
        assertFalse(host.invokeMember("has", "controls-quality:option:fast").asBoolean(),
                "selection closes the controlled inline option list");
        assertEquals(0, host.getMember("diagnostics").getArraySize());

        assertTrue(root.invokeMember("close").asBoolean());
        assertEquals(0, host.invokeMember("nodeCount").asInt());
        assertFalse(root.invokeMember("dispatch", "controls-enabled", "click", context.eval("js", "({})")).asBoolean());
    }

    @Test
    void bundledFormsUseRealValidationTabsAndControlledModalDispatch() throws Exception {
        boot(ScriptType.CLIENT);
        // Exercise the real compiler and runtime; this host does not prove native input or rendering.
        context.eval("js", CLIENT_UI_HOST);
        Path entry = copyExample("client/forms-and-dialogs.tsx",
                paths.clientScripts().resolve("examples/forms-and-dialogs.tsx"));

        loader.loadEntry(entry.toString());
        Value nativeHost = context.getBindings("js").getMember("exampleUiHost");
        Value root = nativeHost.getMember("root");
        assertEquals("Authoring forms and dialogs", nativeHost.getMember("title").asString());
        assertTrue(nativeHost.invokeMember("has", "forms-name").asBoolean());
        assertFalse(nativeHost.invokeMember("has", "forms-preview-item").asBoolean());
        assertEquals("Nothing confirmed", nativeHost.invokeMember("text", "forms-status").asString());

        root.invokeMember("dispatch", "forms-tabs:tab:locked", "click", Map.of("button", 0));
        assertTrue(nativeHost.invokeMember("has", "forms-name").asBoolean());
        root.invokeMember("dispatch", "forms-tabs:tab:preview", "click", Map.of("button", 0));
        assertFalse(nativeHost.invokeMember("has", "forms-name").asBoolean());
        Value item = nativeHost.invokeMember("props", "forms-preview-item");
        assertEquals("minecraft:diamond", item.getMember("item").asString());
        assertEquals(1, item.getMember("count").asInt());
        assertTrue(item.getMember("itemTooltip").asBoolean());
        root.invokeMember("dispatch", "forms-tabs:tab:profile", "click", Map.of("button", 0));

        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        assertEquals("Enter a name", nativeHost.invokeMember("text", "forms-errors").asString());
        assertFalse(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        root.invokeMember("dispatch", "forms-name", "change", Map.of("value", "Alex"));
        root.invokeMember("dispatch", "forms-quantity", "change", Map.of("value", "65"));
        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        assertEquals("Quantity must be between 1 and 64",
                nativeHost.invokeMember("text", "forms-errors").asString());
        assertFalse(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        root.invokeMember("dispatch", "forms-quantity", "change", Map.of("value", "1.5"));
        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        assertEquals("Quantity must be a whole number",
                nativeHost.invokeMember("text", "forms-errors").asString());

        root.invokeMember("dispatch", "forms-quantity", "change", Map.of("value", "3"));
        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        assertTrue(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        assertEquals("", nativeHost.invokeMember("text", "forms-errors").asString());
        assertFalse(root.invokeMember("dispatch", "forms-background", "click", Map.of("button", 0)).asBoolean());
        assertFalse(root.invokeMember("dispatch", "forms-name", "change", Map.of("value", "Blocked")).asBoolean());
        assertFalse(root.invokeMember("dispatch", "forms-tabs:tab:preview", "click", Map.of("button", 0)).asBoolean());
        assertEquals("Background clicks: 0", nativeHost.invokeMember("text", "forms-background-count").asString());
        assertEquals("Alex", nativeHost.invokeMember("props", "forms-name").getMember("value").asString());
        root.invokeMember("dispatch", "forms-cancel", "click", Map.of("button", 0));
        assertFalse(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        assertEquals("Nothing confirmed", nativeHost.invokeMember("text", "forms-status").asString());

        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "forms-dialog", "dismiss", Map.of("reason", "escape"));
        assertFalse(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        root.invokeMember("dispatch", "forms-review", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "forms-confirm", "click", Map.of("button", 0));
        assertFalse(nativeHost.invokeMember("has", "forms-dialog").asBoolean());
        assertEquals("Confirmed Alex x3", nativeHost.invokeMember("text", "forms-status").asString());
        assertEquals(0, nativeHost.getMember("diagnostics").getArraySize());
        assertTrue(root.invokeMember("close").asBoolean());
        assertEquals(0, nativeHost.invokeMember("nodeCount").asInt());
        assertFalse(root.invokeMember("dispatch", "forms-confirm", "click", Map.of("button", 0)).asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"taskbar", "floating", "custom"})
    void bundledWorkspaceRunsThroughTheRealLoaderWithScopedWindowEvents(String presentation) throws Exception {
        boot(ScriptType.CLIENT);
        // This transactional host proves guest integration, not native rendering or physical input.
        context.eval("js", CLIENT_UI_HOST);
        Path entry = copyExample("client/workspace.tsx",
                paths.clientScripts().resolve("examples/workspace.tsx"));
        if (!"taskbar".equals(presentation)) {
            String handbook;
            try (var resource = getClass().getResourceAsStream("/nekojs/authoring/handbook/jsx-ui.md")) {
                assertNotNull(resource);
                handbook = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            }
            var blocks = java.util.regex.Pattern.compile("(?s)```tsx\\R(.*?)\\R```").matcher(handbook);
            String initializer = null;
            String option = "custom".equals(presentation) ? "context =>" : "\"floating\"";
            while (blocks.find()) {
                String block = blocks.group(1).strip();
                if (block.startsWith("const workspace = createWorkspace(host,")
                        && block.contains("minimizedPresentation: " + option)) initializer = block;
            }
            assertNotNull(initializer, "The alternative initializer must come from the exact bundled handbook");
            String source = Files.readString(entry);
            int start = source.indexOf("const workspace = createWorkspace(host, {");
            int end = source.indexOf("\n});", start);
            assertTrue(start >= 0 && end > start);
            Files.writeString(entry, source.substring(0, start) + initializer + source.substring(end + 4));
        }
        loader.loadEntry(entry.toString());
        Value host = context.getBindings("js").getMember("exampleUiHost");
        Value root = host.getMember("root");
        assertEquals("Authoring workspace", host.getMember("title").asString());
        assertTrue(host.getMember("opened").asBoolean());
        for (String id : List.of("notes", "tools", "preview")) {
            assertTrue(host.invokeMember("has", id).asBoolean());
        }
        Value find = context.eval("js", """
                (windowId, type) => {
                    function visit(nodes, predicate) {
                        for (const node of nodes) {
                            if (predicate(node)) return node;
                            const found = visit(node.children, predicate);
                            if (found) return found;
                        }
                        return null;
                    }
                    const window = visit(exampleUiHost.snapshot(), node => node.type === 'window' && node.props.id === windowId);
                    const node = window && visit(window.children, node => node.type === type);
                    if (!node) throw new Error('Missing workspace example descendant: ' + windowId + '/' + type);
                    return node.props;
                }
                """);
        String notesSave = find.execute("notes", "button").getMember("id").asString();
        String toolsClick = find.execute("tools", "button").getMember("id").asString();
        assertFalse(root.invokeMember("dispatch", notesSave, "click", Map.of("button", 0)).asBoolean());
        assertTrue(root.invokeMember("dispatch", "notes", "activate", Map.of()).asBoolean());
        String notesInput = find.execute("notes", "input").getMember("id").asString();
        assertTrue(root.invokeMember("dispatch", notesInput, "change", Map.of("value", "Edited note")).asBoolean());
        assertEquals("Edited note", find.execute("notes", "input").getMember("value").asString());
        assertEquals("Tool input", find.execute("tools", "input").getMember("value").asString());
        assertTrue(root.invokeMember("dispatch", "notes", "minimize", Map.of()).asBoolean());
        assertTrue(host.invokeMember("has", "notes").asBoolean());
        assertFalse(root.invokeMember("dispatch", notesInput, "change", Map.of("value", "Blocked")).asBoolean());
        Value restoreNotes = context.eval("js", """
                (() => {
                    function visit(nodes, insidePresentation) {
                        for (const node of nodes) {
                            const presentation = insidePresentation || node.props.workspacePresentation === true;
                            if (presentation && node.type === 'button' && node.props.text === 'Notes') return node.props.id;
                            const found = visit(node.children, presentation);
                            if (found) return found;
                        }
                        return null;
                    }
                    return visit(exampleUiHost.snapshot(), false);
                })()
                """);
        assertFalse(restoreNotes.isNull(), "The example must provide a semantic Notes restore button");
        assertTrue(root.invokeMember("dispatch", restoreNotes.asString(), "click", Map.of("button", 0)).asBoolean());
        assertEquals(notesInput, find.execute("notes", "input").getMember("id").asString());
        assertEquals("Edited note", find.execute("notes", "input").getMember("value").asString());
        assertTrue(root.invokeMember("dispatch", notesSave, "click", Map.of("button", 0)).asBoolean());
        assertEquals("Notes saved: 1", find.execute("notes", "label").getMember("text").asString());
        assertEquals("Saved=1; clicks=0", find.execute("preview", "label").getMember("text").asString());
        assertTrue(root.invokeMember("dispatch", "tools", "activate", Map.of()).asBoolean());
        assertTrue(root.invokeMember("dispatch", toolsClick, "click", Map.of("button", 0)).asBoolean());
        assertEquals("Saved=1; clicks=1", find.execute("preview", "label").getMember("text").asString());
        assertTrue(root.invokeMember("dispatch", "notes", "dismiss", Map.of("reason", "close")).asBoolean());
        assertFalse(host.invokeMember("has", "notes").asBoolean());
        assertTrue(host.invokeMember("has", "tools").asBoolean());
        assertTrue(host.getMember("opened").asBoolean());
        assertFalse(root.invokeMember("dispatch", notesSave, "click", Map.of("button", 0)).asBoolean());
        assertEquals(0, host.getMember("diagnostics").getArraySize());
        assertTrue(host.invokeMember("close").asBoolean());
        assertEquals(0, host.invokeMember("nodeCount").asInt());
    }

    @Test
    void independentDashboardPrototypeRunsWithSearchSelectionModalAndClipboard() throws Exception {
        boot(ScriptType.CLIENT);
        installDashboardPrototypeHost(true);
        loader.loadEntry(copyDashboardPrototype().toString());
        Value host = context.getBindings("js").getMember("exampleUiHost");
        Value root = host.getMember("root");
        assertTrue(host.getMember("opened").asBoolean());
        assertEquals("player_tick.js", host.invokeMember("text", "dashboard-detail-name").asString());
        assertEquals("3 errors · 11 occurrences", host.invokeMember("text", "dashboard-totals").asString());
        context.getBindings("js").putMember("dashboardPrototypeRoot", root);
        Value bounds = context.eval("js", """
                id => {
                  function find(nodes) {
                    for (const node of nodes) {
                      if(node.id===id)return node.rect;
                      const child=find(node.children);if(child)return child;
                    }
                    return null;
                  }
                  return find(dashboardPrototypeRoot.layout().nodes);
                }
                """);
        Value panel = bounds.execute("dashboard-panel");
        assertEquals(0, panel.getMember("x").asDouble());
        assertEquals(0, panel.getMember("y").asDouble());
        assertEquals(960, panel.getMember("width").asDouble());
        assertTrue(panel.getMember("height").asDouble() >= 480,
                "the main panel must fill the viewport above the reserved prototype controls");

        root.invokeMember("dispatch", "dashboard-search", "change", Map.of("value", "example.js"));
        assertEquals("example.js", host.invokeMember("text", "dashboard-detail-name").asString());
        assertEquals("1 / 3", host.invokeMember("text", "dashboard-result-count").asString());
        root.invokeMember("dispatch", "dashboard-collapse", "click", Map.of("button", 0));
        assertEquals(26, host.invokeMember("props", "dashboard-sidebar").getMember("width").asInt());
        root.invokeMember("dispatch", "dashboard-collapse", "click", Map.of("button", 0));
        assertEquals("example.js", host.invokeMember("props", "dashboard-search").getMember("value").asString());
        root.invokeMember("dispatch", "dashboard-clear", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "dashboard-row:nekojs:server/events/player_tick.js", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "dashboard-toggle-stack", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("props", "dashboard-stack-panel").getMember("visible").asBoolean());
        root.invokeMember("dispatch", "dashboard-copy-full", "click", Map.of("button", 0));
        String copied = context.eval("js", "prototypeClient.keyboardHandler.clipboard").asString();
        assertTrue(copied.contains("at onPlayerTick (server_scripts/events/player_tick.js:42)"));
        assertTrue(copied.contains(">> 异常代码片段"));

        root.invokeMember("dispatch", "dashboard-locate", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("has", "dashboard-dialog").asBoolean());
        assertFalse(root.invokeMember("dispatch", "dashboard-search", "change", Map.of("value", "blocked")).asBoolean());
        root.invokeMember("dispatch", "dashboard-dialog", "dismiss", Map.of("reason", "escape"));
        assertFalse(host.invokeMember("has", "dashboard-dialog").asBoolean());
        assertEquals(copied, context.eval("js", "prototypeClient.keyboardHandler.clipboard").asString());

        root.invokeMember("dispatch", "dashboard-scenario-missing", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("props", "dashboard-locate").getMember("disabled").asBoolean());
        root.invokeMember("dispatch", "dashboard-scenario-long", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "dashboard-toggle-stack", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("text", "dashboard-stack-text").asString().contains("at callback59"));
        root.invokeMember("dispatch", "dashboard-scenario-raw", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("text", "dashboard-raw-text").asString().contains("Raw snapshot line 159"));
        root.invokeMember("dispatch", "dashboard-copy-full", "click", Map.of("button", 0));
        assertTrue(context.eval("js", "prototypeClient.keyboardHandler.clipboard.includes('Raw snapshot line 159')").asBoolean());
        assertTrue(context.eval("js", "prototypeClient.keyboardHandler.clipboard.includes('Error message line 079')").asBoolean());
        root.invokeMember("dispatch", "dashboard-scenario-empty", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("has", "dashboard-empty").asBoolean());
        assertFalse(host.invokeMember("has", "dashboard-copy-full").asBoolean());
        root.invokeMember("dispatch", "dashboard-scenario-normal", "click", Map.of("button", 0));
        root.invokeMember("dispatch", "dashboard-reopen", "click", Map.of("button", 0));
        assertFalse(host.invokeMember("props", "dashboard-panel").getMember("visible").asBoolean());
        assertTrue(bounds.execute("dashboard-prototype-controls").getMember("y").asDouble() >= 480,
                "hiding the panel must keep the prototype controls at the bottom");
        root.invokeMember("dispatch", "dashboard-reopen", "click", Map.of("button", 0));
        assertTrue(host.invokeMember("props", "dashboard-panel").getMember("visible").asBoolean());
        assertTrue(root.invokeMember("close").asBoolean());
        assertEquals(0, host.invokeMember("nodeCount").asInt());
    }

    @Test
    void independentDashboardPrototypeKeepsBothPanesAndScalesItsDesignCanvas() throws Exception {
        boot(ScriptType.CLIENT);
        installDashboardPrototypeHost(true);
        loader.loadEntry(copyDashboardPrototype().toString());
        Value host = context.getBindings("js").getMember("exampleUiHost");
        Value root = host.getMember("root");
        Value node = context.eval("js", """
                (root, id) => {
                  function find(nodes) {
                    for (const node of nodes) {
                      if (node.id === id) return node;
                      const child = find(node.children);
                      if (child) return child;
                    }
                    return null;
                  }
                  return find(root.layout().nodes);
                }
                """);
        root.invokeMember("dispatch", "dashboard-search", "change", Map.of("value", "example.js"));
        for (int[] size : List.of(new int[]{960, 540}, new int[]{426, 250}, new int[]{320, 240}, new int[]{1280, 720})) {
            context.eval("js", "Object.assign(prototypeViewport, {width:" + size[0] + ",height:" + size[1] + "})");
            root.invokeMember("resize", context.getBindings("js").getMember("prototypeViewport"));
            double scale = Math.min(size[0] / 1280.0, size[1] / 720.0);
            assertEquals(scale, root.invokeMember("layout").getMember("viewport").getMember("designScale").asDouble(), 0.0001);
            Value sidebar = node.execute(root, "dashboard-sidebar");
            Value details = node.execute(root, "dashboard-detail-pane");
            assertTrue(sidebar.getMember("visible").asBoolean());
            assertTrue(details.getMember("visible").asBoolean());
            assertEquals(size[0] * 0.26, sidebar.getMember("rect").getMember("width").asDouble(), 0.001);
            assertEquals(sidebar.getMember("rect").getMember("width").asDouble(),
                    details.getMember("rect").getMember("x").asDouble(), 0.001);
            assertFalse(host.invokeMember("has", "dashboard-back-to-list").asBoolean());
            for (String id : List.of("dashboard-copy-path", "dashboard-copy-full", "dashboard-locate")) {
                Value rect = node.execute(root, id).getMember("rect");
                assertTrue(rect.getMember("x").asDouble() >= details.getMember("rect").getMember("x").asDouble());
                assertTrue(rect.getMember("x").asDouble() + rect.getMember("width").asDouble() <= size[0] + 0.001,
                        () -> id + " must stay inside the detail pane at every GUI scale");
            }
            assertEquals(64 * scale, node.execute(root, "dashboard-copy-path").getMember("rect").getMember("width").asDouble(), 0.001);
            assertEquals("example.js", host.invokeMember("props", "dashboard-search").getMember("value").asString());
        }
        root.invokeMember("dispatch", "dashboard-copy-full", "click", Map.of("button", 0));
        assertTrue(context.eval("js", "prototypeClient.keyboardHandler.clipboard.includes(\"SyntaxError: Unexpected token '}'\")").asBoolean());
        assertTrue(root.invokeMember("close").asBoolean());
        assertEquals(0, host.invokeMember("nodeCount").asInt());
    }

    @Test
    void independentDashboardPrototypeWaitsForLoginBeforeConstructingItsHost() throws Exception {
        boot(ScriptType.CLIENT);
        installDashboardPrototypeHost(false);
        loader.loadEntry(copyDashboardPrototype().toString());
        assertTrue(context.eval("js", "typeof exampleUiHost === 'undefined'").asBoolean());
        context.eval("js", "prototypeClient.player = {}; prototypeLogin()");
        assertTrue(context.eval("js", "exampleUiHost.opened").asBoolean());
    }

    private Path copyDashboardPrototype() throws IOException {
        Path source = Path.of("../docs/architecture-refactor/prototypes/error-dashboard-jsx.prototype.tsx").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(source), "The standalone prototype must exist beside its HTML reference");
        Path target = paths.clientScripts().resolve("prototypes/error-dashboard.prototype.tsx");
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
        String compiled = com.tkisor.nekojs.core.compiler.NekoJsxCompiler.compileTsx(source, Files.readString(source)).code();
        var preflightDiagnostics = new ArrayList<String>();
        var view = new com.tkisor.nekojs.api.event.ScriptBindingSchema.View(
                Map.of("ClientUI", new com.tkisor.nekojs.api.event.ScriptBindingSchema.BindingMembers(Set.of("screen"), Set.of())),
                Set.of(context.eval("js", "Object.getOwnPropertyNames(globalThis)").as(String[].class)),
                item -> preflightDiagnostics.add(item.throwable().getMessage()));
        com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator.validate(target, compiled, view);
        assertTrue(preflightDiagnostics.isEmpty(), () -> "Prototype binding preflight must be clean: " + preflightDiagnostics);
        return target;
    }

    private void installDashboardPrototypeHost(boolean playerReady) throws Exception {
        context.eval("js", CLIENT_UI_HOST);
        try (var input = getClass().getResourceAsStream("/assets/nekojs/lang/en_us.json")) {
            assertNotNull(input);
            context.eval("js", "globalThis.prototypeTranslations=" + new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        context.eval("js", """
                globalThis.prototypeViewport={width:960,height:540};
                const originalPrototypeScreen=ClientUI.screen;
                ClientUI.screen=(...args)=>{
                  const host=originalPrototypeScreen(...args);
                  host.viewport=()=>prototypeViewport;
                  return host;
                };
                globalThis.prototypeClient={player:null,keyboardHandler:{clipboard:''}};
                globalThis.Minecraft={getInstance:()=>prototypeClient};
                function translated(key,fallback,args){
                  let index=0;
                  return {string:String(prototypeTranslations[key] || fallback).replace(/%s/g,()=>String(args[index++]))};
                }
                globalThis.Component={
                  translatable:(key,...args)=>translated(key,key,args),
                  translatableWithFallback:(key,fallback,...args)=>translated(key,fallback,args)
                };
                globalThis.ClientEvents={
                  lang:(language,callback)=>callback({add:(key,value)=>{
                    if(language==='en_us')prototypeTranslations[key]=value;
                  }}),
                  loggedIn:callback=>globalThis.prototypeLogin=callback
                };
                """);
        if (playerReady) context.eval("js", "prototypeClient.player={}");
    }

    private void boot(ScriptType type) throws Exception {
        // Keep process-global paths on the shared fixture; module sources remain isolated below.
        TestPlatformInit.ensureInitialized();
        paths = NekoJSPaths.fromGameDir(gameDir);
        paths.initFolders();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        SandboxConfig config = SandboxConfig.defaultConfig();
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        runtime = NekoNodeModuleInstaller.install(context, type,
                new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                paths, new DefaultErrorTracker(paths, config), config, cache);
        loader = context.getBindings("js").getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    private Path copyExample(String name, Path target) throws IOException {
        String resource = "/nekojs/authoring/examples/" + name;
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "bundled example must exist: " + resource);
            Files.createDirectories(target.getParent());
            Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return target;
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "module exports must remain a guest value");
        return (Value) exports;
    }

    private static Object log4jContext() throws Exception {
        return Class.forName("org.apache.logging.log4j.LogManager")
                .getMethod("getContext", boolean.class).invoke(null, false);
    }

    private static Object log4jConfiguration() throws Exception {
        Object ctx = log4jContext();
        return ctx.getClass().getMethod("getConfiguration").invoke(ctx);
    }

    private static void closeTestLogger() throws Exception {
        // Log4j core is runtime-only here; mirror the existing logger test cleanup without stopping shared logging.
        synchronized (NekoJSLoggers.class) {
            Object ctx = log4jContext();
            Object cfg = ctx.getClass().getMethod("getConfiguration").invoke(ctx);
            var appendersField = NekoJSLoggers.class.getDeclaredField("APPENDERS");
            appendersField.setAccessible(true);
            List<?> appenders = (List<?>) appendersField.get(null);
            for (Object appender : new ArrayList<>(appenders)) {
                String name = (String) appender.getClass().getMethod("getName").invoke(appender);
                if ("NekoJS-Collapse-test".equals(name)) {
                    appenders.remove(appender);
                    appender.getClass().getMethod("flush").invoke(appender);
                }
            }
            for (String name : List.of("NekoJS-Collapse-test", "NekoJS-Async-test", "NekoJS-File-test")) {
                cfg.getClass().getMethod("removeAppender", String.class).invoke(cfg, name);
            }
            cfg.getClass().getMethod("removeLogger", String.class).invoke(cfg, "nekojs.test");
            ctx.getClass().getMethod("updateLoggers").invoke(ctx);
            var cacheField = NekoJSLoggers.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            ((Map<?, ?>) cacheField.get(null)).remove("test");
        }
    }

    private static final String CLIENT_UI_HOST = """
            globalThis.ClientUI = {
                screen(title, pausesGame) {
                    let nodes = new Map();
                    let roots = [];
                    let nextId = 1;
                    const host = {
                        title, pausesGame, opened: false, root: null, diagnostics: [],
                        isOwnerThread: () => true,
                        enqueue: () => false,
                        supportsPrimitive: type => ['screen', 'panel', 'row', 'column', 'stack', 'scroll', 'label', 'button', 'input', 'slider', 'image', 'spacer', 'dialog', 'item', 'workspace', 'window'].includes(type),
                        viewport: () => ({width: 320, height: 180}),
                        measureText: (text, size, maxWidth) => ({width: Math.min(maxWidth, String(text).length * 6), height: size}),
                        layout: () => {},
                        reportDiagnostic: diagnostic => host.diagnostics.push(diagnostic),
                        bindRoot: root => { host.root = root; },
                        open: () => { host.opened = true; },
                        close: () => {
                            if (host.root.close() !== true) return false;
                            host.opened = false;
                            return true;
                        },
                        begin() {
                            const staged = new Map(Array.from(nodes, ([id, node]) => [id, {
                                ...node, props: {...node.props}, children: node.children.slice()
                            }]));
                            let stagedRoots = roots.slice();
                            return {
                                create(type, key, props) {
                                    const id = nextId++;
                                    staged.set(id, {type, key, props: {...props}, children: []});
                                    return id;
                                },
                                update(id, type, key, props) {
                                    const node = staged.get(id);
                                    if (node == null) throw new Error('Unknown host node: ' + id);
                                    Object.assign(node, {type, key, props: {...props}});
                                },
                                order(parent, children) {
                                    if (parent == null) stagedRoots = children.slice();
                                    else staged.get(parent).children = children.slice();
                                },
                                remove: id => staged.delete(id),
                                commit(nextRoots) {
                                    if (nextRoots.join(',') !== stagedRoots.join(',')) throw new Error('Root order mismatch');
                                    nodes = staged;
                                    roots = stagedRoots;
                                },
                                rollback: () => {}
                            };
                        },
                        snapshot: () => roots.map(snapshotNode),
                        nodeCount: () => nodes.size,
                        has: id => Array.from(nodes.values()).some(node => node.props.id === id),
                        props(id) {
                            const node = Array.from(nodes.values()).find(node => node.props.id === id);
                            if (node == null) throw new Error('Missing host node: ' + id);
                            return node.props;
                        },
                        text(id) {
                            const node = Array.from(nodes.values()).find(node => node.props.id === id);
                            if (node == null) throw new Error('Missing host node: ' + id);
                            return String(node.props.text == null ? '' : node.props.text);
                        }
                    };
                    function snapshotNode(id) {
                        const node = nodes.get(id);
                        return {type: node.type, props: node.props, children: node.children.map(snapshotNode)};
                    }
                    globalThis.exampleUiHost = host;
                    return host;
                }
            };
            """;
}
