//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.core.compiler.NekoJsxCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeDashboardScrollTest {
    @ParameterizedTest
    @CsvSource({"426,250", "427,250", "854,499", "1280,720"})
    void expandedPrototypePanesScrollIndependently(int width, int height) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            installPrototype(fixture, width, height);
            fixture.context.eval("js", "changeScenario('raw')");
            JsxHostTree.Node messageScroll = find(fixture.adapter.roots(), "dashboard-message-scroll");
            assertNotNull(messageScroll, "long Error messages must own an independent scroll viewport");
            JsxHostTree.Node scroll = find(fixture.adapter.roots(), "dashboard-detail-scroll");
            assertTrue(messageScroll.scrollRange > 0);
            int messageBefore = find(fixture.adapter.roots(), "dashboard-message").y;
            double outerMessageBefore = offset(scroll);
            fixture.adapter.dispatchScroll(messageScroll.clipX + 2, messageScroll.clipY + 2, -1);
            assertTrue(find(fixture.adapter.roots(), "dashboard-message").y < messageBefore);
            assertEquals(outerMessageBefore, offset(find(fixture.adapter.roots(), "dashboard-detail-scroll")), 0.000001);
            fixture.adapter.dispatchScroll(messageScroll.clipX + 2, messageScroll.clipY + 2, -10000);
            assertTrue(fixture.paint().stream().anyMatch(draw -> draw.text().contains("Error message line 079")),
                    "the last Error message line must be reachable");
            JsxHostTree.Node rawScroll = find(fixture.adapter.roots(), "dashboard-raw-scroll");
            assertNotNull(rawScroll, "expanded raw details must own an independent scroll viewport");
            JsxHostTree.Node raw = find(fixture.adapter.roots(), "dashboard-raw-text");
            assertTrue(raw.height + 1 >= raw.textLayout.height(), "wrapped raw lines must remain reachable");
            assertTrue(rawScroll.scrollRange > 0, "long raw details must exceed the bounded inner viewport");
            assertTrue(rawScroll.height <= 160 * Math.min(width / 1280.0, height / 720.0) + 1);
            int before = raw.y;
            double outerBefore = offset(scroll);
            assertTrue(fixture.adapter.dispatchScroll(rawScroll.clipX + 2, rawScroll.clipY + 2, -1));
            assertTrue(find(fixture.adapter.roots(), "dashboard-raw-text").y < before);
            assertEquals(outerBefore, offset(find(fixture.adapter.roots(), "dashboard-detail-scroll")), 0.000001,
                    "scrolling raw text must leave the outer details position unchanged");
            fixture.adapter.dispatchScroll(rawScroll.clipX + 2, rawScroll.clipY + 2, -10000);
            rawScroll = find(fixture.adapter.roots(), "dashboard-raw-scroll");
            raw = find(fixture.adapter.roots(), "dashboard-raw-text");
            assertTrue(raw.y + raw.height <= rawScroll.y + rawScroll.height + 1,
                    "the end of the inner scroll must reveal the last raw line");
            assertTrue(fixture.paint().stream().anyMatch(draw -> draw.text().contains("Raw snapshot line 159")),
                    "visible-line culling must still paint the last raw line");
            double rawBefore = offset(rawScroll);
            // Make explanatory text overflow separately from the three bounded text panes.
            fixture.context.eval("js", "translations['nekojs.gui.dashboard.no_excerpt']='Outer explanatory line\\n'.repeat(80); stackOpen.set(true); probeRoot.refresh()");
            scroll = find(fixture.adapter.roots(), "dashboard-detail-scroll");
            assertTrue(scroll.scrollRange > 0);
            fixture.adapter.dispatchScroll(scroll.x + 1, scroll.y + 1, -10000);
            assertTrue(offset(find(fixture.adapter.roots(), "dashboard-detail-scroll")) > outerBefore);
            assertEquals(rawBefore, offset(find(fixture.adapter.roots(), "dashboard-raw-scroll")), 0.000001,
                    "outer scrolling must retain the independent raw offset");
            JsxHostTree.Node stackScroll = find(fixture.adapter.roots(), "dashboard-stack-scroll");
            assertNotNull(stackScroll);
            assertTrue(stackScroll.scrollRange > 0);
            before = find(fixture.adapter.roots(), "dashboard-stack-text").y;
            outerBefore = offset(find(fixture.adapter.roots(), "dashboard-detail-scroll"));
            fixture.adapter.dispatchScroll(stackScroll.clipX + 2, stackScroll.clipY + 2, -1);
            assertTrue(find(fixture.adapter.roots(), "dashboard-stack-text").y < before);
            assertEquals(outerBefore, offset(find(fixture.adapter.roots(), "dashboard-detail-scroll")), 0.000001);
            fixture.adapter.dispatchScroll(stackScroll.clipX + 2, stackScroll.clipY + 2, -10000);
            assertTrue(fixture.paint().stream().anyMatch(draw -> draw.text().contains("callback59")),
                    "visible-line culling must still paint the last stack frame");

        }
    }

    @Test
    void expandedStackPaintWorkStaysWithinItsVisibleViewport() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            installPrototype(fixture);
            fixture.context.eval("js", "changeScenario('long')");
            fixture.resetFontMeasurements();
            long started = System.nanoTime();
            fixture.context.eval("js", "stackOpen.set(true)");
            long expandNanos = System.nanoTime() - started;
            var measured = fixture.fontMeasurements();
            JsxHostTree.Node stack = find(fixture.adapter.roots(), "dashboard-stack-text");
            fixture.resetFontMeasurements();
            started = System.nanoTime();
            var frame = fixture.paint();
            long paintNanos = System.nanoTime() - started;
            long stackDraws = frame.stream().filter(draw -> draw.text().contains("callback")).count();
            int visibleLines = (int) Math.ceil(stack.clipHeight / (stack.textFont.lineHeight() * stack.textScale)) + 2;
            JsxHostTree.Node viewport = find(fixture.adapter.roots(), "dashboard-stack-scroll");
            fixture.resetFontMeasurements();
            started = System.nanoTime();
            fixture.adapter.dispatchScroll(viewport.clipX + 2, viewport.clipY + 2, -1);
            long scrollNanos = System.nanoTime() - started;
            var scrollMeasured = fixture.fontMeasurements();
            long[] warmPaint = new long[7];
            long[] warmScroll = new long[7];
            for (int index = 0; index < 11; index++) {
                started = System.nanoTime();
                fixture.paint();
                long elapsedPaint = System.nanoTime() - started;
                started = System.nanoTime();
                fixture.adapter.dispatchScroll(viewport.clipX + 2, viewport.clipY + 2, index % 2 == 0 ? -1 : 1);
                if (index >= 4) {
                    warmPaint[index - 4] = elapsedPaint;
                    warmScroll[index - 4] = System.nanoTime() - started;
                }
            }
            java.util.Arrays.sort(warmPaint);
            java.util.Arrays.sort(warmScroll);
            Path report = Path.of("build/reports/dashboard-stack-performance.json");
            Files.createDirectories(report.getParent());
            Files.writeString(report, "{\"expandNanos\":" + expandNanos + ",\"measureCalls\":" + measured.calls()
                    + ",\"measuredCharacters\":" + measured.characters() + ",\"paintNanos\":" + paintNanos
                    + ",\"frameDraws\":" + frame.size() + ",\"stackDraws\":" + stackDraws
                    + ",\"visibleLines\":" + visibleLines + ",\"scrollNanos\":" + scrollNanos
                    + ",\"scrollMeasureCalls\":" + scrollMeasured.calls()
                    + ",\"scrollMeasuredCharacters\":" + scrollMeasured.characters()
                    + ",\"warmPaintMedianNanos\":" + warmPaint[3] + ",\"warmScrollMedianNanos\":" + warmScroll[3] + "}\n");
            assertTrue(stackDraws <= visibleLines,
                    () -> "Hidden stack lines were still submitted for painting: draws=" + stackDraws + ", visible=" + visibleLines);
            assertEquals(0, scrollMeasured.calls(), "scrolling unchanged text must reuse the Java text layouts");
        }
    }

    private static void installPrototype(NativeUiScreenFixture fixture) throws Exception {
        installPrototype(fixture, 426, 250);
    }

    private static void installPrototype(NativeUiScreenFixture fixture, int width, int height) throws Exception {
        fixture.installRuntime();
        fixture.adapter.resize(width, height);
        fixture.context.getBindings("js").putMember("nativeHost", fixture.adapter);
        fixture.context.getBindings("js").putMember("viewWidth", width);
        fixture.context.getBindings("js").putMember("viewHeight", height);
        try (var input = NativeDashboardScrollTest.class.getResourceAsStream("/assets/nekojs/lang/en_us.json")) {
            assertNotNull(input);
            fixture.context.eval("js", "globalThis.translations=" + new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        fixture.context.eval("js", """
                globalThis.__nekoJsxFactory=(type,props,...children)=>UI.element(type,{...props,children});
                globalThis.Component={
                  translatable:(key,...args)=>{
                    let index=0;
                    return {string:String(translations[key]||key).replace(/%s/g,()=>String(args[index++]))};
                  },
                  translatableWithFallback:(key,fallback)=>({string:translations[key]||fallback})
                };
                globalThis.ClientEvents={lang:()=>{},loggedIn:()=>{}};
                globalThis.Minecraft={getInstance:()=>({player:null})};
                globalThis.probeAdapter={
                  isOwnerThread:()=>nativeHost.isOwnerThread(),
                  enqueue:action=>nativeHost.enqueue(action),
              viewport:()=>({width:viewWidth,height:viewHeight}),
                  supportsPrimitive:type=>nativeHost.supportsPrimitive(type),
                  measureText:(text,size,width)=>nativeHost.measureText(text,size,Math.floor(width)),
                  layout:(tree,viewport,snapshot,publish)=>nativeHost.layout(tree,viewport,snapshot,publish===true),
                  begin:()=>nativeHost.begin(),
                  reportDiagnostic:value=>nativeHost.reportDiagnostic(value)
                };
                """);
        Path path = Path.of("docs/architecture-refactor/prototypes/error-dashboard-jsx.prototype.tsx");
        if (!Files.isRegularFile(path)) path = Path.of("../../").resolve(path);
        String source = Files.readString(path).replace("import { UI } from \"nekojs/jsx-runtime\";", "")
                .replace("export function openDashboardPrototype", "function openDashboardPrototype");
        fixture.context.eval("js", NekoJsxCompiler.compileTsx(path, source).code());
        fixture.context.eval("js", """
                globalThis.probeRoot=UI.createRoot(Dashboard,probeAdapter,
              {viewport:{width:viewWidth,height:viewHeight,designWidth:1280,designHeight:720}});
                nativeHost.bindRoot(probeRoot);
                """);
    }

    private static double offset(JsxHostTree.Node node) {
        Object value = node.props.get("scrollOffset");
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static JsxHostTree.Node find(List<JsxHostTree.Node> nodes, String id) {
        for (JsxHostTree.Node node : nodes) {
            if (id.equals(node.props.get("id"))) return node;
            JsxHostTree.Node child = find(node.children, id);
            if (child != null) return child;
        }
        return null;
    }
}
//?}
