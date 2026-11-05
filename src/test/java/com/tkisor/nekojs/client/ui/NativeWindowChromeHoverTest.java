//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises chrome feedback through the Screen's real render extraction entry point. */
class NativeWindowChromeHoverTest {
    private static final int EDGE = 0xFF80BFFF;

    private static void install(NativeUiScreenFixture fixture) throws Exception {
        fixture.installRuntime();
        fixture.adapter.resize(300, 240);
        fixture.context.eval("js", """
                globalThis.mode=UI.createSignal('normal');
                globalThis.resizable=UI.createSignal(true);
                globalThis.overlay=UI.createSignal(false);
                globalThis.modal=UI.createSignal(false);
                globalThis.cover=UI.createSignal(false);
                globalThis.hidden=UI.createSignal(false);
                globalThis.controls=UI.createSignal(true);
                globalThis.root=UI.createRoot(()=>UI.element('screen',{
                  width:'fill',height:'fill',layout:'stack',children:[
                    UI.element('workspace',{width:'fill',height:'fill',children:[
                      UI.element('window',{id:'notes',title:'Notes',x:20,y:20,width:160,height:140,
                        active:true,titlebarHeight:24,titlebarButtonWidth:20,mode:mode.get(),
                        visible:!hidden.get(),resizable:resizable.get(),
                        closable:controls.get(),minimizable:controls.get(),maximizable:controls.get(),onResize:()=>{},
                        children:[UI.element('panel',{width:'fill',height:'fill'}),
                          modal.get()?UI.element('dialog',{id:'modal'}):null]}),
                      cover.get()?UI.element('window',{id:'cover',title:'Cover',x:20,y:20,
                        width:160,height:140,active:false,closable:false}):null]}),
                    overlay.get()?UI.element('panel',{width:200,height:180,anchor:'topLeft',
                      workspacePresentation:true}):null
                  ]}),host,{id:'native-chrome-hover',viewport:{width:300,height:240}});
                host.bindRoot(root);
                """);
    }

