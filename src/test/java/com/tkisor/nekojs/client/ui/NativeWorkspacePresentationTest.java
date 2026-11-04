//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises canonical minimized presentations through native pointer, keyboard and scroll routing. */
class NativeWorkspacePresentationTest {
    @Test
    void taskbarReservesWorkAreaAndRestoresTheRetainedInputThroughNativeClick() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            installWorkspace(fixture, 240, 200);
            fixture.context.eval("js", """
                    globalThis.text = UI.createSignal('Draft');
                    globalThis.workspace = __nekoNodeBuiltins['nekojs/jsx-workspace'].createWorkspace(host,
                      {id:'native-taskbar', viewport:{width:240,height:200}, minimizedPresentation:'taskbar'});
                    if (!workspace.add('notes', () => UI.element('column', {width:'fill',padding:6,
                      children:UI.element('input',{id:'notes-text',width:'fill',height:20,
                        value:text.get(),onChange:event=>text.set(event.value)})}),
                      {title:'Notes',rect:{x:12,y:12,width:90,height:100},minimizable:true,maximizable:true})) {
                      throw new Error('Canonical taskbar window must publish');
                    }
                    """);
            JsxHostTree.Node desktop = type(fixture.adapter.roots(), "workspace");
            JsxHostTree.Node bar = presentation(fixture);
            assertBounds(desktop, 0, 0, 240, 172);
            assertBounds(bar, 0, 172, 240, 28);
            long inputIdentity = windowInput(fixture, "notes").identity;
            assertTrue(fixture.context.eval("js", "workspace.maximize('notes')").asBoolean());
            assertBounds(id(fixture, "notes"), 0, 0, 240, 172);
            assertTrue(fixture.context.eval("js", "workspace.restore('notes') && workspace.minimize('notes')").asBoolean());
            fixture.context.eval("js", "text.set('Saved while minimized')");
            assertEquals(inputIdentity, windowInput(fixture, "notes").identity);
            assertEquals("Saved while minimized", windowInput(fixture, "notes").inputValue);
            assertTrue(fixture.adapter.dispatchAt(12, 184, "click", 0, 0),
                    "the taskbar must receive pointer input with no active window");
            fixture.adapter.dispatchRelease(12, 184, 0);
            assertEquals("normal", fixture.context.eval("js", "workspace.snapshot().windows[0].mode").asString());
            assertEquals(inputIdentity, windowInput(fixture, "notes").identity);
            assertEquals("Saved while minimized", windowInput(fixture, "notes").inputValue);
            assertBounds(id(fixture, "notes"), 12, 12, 90, 100);
            assertTrue(fixture.adapter.dispatchAt(24, 48, "click", 0, 0));
            fixture.adapter.dispatchRelease(24, 48, 0);
            assertTrue(windowInput(fixture, "notes").focused);
            assertEquals(1, JsxHostAdapter.focusedIds(fixture.adapter.roots()).size());
        }
    }

    @Test
    void floatingListUsesNativeScrollOpaqueBoundsKeyboardAndGlobalModalPriority() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            installWorkspace(fixture, 320, 240);
            fixture.context.eval("js", """
                    globalThis.backgroundClicks=0; globalThis.modalClicks=0; globalThis.dismissals=0;
                    globalThis.modalOpen=UI.createSignal(false);
                    globalThis.workspace=__nekoNodeBuiltins['nekojs/jsx-workspace'].createWorkspace(host,
                      {id:'native-floating',viewport:{width:320,height:240},minimizedPresentation:'floating'});
                    if (!workspace.add('background',()=>UI.element('column',{width:'fill',height:'fill',children:[
                      UI.element('button',{id:'background-action',width:'fill',height:'fill',text:'Background',
                        onClick:()=>backgroundClicks++}),
                      modalOpen.get() ? UI.element('dialog',{id:'global-modal',onDismiss:()=>dismissals++,children:
                        UI.element('panel',{width:80,height:60,children:
                          UI.element('button',{id:'modal-action',width:80,height:20,text:'Modal',onClick:()=>modalClicks++})})}) : null
                    ]}),{title:'Background',rect:{x:0,y:50,width:300,height:180}})) {
                      throw new Error('Canonical background window must publish');
                    }
                    for(let index=0;index<10;index++) {
                      if (!workspace.add('w'+index,()=>UI.element('label',{text:'Retained'}),
                        {title:'Window '+index,rect:{x:220,y:0,width:90,height:60},minimizable:true})
                        || !workspace.minimize('w'+index)) throw new Error('Canonical floating entries must publish');
                    }
                    """);
            assertBounds(presentation(fixture), 0, 80, 220, 160);
            JsxHostTree.Node scroll = type(presentation(fixture).children, "scroll");
            assertBounds(scroll, 4, 84, 212, 152);
            assertEquals(84d, scroll.scrollRange, "ten 20px entries and nine 4px gaps exceed 152px by 84px");
            long scrollIdentity = scroll.identity;
            assertTrue(fixture.adapter.dispatchScroll(20, 100, -1000));
            scroll = type(presentation(fixture).children, "scroll");
            assertEquals(84d, ((Number) scroll.props.get("scrollOffset")).doubleValue());
            assertEquals(scrollIdentity, scroll.identity);
            assertTrue(fixture.adapter.dispatchScroll(20, 100, 1000));
            assertEquals(0d, ((Number) type(presentation(fixture).children, "scroll").props.get("scrollOffset")).doubleValue());

            // Empty presentation padding occludes the window; a point outside its bounds does not.
            assertTrue(fixture.adapter.dispatchAt(2, 82, "click", 0, 0));
            fixture.adapter.dispatchRelease(2, 82, 0);
            assertEquals(0, fixture.context.eval("js", "backgroundClicks").asInt());
            assertTrue(fixture.adapter.dispatchAt(250, 100, "click", 0, 0));
            fixture.adapter.dispatchRelease(250, 100, 0);
            assertEquals(1, fixture.context.eval("js", "backgroundClicks").asInt());
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0));
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ENTER, 0));
            assertEquals("normal", fixture.context.eval("js", "workspace.snapshot().windows.find(w=>w.id==='w0').mode").asString());
            assertEquals(1, fixture.context.eval("js", "backgroundClicks").asInt(),
                    "Tab and Enter must reach a presentation button instead of cycling the active window alone");

            // The modal remains global even though its background window is no longer active.
            fixture.context.eval("js", "modalOpen.set(true)");
            assertEquals("w0", fixture.context.eval("js", "workspace.snapshot().activeId").asString());
            assertTrue(fixture.adapter.dispatchAt(10, 100, "click", 0, 0));
            fixture.adapter.dispatchRelease(10, 100, 0);
            assertEquals(1, fixture.context.eval("js", "dismissals").asInt());
            assertTrue(fixture.adapter.dispatchScroll(20, 100, -1000));
            assertEquals(0d, ((Number) type(presentation(fixture).children, "scroll").props.get("scrollOffset")).doubleValue());
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0));
            JsxHostTree.Node modal = type(id(fixture, "background").children, "dialog");
            assertNotNull(modal);
            JsxHostTree.Node modalAction = type(modal.children, "button");
            assertNotNull(modalAction);
            assertTrue(modalAction.focused);
            assertEquals(1, JsxHostAdapter.focusedIds(fixture.adapter.roots()).size());
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ENTER, 0));
            assertEquals(1, fixture.context.eval("js", "modalClicks").asInt());
            assertEquals(9, fixture.context.eval("js", "workspace.snapshot().windows.filter(w=>w.mode==='minimized').length").asInt());
        }
    }

    @Test
    void activationThatPublishesAPresentationConsumesTheOriginalWindowClick() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.activated=UI.createSignal(false);
                    globalThis.activations=0;globalThis.windowClicks=0;globalThis.panelClicks=0;
                    globalThis.root=UI.createRoot(()=>UI.element('screen',{
                      width:'fill',height:'fill',layout:'stack',children:[
                        UI.element('workspace',{width:'fill',height:'fill',children:UI.element('window',{
                          id:'target',title:'Target',x:12,y:12,width:180,height:160,active:activated.get(),closable:false,
                          onActivate:()=>{activations++;activated.set(true)},
                          children:UI.element('button',{id:'window-action',width:'fill',height:'fill',
                            text:'Window action',onClick:()=>windowClicks++})
                        })}),
                        activated.get()?UI.element('panel',{id:'new-presentation',width:'fill',height:'fill',
                          anchor:'topLeft',workspacePresentation:true,
                          children:UI.element('button',{id:'panel-action',width:'fill',height:'fill',
                            text:'Panel action',onClick:()=>panelClicks++})}):null
                      ]}),host,{id:'native-reentrant-presentation',viewport:{width:240,height:200}});
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.adapter.dispatchAt(40, 70, "click", 0, 0));
            assertEquals(1, fixture.context.eval("js", "activations").asInt());
            assertBounds(presentation(fixture), 0, 0, 240, 200);
            assertEquals(0, fixture.context.eval("js", "windowClicks").asInt(),
                    "the newly published opaque panel must block the original window action");
            assertEquals(0, fixture.context.eval("js", "panelClicks").asInt(),
                    "the activation click must not be relayed to the new presentation either");
            fixture.adapter.dispatchRelease(40, 70, 0);
            assertTrue(fixture.adapter.dispatchAt(40, 70, "click", 0, 0));
            fixture.adapter.dispatchRelease(40, 70, 0);
            assertEquals(1, fixture.context.eval("js", "panelClicks").asInt());
            assertEquals(0, fixture.context.eval("js", "windowClicks").asInt());
        }
    }

    private void installWorkspace(NativeUiScreenFixture fixture, int width, int height) throws Exception {
        fixture.installRuntime();
        fixture.adapter.resize(width, height);
        fixture.context.eval("js", """
                globalThis.__nekoNodeBuiltins={'nekojs/jsx-runtime':{UI}};
                globalThis.__nekoNodeDefine=(ids,exports)=>{
                  for(const id of ids) globalThis.__nekoNodeBuiltins[id]=exports;
                };
                """);
        try (var source = getClass().getResourceAsStream("/nekojs/node/modules/jsx-workspace.ts")) {
            assertNotNull(source, "Canonical workspace runtime must be present");
            fixture.context.eval("js", com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler.eraseTypescript(
                    Path.of("jsx-workspace.ts"), new String(source.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static JsxHostTree.Node id(NativeUiScreenFixture fixture, String id) {
        JsxHostTree.Node result = find(fixture.adapter.roots(), "id", id);
        assertNotNull(result, "Expected published node " + id);
        return result;
    }

    private static JsxHostTree.Node windowInput(NativeUiScreenFixture fixture, String windowId) {
        JsxHostTree.Node window = id(fixture, windowId);
        assertEquals("window", window.type);
        JsxHostTree.Node input = type(window.children, "input");
        assertNotNull(input, "Expected retained input in window " + windowId);
        return input;
    }

    private static JsxHostTree.Node presentation(NativeUiScreenFixture fixture) {
        JsxHostTree.Node result = find(fixture.adapter.roots(), "workspacePresentation", true);
        assertNotNull(result, "Canonical presentation must publish a marked panel");
        return result;
    }

    private static JsxHostTree.Node find(List<JsxHostTree.Node> nodes, String property, Object value) {
        for (JsxHostTree.Node node : nodes) {
            if (value.equals(node.props.get(property))) return node;
            JsxHostTree.Node nested = find(node.children, property, value);
            if (nested != null) return nested;
        }
        return null;
    }

    private static JsxHostTree.Node type(List<JsxHostTree.Node> nodes, String type) {
        for (JsxHostTree.Node node : nodes) {
            if (type.equals(node.type)) return node;
            JsxHostTree.Node nested = type(node.children, type);
            if (nested != null) return nested;
        }
        return null;
    }

    private static void assertBounds(JsxHostTree.Node node, int x, int y, int width, int height) {
        assertNotNull(node);
        assertAll(() -> assertEquals(x, node.x), () -> assertEquals(y, node.y),
                () -> assertEquals(width, node.width), () -> assertEquals(height, node.height));
    }
}
//?}
