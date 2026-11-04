//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

class NativeWorkspaceMountTest {
    @Test
    void topmostOpaqueWindowActivatesBeforeContentAndBlocksUnderlyingHits() throws Exception {
        try (NativeUiScreenFixture fixture = fixture()) {
            assertTrue(fixture.adapter.dispatchAt(30, 40, "click", 0, 0));
            assertEquals("activate:front", fixture.context.eval("js", "events[0]").asString());
            assertEquals("front", fixture.context.eval("js", "events[1]").asString());
            assertTrue(fixture.adapter.dispatchAt(85, 70, "click", 0, 0), "opaque empty window body consumes the hit");
            assertEquals(2, fixture.context.eval("js", "events.length").asInt());
            fixture.context.eval("js", "active='back'; root.refresh()");
            assertTrue(fixture.adapter.dispatchAt(30, 40, "click", 0, 0));
            assertEquals("front", fixture.context.eval("js", "active").asString());
            assertEquals("front", fixture.context.eval("js", "events[events.length-1]").asString());
        }
    }

    @Test
    void titlebarCaptureReportsControlledWorkspaceLocalMovesAndCloseReason() throws Exception {
        try (NativeUiScreenFixture fixture = fixture()) {
            assertTrue(fixture.adapter.dispatchAt(30, 15, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchDrag(150, 160, 0));
            assertEquals(1, fixture.context.eval("js", "moves.length").asInt());
            assertEquals(140, fixture.context.eval("js", "moves[0].x").asInt());
            assertEquals(155, fixture.context.eval("js", "moves[0].y").asInt());
            assertEquals(20, front(fixture.adapter.roots()).x, "movement remains controlled until the next committed render");
            assertTrue(fixture.adapter.dispatchRelease(150, 160, 0));
            assertFalse(fixture.adapter.dispatchDrag(160, 170, 0));
            assertTrue(fixture.adapter.dispatchAt(80, 20, "click", 0, 0));
            assertEquals("close", fixture.context.eval("js", "dismissed").asString());
        }
    }

    @Test
    void inactiveWindowsCannotKeepKeyboardFocusAndGlobalModalStillWins() throws Exception {
        try (NativeUiScreenFixture fixture = fixture()) {
            fixture.context.eval("js", "inputMode=true; active='front'; root.refresh()");
            assertTrue(fixture.adapter.dispatchAt(30, 40, "click", 0, 0));
            assertTrue(fixture.adapter.textInput('x'));
            fixture.context.eval("js", "active='back'; root.refresh()");
            assertFalse(fixture.adapter.textInput('z'));
            assertEquals("x", fixture.context.eval("js", "typed").asString());
            fixture.context.eval("js", "showModal=true; root.refresh()");
            assertTrue(fixture.adapter.dispatchAt(30, 40, "click", 0, 0));
            assertEquals("back", fixture.context.eval("js", "active").asString(), "modal in inactive window must not activate that window");
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ESCAPE, 0));
            assertEquals("escape", fixture.context.eval("js", "modalDismissed").asString());
        }
    }

    private static NativeUiScreenFixture fixture() throws Exception {
        NativeUiScreenFixture fixture = new NativeUiScreenFixture();
        try {
            assertTrue(fixture.adapter.supportsPrimitive("workspace"), "native workspace primitive must be available");
            assertTrue(fixture.adapter.supportsPrimitive("window"));
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.events=[]; globalThis.moves=[]; globalThis.dismissed='';
                    globalThis.active='back'; globalThis.inputMode=false; globalThis.showModal=false;
                    globalThis.typed=''; globalThis.modalDismissed='';
                    globalThis.root=UI.createRoot(() => UI.element('workspace', {width:100,height:100,children:[
                      UI.element('window', {id:'back',title:'Back',x:0,y:0,width:80,height:70,active:active==='back',
                        onActivate:()=>{events.push('activate:back');active='back';root.refresh()},
                        children:UI.element('button',{id:'back-button',text:'Back',width:60,height:30,onClick:()=>events.push('back')})}),
                      UI.element('window', {id:'front',title:'Front',x:20,y:10,width:70,height:70,active:active==='front',
                        onActivate:()=>{events.push('activate:front');active='front';root.refresh()},
                        onMove:e=>moves.push(e), onDismiss:e=>dismissed=e.reason,
                        children:showModal ? UI.element('dialog',{id:'modal',onDismiss:e=>modalDismissed=e.reason,
                          children:UI.element('button',{id:'modal-button',text:'Modal',width:20,height:20})})
                          :inputMode ? UI.element('input',{id:'front-input',width:60,height:20,value:typed,onChange:e=>typed=e.value})
                          :UI.element('button',{id:'front-button',text:'Front',width:60,height:20,onClick:()=>events.push('front')})})
                    ]}),host);
                    host.bindRoot(root);
                    """);
            return fixture;
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    private static JsxHostTree.Node front(java.util.List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node root : roots) {
            if ("front".equals(root.props.get("id"))) return root;
            JsxHostTree.Node nested=front(root.children);
            if (nested!=null) return nested;
        }
        return null;
    }
}
//?}
