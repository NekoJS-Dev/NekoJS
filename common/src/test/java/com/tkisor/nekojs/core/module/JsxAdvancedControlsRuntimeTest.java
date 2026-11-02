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
class JsxAdvancedControlsRuntimeTest {
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
    void formValidationCollectsDeterministicImmutableErrorsWithoutMutatingInputs() throws Exception {
        Value proof = run("""
                const values = {name:'  ',quantity:'65',flag:false,zero:0};
                let observed = null;
                const rules = {
                    name:[Validators.required('Name required'),Validators.minLength(3,'Need 3')],
                    quantity:[Validators.numberRange(1,64,'Quantity 1..64')],
                    flag:[Validators.required()],zero:[Validators.required()],
                    extra:[(value,snapshot)=>{observed=snapshot;return null}]
                };
                export const invalid = validateForm(values,rules);
                values.name = 'changed';
                export const snapshot = observed;
                export const frozen = Object.isFrozen(invalid) && Object.isFrozen(invalid.errors)
                    && Object.isFrozen(invalid.errors.name) && Object.isFrozen(snapshot);
                export const valid = validateForm({name:'Neko',quantity:' 32 ',flag:false,zero:0},rules);
                export const namedExports = typeof Validators.required === "function";
                """);
        Value invalid = proof.getMember("invalid");
        assertFalse(invalid.getMember("valid").asBoolean());
        assertEquals(2, invalid.getMember("errors").getMember("name").getArraySize());
        assertEquals("Name required", invalid.getMember("errors").getMember("name").getArrayElement(0).asString());
        assertEquals("Need 3", invalid.getMember("errors").getMember("name").getArrayElement(1).asString());
        assertEquals("Quantity 1..64", invalid.getMember("errors").getMember("quantity").getArrayElement(0).asString());
        assertFalse(invalid.getMember("errors").hasMember("flag"));
        assertEquals("  ", proof.getMember("snapshot").getMember("name").asString());
        assertTrue(proof.getMember("frozen").asBoolean());
        assertTrue(proof.getMember("namedExports").asBoolean());
        assertTrue(proof.getMember("valid").getMember("valid").asBoolean());
        assertEquals(0, proof.getMember("valid").getMember("errors").getMemberKeys().size());
    }

    @Test
    void validationRejectsNonScalarAsyncMalformedRulesAndInvalidFactories() throws Exception {
        Value proof = run("""
                function throws(action) {try {action();return false} catch(error) {return true}}
                const badValues = [[],null,{x:undefined},{x:NaN},{x:Infinity},{x:{}},{x:[]},{x:()=>0}];
                const badRules = [[],null,{x:null},{x:()=>null},{x:[null]},{x:[()=>false]},{x:[()=>Promise.resolve(null)]}];
                export const rejectedValues = badValues.every(value=>throws(()=>validateForm(value,{})));
                export const rejectedRules = badRules.every(rules=>throws(()=>validateForm({},rules)));
                export const rejectedFactories = [()=>Validators.minLength(-1),()=>Validators.maxLength(1.5),
                    ()=>Validators.minLength(Infinity),()=>Validators.required(null),()=>Validators.numberRange(2,1),
                    ()=>Validators.numberRange(NaN,1),()=>Validators.numberRange(0,Infinity),()=>Validators.maxLength(1,3)]
                    .every(throws);
                export const required = [null,undefined,'','  '].every(value=>Validators.required()(value,{})==='Required')
                    && [false,0,'x'].every(value=>Validators.required()(value,{})===null);
                export const numbers = [0,' 1 ','0x10','1e1'].every(value=>Validators.numberRange(0,16)(value,{})===null)
                    && ['', ' ',true,null,'NaN','Infinity',17].every(value=>typeof Validators.numberRange(0,16)(value,{})==='string');
                export const endpoints = Validators.minLength(2)('ab',{})===null && Validators.maxLength(2)('ab',{})===null
                    && typeof Validators.minLength(2)('a',{})==='string' && typeof Validators.maxLength(2)('abc',{})==='string';
                let calls = 0;
                const mutationRules = {x:[()=>{mutationRules.x.length=0;return ''},()=>{calls++;return 'second'}]};
                const mutationResult = validateForm({x:'ok'},mutationRules);
                export const stableRules = calls===1 && mutationResult.errors.x.length===2;
                let exact = false;
                try {validateForm({}, {x:[()=>{throw new Error('custom failure')}]})} catch(error) {exact=error.message==='custom failure'}
                export const customFailure = exact;
                const reserved = Object.fromEntries([['__proto__',''],['constructor','']]);
                const reservedRules = Object.fromEntries([['__proto__',[Validators.required()]],['constructor',[Validators.required()]]]);
                export const safeKeys = Object.keys(validateForm(reserved,reservedRules).errors).join(',')==='__proto__,constructor';
                """);
        for (String key : new String[]{"rejectedValues", "rejectedRules", "rejectedFactories", "required", "numbers", "endpoints", "stableRules", "customFailure", "safeKeys"}) {
            assertTrue(proof.getMember(key).asBoolean(), key);
        }
    }

