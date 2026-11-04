//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NativeWindowChromeBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {"close", "maximize", "minimize"})
    void titlebarControlOwnsItsRightmostPixelButTheBodyEdgeStillResizes(String action) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.getBindings("js").putMember("action", action);
            fixture.context.eval("js", """
                    globalThis.events=[]; globalThis.resizeEvents=[];
                    globalThis.root=UI.createRoot(()=>UI.element('workspace',{children:UI.element('window',{
                      id:'notes',title:'Notes',x:12,y:12,width:90,height:100,active:true,
                      titlebarHeight:24,titlebarButtonWidth:18,resizable:true,
                      closable:action==='close',maximizable:action==='maximize',minimizable:action==='minimize',
                      onDismiss:()=>events.push('close'),onMaximize:()=>events.push('maximize'),
                      onMinimize:()=>events.push('minimize'),onResize:event=>resizeEvents.push(event)
                    })}),host,{id:'native-control-boundary',viewport:{width:240,height:200}});
                    host.bindRoot(root);
                    """);
            // The control and east resize band overlap at x=101; the visible control owns this point.
            assertTrue(fixture.adapter.dispatchAt(101, 24, "click", 0, 0));
            assertEquals(1, fixture.context.eval("js", "events.length").asInt());
            assertEquals(action, fixture.context.eval("js", "events[0]").asString());
            assertFalse(fixture.adapter.dispatchDrag(121, 24, 0), "a chrome action must not start an edge capture");
            fixture.adapter.dispatchRelease(121, 24, 0);
            assertEquals(0, fixture.context.eval("js", "resizeEvents.length").asInt());

            // The same rightmost pixel below the titlebar remains an ordinary east resize edge.
            assertTrue(fixture.adapter.dispatchAt(101, 70, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchDrag(121, 70, 0));
            assertEquals(1, fixture.context.eval("js", "resizeEvents.length").asInt());
            assertEquals("e", fixture.context.eval("js", "resizeEvents[0].edge").asString());
            assertEquals(110d, fixture.context.eval("js", "resizeEvents[0].width").asDouble());
            assertTrue(fixture.adapter.dispatchRelease(121, 70, 0));
        }
    }
}
//?}
