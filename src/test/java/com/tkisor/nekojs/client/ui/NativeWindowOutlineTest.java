//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NativeWindowOutlineTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void defaultOutlineRemainsOneScreenPixelAndPaintsAboveContent(int scale) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.guiScale(scale);
            fixture.installRuntime();
            fixture.adapter.resize(300, 240);
            fixture.context.eval("js", """
                    globalThis.active=UI.createSignal(true);
                    globalThis.shown=UI.createSignal(true);
                    globalThis.titleHeight=UI.createSignal(24);
                    globalThis.root=UI.createRoot(()=>UI.element('workspace',{children:
                      UI.element('window',{id:'notes',title:'Notes',x:20,y:20,width:160,height:140,
                        active:active.get(),visible:shown.get(),titlebarHeight:titleHeight.get(),
                        resizable:true,children:UI.element('panel',{width:'fill',height:'fill',background:'#FFFFFF'})})
                    }),host,{id:'native-window-outline',viewport:{width:300,height:240}});
                    host.bindRoot(root);
                    """);
            var frame = fixture.chromeAt(80, 80);
            assertOutline(frame, scale, 0xFF697582);
            assertEquals(0xFF697582, frame.transformedFills().getLast().color(),
                    "the outline must not be painted over by the content panel");
            assertTrue(frame.cursors().isEmpty(), "a passive outline must not create a resize hit");

            var hovered = fixture.chromeAt(21, 80);
            int lastOutline = -1;
            int firstHighlight = -1;
            for (int index = 0; index < hovered.fills().size(); index++) {
                int color = hovered.fills().get(index).color();
                if (color == 0xFF697582) lastOutline = index;
                if (color == 0xFF80BFFF && firstHighlight < 0) firstHighlight = index;
            }
            assertTrue(lastOutline >= 0 && firstHighlight > lastOutline,
                    "resize feedback must remain above the passive outline");

            fixture.context.eval("js", "active.set(false); titleHeight.set(0)");
            assertOutline(fixture.chromeAt(80, 80), scale, 0xFF454D57);
            fixture.context.eval("js", "shown.set(false)");
            assertTrue(fixture.chromeAt(80, 80).transformedFills().isEmpty());
        }
    }

    private static void assertOutline(NativeUiScreenFixture.ChromeFrame frame, int scale, int color) {
        var edges = frame.transformedFills().stream().filter(fill -> fill.color() == color).toList();
        assertEquals(4, edges.size(), "all four window edges must have a passive outline");
        for (var edge : edges) {
            double width = edge.right() - edge.left();
            double height = edge.bottom() - edge.top();
            assertEquals(1, Math.min(width, height) * scale, 0.0001,
                    "the passive outline must stay one physical framebuffer pixel across GUI scales");
            assertTrue(edge.left() >= 20 && edge.top() >= 20);
            assertTrue(edge.right() <= 180.0001 && edge.bottom() <= 160.0001,
                    "outline geometry must stay inside the existing window bounds");
        }
    }
}
//?}