    @Test
    void tabsMountOnlySelectedRenderAndPreserveCallerStateWithKeyboardNavigation() throws Exception {
        Value proof = run("""
                const active = UI.createSignal('first');
                const text = UI.createSignal('caller state');
                const disabled = UI.createSignal(false);
                const order = UI.createSignal(false);
                const calls = {first:0,second:0,disabled:0};
                const host = fakeHost();
                const first = {value:'first',label:'Same',render:()=>{calls.first++;return <input id="name" value={text.get()} onChange={e=>text.set(e.value)} />}};
                const second = {value:'storage / x',label:'Same',render:()=>{calls.second++;return <label id="other">Other</label>}};
                const unavailable = {value:'disabled',label:'Disabled',disabled:true,render:()=>{calls.disabled++;return null}};
                const root = UI.createRoot(()=> <Tabs id="tabs" value={active.get()} disabled={disabled.get()}
                    tabs={order.get()?[second,unavailable,first]:[first,unavailable,second]} onChange={value=>active.set(value)} />,host,viewport);
                export const initial = calls.first===1 && calls.second===0 && calls.disabled===0 && host.props('name').value==='caller state';
                root.dispatch('tabs:tab:first','key',{key:'right'});
                export const next = active.get()==='storage / x' && host.props('name')===undefined && calls.second===1;
                root.dispatch('tabs:tab:storage%20%2F%20x','key',{key:'Home'});
                root.dispatch('name','change',{value:'retained'});
                order.set(true);
                export const reordered = active.get()==='first' && host.props('name').value==='retained';
                root.dispatch('tabs:tab:first','key',{key:'home'});
                export const homeAfterOrder = active.get()==='storage / x';
                root.dispatch('tabs:tab:storage%20%2F%20x','key',{key:'end'});
                export const end = active.get()==='first';
                export const disabledTab = root.dispatch('tabs:tab:disabled','click',{})===false && calls.disabled===0;
                host.owner=false;
                const queued = root.dispatch('tabs:tab:storage%20%2F%20x','click',{});
                host.owner=true;
                disabled.set(true);
                host.queue.shift()();
                export const disabledQueue = queued==='queued' && active.get()==='first' && root.dispatch('tabs:tab:first','key',{key:'right'})===false;
                root.close();
                export const closed = root.dispatch('tabs:tab:first','click',{})===false && host.props('name')===undefined;
                """);
        for (String key : new String[]{"initial", "next", "reordered", "homeAfterOrder", "end", "disabledTab", "disabledQueue", "closed"}) assertTrue(proof.getMember(key).asBoolean(), key);
    }

