//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Records the real native tooltip scheduling call without requiring a GPU. */
class NativeWorkspacePresentationHoverTest {
    @Test
    void opaquePresentationPaddingSuppressesOnlyTheCoveredWindowTooltip() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.overlay=UI.createSignal(false);
                    globalThis.root=UI.createRoot(()=>UI.element('screen',{
                      width:'fill',height:'fill',layout:'stack',children:[
                        UI.element('workspace',{width:'fill',height:'fill',children:UI.element('window',{
                          id:'background',title:'Background',x:0,y:0,width:240,height:200,active:true,
                          titlebarHeight:0,closable:false,
                          children:UI.element('button',{id:'background-action',width:'fill',height:'fill',
                            text:'Background action',tooltip:'Window tooltip'})
                        })}),
                        overlay.get()?UI.element('panel',{id:'presentation',width:120,height:100,
                          anchor:'bottomLeft',padding:4,workspacePresentation:true}):null
                      ]}),host,{id:'native-presentation-hover',viewport:{width:240,height:200}});
                    host.bindRoot(root);
                    """);
            assertEquals("Window tooltip", fixture.tooltipAt(2, 102));
            assertEquals("Window tooltip", fixture.tooltipAt(150, 102));
            fixture.context.eval("js", "overlay.set(true)");
            JsxHostTree.Node panel = fixture.adapter.roots().getFirst().children.getLast();
            assertEquals("panel", panel.type);
            assertEquals(0, panel.x);
            assertEquals(100, panel.y);
            assertEquals(120, panel.width);
            assertEquals(100, panel.height);
            assertEquals("Window tooltip", fixture.tooltipAt(150, 102),
                    "the uncovered part of the window must retain its tooltip");
            assertNull(fixture.tooltipAt(2, 102),
                    "empty opaque presentation padding must not leak a covered window tooltip");
            fixture.context.eval("js", "overlay.set(false)");
            assertEquals("Window tooltip", fixture.tooltipAt(2, 102));
        }
    }
}
//?}
