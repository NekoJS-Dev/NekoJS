package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
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
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real compiler, module loader and UI runtime with only the platform host replaced. */
class JsxWorkspaceRuntimeTest {
    @TempDir
    Path gameDir;
    private NekoJSPaths paths;
    private Context context;
    private NekoNodeRuntime runtime;
    private NekoScriptModuleLoaderHost loader;

    @BeforeEach
    void boot() throws Exception {
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
        IOAccess io = IOAccess.newBuilder().fileSystem(new NekoJSFileSystem(paths.root(),
                new SandboxPolicy(config, paths), paths, cache)).build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(io).build();
        runtime = NekoNodeModuleInstaller.install(context, ScriptType.TEST,
                new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                paths, new DefaultErrorTracker(paths, config), config, cache);
        loader = context.getBindings("js").getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    @AfterEach
    void close() {
        try {
            if (runtime != null) runtime.close();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void twoWindowsShareOneManagedRootAndOpeningDoesNotCreateAnotherRoot() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {id:'desktop', viewport:{width:320,height:240}});
                workspace.add('first', () => UI.element('label', {text:'First'}),
                    {title:'First',rect:{x:10,y:12,width:120,height:90}});
                workspace.add('second', () => UI.element('label', {text:'Second'}),
                    {title:'Second',rect:{x:45,y:32,width:130,height:100}});
                workspace.open();
                export const bindings = host.bindings;
                export const opens = host.opens;
                export const rootCount = host.roots.length;
                export const rootType = host.roots[0].type;
                export const windows = host.roots[0].children.map(node => node.props.id);
                export const activeId = workspace.snapshot().activeId;
                if(host.roots[0].children.length!==2) throw new Error(host.diagnostics.map(item=>String(item.error)).join('; '));
                export const contentY = host.snapshot.nodes[0].children[0].children[0].rect.y;
                """);
        assertEquals(1, proof.getMember("bindings").asInt());
        assertEquals(1, proof.getMember("opens").asInt());
        assertEquals(1, proof.getMember("rootCount").asInt());
        assertEquals("workspace", proof.getMember("rootType").asString());
        assertEquals("first", proof.getMember("windows").getArrayElement(0).asString());
        assertEquals("second", proof.getMember("windows").getArrayElement(1).asString());
        assertEquals("second", proof.getMember("activeId").asString());
        assertEquals(32, proof.getMember("contentY").asInt());
    }

    @Test
    void autoColumnInsideWorkspaceScrollPreservesFixedControlBoundsAndVisibleCount() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {viewport:{width:427,height:240}});
                workspace.add('notes', () => (
                    <scroll width="fill" height="fill">
                        <column padding={6} gap={6} width="fill">
                            <input id="notes-text" width="fill" height={20} value="Draft note" />
                            <button id="notes-save" height={20}>Save locally</button>
                            <label id="notes-count">Notes saved: 0</label>
                        </column>
                    </scroll>
                ), {title:'Notes',rect:{x:26,y:100,width:190,height:136}});
                const window = workspace.layout().nodes[0].children[0];
                const scroll = window.children[0];
                const column = scroll.children[0];
                const [input, button, count] = column.children;
                export const geometry = {scroll:scroll.rect,column:column.rect,input:input.rect,
                    button:button.rect,buttonClip:button.clip,count:count.rect,countClip:count.clip};
                """);
        Value geometry = proof.getMember("geometry");
        assertEquals(120, geometry.getMember("scroll").getMember("y").asInt());
        assertEquals(116, geometry.getMember("scroll").getMember("height").asInt());
        assertEquals(126, geometry.getMember("input").getMember("y").asInt());
        assertEquals(20, geometry.getMember("input").getMember("height").asInt());
        assertEquals(152, geometry.getMember("button").getMember("y").asInt());
        assertEquals(20, geometry.getMember("button").getMember("height").asInt());
        assertEquals(73, geometry.getMember("column").getMember("height").asInt());
        assertEquals(20, geometry.getMember("buttonClip").getMember("height").asInt());
        assertEquals(178, geometry.getMember("count").getMember("y").asInt());
        assertEquals(9, geometry.getMember("countClip").getMember("height").asInt());
    }

    @Test
    void autoSizedRowsAndColumnsIncludeExplicitChildDimensions() throws Exception {
        Value proof = run("""
                function measure(direction) {
                    const host = fakeHost();
                    const root = UI.createRoot(() => UI.element('stack', {children:
                        UI.element(direction, {width:'auto',height:'auto',padding:6,gap:6,children:[
                            UI.element('label', {text:'A',width:40,height:10}),
                            UI.element('label', {text:'B',width:50,height:20})
                        ]})}), host, {viewport:{width:200,height:120}});
                    return root.layout().nodes[0].children[0].rect;
                }
                export const row = measure('row');
                export const column = measure('column');
                """);
        assertEquals(108, proof.getMember("row").getMember("width").asDouble(), 0.0001);
        assertEquals(32, proof.getMember("row").getMember("height").asInt());
        assertEquals(62, proof.getMember("column").getMember("width").asInt());
        assertEquals(48, proof.getMember("column").getMember("height").asInt());
    }

    @Test
    void preferredChildMeasurementKeepsFillAndPercentAllocationDeferred() throws Exception {
        Value proof = run("""
                function measure(width) {
                    const root = UI.createRoot(() => UI.element('stack', {children:
                        UI.element('row', {width:'auto',height:'auto',children:[
                            UI.element('label', {text:'A',width:40}),
                            UI.element('label', {text:'B',width})
                        ]})}), fakeHost(), {viewport:{width:200,height:120}});
                    return root.layout().nodes[0].children[0];
                }
                export const fill = measure('fill');
                export const percent = measure('50%');
                const root = UI.createRoot(() => UI.element('column', {width:200,height:100,gap:6,children:[
                    UI.element('label', {text:'A',height:20}),
                    UI.element('label', {text:'B',height:'fill'})
                ]}), fakeHost(), {viewport:{width:200,height:120}});
                export const remainingHeight = root.layout().nodes[0].children[1].rect.height;
                """);
        assertEquals(45.4, proof.getMember("fill").getMember("rect").getMember("width").asDouble(), 0.0001);
        assertEquals(5.4, proof.getMember("fill").getMember("children").getArrayElement(1)
                .getMember("rect").getMember("width").asDouble(), 0.0001);
        assertEquals(45.4, proof.getMember("percent").getMember("rect").getMember("width").asDouble(), 0.0001);
        assertEquals(22.7, proof.getMember("percent").getMember("children").getArrayElement(1)
                .getMember("rect").getMember("width").asDouble(), 0.0001);
        assertEquals(74, proof.getMember("remainingHeight").asInt());
    }