    @ParameterizedTest
    @CsvSource({"21,80,w", "179,80,e", "80,21,n", "80,159,s",
            "21,21,nw", "179,159,se", "21,159,sw", "179,21,ne"})
    void edgeFeedbackUsesTheResizeHitAndPaintsOverContent(int x, int y, String edge) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            fixture.context.eval("js", "controls.set(false)");
            var frame = fixture.chromeAt(x, y);
            assertEquals(1, frame.cursors().size(), "a resize hit must request a cursor");
            assertSame(edge.length() == 2 ? CursorTypes.RESIZE_ALL
                    : "ew".contains(edge) ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_NS,
                    frame.cursors().getFirst());
            var fills = frame.fills().stream().filter(fill -> fill.color() == EDGE).toList();
            assertEquals(edge.length(), fills.size(), "only the hit edge or corner edges highlight");
            if (edge.contains("n")) assertTrue(fills.contains(new NativeUiScreenFixture.Fill(20, 20, 180, 22, EDGE)));
            if (edge.contains("s")) assertTrue(fills.contains(new NativeUiScreenFixture.Fill(20, 158, 180, 160, EDGE)));
            if (edge.contains("w")) assertTrue(fills.contains(new NativeUiScreenFixture.Fill(20, 20, 22, 160, EDGE)));
            if (edge.contains("e")) assertTrue(fills.contains(new NativeUiScreenFixture.Fill(178, 20, 180, 160, EDGE)));
            assertTrue(frame.fills().indexOf(fills.getFirst()) > frame.fills().indexOf(
                    new NativeUiScreenFixture.Fill(20, 44, 180, 160, 0xB0101010)),
                    "edge feedback must be painted after the content panel");
        }
    }

    @ParameterizedTest
    @CsvSource({"179,dismiss", "150,maximize", "130,minimize"})
    void onlyTheHoveredControlHighlightsAndWinsOverTheResizeBand(int x, String action) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            var frame = fixture.chromeAt(x, 21);
            int left = "dismiss".equals(action) ? 160 : "maximize".equals(action) ? 140 : 120;
            int color = "dismiss".equals(action) ? 0xFFE81123 : 0xFF5A6D82;
            assertTrue(frame.fills().contains(new NativeUiScreenFixture.Fill(left, 20, left + 20, 44, color)));
            assertEquals(1, frame.fills().stream().filter(fill -> fill.color() == 0xFFE81123
                    || fill.color() == 0xFF5A6D82).count());
            assertTrue(frame.cursors().isEmpty(), "a titlebar control is not a resize target");
            assertTrue(frame.fills().stream().noneMatch(fill -> fill.color() == EDGE));
            if ("maximize".equals(action)) {
                fixture.context.eval("js", "mode.set('maximized')");
                JsxHostTree.Node window = fixture.adapter.roots().getFirst().children.getFirst().children.getFirst();
                var restored = fixture.chromeAt(window.x + window.width - 30, window.y + 2);
                assertTrue(restored.fills().stream().anyMatch(fill -> fill.color() == 0xFF5A6D82),
                        "restore control must also highlight");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"overlay", "modal", "cover", "disabled", "hidden", "ancestorDisabled"})
    void blockedChromeHasNoControlOrResizeFeedback(String blocker) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            assertFalse(fixture.chromeAt(179, 21).fills().stream().noneMatch(fill -> fill.color() == 0xFFE81123));
            assertFalse(fixture.chromeAt(21, 80).cursors().isEmpty());
            if ("disabled".equals(blocker)) {
                fixture.adapter.roots().getFirst().children.getFirst().children.getFirst().props.put("disabled", true);
            } else if ("ancestorDisabled".equals(blocker)) {
                fixture.adapter.roots().getFirst().children.getFirst().props.put("disabled", true);
            } else {
                fixture.context.eval("js", blocker + ".set(true)");
            }
            for (int[] point : new int[][]{{179, 21}, {21, 80}}) {
                var frame = fixture.chromeAt(point[0], point[1]);
                assertTrue(frame.cursors().isEmpty());
                assertTrue(frame.fills().stream().noneMatch(fill -> fill.color() == EDGE
                        || fill.color() == 0xFFE81123 || fill.color() == 0xFF5A6D82));
            }
        }
    }

    @Test
    void resizeCursorFollowsCaptureOutsideBoundsAndStopsOnReleaseOrCapabilityChange() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            assertTrue(fixture.adapter.dispatchAt(21, 80, "click", 0, 0));
            assertEquals(1, fixture.chromeAt(280, 220).cursors().size());
            fixture.adapter.dispatchRelease(280, 220, 0);
            assertTrue(fixture.chromeAt(280, 220).cursors().isEmpty());
            assertTrue(fixture.adapter.dispatchAt(21, 80, "click", 0, 0));
            fixture.context.eval("js", "resizable.set(false)");
            assertTrue(fixture.chromeAt(21, 80).cursors().isEmpty());
            assertTrue(fixture.chromeAt(280, 220).cursors().isEmpty());
            fixture.context.eval("js", "resizable.set(true); mode.set('maximized')");
            assertTrue(fixture.chromeAt(1, 100).cursors().isEmpty());
            fixture.context.eval("js", "mode.set('normal')");
            assertTrue(fixture.chromeAt(80, 80).cursors().isEmpty());
            assertTrue(fixture.adapter.dispatchAt(21, 80, "click", 0, 0));
            fixture.context.eval("js", "modal.set(true)");
            assertTrue(fixture.chromeAt(280, 220).cursors().isEmpty(), "modal publication cancels resize feedback");
        }
    }

    @Test
    void regionCursorUsesCallerCoordinatesAndDoesNotOverrideTheCallerOutsideResizeHits() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            var regionMount = JsxHostAdapter.class.getDeclaredField("regionMount");
            regionMount.setAccessible(true);
            regionMount.setBoolean(fixture.adapter, true);
            fixture.adapter.setRegion(40, 30, 300, 240);
            assertEquals(1, fixture.chromeAt(61, 110, true).cursors().size());
            assertTrue(fixture.chromeAt(21, 80, true).cursors().isEmpty(), "outside the region must not request a cursor");
            assertTrue(fixture.chromeAt(120, 110, true).cursors().isEmpty(), "ordinary content must preserve caller cursor requests");
            assertTrue(fixture.adapter.mouseClicked(61, 110, 0, 0));
            assertEquals(1, fixture.chromeAt(5, 5, true).cursors().size(), "captured resize keeps its cursor outside the region");
            fixture.adapter.mouseReleased(5, 5, 0);
            assertTrue(fixture.chromeAt(5, 5, true).cursors().isEmpty());
        }
    }

    @Test
    void leavingChromeAndDisposingTheRootStopRequestingCursors() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            install(fixture);
            assertEquals(1, fixture.chromeAt(21, 80).cursors().size());
            for (int[] point : new int[][]{{80, 80}, {19, 80}, {180, 80}, {80, 160}, {80, 19}}) {
                var frame = fixture.chromeAt(point[0], point[1]);
                assertTrue(frame.cursors().isEmpty());
                assertTrue(frame.fills().stream().noneMatch(fill -> fill.color() == EDGE
                        || fill.color() == 0xFFE81123 || fill.color() == 0xFF5A6D82));
            }
            fixture.context.eval("js", "root.forceClose()");
            assertTrue(fixture.chromeAt(21, 80).cursors().isEmpty());
        }
    }
}
//?}