    @Test
    void dialogEscapesNestedClipAndFlowAndRetainsExactDfsModalOrder() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const root = UI.createRoot(()=> <column width={100} height={30} gap={5}>
                    <label id="before" height={10}>Before</label>
                    <Dialog id="modal" open={true} title="Dialog" onOpenChange={()=>{}}><button id="inside">Inside</button></Dialog>
                    <label id="after" height={10}>After</label>
                </column>,host,{id:'safe',viewport:{width:320,height:240,safeArea:{left:10,top:20,right:30,bottom:40}}});
                const modal = find(root.layout().nodes,'modal');
                const panel = find(root.layout().nodes,'modal:panel');
                const before = find(root.layout().nodes,'before');
                const after = find(root.layout().nodes,'after');
                export const portal = modal.rect.x===10 && modal.rect.y===20 && modal.rect.width===280 && modal.rect.height===180
                    && modal.clip.width===280 && modal.clip.height===180;
                export const centered = panel.rect.x===38 && panel.rect.y===38 && panel.rect.width===224 && panel.rect.height===144;
                export const outOfFlow = after.rect.y-before.rect.y===15;
                export const order = root.layout().nodes[0].children.map(node=>node.id).join(',')==='before,modal,after';
                root.resize({width:640,height:480,safeArea:{left:20,top:10,right:20,bottom:10}});
                export const resized = find(root.layout().nodes,'modal').clip.width===600 && find(root.layout().nodes,'modal:panel').rect.width===480;
                root.close();
                """);
        for (String key : new String[]{"portal", "centered", "outOfFlow", "order", "resized"}) assertTrue(proof.getMember(key).asBoolean(), key);
    }

    @Test
    void committedTopModalFencesDirectQueuedDismissAndFailedCandidateDispatch() throws Exception {
        Value proof = run("""
                const open = UI.createSignal(false), top = UI.createSignal(false), hidden = UI.createSignal(false), invalid = UI.createSignal(false);
                const requests = [];
                let background = 0, lower = 0, upper = 0;
                const host = fakeHost();
                const root = UI.createRoot(()=> <column>
                    <button id="background" onClick={()=>background++}>Background</button>
                    <column visible={!hidden.get()}>
                      <Dialog id="lower" open={open.get()} onOpenChange={value=>requests.push(value)} closeOnBackdrop={false}>
                        <button id="lower-button" onClick={()=>lower++}>Lower</button>
                      </Dialog>
                    </column>
                    <Dialog id="upper" open={top.get()} onOpenChange={value=>top.set(value)} closeOnEscape={false}>
                      <button id="upper-button" onClick={()=>upper++}>Upper</button>
                    </Dialog>
                    {invalid.get()?<item item="Invalid" />:null}
                </column>,host,viewport);
                host.owner=false;
                const queued = root.dispatch('background','click',{});
                host.owner=true;
                open.set(true);
                host.queue.shift()();
                export const queuedBlocked = queued==='queued' && background===0 && root.dispatch('background','click',{})===false;
                root.dispatch('lower','dismiss',{reason:'escape'});
                export const controlled = requests.length===1 && requests[0]===false && find(root.layout().nodes,'lower')!==null
                    && root.dispatch('lower','dismiss',{reason:'backdrop'})===false;
                top.set(true);
                export const stacked = root.dispatch('lower-button','click',{})===false && root.dispatch('upper-button','click',{})===true && upper===1;
                export const noEscape = root.dispatch('upper','dismiss',{reason:'escape'})===false && top.get()===true;
                invalid.set(true);
                export const retained = host.diagnostics.length===1 && root.dispatch('background','click',{})===false && root.dispatch('upper-button','click',{})===true && upper===2;
                invalid.set(false);
                root.dispatch('upper','dismiss',{reason:'backdrop'});
                export const lowerResumes = top.get()===false && root.dispatch('lower-button','click',{})===true && lower===1;
                hidden.set(true);
                export const hiddenResumes = root.dispatch('background','click',{})===true && background===1;
                root.close();
                """);
        for (String key : new String[]{"queuedBlocked", "controlled", "stacked", "noEscape", "retained", "lowerResumes", "hiddenResumes"}) assertTrue(proof.getMember(key).asBoolean(), key);
    }

    @Test
    void itemIsDetachedReadonlyDataWithStrictFieldsAndSixteenPixelNaturalSize() throws Exception {
        Value proof = run("""
                const host = fakeHost();
                const root = UI.createRoot(()=> <row><item id="item" item="minecraft:diamond" /><item id="custom" item="minecraft:stone" count={99} itemTooltip={false} tooltip="Custom" width={32} height={24} /></row>,host,viewport);
                export const defaults = host.props('item').count===1 && host.props('item').itemTooltip===true && Object.isFrozen(host.props('item'));
                export const natural = find(root.layout().nodes,'item').rect.width===16 && find(root.layout().nodes,'item').rect.height===16;
                export const custom = host.props('custom').count===99 && host.props('custom').itemTooltip===false && host.props('custom').tooltip==='Custom';
                const bad = [{},{item:{}},{item:'diamond'},{item:'Minecraft:stone'},{item:'minecraft:air'},{item:'minecraft:stone',count:0},
                    {item:'minecraft:stone',count:100},{item:'minecraft:stone',count:1.5},{item:'minecraft:stone',count:NaN},
                    {item:'minecraft:stone',itemTooltip:1},{item:'minecraft:stone',tooltip:true},{item:'minecraft:stone',tooltip:null},{item:'minecraft:stone',stack:{}}];
                export const invalidItems = bad.map(props=>rejected(()=>UI.element('item',props)));
                root.close();
                """);
        for (String key : new String[]{"defaults", "natural", "custom"}) assertTrue(proof.getMember(key).asBoolean(), key);
        Value bad = proof.getMember("invalidItems");
        assertEquals(13, bad.getArraySize());
        for (long i = 0; i < bad.getArraySize(); i++) {
            assertTrue(bad.getArrayElement(i).getMember("rejected").asBoolean(), "case " + i);
            assertEquals(0, bad.getArrayElement(i).getMember("creates").asInt(), "case " + i);
        }
    }

    @Test
    void advancedWrappersRejectInvalidContractsBeforeCreatingHostNodes() throws Exception {
        Value proof = run("""
                const tab = {value:'a',label:'A',render:()=>null};
                const tabs = {id:'tabs',value:'a',tabs:[tab],onChange:()=>{}};
                const badTabs = [{id:''},{value:1},{value:'missing'},{tabs:[]},{tabs:[tab,tab]},
                    {tabs:[{value:'a',label:'A',content:null}]},{tabs:[{value:'a',label:'A',render:null}]},
                    {tabs:[{value:'a',label:'A',disabled:1,render:()=>null}]},{onChange:null},{appearance:{unknown:'red'}}];
                const dialog = {id:'dialog',open:true,onOpenChange:()=>{}};
                const badDialogs = [{id:''},{open:1},{onOpenChange:null},{onClose:()=>{}},{title:null},
                    {closeOnEscape:1},{closeOnBackdrop:null},{width:-1},{height:'bad'}];
                export const invalidWrappers = badTabs.map(props=>rejected(()=>UI.element(Tabs,Object.assign({},tabs,props))))
                    .concat(badDialogs.map(props=>rejected(()=>UI.element(Dialog,Object.assign({},dialog,props)))));
                const host = fakeHost();
                const root = UI.createRoot(()=> <Dialog id="closed" open={false} onOpenChange={()=>{}} />,host,viewport);
                export const closed = root.layout().nodes.length===0 && host.creates===0;
                root.close();
                const rawHost = fakeHost();
                const raw = UI.createRoot(()=> <dialog id="raw" onDismiss={()=>{}} />,rawHost,viewport);
                export const rawDefaults = rawHost.props('raw').closeOnEscape===true && rawHost.props('raw').closeOnBackdrop===true;
                raw.close();
                """);
        assertTrue(proof.getMember("closed").asBoolean());
        assertTrue(proof.getMember("rawDefaults").asBoolean());
        Value bad = proof.getMember("invalidWrappers");
        assertEquals(19, bad.getArraySize());
        for (long i = 0; i < bad.getArraySize(); i++) {
            assertTrue(bad.getArrayElement(i).getMember("rejected").asBoolean(), "case " + i);
            assertEquals(0, bad.getArrayElement(i).getMember("creates").asInt(), "case " + i);
        }
    }

    @Test
    void extractedAdvancedExportsHaveConcreteImmutableCallableContracts() throws Exception {
        try (var stream = getClass().getResourceAsStream("/nekojs/node/modules/jsx-runtime.ts")) {
            assertNotNull(stream);
            String declarations = NodeModuleTypeDocs.extractTS(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            for (String component : new String[]{"Tabs", "Dialog"}) {
                assertTrue(declarations.contains("export const " + component + ": NekoUi" + component), declarations);
                assertTrue(declarations.contains("(props: NekoUi" + component + "Props) => NekoVNode"), declarations);
                assertFalse(declarations.contains("export const " + component + ": Function"), declarations);
            }
            assertTrue(declarations.contains("export const validateForm: NekoUiValidateForm"), declarations);
            assertTrue(declarations.contains("export const Validators: NekoUiValidators"), declarations);
            assertTrue(declarations.contains("readonly errors: Readonly<Record<string, readonly string[]>>"), declarations);
            assertTrue(declarations.contains("render: () => NekoUiRenderable"), declarations);
            assertTrue(declarations.contains("onOpenChange: (open: boolean) => void"), declarations);
            assertTrue(declarations.contains("dialog: NekoUiPrimitivePropsByType['dialog']"), declarations);
            assertTrue(declarations.contains("item: NekoUiPrimitivePropsByType['item']"), declarations);
        }
    }

    private Value run(String source) throws Exception {
        Path entry = paths.testScripts().resolve("advanced.tsx");
        Files.writeString(entry, "import { UI, Tabs, Dialog, Validators, validateForm } from 'nekojs/jsx-runtime';" + '\n' + HOST + source);
        Object exports = loader.loadEntry(entry.toString());
        assertTrue(exports instanceof Value);
        return (Value) exports;
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
