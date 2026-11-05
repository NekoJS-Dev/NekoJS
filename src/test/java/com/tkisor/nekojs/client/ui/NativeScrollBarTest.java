//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeScrollBarTest {
    private static final int THUMB = 0xFF7D8A9A;
    private static final int TRACK = 0xFF252D38;

    @ParameterizedTest
    @ValueSource(ints = {426, 427, 854, 1280})
    void thumbShowsProgressAndDisappearsWithoutOverflow(int width) throws Exception {
        try (NativeUiScreenFixture fixture = fixture(width)) {
            var first = thumb(fixture);
            var track = track(fixture);
            assertTrue(first.bottom() > first.top());
            assertTrue(first.bottom() - first.top() < track.bottom() - track.top());
            assertEquals(track.top(), first.top());
            JsxHostTree.Node inner = find(fixture.adapter.roots(), "inner");
            fixture.adapter.dispatchScroll(inner.x + 2, inner.y + 2, -1);
            assertTrue(thumb(fixture).top() > first.top());
            inner = find(fixture.adapter.roots(), "inner");
            fixture.adapter.dispatchScroll(inner.x + 2, inner.y + 2, -10000);
            assertEquals(track.bottom(), thumb(fixture).bottom());
            fixture.context.eval("js", "short=true; root.refresh()");
            assertTrue(fixture.chromeAt(-1, -1).fills().stream().noneMatch(fill -> fill.color() == THUMB));
        }
    }

    @Test
    void thumbCaptureAndTrackPagingStayInsideTheInnerScroll() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(1280)) {
            var thumb = thumb(fixture);
            double x = (thumb.left() + thumb.right()) / 2.0;
            double y = (thumb.top() + thumb.bottom()) / 2.0;
            assertTrue(fixture.adapter.dispatchAt(x, y, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchDrag(x + 100, 500, 0));
            JsxHostTree.Node inner = find(fixture.adapter.roots(), "inner");
            assertEquals(inner.scrollRange, offset(inner), 0.000001);
            assertEquals(0, offset(find(fixture.adapter.roots(), "outer")));
            assertEquals(0, fixture.context.eval("js", "clicks").asInt());
            assertEquals(1, fixture.context.eval("js", "scrolls").asInt());
            assertTrue(fixture.adapter.dispatchRelease(x + 100, 500, 0));
            assertFalse(fixture.adapter.dispatchDrag(x, 0, 0));
            fixture.adapter.dispatchScroll(inner.x + 2, inner.y + 2, 10000);
            var track = track(fixture);
            assertTrue(fixture.adapter.dispatchAt(x, track.bottom() - 1, "click", 0, 0));
            inner = find(fixture.adapter.roots(), "inner");
            assertEquals(inner.height, offset(inner), 0.000001);
            assertTrue(fixture.adapter.dispatchRelease(x, track.bottom() - 1, 0));
            assertEquals(0, fixture.context.eval("js", "clicks").asInt());
        }
    }

    @Test
    void horizontalThumbUsesTheBottomEdgeAndDragsToTheLastColumn() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(427, true)) {
            var thumb = thumb(fixture);
            var track = track(fixture);
            assertTrue(track.right() - track.left() > track.bottom() - track.top());
            assertEquals(track.left(), thumb.left());
            double x = (thumb.left() + thumb.right()) / 2.0;
            double y = (thumb.top() + thumb.bottom()) / 2.0;
            assertTrue(fixture.adapter.dispatchAt(x, y, "click", 0, 0));
            fixture.adapter.dispatchDrag(1000, y, 0);
            JsxHostTree.Node inner = find(fixture.adapter.roots(), "inner");
            assertEquals(inner.scrollRange, offset(inner), 0.000001);
            fixture.adapter.dispatchRelease(1000, y, 0);
            assertEquals(track.right(), thumb(fixture).right());
            assertEquals(0, offset(find(fixture.adapter.roots(), "outer")));
        }
    }

    @Test
    void aCoveringPanelReceivesTheClickInsteadOfTheScrollbar() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(1280)) {
            var track = track(fixture);
            fixture.context.eval("js", "cover=true; root.refresh()");
            fixture.adapter.dispatchAt(track.left() + 1, track.bottom() - 1, "click", 0, 0);
            assertEquals(1, fixture.context.eval("js", "coverClicks").asInt());
            assertEquals(0, offset(find(fixture.adapter.roots(), "inner")));
        }
    }

    @Test
    void removingOverflowOrOpeningAModalEndsTheOldDrag() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(1280)) {
            var thumb = thumb(fixture);
            double x = (thumb.left() + thumb.right()) / 2.0;
            assertTrue(fixture.adapter.dispatchAt(x, thumb.top() + 1, "click", 0, 0));
            fixture.context.eval("js", "short=true; root.refresh()");
            assertFalse(fixture.adapter.dispatchDrag(x, 500, 0));
            fixture.context.eval("js", "short=false; root.refresh()");
            thumb = thumb(fixture);
            fixture.adapter.dispatchAt(x, thumb.top() + 1, "click", 0, 0);
            fixture.context.eval("js", "modal=true; root.refresh()");
            fixture.adapter.dispatchDrag(x, 500, 0);
            assertEquals(0, offset(find(fixture.adapter.roots(), "inner")));
            fixture.context.eval("js", "modal=false; root.refresh()");
            assertFalse(fixture.adapter.dispatchDrag(x, 500, 0));
        }
    }

    private static NativeUiScreenFixture fixture(int width) throws Exception {
        return fixture(width, false);
    }

    private static NativeUiScreenFixture fixture(int width, boolean horizontal) throws Exception {
        NativeUiScreenFixture fixture = new NativeUiScreenFixture();
        fixture.installRuntime();
        fixture.adapter.resize(width, 720);
        fixture.context.getBindings("js").putMember("viewWidth", width);
        fixture.context.getBindings("js").putMember("horizontal", horizontal);
        fixture.context.eval("js", """
                globalThis.short=false; globalThis.modal=false; globalThis.clicks=0;
                globalThis.cover=false; globalThis.coverClicks=0; globalThis.scrolls=0;
                globalThis.root=UI.createRoot(()=>UI.element('screen',{
                  width:'fill',height:'fill',layout:'stack',children:[
                    UI.element('scroll',{id:'outer',width:200,height:110,scrollY:true,coordinateSpace:'design',children:
                      UI.element('column',{width:'fill',coordinateSpace:'design',children:[
                        UI.element('scroll',{id:'inner',width:160,height:60,scrollY:true,
                          direction:horizontal?'row':'column',onScroll:()=>scrolls++,coordinateSpace:'design',children:
                          UI.element('panel',{id:'content',width:horizontal?(short?20:300):'fill',
                            height:horizontal?'fill':(short?20:300),
                            coordinateSpace:'design',onClick:()=>clicks++})}),
                        UI.element('spacer',{height:short?0:200,coordinateSpace:'design'})]})}),
                    cover?UI.element('panel',{id:'cover',width:160,height:60,coordinateSpace:'design',onClick:()=>coverClicks++}):null,
                    modal?UI.element('dialog',{id:'modal',width:100,height:80}):null
                  ]}),host,{viewport:{width:viewWidth,height:720,designWidth:1280,designHeight:720}});
                host.bindRoot(root);
                """);
        return fixture;
    }

    private static NativeUiScreenFixture.Fill thumb(NativeUiScreenFixture fixture) throws Exception {
        return fill(fixture, THUMB);
    }

    private static NativeUiScreenFixture.Fill track(NativeUiScreenFixture fixture) throws Exception {
        return fill(fixture, TRACK);
    }

    private static NativeUiScreenFixture.Fill fill(NativeUiScreenFixture fixture, int color) throws Exception {
        JsxHostTree.Node inner = find(fixture.adapter.roots(), "inner");
        return fixture.chromeAt(-1, -1).fills().stream()
                .filter(fill -> fill.color() == color && fill.right() <= inner.x + inner.width)
                .findFirst().orElseThrow(() -> new AssertionError("Overflow must paint a visible native scrollbar"));
    }

    private static double offset(JsxHostTree.Node node) {
        return ((Number) node.props.get("scrollOffset")).doubleValue();
    }

    private static JsxHostTree.Node find(List<JsxHostTree.Node> roots, String id) {
        for (JsxHostTree.Node node : roots) {
            if (id.equals(node.props.get("id"))) return node;
            JsxHostTree.Node child = find(node.children, id);
            if (child != null) return child;
        }
        return null;
    }
}
//?}
