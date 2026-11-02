//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NativeDialogModalTest {
    @Test
    void modalConfinesPointerTextTabScrollAndNarration() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(true)) {
            JsxHostTree tree = tree(fixture);
            assertEquals(Set.of("first"), JsxHostAdapter.focusedIds(tree.roots()));
            assertEquals("First", fixture.adapter.narration());
            assertTrue(fixture.adapter.dispatchAt(1, 1, "click", 0, 0));
            assertEquals("[\"backdrop\"]", json(fixture, "events"));
            assertFalse(fixture.context.eval("js", "root.dispatch('background', 'change', {value:'bad'})").asBoolean());
            assertTrue(fixture.adapter.dispatchScroll(1, 1, -1));
            assertEquals(0, fixture.context.eval("js", "backgroundEvents").asInt());
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0));
            assertEquals(Set.of("edit"), JsxHostAdapter.focusedIds(tree.roots()));
            assertEquals("Modal editor", fixture.adapter.narration());
            assertTrue(fixture.adapter.textInput('x'));
            assertEquals("x", find(tree.roots(), "edit").inputValue);
            assertEquals("background", find(tree.roots(), "background").inputValue);
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0));
            assertEquals(Set.of("first"), JsxHostAdapter.focusedIds(tree.roots()));
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT));
            assertEquals(Set.of("edit"), JsxHostAdapter.focusedIds(tree.roots()));
            assertEquals("x", fixture.adapter.narration());
        }
    }

    @Test
    void escapeAndBackdropRequestDismissWithoutAutomaticallyClosing() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(true)) {
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ESCAPE, 0));
            assertNotNull(find(tree(fixture).roots(), "modal"));
            assertTrue(fixture.adapter.dispatchAt(99, 99, "click", 1, 0));
            assertEquals("[\"escape\"]", json(fixture, "events"));
            assertTrue(fixture.adapter.dispatchAt(99, 99, "click", 0, 0));
            assertEquals("[\"escape\",\"backdrop\"]", json(fixture, "events"));
            fixture.context.eval("js", "escapeAllowed=false; backdropAllowed=false; root.refresh()");
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ESCAPE, 0));
            assertTrue(fixture.adapter.dispatchAt(99, 99, "click", 0, 0));
            assertEquals("[\"escape\",\"backdrop\"]", json(fixture, "events"));
            assertNotNull(find(tree(fixture).roots(), "modal"));
        }
    }

    @Test
    void nestedModalStackRestoresRetainedFocusAndCancelsCaptureAtEachTransition() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(false)) {
            JsxHostTree tree = tree(fixture);
            assertTrue(fixture.adapter.dispatchAt(5, 5, "click", 0, 0));
            assertNotNull(tree.captured(0));
            fixture.context.eval("js", "open=true; root.refresh()");
            assertNull(tree.captured(0));
            assertEquals(Set.of("first"), JsxHostAdapter.focusedIds(tree.roots()));
            fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0);
            assertEquals(Set.of("edit"), JsxHostAdapter.focusedIds(tree.roots()));
            fixture.context.eval("js", "inner=true; root.refresh()");
            assertEquals(Set.of("innerAction"), JsxHostAdapter.focusedIds(tree.roots()));
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0));
            assertEquals(Set.of("innerAction"), JsxHostAdapter.focusedIds(tree.roots()));
            fixture.context.eval("js", "inner=false; root.refresh()");
            assertEquals(Set.of("edit"), JsxHostAdapter.focusedIds(tree.roots()));
            fixture.context.eval("js", "open=false; root.refresh()");
            assertEquals(Set.of("background"), JsxHostAdapter.focusedIds(tree.roots()));
            assertFalse(fixture.adapter.dispatchDrag(80, 80, 0));
            assertFalse(fixture.adapter.dispatchRelease(80, 80, 0));
        }
    }

    @Test
    void removedHiddenDisabledAndRetypedReturnTargetsUseAnEligibleFallback() throws Exception {
        for (String mutation : List.of("backgroundDisabled=true", "backgroundVisible=false", "backgroundType='label'")) {
            try (NativeUiScreenFixture fixture = fixture(false)) {
                fixture.adapter.key(GLFW.GLFW_KEY_TAB, 0);
                fixture.context.eval("js", "open=true; root.refresh()");
                fixture.context.eval("js", mutation + "; root.refresh(); open=false; root.refresh()");
                assertEquals(Set.of("fallback"), JsxHostAdapter.focusedIds(tree(fixture).roots()), mutation);
            }
        }
    }

    @Test
    void aFailedModalCandidateCannotChangePublishedFocusOrCapture() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(false)) {
            fixture.adapter.dispatchAt(5, 5, "click", 0, 0);
            JsxHostTree tree = tree(fixture);
            JsxHostTree.Node published = tree.roots().getFirst();
            fixture.context.eval("js", "open=true; badTitle=true; root.refresh()");
            assertSame(published, tree.roots().getFirst());
            assertEquals(Set.of("background"), JsxHostAdapter.focusedIds(tree.roots()));
            assertNotNull(tree.captured(0));
            assertNull(find(tree.roots(), "modal"));
        }
    }

    @Test
    void aDialogUnderAScrolledTinyAncestorIsAnIndependentViewportPortal() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(true)) {
            JsxHostTree tree = tree(fixture);
            JsxHostTree.Node scroll = find(tree.roots(), "tinyScroll");
            scroll.props.put("scrollOffset", 25);
            fixture.context.eval("js", "root.resize({width:100,height:100})");
            JsxHostTree.Node modal = find(tree.roots(), "modal");
            assertEquals(0, modal.x);
            assertEquals(0, modal.y);
            assertEquals(100, modal.width);
            assertEquals(100, modal.height);
            assertEquals(0, modal.clipX);
            assertEquals(0, modal.clipY);
            assertEquals(100, modal.clipWidth);
            assertEquals(100, modal.clipHeight);
            assertEquals(10, find(tree.roots(), "modalPanel").x);
            assertEquals(10, find(tree.roots(), "modalPanel").y);
            assertTrue(fixture.adapter.dispatchAt(15, 15, "click", 0, 0));
            assertEquals("[\"first\"]", json(fixture, "events"));
            Painter painter = painter();
            fixture.screen.extractRenderState(painter, 15, 15, 0);
            assertEquals(painter.pushes, painter.pops);
            assertTrue(painter.clips.isEmpty());
            assertTrue(painter.texts.contains("First"));
            assertTrue(painter.dimClips.contains(new Clip(0, 0, 100, 100)));
        }
    }

    @Test
    void topModalOwnsPointerKeyboardAndEscapeEvenWithMultipleVisibleDialogs() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(true)) {
            fixture.context.eval("js", "inner=true; root.refresh()");
            assertTrue(fixture.adapter.dispatchAt(15, 15, "click", 0, 0));
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ENTER, 0));
            assertTrue(fixture.adapter.key(GLFW.GLFW_KEY_ESCAPE, 0));
            assertEquals("[\"inner\",\"inner\",\"inner-escape\"]", json(fixture, "events"));
            assertEquals(Set.of("innerAction"), JsxHostAdapter.focusedIds(tree(fixture).roots()));
        }
    }

    @Test
    void nativeKeyNamesMatchTabsSelectionNavigationContract() throws Exception {
        try (NativeUiScreenFixture fixture = fixture(true)) {
            for (int key : List.of(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP,
                    GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END)) assertTrue(fixture.adapter.key(key, 0));
            assertEquals("[\"left\",\"right\",\"up\",\"down\",\"home\",\"end\"]", json(fixture, "keys"));
        }
    }

    @Test
    void aModalScopeWithoutDialogsPreservesExistingFocusAndCapture() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction transaction = tree.begin();
        Object disabled = transaction.create("input", "disabled", java.util.Map.of("id", "disabled", "disabled", true));
        Object captured = transaction.create("button", "captured", java.util.Map.of("id", "captured"));
        transaction.order(null, List.of(disabled, captured));
        transaction.commit(List.of(disabled, captured));
        JsxModalScope scope = new JsxModalScope();
        scope.synchronize(tree);
        assertTrue(JsxHostAdapter.focusedIds(tree.roots()).isEmpty());
        JsxHostTree.Node input = tree.roots().getFirst();
        JsxHostTree.Node button = tree.roots().getLast();
        input.focused = true;
        tree.capture(button, 0);
        scope.synchronize(tree);
        scope.synchronize(tree);
        assertTrue(input.focused, "A modal-free tree must retain its existing nonmodal appearance state");
        assertSame(button, tree.captured(0));
    }

    @Test
    void rawDialogValidationRejectsInvalidNativeDescriptors() {
        for (var props : List.of(java.util.Map.<String, Object>of(), java.util.Map.<String, Object>of("id", ""),
                java.util.Map.<String, Object>of("id", "modal", "title", 2),
                java.util.Map.<String, Object>of("id", "modal", "closeOnEscape", "false"))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new JsxHostTree().begin().create("dialog", "dialog", props));
            assertTrue(error.getMessage().startsWith("[NEKO-7012]"));
        }
    }

    private static NativeUiScreenFixture fixture(boolean open) throws Exception {
        NativeUiScreenFixture fixture = new NativeUiScreenFixture();
        fixture.installRuntime();
        fixture.context.eval("js", "globalThis.open=" + open + ";");
        fixture.context.eval("js", """
                globalThis.events=[]; globalThis.keys=[]; globalThis.backgroundEvents=0;
                globalThis.inner=false; globalThis.backgroundDisabled=false; globalThis.backgroundVisible=true;
                globalThis.backgroundType='input'; globalThis.badTitle=false;
                globalThis.escapeAllowed=true; globalThis.backdropAllowed=true;
                globalThis.root=UI.createRoot(() => UI.element('column',{id:'outer',width:100,height:100,children:[
                  UI.element('column',{id:'backgroundGroup',visible:backgroundVisible,width:100,height:20,children:[
                    UI.element(backgroundType,{id:'background',
                      ...(backgroundType==='input'?{value:'background',disabled:backgroundDisabled}:{}),width:100,height:20,
                      onChange:()=>backgroundEvents++})]}),
                  UI.element('button',{id:'fallback',text:'Fallback',width:100,height:20,onClick:()=>backgroundEvents++}),
                  UI.element('scroll',{id:'tinyScroll',width:20,height:20,children:[
                    UI.element('spacer',{width:20,height:200}),
                    open ? UI.element('dialog',{id:'modal',title:badTitle?3:'Modal title',
                      closeOnEscape:escapeAllowed,closeOnBackdrop:backdropAllowed,
                      onDismiss:e=>events.push(e.reason),children:[
                      UI.element('panel',{id:'modalPanel',width:80,height:80,children:[
                        UI.element('button',{id:'first',text:'First',width:80,height:20,
                          onClick:()=>events.push('first'),onKey:e=>keys.push(e.key)}),
                        UI.element('input',{id:'edit',value:'',placeholder:'Modal editor',width:80,height:20}),
                        inner ? UI.element('dialog',{id:'inner',onDismiss:e=>events.push('inner-'+e.reason),children:[
                          UI.element('panel',{width:80,height:80,children:[
                            UI.element('button',{id:'innerAction',text:'Inner',width:80,height:20,onClick:()=>events.push('inner')})]})]}) : null
                      ]})]}) : null]})
                ]}),host,{id:'modal-native-test',viewport:{width:100,height:100}});
                host.bindRoot(root);
                """);
        return fixture;
    }

    private static String json(NativeUiScreenFixture fixture, String expression) {
        return fixture.context.eval("js", "JSON.stringify(" + expression + ")").asString();
    }

    private static JsxHostTree tree(NativeUiScreenFixture fixture) throws Exception {
        Field field = JsxHostAdapter.class.getDeclaredField("tree");
        field.setAccessible(true);
        return (JsxHostTree) field.get(fixture.adapter);
    }

    private static JsxHostTree.Node find(List<JsxHostTree.Node> nodes, String id) {
        for (JsxHostTree.Node node : nodes) {
            if (id.equals(node.props.get("id"))) return node;
            JsxHostTree.Node child = find(node.children, id);
            if (child != null) return child;
        }
        return null;
    }

    private static Painter painter() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Painter painter = (Painter) ((Unsafe) field.get(null)).allocateInstance(Painter.class);
        painter.clips = new ArrayDeque<>();
        painter.texts = new ArrayList<>();
        painter.dimClips = new ArrayList<>();
        return painter;
    }

    private record Clip(int left, int top, int right, int bottom) {
        Clip intersect(Clip other) {
            int x = Math.max(left, other.left), y = Math.max(top, other.top);
            return new Clip(x, y, Math.max(x, Math.min(right, other.right)), Math.max(y, Math.min(bottom, other.bottom)));
        }
    }

    private static final class Painter extends GuiGraphicsExtractor {
        ArrayDeque<Clip> clips;
        List<String> texts;
        List<Clip> dimClips;
        int pushes;
        int pops;
        private Painter() { super(null, null, 0, 0); }
        @Override public void enableScissor(int x, int y, int right, int bottom) {
            Clip next = new Clip(x, y, right, bottom);
            clips.push(clips.isEmpty() ? next : clips.peek().intersect(next)); pushes++;
        }
        @Override public void disableScissor() { clips.pop(); pops++; }
        @Override public void fill(int x, int y, int right, int bottom, int color) {
            if (color == 0xA0000000) dimClips.add(clips.peek());
        }
        @Override public void outline(int x, int y, int right, int bottom, int color) { }
        @Override public void text(Font font, String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        @Override public void centeredText(Font font, String text, int x, int y, int color) { texts.add(text); }
        @Override public void setTooltipForNextFrame(Font font, Component text, int x, int y) { }
    }
}
//?}
