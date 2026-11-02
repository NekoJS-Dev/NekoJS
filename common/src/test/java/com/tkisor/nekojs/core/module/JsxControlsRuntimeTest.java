package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NodeModuleTypeDocs;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real compiler, module loader and UI runtime with only the platform host replaced. */
class JsxControlsRuntimeTest {
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
    void sliderDefaultsLayoutAndPrimitiveEventKeepTheirOriginalTypesWithoutSnapping() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                let observed = null;
                const root = UI.createRoot(() => <column>
                    <slider id="default" />
                    <slider id="fraction" min={0} max={1} step={0.25} value={0.3}
                        tooltip="Volume" appearance={{trackColor: '#123', thumbColor: 'white'}}
                        onChange={event => observed = { value: event.value, type: typeof event.value }} />
                </column>, host, viewport);
                export const defaults = host.props('default');
                export const fraction = host.props('fraction');
                export const rect = find(root.layout().nodes, 'default').rect;
                export const dispatched = root.dispatch('fraction', 'change', {value: '0.5'});
                export const event = observed;
                root.close();
                """);
        Value defaults = proof.getMember("defaults");
        assertEquals(0, defaults.getMember("value").asInt());
        assertEquals(0, defaults.getMember("min").asInt());
        assertEquals(100, defaults.getMember("max").asInt());
        assertEquals(1, defaults.getMember("step").asInt());
        assertEquals(0.3, proof.getMember("fraction").getMember("value").asDouble());
        assertEquals(100, proof.getMember("rect").getMember("width").asInt());
        assertEquals(20, proof.getMember("rect").getMember("height").asInt());
        assertTrue(proof.getMember("dispatched").asBoolean());
        assertEquals("0.5", proof.getMember("event").getMember("value").asString());
        assertEquals("string", proof.getMember("event").getMember("type").asString());
    }

    @Test
    void invalidSliderConfigurationsFailBeforeCreatingHostNodes() throws Exception {
        Value proof = run("""
                const invalid = [
                    {value: NaN}, {value: Infinity}, {value: -1}, {value: 101}, {value: '1'},
                    {min: 1}, {min: 100, max: 100}, {min: 101, max: 100}, {min: -Infinity},
                    {max: NaN}, {step: 0}, {step: -1}, {step: Infinity}, {step: '1'}, {value: null},
                    {min: -1e308, max: 1e308}, {max: 1e308, step: 1e-308}, {step: null}
                ];
                export const failures = invalid.map(props => rejected(() => UI.element('slider', props)));
                const host = fakeHost();
                const root = UI.createRoot(() => <column>
                    <slider min={-2} max={2} step={0.5} value={-2}/>
                    <slider min={-2} max={2} step={0.5} value={2}/>
                </column>, host, viewport);
                export const boundaryCount = host.creates;
                root.close();
                """);
        assertRejectedBeforeHost(proof.getMember("failures"), 18);
        assertEquals(3, proof.getMember("boundaryCount").asInt());
    }

    @Test
    void appearanceAcceptsControlledColorsAndRejectsUnknownNestedOrInvalidStates() throws Exception {
        Value proof = run("""
                const invalid = [null, [], 'red', {bogus: 'red'}, {background: null}, {color: NaN},
                    {color: 1.5}, {color: 4294967296}, {color: -2147483649}, {color: 'rgb(0,0,0)'},
                    {hover: {pressed: {color: 'red'}}}, {focused: {bogus: 'red'}}, {disabled: null},
                    {pressed: []}, {background: '#12'}, {trackColor: true}];
                export const failures = ['button', 'input', 'slider'].flatMap(type =>
                    invalid.map(invalidAppearance => rejected(() => UI.element(type, {appearance: invalidAppearance}))));
                const validAppearance = {
                    background: -2147483648, borderColor: 4294967295, color: ' White ',
                    placeholderColor: '#123', selectionColor: '#123456', cursorColor: '#80123456',
                    trackColor: 'transparent', thumbColor: 'aqua', hover: {background: 'red'},
                    focused: {borderColor: 'blue'}, pressed: {color: 'lime'}, disabled: {color: 'gray'}
                };
                const host = fakeHost();
                const root = UI.createRoot(() => <column><button id="button" appearance={validAppearance}/>
                    <input id="input" tooltip="Hint" appearance={validAppearance}/>
                    <slider id="slider" appearance={validAppearance}/></column>, host, viewport);
                export const forwarded = host.props('slider').appearance;
                root.close();
                """);
        assertRejectedBeforeHost(proof.getMember("failures"), 48);
        assertEquals("aqua", proof.getMember("forwarded").getMember("thumbColor").asString());
        assertEquals("lime", proof.getMember("forwarded").getMember("pressed").getMember("color").asString());
    }

    @Test
    void reusableControlsAreCallerControlledAndSelectIdsSurviveOptionReordering() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const checked = UI.createSignal(false);
                const value = UI.createSignal(0.3);
                const selected = UI.createSignal('a');
                const expanded = UI.createSignal(false);
                const options = UI.createSignal([{value: 'a', label: 'Same'}, {value: 'b/x', label: 'Same'},
                    {value: 'blocked', label: 'Blocked', disabled: true}]);
                const changed = [];
                const root = UI.createRoot(() => <column>
                    <Toggle id="toggle" checked={checked.get()} label="Enabled" onChange={next => checked.set(next)}/>
                    <Slider id="volume" value={value.get()} max={1} step={0.25}
                        onChange={next => {changed.push(typeof next); value.set(next)}}/>
                    <Select id="choice" value={selected.get()} options={options.get()} expanded={expanded.get()}
                        onExpandedChange={next => expanded.set(next)} onChange={next => selected.set(next)}/>
                </column>, host, viewport);
                export const initial = host.props('toggle').text;
                root.dispatch('toggle', 'click', {});
                export const toggled = host.props('toggle').text;
                root.dispatch('volume', 'change', {value: '0.75'});
                export const numeric = {value: value.get(), type: changed[0]};
                root.dispatch('choice', 'click', {});
                export const listHeight = find(root.layout().nodes, 'choice:options').rect.height;
                export const blocked = root.dispatch('choice:option:blocked', 'click', {});
                options.set(options.get().slice().reverse());
                export const choiceStillPresent = host.props('choice:option:b%2Fx').text;
                export const selectedDispatch = root.dispatch('choice:option:b%2Fx', 'click', {});
                export const state = {selected: selected.get(), expanded: expanded.get(), checked: checked.get()};
                root.refresh();
                export const afterRefresh = {text: host.props('toggle').text, value: host.props('volume').value,
                    label: host.props('choice').text, renders: host.commits};
                root.close();
                export const detached = root.dispatch('toggle', 'click', {});
                """);
        assertEquals("[ ] Enabled", proof.getMember("initial").asString());
        assertEquals("[x] Enabled", proof.getMember("toggled").asString());
        assertEquals(0.75, proof.getMember("numeric").getMember("value").asDouble());
        assertEquals("number", proof.getMember("numeric").getMember("type").asString());
        assertEquals(60, proof.getMember("listHeight").asInt());
        assertFalse(proof.getMember("blocked").asBoolean());
        assertEquals("Same", proof.getMember("choiceStillPresent").asString());
        assertTrue(proof.getMember("selectedDispatch").asBoolean());
        assertEquals("b/x", proof.getMember("state").getMember("selected").asString());
        assertFalse(proof.getMember("state").getMember("expanded").asBoolean());
        assertTrue(proof.getMember("state").getMember("checked").asBoolean());
        assertEquals("[x] Enabled", proof.getMember("afterRefresh").getMember("text").asString());
        assertEquals(0.75, proof.getMember("afterRefresh").getMember("value").asDouble());
        assertEquals(7, proof.getMember("afterRefresh").getMember("renders").asInt(),
                "Select value and expansion callbacks publish one batched render");
        assertFalse(proof.getMember("detached").asBoolean());
    }

    @Test
    void disabledControlsAndOptionsCannotBeInvokedThroughDirectOrQueuedDispatch() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const disabled = UI.createSignal(false);
                let calls = 0;
                const root = UI.createRoot(() => <column>
                    <button id="button" disabled={disabled.get()} onClick={() => calls++}/>
                    <input id="input" disabled={disabled.get()} onChange={() => calls++}/>
                    <slider id="primitive" disabled={disabled.get()} onChange={() => calls++}/>
                    <Toggle id="toggle" disabled={disabled.get()} checked={false} onChange={() => calls++}/>
                    <Slider id="slider" disabled={disabled.get()} value={0} onChange={() => calls++}/>
                    <Select id="select" disabled={disabled.get()} value="a" options={[{value:'a',label:'A'}]}
                        expanded={true} onChange={() => calls++} onExpandedChange={() => calls++}/>
                </column>, host, viewport);
                host.owner = false;
                export const queued = root.dispatch('button', 'click', {});
                host.owner = true;
                disabled.set(true);
                host.queue.shift()();
                export const results = [root.dispatch('button', 'click', {}), root.dispatch('input', 'change', {value:'x'}),
                    root.dispatch('primitive', 'change', {value:'1'}), root.dispatch('toggle', 'click', {}),
                    root.dispatch('slider', 'change', {value:'1'}), root.dispatch('select', 'click', {}),
                    root.dispatch('select:option:a', 'click', {})];
                export const count = calls;
                root.close();
                """);
        assertEquals("queued", proof.getMember("queued").asString());
        for (long index = 0; index < proof.getMember("results").getArraySize(); index++) {
            assertFalse(proof.getMember("results").getArrayElement(index).asBoolean());
        }
        assertEquals(0, proof.getMember("count").asInt());
    }

    @Test
    void malformedSliderEventsAreDiagnosedWithoutCallingNumericHandler() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                let calls = 0;
                const root = UI.createRoot(() => <Slider id="volume" value={0.3} max={1} step={0.25}
                    onChange={() => calls++}/>, host, viewport);
                export const invalidEventResults = [null, {}, {value: null}, {value: 0.5}, {value: ''}, {value:' '},
                    {value:'NaN'}, {value:'Infinity'}, {value:'-0.1'}, {value:'1.1'}]
                    .map(event => root.dispatch('volume', 'change', event));
                export const callsBefore = calls;
                export const valid = root.dispatch('volume', 'change', {value:'0.3'});
                export const callsAfter = calls;
                export const phases = host.diagnostics.map(diagnostic => diagnostic.phase);
                root.close();
                """);
        Value rejected = proof.getMember("invalidEventResults");
        assertEquals(10, rejected.getArraySize());
        for (long index = 0; index < rejected.getArraySize(); index++) assertFalse(rejected.getArrayElement(index).asBoolean());
        assertEquals(0, proof.getMember("callsBefore").asInt());
        assertTrue(proof.getMember("valid").asBoolean());
        assertEquals(1, proof.getMember("callsAfter").asInt());
        assertEquals(10, proof.getMember("phases").getArraySize());
        for (long index = 0; index < 10; index++) assertEquals("event", proof.getMember("phases").getArrayElement(index).asString());
    }

    @Test
    void malformedControlledPropsRejectAndLongSelectUsesBoundedInlineViewport() throws Exception {
        Value proof = run("""
                const toggle = {id:'t', checked:false, onChange:()=>{}};
                const slider = {id:'s', value:0, onChange:()=>{}};
                const select = {id:'c', value:'a', options:[{value:'a',label:'A'}], expanded:false,
                    onChange:()=>{}, onExpandedChange:()=>{}};
                const invalid = [
                    [Toggle, {...toggle, id:''}], [Toggle, {id:'t',checked:false}],
                    [Toggle, {...toggle, checked:0}], [Toggle, {...toggle, label:1}],
                    [Toggle, {...toggle, mystery:1}], [Slider, {id:'s',onChange:()=>{}}],
                    [Slider, {...slider, onChange:1}], [Select, {...select, options:{}}],
                    [Select, {...select, options:[null]}], [Select, {...select, options:[{value:'a',label:'A',other:1}]}],
                    [Select, {...select, options:[{value:'a',label:'A'},{value:'a',label:'B'}]}],
                    [Select, {...select, options:[{value:'a',label:1}]}],
                    [Select, {...select, options:[{value:'a',label:'A',disabled:'yes'}]}],
                    [Select, {...select, expanded:1}], [Select, {...select, value:'missing'}],
                    [Select, {...select, onExpandedChange:null}]
                ];
                export const failures = invalid.map(([component, props]) => rejected(() => UI.element(component, props)));
                const host = fakeHost();
                const options = Array.from({length:20}, (_, index) => ({value:String(index),label:'Option'}));
                const root = UI.createRoot(() => <Select id="long" value="0" options={options} expanded={true}
                    onChange={()=>{}} onExpandedChange={()=>{}}/>, host, viewport);
                export const height = find(root.layout().nodes, 'long:options').rect.height;
                root.close();
                """);
        assertRejectedBeforeHost(proof.getMember("failures"), 16);
        assertEquals(120, proof.getMember("height").asInt());
    }

    @Test
    void nestedSelectKeepsItsTriggerAndBoundedOptionsInsideTheAncestorClip() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const expanded = UI.createSignal(false);
                const options = UI.createSignal([{value:'0',label:'Zero'}]);
                const root = UI.createRoot(() => <column>
                    <Select id="choice" value="0" options={options.get()} expanded={expanded.get()}
                        onExpandedChange={next => expanded.set(next)} onChange={()=>{}}/>
                    <Select id="explicit" value="0" options={[{value:'0',label:'Zero'}]} expanded={true}
                        width={123} height={30} onExpandedChange={()=>{}} onChange={()=>{}}/>
                </column>, host, viewport);
                export const collapsed = find(root.layout().nodes, 'choice');
                expanded.set(true);
                export const oneOption = find(root.layout().nodes, 'choice:option:0');
                options.set(Array.from({length:20}, (_,index)=>({value:String(index),label:String(index)})));
                export const list = find(root.layout().nodes, 'choice:options');
                export const sixth = find(root.layout().nodes, 'choice:option:5');
                export const seventh = find(root.layout().nodes, 'choice:option:6');
                export const explicit = find(root.layout().nodes, 'explicit:root');
                root.close();
                """);
        assertEquals(20, proof.getMember("collapsed").getMember("rect").getMember("height").asInt());
        assertEquals(20, proof.getMember("collapsed").getMember("clip").getMember("height").asInt());
        assertEquals(20, proof.getMember("oneOption").getMember("clip").getMember("height").asInt());
        assertEquals(120, proof.getMember("list").getMember("rect").getMember("height").asInt());
        assertEquals(120, proof.getMember("list").getMember("clip").getMember("height").asInt());
        assertEquals(20, proof.getMember("sixth").getMember("clip").getMember("height").asInt());
        assertEquals(0, proof.getMember("seventh").getMember("clip").getMember("height").asInt());
        assertEquals(123, proof.getMember("explicit").getMember("rect").getMember("width").asInt());
        assertEquals(30, proof.getMember("explicit").getMember("rect").getMember("height").asInt());
    }

    @Test
    void selectNaturalHeightResolvesResponsiveSpacingDirectionAndDesignScale() throws Exception {
        Value proof = run("""
                const option = [{value:'0',label:'Zero'}];
                function inspect(props, configuration = viewport) {
                    const host = fakeHost();
                    const root = UI.createRoot(() => <column><Select id="s" value="0" options={option}
                        expanded={true} onExpandedChange={()=>{}} onChange={()=>{}} {...props}/></column>, host, configuration);
                    const before = root.layout();
                    root.resize({width:640,height:360});
                    const after = root.layout();
                    root.close();
                    return {before:find(before.nodes,'s:root').rect.height, after:find(after.nodes,'s:root').rect.height,
                        triggerClip:find(before.nodes,'s').clip.height, listClip:find(before.nodes,'s:options').clip.height};
                }
                const spacing = {height:'auto',gap:999,spacing:{base:2,profiles:{4:7}},padding:{base:3,profiles:{4:5}}};
                export const responsive = inspect(spacing);
                export const row = inspect({direction:'row',width:200,gap:2,padding:3});
                export const minimum = inspect({minHeight:70});
                export const maximum = inspect({maxHeight:30});
                export const percentage = inspect({height:'50%'});
                export const fill = inspect({height:'fill'});
                export const override = inspect({...spacing,height:{base:'auto',profiles:{4:100}}});
                export const design = inspect({...spacing,coordinateSpace:'design'},
                    {id:'design',viewport:{width:320,height:240,designWidth:160,designHeight:120}});
                """);
        assertEquals(48, proof.getMember("responsive").getMember("before").asInt());
        assertEquals(57, proof.getMember("responsive").getMember("after").asInt());
        assertEquals(20, proof.getMember("responsive").getMember("triggerClip").asInt());
        assertEquals(20, proof.getMember("responsive").getMember("listClip").asInt());
        assertEquals(26, proof.getMember("row").getMember("before").asInt());
        assertEquals(20, proof.getMember("row").getMember("listClip").asInt());
        assertEquals(70, proof.getMember("minimum").getMember("before").asInt());
        assertEquals(30, proof.getMember("maximum").getMember("before").asInt());
        assertEquals(120, proof.getMember("percentage").getMember("before").asInt());
        assertEquals(240, proof.getMember("fill").getMember("before").asInt());
        assertEquals(48, proof.getMember("override").getMember("before").asInt());
        assertEquals(100, proof.getMember("override").getMember("after").asInt());
        assertEquals(96, proof.getMember("design").getMember("before").asInt());
        assertEquals(40, proof.getMember("design").getMember("triggerClip").asInt());
        assertEquals(40, proof.getMember("design").getMember("listClip").asInt());
    }

    @Test
    void extractedControlExportsAreConcreteCallableTypesAndPrimitiveEventsRemainStrings() throws Exception {
        try (var stream = getClass().getResourceAsStream("/nekojs/node/modules/jsx-runtime.ts")) {
            assertNotNull(stream);
            String declarations = NodeModuleTypeDocs.extractTS(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            for (String component : new String[]{"Toggle", "Slider", "Select"}) {
                assertTrue(declarations.contains("export const " + component + ": NekoUi" + component), declarations);
                assertTrue(declarations.contains("(props: NekoUi" + component + "Props) => NekoVNode"), declarations);
                assertFalse(declarations.contains("export const " + component + ": Function"), declarations);
            }
            assertTrue(declarations.contains("readonly value?: string"), declarations);
            assertTrue(declarations.contains("slider: NekoUiPrimitivePropsByType['slider']"), declarations);
        }
    }

    private Value run(String source) throws Exception {
        Path entry = paths.testScripts().resolve("controls.tsx");
        Files.writeString(entry, "import { UI, Toggle, Slider, Select } from 'nekojs/jsx-runtime';\n" + HOST + source);
        Object exports = loader.loadEntry(entry.toString());
        assertTrue(exports instanceof Value);
        return (Value) exports;
    }

    private static void assertRejectedBeforeHost(Value failures, int expected) {
        assertEquals(expected, failures.getArraySize());
        for (long index = 0; index < failures.getArraySize(); index++) {
            Value failure = failures.getArrayElement(index);
            assertTrue(failure.getMember("rejected").asBoolean(), "case " + index);
            assertEquals(0, failure.getMember("creates").asInt(), "case " + index);
            assertEquals(0, failure.getMember("commits").asInt(), "case " + index);
            assertTrue(failure.getMember("diagnostics").asInt() > 0, "case " + index);
        }
    }

    private static final String HOST = """
            const viewport = {id:'controls-test', viewport:{width:320,height:240}};
            function find(nodes, id) {
                for (const node of nodes) {
                    if (node.id === id) return node;
                    const result = find(node.children, id);
                    if (result) return result;
                }
                return null;
            }
            function fakeHost() {
                let sequence = 0;
                let nodes = new Map();
                const host = {
                    owner:true, queue:[], creates:0, commits:0, diagnostics:[],
                    isOwnerThread: () => host.owner,
                    enqueue: action => {host.queue.push(action);return true},
                    supportsPrimitive: type => UI.primitives().includes(type),
                    measureText: (text,size,width) => ({width:Math.min(width,String(text).length*6),height:size}),
                    layout: () => {},
                    reportDiagnostic: diagnostic => host.diagnostics.push(diagnostic),
                    props: id => Array.from(nodes.values()).find(node => node.id === id),
                    begin: () => {
                        const staged = new Map(nodes);
                        return {
                            create: (type,key,props) => {const handle = ++sequence;host.creates++;staged.set(handle,props);return handle},
                            update: (handle,type,key,props) => staged.set(handle,props),
                            remove: handle => staged.delete(handle), order: () => {},
                            commit: () => {nodes=staged;host.commits++}, rollback: () => {}
                        };
                    }
                };
                return host;
            }
            function rejected(render) {
                const host = fakeHost();
                let failure = false;
                try {const root = UI.createRoot(render,host,viewport);root.close()} catch (error) {failure=true}
                return {rejected:failure,creates:host.creates,commits:host.commits,diagnostics:host.diagnostics.length};
            }
            """;
}