    @Test
    void preferredChildMeasurementResolvesResponsiveDesignSizesAndNumericConstraints() throws Exception {
        Value proof = run("""
                const root = UI.createRoot(() => UI.element('stack', {children:
                    UI.element('column', {width:'auto',height:'auto',children:
                        UI.element('label', {text:'A',coordinateSpace:'design',width:{base:20},height:{base:10},
                            minWidth:25,maxHeight:8})})}), fakeHost(),
                    {viewport:{width:320,height:180,designWidth:160,designHeight:90}});
                export const column = root.layout().nodes[0].children[0].rect;
                """);
        assertEquals(50, proof.getMember("column").getMember("width").asInt());
        assertEquals(16, proof.getMember("column").getMember("height").asInt());
    }

    @Test
    void rejectedChangesCannotReappearOnALaterSignalRender() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const text = UI.createSignal('before');
                const workspace = createWorkspace(host);
                workspace.add('first', () => UI.element('label', {text:text.get()}),
                    {title:'Before',rect:{x:10,y:12,width:120,height:90}});
                workspace.add('second', () => UI.element('label', {text:'Second'}));
                const saved = workspace.snapshot();
                host.failCommit = true;
                export const rejected = workspace.update('first', {title:'Rejected',rect:{x:200}});
                export const activateRejected = workspace.activate('first');
                export const hideRejected = workspace.hide('second');
                host.failCommit = false;
                text.set('after');
                export const current = workspace.snapshot();
                export const frozen = Object.isFrozen(saved) && Object.isFrozen(saved.windows)
                    && Object.isFrozen(saved.windows[0]) && Object.isFrozen(saved.windows[0].rect);
                export const savedTitle = saved.windows[0].title;
                export const publishedTitle = host.roots[0].children[0].props.title;
                export const publishedText = host.roots[0].children[0].children[0].props.text;
                """);
        assertFalse(proof.getMember("rejected").asBoolean());
        assertFalse(proof.getMember("activateRejected").asBoolean());
        assertFalse(proof.getMember("hideRejected").asBoolean());
        assertTrue(proof.getMember("frozen").asBoolean());
        Value snapshot = proof.getMember("current");
        assertEquals("second", snapshot.getMember("activeId").asString());
        assertEquals("Before", snapshot.getMember("windows").getArrayElement(0).getMember("title").asString());
        assertEquals(10, snapshot.getMember("windows").getArrayElement(0).getMember("rect").getMember("x").asInt());
        assertTrue(snapshot.getMember("windows").getArrayElement(1).getMember("visible").asBoolean());
        assertEquals("Before", proof.getMember("savedTitle").asString());
        assertEquals("Before", proof.getMember("publishedTitle").asString());
        assertEquals("after", proof.getMember("publishedText").asString());
    }

    @Test
    void hiddenWindowsRetainSubscriptionsAndScopedIdentityButRejectInput() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const text = UI.createSignal('before');
                const workspace = createWorkspace(host);
                const clicks = [];
                workspace.add('first', () => UI.element('button', {id:'shared',text:text.get(),onClick:()=>clicks.push('first')}));
                workspace.add('second', () => UI.element('button', {id:'shared',text:'second',onClick:()=>clicks.push('second')}));
                const content = id => host.roots[0].children.find(node=>node.props.id===id).children[0];
                const original = content('first');
                const firstId = original.props.id;
                const secondId = content('second').props.id;
                export const distinctIds = firstId !== secondId;
                export const inactiveDispatch = host.root.dispatch(firstId,'click',{});
                workspace.activate('first');
                export const activeDispatch = host.root.dispatch(firstId,'click',{});
                workspace.hide('first');
                const beforeHiddenChange = host.commits;
                text.set('hidden-update');
                export const hiddenSubscribed = host.commits > beforeHiddenChange;
                export const hiddenText = content('first').props.text;
                export const hiddenDispatch = host.root.dispatch(firstId,'click',{});
                workspace.show('first');
                export const sameIdentity = original.handle === content('first').handle && firstId === content('first').props.id;
                export const activeAfterShow = workspace.snapshot().activeId;
                workspace.closeWindow('first');
                const afterClose = host.commits;
                text.set('closed-update');
                export const closedUnsubscribed = host.commits === afterClose;
                export const clickCount = clicks.length;
                """);
        assertTrue(proof.getMember("distinctIds").asBoolean());
        assertFalse(proof.getMember("inactiveDispatch").asBoolean());
        assertTrue(proof.getMember("activeDispatch").asBoolean());
        assertTrue(proof.getMember("hiddenSubscribed").asBoolean());
        assertEquals("hidden-update", proof.getMember("hiddenText").asString());
        assertFalse(proof.getMember("hiddenDispatch").asBoolean());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertEquals("first", proof.getMember("activeAfterShow").asString());
        assertTrue(proof.getMember("closedUnsubscribed").asBoolean());
        assertEquals(1, proof.getMember("clickCount").asInt());
    }

    @Test
    void minimizingRetainsContentSubscriptionsAndRestoringKeepsIdentity() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const text = UI.createSignal('before');
                const workspace = createWorkspace(host);
                workspace.add('notes', () => UI.element('button', {id:'edit',text:text.get(),onClick:()=>{}}),
                    {minimizable:true,rect:{x:10,y:12,width:120,height:100}});
                const content = () => host.roots[0].children[0].children[0];
                const original = content();
                export const minimized = workspace.minimize('notes');
                const minimizedState = workspace.snapshot();
                export const mode = minimizedState.windows[0].mode;
                export const visible = minimizedState.windows[0].visible;
                export const inactive = minimizedState.activeId === null;
                export const inputRejected = !host.root.dispatch(original.props.id,'click',{});
                const commits = host.commits;
                text.set('minimized-update');
                export const subscribed = host.commits > commits && content().props.text === 'minimized-update';
                export const restored = workspace.restore('notes');
                export const restoredMode = workspace.snapshot().windows[0].mode;
                export const sameIdentity = original.handle === content().handle && original.props.id === content().props.id;
                export const unchangedBounds = JSON.stringify(workspace.snapshot().windows[0].rect) ===
                    JSON.stringify({x:10,y:12,width:120,height:100});
                export const active = workspace.snapshot().activeId;
                """);
        assertTrue(proof.getMember("minimized").asBoolean());
        assertEquals("minimized", proof.getMember("mode").asString());
        assertTrue(proof.getMember("visible").asBoolean());
        assertTrue(proof.getMember("inactive").asBoolean());
        assertTrue(proof.getMember("inputRejected").asBoolean());
        assertTrue(proof.getMember("subscribed").asBoolean());
        assertTrue(proof.getMember("restored").asBoolean());
        assertEquals("normal", proof.getMember("restoredMode").asString());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertTrue(proof.getMember("unchangedBounds").asBoolean());
        assertEquals("notes", proof.getMember("active").asString());
    }

    @Test
    void maximizingThenMinimizingRestoresPriorModeAndNormalGeometry() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {viewport:{width:320,height:240}});
                workspace.add('notes', () => UI.element('label', {id:'body',text:'retained'}),
                    {minimizable:true,maximizable:true,rect:{x:10,y:12,width:120,height:100}});
                const original = host.roots[0].children[0].children[0];
                const requested = JSON.stringify(workspace.snapshot().windows[0].rect);
                export const maximized = workspace.maximize('notes');
                export const maxMode = workspace.snapshot().windows[0].mode;
                workspace.minimize('notes');
                export const minimizedMode = workspace.snapshot().windows[0].mode;
                export const priorMode = workspace.snapshot().windows[0].restoreMode;
                workspace.restore('notes');
                export const firstRestore = workspace.snapshot().windows[0].mode;
                workspace.restore('notes');
                export const secondRestore = workspace.snapshot().windows[0].mode;
                export const sameBounds = requested === JSON.stringify(workspace.snapshot().windows[0].rect);
                const current = host.roots[0].children[0].children[0];
                export const sameIdentity = original.handle === current.handle && original.props.id === current.props.id;
                export const publishedNormal = workspace.layout().nodes[0].children[0].rect;
                """);
        assertTrue(proof.getMember("maximized").asBoolean());
        assertEquals("maximized", proof.getMember("maxMode").asString());
        assertEquals("minimized", proof.getMember("minimizedMode").asString());
        assertEquals("maximized", proof.getMember("priorMode").asString());
        assertEquals("maximized", proof.getMember("firstRestore").asString());
        assertEquals("normal", proof.getMember("secondRestore").asString());
        assertTrue(proof.getMember("sameBounds").asBoolean());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        Value bounds = proof.getMember("publishedNormal");
        assertEquals(10, bounds.getMember("x").asInt());
        assertEquals(12, bounds.getMember("y").asInt());
        assertEquals(120, bounds.getMember("width").asInt());
        assertEquals(100, bounds.getMember("height").asInt());
    }

    @Test
    void nativeResizeMaterializesResponsiveGeometryAndKeepsTheOppositeEdge() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {viewport:{width:320,height:240}});
                workspace.add('notes', () => UI.element('label', {id:'body',text:'retained'}),
                    {resizable:true,rect:{x:10,y:12,width:120,height:100},
                     responsiveRect:{x:'10%',y:20,width:{base:'50%',profiles:{2:'60%'}},height:'50%'},
                     minWidth:80,maxWidth:220,minHeight:60,maxHeight:180});
                const shown = () => workspace.layout().nodes[0].children[0].rect;
                const original = host.roots[0].children[0].children[0];
                export const responsive = shown();
                export const nativeResize = host.root.dispatch('notes','resize',
                    {x:200,y:20,width:24,height:120,edge:'w'});
                export const anchored = shown();
                export const materialized = workspace.snapshot().windows[0].rect;
                export const clearedPolicy = workspace.snapshot().windows[0].responsiveRect === null;
                export const resized = workspace.resize('notes',{width:999,height:999});
                export const capped = shown();
                host.root.dispatch('notes','move',{x:999,y:-30});
                export const moved = shown();
                export const requestedMove = workspace.snapshot().windows[0].rect;
                const current = host.roots[0].children[0].children[0];
                export const sameIdentity = original.handle === current.handle && original.props.id === current.props.id;
                """);
        Value responsive = proof.getMember("responsive");
        assertEquals(32, responsive.getMember("x").asInt());
        assertEquals(20, responsive.getMember("y").asInt());
        assertEquals(192, responsive.getMember("width").asInt());
        assertEquals(120, responsive.getMember("height").asInt());
        assertTrue(proof.getMember("nativeResize").asBoolean());
        Value anchored = proof.getMember("anchored");
        assertEquals(144, anchored.getMember("x").asInt());
        assertEquals(80, anchored.getMember("width").asInt());
        assertEquals(144, proof.getMember("materialized").getMember("x").asInt());
        assertEquals(80, proof.getMember("materialized").getMember("width").asInt());
        assertTrue(proof.getMember("clearedPolicy").asBoolean());
        assertTrue(proof.getMember("resized").asBoolean());
        assertEquals(220, proof.getMember("capped").getMember("width").asInt());
        assertEquals(180, proof.getMember("capped").getMember("height").asInt());
        assertEquals(100, proof.getMember("moved").getMember("x").asInt());
        assertEquals(0, proof.getMember("moved").getMember("y").asInt());
        assertEquals(999, proof.getMember("requestedMove").getMember("x").asInt());
        assertEquals(-30, proof.getMember("requestedMove").getMember("y").asInt());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
    }

    @Test
    void hiddenPartialResizePreservesUnchangedNormalGeometry() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host,{viewport:{width:320,height:240}});
                workspace.add('notes',()=>UI.element('label',{id:'body',text:'retained'}),
                    {resizable:true,rect:{x:20,y:30,width:120,height:100}});
                const content=() => host.roots[0].children[0].children[0];
                const original=content();
                export const hidden=workspace.hide('notes');
                export const resized=workspace.resize('notes',{width:160});
                export const requested=workspace.snapshot().windows[0].rect;
                export const shown=workspace.show('notes');
                export const published=workspace.layout().nodes[0].children[0].rect;
                export const sameIdentity=original.handle === content().handle && original.props.id === content().props.id;
                export const bindings=host.bindings;
                """);
        assertTrue(proof.getMember("hidden").asBoolean());
        assertTrue(proof.getMember("resized").asBoolean());
        Value requested = proof.getMember("requested");
        assertEquals(160, requested.getMember("width").asInt());
        assertEquals(100, requested.getMember("height").asInt());
        assertEquals(20, requested.getMember("x").asInt());
        assertEquals(30, requested.getMember("y").asInt());
        assertTrue(proof.getMember("shown").asBoolean());
        Value published = proof.getMember("published");
        assertEquals(20, published.getMember("x").asInt());
        assertEquals(30, published.getMember("y").asInt());
        assertEquals(160, published.getMember("width").asInt());
        assertEquals(100, published.getMember("height").asInt());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertEquals(1, proof.getMember("bindings").asInt());
    }

    @Test
    void nativeChromeMaximizesPublishedBoundsAndRestoresTheNormalWindow() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {viewport:{width:320,height:240}});
                workspace.add('notes', () => UI.element('label',{id:'body',text:'retained'}),
                    {minimizable:true,maximizable:true,titlebarHeight:32,titlebarButtonWidth:16,titlebarTitle:false,
                     rect:{x:10,y:12,width:120,height:100}});
                const window = () => host.roots[0].children[0];
                const layout = () => workspace.layout().nodes[0].children[0];
                const original = window().children[0];
                export const chrome = window().props;
                export const normalContentY = layout().children[0].rect.y;
                export const maximizedByChrome = host.root.dispatch('notes','maximize');
                export const maximized = layout().rect;
                export const maxContentY = layout().children[0].rect.y;
                export const normalRestoreRect = workspace.snapshot().windows[0].rect;
                export const minimizedByChrome = host.root.dispatch('notes','minimize');
                export const minimizedMode = workspace.snapshot().windows[0].mode;
                export const invisible = window().props.visible === false;
                workspace.restore('notes');
                export const restoredMode = workspace.snapshot().windows[0].mode;
                export const restoredMaxWidth = layout().rect.width;
                export const restoredByChrome = host.root.dispatch('notes','restore');
                export const restored = layout().rect;
                export const sameIdentity = original.handle === window().children[0].handle
                    && original.props.id === window().children[0].props.id;
                """);
        Value chrome = proof.getMember("chrome");
        assertEquals(32, chrome.getMember("titlebarHeight").asInt());
        assertEquals(16, chrome.getMember("titlebarButtonWidth").asInt());
        assertFalse(chrome.getMember("titlebarTitle").asBoolean());
        assertTrue(chrome.getMember("minimizable").asBoolean());
        assertTrue(chrome.getMember("maximizable").asBoolean());
        assertEquals(44, proof.getMember("normalContentY").asInt());
        assertTrue(proof.getMember("maximizedByChrome").asBoolean());
        Value maximized = proof.getMember("maximized");
        assertEquals(0, maximized.getMember("x").asInt());
        assertEquals(0, maximized.getMember("y").asInt());
        assertEquals(320, maximized.getMember("width").asInt());
        assertEquals(240, maximized.getMember("height").asInt());
        assertEquals(32, proof.getMember("maxContentY").asInt());
        assertEquals(10, proof.getMember("normalRestoreRect").getMember("x").asInt());
        assertEquals(120, proof.getMember("normalRestoreRect").getMember("width").asInt());
        assertTrue(proof.getMember("minimizedByChrome").asBoolean());
        assertEquals("minimized", proof.getMember("minimizedMode").asString());
        assertTrue(proof.getMember("invisible").asBoolean());
        assertEquals("maximized", proof.getMember("restoredMode").asString());
        assertEquals(320, proof.getMember("restoredMaxWidth").asInt());
        assertTrue(proof.getMember("restoredByChrome").asBoolean());
        assertEquals(10, proof.getMember("restored").getMember("x").asInt());
        assertEquals(12, proof.getMember("restored").getMember("y").asInt());
        assertEquals(120, proof.getMember("restored").getMember("width").asInt());
        assertEquals(100, proof.getMember("restored").getMember("height").asInt());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
    }

    @Test
    void taskbarReservesWorkAreaAndRestoresTheSameMaximizedWindow() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host,{minimizedPresentation:'taskbar',
                    viewport:{width:320,height:240,safeArea:{left:10,top:12}}});
                const desktop = () => host.roots[0].type === 'screen' ? host.roots[0].children[0] : host.roots[0];
                const desktopLayout = () => workspace.layout().nodes[0].type === 'screen'
                    ? workspace.layout().nodes[0].children[0] : workspace.layout().nodes[0];
                export const rootType = host.roots[0].type;
                export const emptyWorkHeight = desktopLayout().rect.height;
                workspace.add('notes', () => UI.element('label',{id:'body',text:'retained'}),
                    {title:'Notes',minimizable:true,maximizable:true,rect:{x:20,y:22,width:120,height:100}});
                const original = desktop().children[0].children[0];
                workspace.maximize('notes');
                export const maximized = desktopLayout().children[0].rect;
                host.root.dispatch('notes','minimize');
                function findButton(nodes) {
                    for (const node of nodes) {
                        if (node.type === 'button' && node.props.text === 'Notes') return node;
                        const nested = findButton(node.children);
                        if (nested) return nested;
                    }
                    return null;
                }
                const button = findButton(host.roots);
                export const hasEntry = button !== null;
                export const equalFill = button?.props.width === 'fill';
                const bar = workspace.layout().nodes[0].children.find(node => node.type === 'panel');
                export const barY = bar?.rect.y ?? -1;
                export const barHeight = bar?.rect.height ?? -1;
                export const restored = button ? host.root.dispatch(button.props.id,'click') : false;
                export const restoredMode = workspace.snapshot().windows[0].mode;
                export const active = workspace.snapshot().activeId;
                export const sameIdentity = original.handle === desktop().children[0].children[0].handle
                    && original.props.id === desktop().children[0].children[0].props.id;
                workspace.closeWindow('notes');
                export const retainedEmptyDesktop = !workspace.isDisposed() && host.closes === 0;
                export const finalWorkHeight = desktopLayout().rect.height;
                export const binds = host.bindings;
                """);
        assertEquals("screen", proof.getMember("rootType").asString());
        assertEquals(200, proof.getMember("emptyWorkHeight").asInt());
        Value maximized = proof.getMember("maximized");
        assertEquals(10, maximized.getMember("x").asInt());
        assertEquals(12, maximized.getMember("y").asInt());
        assertEquals(310, maximized.getMember("width").asInt());
        assertEquals(200, maximized.getMember("height").asInt());
        assertTrue(proof.getMember("hasEntry").asBoolean());
        assertTrue(proof.getMember("equalFill").asBoolean());
        assertEquals(212, proof.getMember("barY").asInt());
        assertEquals(28, proof.getMember("barHeight").asInt());
        assertTrue(proof.getMember("restored").asBoolean());
        assertEquals("maximized", proof.getMember("restoredMode").asString());
        assertEquals("notes", proof.getMember("active").asString());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertTrue(proof.getMember("retainedEmptyDesktop").asBoolean());
        assertEquals(200, proof.getMember("finalWorkHeight").asInt());
        assertEquals(1, proof.getMember("binds").asInt());
    }

    @Test
    void floatingRestoreButtonsStayBoundedWithoutReservingTheWorkspace() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host,{minimizedPresentation:'floating',
                    viewport:{width:320,height:240,safeArea:{left:10,top:12}}});
                const desktop = () => host.roots[0].children[0];
                const desktopLayout = () => workspace.layout().nodes[0].children[0];
                const presentation = () => workspace.layout().nodes[0].children.find(node => node.type === 'panel');
                export const rootLayout = host.roots[0].props.layout;
                export const noEmptyOverlay = presentation() === undefined;
                export const workHeight = desktopLayout().rect.height;
                workspace.add('notes',()=>UI.element('label',{id:'body',text:'retained'}),
                    {title:'Notes',minimizable:true,maximizable:true,rect:{x:20,y:22,width:120,height:100}});
                workspace.add('tool',()=>UI.element('label',{id:'body',text:'tool'}),{title:'Tool',minimizable:true});
                const note = () => desktop().children.find(node => node.props.id === 'notes');
                const original = note().children[0];
                workspace.maximize('notes');
                export const maximized = desktopLayout().children.find(node => node.id === 'notes').rect;
                workspace.minimize('notes');
                workspace.minimize('tool');
                export const bounds = presentation().rect;
                export const hasScroll = presentation().children[0].type === 'scroll';
                function findButton(nodes) {
                    for (const node of nodes) {
                        if (node.type === 'button' && node.props.text === 'Notes') return node;
                        const nested = findButton(node.children);
                        if (nested) return nested;
                    }
                    return null;
                }
                const button = findButton(host.roots);
                export const restored = button ? host.root.dispatch(button.props.id,'click') : false;
                export const restoredMode = workspace.snapshot().windows.find(window => window.id === 'notes').mode;
                export const singleEntryHeight = presentation().rect.height;
                export const sameIdentity = original.handle === note().children[0].handle
                    && original.props.id === note().children[0].props.id;
                workspace.activate('tool');
                export const noFinalOverlay = presentation() === undefined;
                export const finalWorkHeight = desktopLayout().rect.height;
                export const bindings = host.bindings;
                """);
        assertEquals("stack", proof.getMember("rootLayout").asString());
        assertTrue(proof.getMember("noEmptyOverlay").asBoolean());
        assertEquals(228, proof.getMember("workHeight").asInt());
        assertEquals(310, proof.getMember("maximized").getMember("width").asInt());
        assertEquals(228, proof.getMember("maximized").getMember("height").asInt());
        Value bounds = proof.getMember("bounds");
        assertEquals(10, bounds.getMember("x").asInt());
        assertEquals(188, bounds.getMember("y").asInt());
        assertEquals(220, bounds.getMember("width").asInt());
        assertEquals(52, bounds.getMember("height").asInt());
        assertTrue(proof.getMember("hasScroll").asBoolean());
        assertTrue(proof.getMember("restored").asBoolean());
        assertEquals("maximized", proof.getMember("restoredMode").asString());
        assertEquals(28, proof.getMember("singleEntryHeight").asInt());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertTrue(proof.getMember("noFinalOverlay").asBoolean());
        assertEquals(228, proof.getMember("finalWorkHeight").asInt());
        assertEquals(1, proof.getMember("bindings").asInt());
    }

    @Test
    void floatingPresetFitsSmallViewportAndKeepsTheFirstRestoreControlVisible() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host,{minimizedPresentation:'floating',viewport:{width:100,height:80}});
                for (let index=0;index<7;index++) {
                    workspace.add('window-'+index,()=>UI.element('label',{text:'content'}),{minimizable:true});
                    workspace.minimize('window-'+index);
                }
                const root=workspace.layout().nodes[0];
                const panel=root.children.find(node => node.type === 'panel');
                const scroll=panel.children[0];
                const first=scroll.children[0].children[0];
                export const panelBounds=panel.rect;
                export const scrollClip=scroll.clip;
                export const firstBounds=first.rect;
                export const firstClip=first.clip;
                export const workHeight=root.children[0].rect.height;
                export const bindings=host.bindings;
                """);
        Value panel = proof.getMember("panelBounds");
        assertEquals(0, panel.getMember("x").asInt());
        assertEquals(0, panel.getMember("y").asInt());
        assertEquals(100, panel.getMember("width").asInt());
        assertEquals(80, panel.getMember("height").asInt());
        Value scroll = proof.getMember("scrollClip");
        assertEquals(4, scroll.getMember("x").asInt());
        assertEquals(4, scroll.getMember("y").asInt());
        assertEquals(92, scroll.getMember("width").asInt());
        assertEquals(72, scroll.getMember("height").asInt());
        Value first = proof.getMember("firstBounds");
        assertEquals(4, first.getMember("x").asInt());
        assertEquals(4, first.getMember("y").asInt());
        assertEquals(92, first.getMember("width").asInt());
        assertEquals(20, first.getMember("height").asInt());
        Value firstClip = proof.getMember("firstClip");
        assertEquals(4, firstClip.getMember("x").asInt());
        assertEquals(4, firstClip.getMember("y").asInt());
        assertEquals(92, firstClip.getMember("width").asInt());
        assertEquals(20, firstClip.getMember("height").asInt());
        assertEquals(80, proof.getMember("workHeight").asInt());
        assertEquals(1, proof.getMember("bindings").asInt());
    }

    @Test
    void customMinimizedPanelUsesFrozenContextAndRejectsInvalidCandidatesAtomically() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const text = UI.createSignal('before');
                let calls=0, initialRejected=false, invalid=null, lastContext=null, outerExpanded=false;
                function Restore(props) {
                    const window = props.context.windows[0];
                    return UI.element('button',{id:'custom-restore',key:window.id,width:'fill',height:'fill',
                        text:window.title,onClick:()=>props.context.restore(window.id)});
                }
                const workspace = createWorkspace(host,{viewport:{width:320,height:240,safeArea:{left:10,top:12}},
                    minimizedPresentation: context => {
                        if (calls++ === 0) {
                            try { context.activate('absent') }
                            catch(error) { initialRejected=String(error).includes('[NEKO-7013]') }
                        }
                        if (context.windows.length === 0) return null;
                        lastContext=context;
                        if (invalid === 'component') return UI.element(() => {
                            outerExpanded=true;return UI.element('panel',{width:180,height:28,anchor:'bottomRight'});
                        },{});
                        const props={key:'custom-panel',width:180,anchor:'bottomRight',workspacePresentation:false,
                            children:UI.element(Restore,{context})};
                        if (invalid !== 'bounds') props.height=28;
                        return UI.element('panel',props);
                    }});
                const desktop = () => host.roots[0].children[0];
                const panel = () => host.roots[0].children.find(node => node.type === 'panel');
                const note = () => desktop().children.find(node => node.props.id === 'notes');
                workspace.add('hidden',()=>UI.element('label',{text:'hidden'}),{visible:false,minimizable:true});
                workspace.minimize('hidden');
                export const hiddenHasNoOverlay = panel() === undefined;
                workspace.add('notes',()=>UI.element('label',{id:'body',text:text.get()}),
                    {title:'Notes',minimizable:true,maximizable:true,rect:{x:20,y:22,width:120,height:100}});
                workspace.maximize('notes');
                const original=note().children[0];
                workspace.minimize('notes');
                const context=lastContext;
                export const initialGuard=initialRejected;
                export const frozen=Object.isFrozen(context) && Object.isFrozen(context.windows)
                    && Object.isFrozen(context.windows[0]) && Object.isFrozen(context.windows[0].rect);
                export const filtered=context.windows.length === 1 && context.windows[0].id === 'notes'
                    && context.windows[0].mode === 'minimized' && context.windows[0].visible;
                export const actions=typeof context.restore === 'function' && typeof context.activate === 'function';
                export const panelKey=panel().key;
                export const marked=panel().props.workspacePresentation === true;
                export const bounds=workspace.layout().nodes[0].children.find(node => node.type === 'panel').rect;
                export const restored=host.root.dispatch('custom-restore','click');
                export const restoredMode=workspace.snapshot().windows.find(window => window.id === 'notes').mode;
                export const nullHasNoOverlay=panel() === undefined;
                let allRejected=true;
                for (const candidate of ['component','bounds']) {
                    invalid=candidate;
                    if (workspace.minimize('notes') !== false || panel() !== undefined
                        || workspace.snapshot().windows.find(window => window.id === 'notes').mode !== 'maximized') allRejected=false;
                }
                export const atomicRejections=allRejected && !outerExpanded;
                export const diagnosed=host.diagnostics.length === 2 && host.diagnostics.every(diagnostic =>
                    String(diagnostic.error).includes('[NEKO-7013]'));
                text.set('after-rejection');
                export const signalKeptCommittedState=panel() === undefined && note().children[0].props.text === 'after-rejection'
                    && workspace.snapshot().windows.find(window => window.id === 'notes').mode === 'maximized';
                export const sameIdentity=original.handle === note().children[0].handle
                    && original.props.id === note().children[0].props.id;
                export const workHeight=workspace.layout().nodes[0].children[0].rect.height;
                export const bindings=host.bindings;
                """);
        assertTrue(proof.getMember("initialGuard").asBoolean());
        assertTrue(proof.getMember("hiddenHasNoOverlay").asBoolean());
        assertTrue(proof.getMember("frozen").asBoolean());
        assertTrue(proof.getMember("filtered").asBoolean());
        assertTrue(proof.getMember("actions").asBoolean());
        assertEquals("custom-panel", proof.getMember("panelKey").asString());
        assertTrue(proof.getMember("marked").asBoolean());
        Value bounds=proof.getMember("bounds");
        assertEquals(140, bounds.getMember("x").asInt());
        assertEquals(212, bounds.getMember("y").asInt());
        assertEquals(180, bounds.getMember("width").asInt());
        assertEquals(28, bounds.getMember("height").asInt());
        assertTrue(proof.getMember("restored").asBoolean());
        assertEquals("maximized", proof.getMember("restoredMode").asString());
        assertTrue(proof.getMember("nullHasNoOverlay").asBoolean());
        assertTrue(proof.getMember("atomicRejections").asBoolean());
        assertTrue(proof.getMember("diagnosed").asBoolean());
        assertTrue(proof.getMember("signalKeptCommittedState").asBoolean());
        assertTrue(proof.getMember("sameIdentity").asBoolean());
        assertEquals(228, proof.getMember("workHeight").asInt());
        assertEquals(1, proof.getMember("bindings").asInt());
    }

    @Test
    void nativeWindowEventsRaiseMoveClampAndCloseWithoutClosingTheDesktop() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host);
                workspace.add('first', () => UI.element('label', {text:'First'}), {rect:{x:10,y:12,width:120,height:100}});
                workspace.add('second', () => UI.element('label', {text:'Second'}));
                host.root.dispatch('first','activate',{});
                export const raisedId = host.roots[0].children[1].props.id;
                host.root.dispatch('first','move',{x:999,y:-30});
                export const requested = workspace.snapshot().windows[1].rect;
                export const effective = workspace.layout().nodes[0].children[1].rect;
                host.root.dispatch('first','dismiss',{reason:'close'});
                export const remaining = workspace.snapshot().windows[0].id;
                workspace.closeWindow('second');
                export const empty = workspace.snapshot().windows.length;
                export const desktopAlive = !workspace.isDisposed() && host.roots.length === 1;
                export const closes = host.closes;
                """);
        assertEquals("first", proof.getMember("raisedId").asString());
        assertEquals(999, proof.getMember("requested").getMember("x").asInt());
        assertEquals(-30, proof.getMember("requested").getMember("y").asInt());
        assertEquals(200, proof.getMember("effective").getMember("x").asInt());
        assertEquals(0, proof.getMember("effective").getMember("y").asInt());
        assertEquals("second", proof.getMember("remaining").asString());
        assertEquals(0, proof.getMember("empty").asInt());
        assertTrue(proof.getMember("desktopAlive").asBoolean());
        assertEquals(0, proof.getMember("closes").asInt());
    }

    @Test
    void offOwnerMutationsWaitForAdmissionAndCloseFailureRemainsRetryable() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const text = UI.createSignal('before');
                const workspace = createWorkspace(host);
                host.owner=false;
                export const queued = workspace.add('first',()=>UI.element('label',{text:text.get()}));
                export const beforeAdmission = workspace.snapshot().windows.length;
                host.owner=true;
                host.queue.splice(0).forEach(action=>action());
                export const afterAdmission = workspace.snapshot().windows.length;
                host.failCommit=true;
                export const rejectedClose = workspace.close();
                export const retryable = !workspace.isDisposed() && workspace.snapshot().windows.length===1;
                host.failCommit=false;
                text.set('still-subscribed');
                export const retainedText = host.roots[0].children[0].children[0].props.text;
                export const successfulClose = workspace.close();
                const afterClose=host.commits;
                text.set('released');
                export const released = workspace.isDisposed() && workspace.snapshot().windows.length===0 && host.commits===afterClose;
                export const closes = host.closes;
                """);
        assertEquals("queued", proof.getMember("queued").asString());
        assertEquals(0, proof.getMember("beforeAdmission").asInt());
        assertEquals(1, proof.getMember("afterAdmission").asInt());
        assertFalse(proof.getMember("rejectedClose").asBoolean());
        assertTrue(proof.getMember("retryable").asBoolean());
        assertEquals("still-subscribed", proof.getMember("retainedText").asString());
        assertTrue(proof.getMember("successfulClose").asBoolean());
        assertTrue(proof.getMember("released").asBoolean());
        assertEquals(1, proof.getMember("closes").asInt());
    }

    @Test
    void nestedBusinessComponentsCannotOpenTheHostDuringASignalRender() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const trigger = UI.createSignal(false);
                const workspace = createWorkspace(host);
                function Nested() {
                    if(trigger.get()) workspace.open();
                    return UI.element('label',{text:'stable'});
                }
                workspace.add('first',()=>UI.element(Nested,{}));
                const before = host.roots[0].children[0].handle;
                trigger.set(true);
                export const opens = host.opens;
                export const retained = !workspace.isDisposed() && host.roots[0].children[0].handle===before;
                export const diagnosed = host.diagnostics.some(item=>String(item.error).includes('Workspace cannot be changed'));
                """);
        assertEquals(0, proof.getMember("opens").asInt());
        assertTrue(proof.getMember("retained").asBoolean());
        assertTrue(proof.getMember("diagnosed").asBoolean());
    }

    @Test
    void viewportResizeConstrainsPublishedGeometryWithoutRerenderingBusinessContent() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                let renders = 0;
                const workspace = createWorkspace(host, {viewport:{width:320,height:240,safeArea:{left:5,top:7,right:15,bottom:13}}});
                workspace.add('first', () => {renders++;return UI.element('label',{text:'content'})},
                    {rect:{x:999,y:999,width:120,height:100}});
                export const before = workspace.layout().nodes[0].children[0].rect;
                const beforeRenders = renders;
                host.root.resize({width:200,height:120,safeArea:{left:5,top:7,right:15,bottom:13}});
                export const after = workspace.layout().nodes[0].children[0].rect;
                export const contentClip = workspace.layout().nodes[0].children[0].children[0].clip;
                export const noBusinessRerender = renders === beforeRenders;
                export const requestedX = workspace.snapshot().windows[0].rect.x;
                """);
        assertEquals(185, proof.getMember("before").getMember("x").asInt());
        assertEquals(127, proof.getMember("before").getMember("y").asInt());
        assertEquals(65, proof.getMember("after").getMember("x").asInt());
        assertEquals(7, proof.getMember("after").getMember("y").asInt());
        assertEquals(27, proof.getMember("contentClip").getMember("y").asInt());
        assertTrue(proof.getMember("noBusinessRerender").asBoolean());
        assertEquals(999, proof.getMember("requestedX").asInt());
    }

    @Test
    void explicitEmptyPolicyClosesAndForcedHostDisposalReleasesSubscriptions() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host, {closeWhenEmpty:true});
                workspace.add('first',()=>UI.element('label',{text:'first'}));
                workspace.closeWindow('first');
                export const closedWhenEmpty = workspace.isDisposed() && host.closes===1;
                const secondHost = fakeHost();
                const text = UI.createSignal('before');
                const secondWorkspace = createWorkspace(secondHost);
                secondWorkspace.add('second',()=>UI.element('label',{text:text.get()}));
                secondHost.failCommit=true;
                secondHost.root.forceClose();
                const before = secondHost.commits;
                text.set('after');
                export const forced = secondWorkspace.isDisposed() && secondWorkspace.snapshot().windows.length===0;
                export const unsubscribed = secondHost.commits===before;
                export const rejectedAfterDispose = secondWorkspace.add('late',()=>null);
                """);
        assertTrue(proof.getMember("closedWhenEmpty").asBoolean());
        assertTrue(proof.getMember("forced").asBoolean());
        assertTrue(proof.getMember("unsubscribed").asBoolean());
        assertFalse(proof.getMember("rejectedAfterDispose").asBoolean());
    }

    @Test
    void visibleModalOutranksTheContainingInactiveWindow() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host);
                let modalClicks=0, backgroundClicks=0;
                workspace.add('first',()=>UI.element('dialog',{id:'modal',children:
                    UI.element('button',{id:'action',text:'modal',onClick:()=>modalClicks++})}));
                workspace.add('second',()=>UI.element('button',{id:'background',text:'background',onClick:()=>backgroundClicks++}));
                const first=host.roots[0].children[0], second=host.roots[0].children[1];
                export const modalDispatch=host.root.dispatch(first.children[0].children[0].props.id,'click',{});
                export const blockedDispatch=host.root.dispatch(second.children[0].props.id,'click',{});
                export const blockedActivation=host.root.dispatch('first','activate',{});
                export const activeId=workspace.snapshot().activeId;
                export const modalCount=modalClicks;
                export const backgroundCount=backgroundClicks;
                """);
        assertTrue(proof.getMember("modalDispatch").asBoolean());
        assertFalse(proof.getMember("blockedDispatch").asBoolean());
        assertFalse(proof.getMember("blockedActivation").asBoolean());
        assertEquals("second", proof.getMember("activeId").asString());
        assertEquals(1, proof.getMember("modalCount").asInt());
        assertEquals(0, proof.getMember("backgroundCount").asInt());
    }

    @Test
    void malformedNestedWindowsAreRejectedBeforeControllerPublication() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const workspace = createWorkspace(host);
                workspace.add('valid',()=>UI.element('label',{text:'valid'}));
                export const nestedWindow = workspace.add('bad-window',()=>UI.element('window',
                    {id:'nested',title:'Nested',x:0,y:0,width:80,height:60,active:true}));
                export const nestedWorkspace = workspace.add('bad-workspace',()=>UI.element('workspace',{}));
                export const count = workspace.snapshot().windows.length;
                export const publishedCount = host.roots[0].children.length;
                export const active = workspace.snapshot().activeId;
                """);
        assertFalse(proof.getMember("nestedWindow").asBoolean());
        assertFalse(proof.getMember("nestedWorkspace").asBoolean());
        assertEquals(1, proof.getMember("count").asInt());
        assertEquals(1, proof.getMember("publishedCount").asInt());
        assertEquals("valid", proof.getMember("active").asString());
    }

    private Value run(String source) throws Exception {
        Path entry = paths.testScripts().resolve("workspace.tsx");
        Files.writeString(entry, "import { UI } from 'nekojs/jsx-runtime';\n"
                + "import { createWorkspace } from 'nekojs/jsx-workspace';\n" + HOST + source);
        Object exports = loader.loadEntry(entry.toString());
        assertTrue(exports instanceof Value);
        return (Value) exports;
    }

    private static final String HOST = """
            function fakeHost() {
                let sequence = 0;
                let nodes = new Map();
                let pendingSnapshot = null;
                const host = {
                    owner:true, queue:[], roots:[], snapshot:null, bindings:0, opens:0,
                    commits:0, closes:0, failCommit:false, diagnostics:[],
                    isOwnerThread: () => host.owner,
                    enqueue: action => {host.queue.push(action);return true},
                    supportsPrimitive: type => UI.primitives().includes(type),
                    measureText: (text,fontSize,maxWidth) => ({width:Math.min(text.length*fontSize*0.6,maxWidth),height:fontSize}),
                    bindRoot: root => {host.root=root;host.bindings++},
                    open: () => {host.opens++},
                    close: () => {if(host.root.close()!==true) return false;host.closes++;return true},
                    viewport: () => ({width:320,height:240}),
                    layout: (tree,viewport,snapshot,publish) => {
                        if (publish) host.snapshot=snapshot;
                        else pendingSnapshot=snapshot;
                    },
                    reportDiagnostic: diagnostic => host.diagnostics.push(diagnostic),
                    begin: () => {
                        const staged = new Map(Array.from(nodes, ([id,node]) => [id,{...node,children:node.children.slice()}]));
                        const tree = id => {const node=staged.get(id);return {...node,children:node.children.map(tree)}};
                        return {
                            create: (type,key,props) => {
                                const handle=++sequence;staged.set(handle,{handle,type,key,props,children:[]});return handle;
                            },
                            update: (handle,type,key,props) => {const node=staged.get(handle);staged.set(handle,{...node,type,key,props})},
                            remove: handle => staged.delete(handle),
                            order: (parent,children) => {if(parent!=null) staged.get(parent).children=Array.from(children)},
                            commit: roots => {
                                if(host.failCommit) throw new Error('fixture rejected commit');
                                host.roots=Array.from(roots,tree);host.snapshot=pendingSnapshot;nodes=staged;host.commits++;
                            },
                            rollback: () => {}
                        };
                    }
                };
                return host;
            }
            """;
}
