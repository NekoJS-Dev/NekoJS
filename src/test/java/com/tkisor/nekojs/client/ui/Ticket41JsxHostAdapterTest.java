//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused ticket 41 checks for retained transactions and the guest-free paint path. */
class Ticket41JsxHostAdapterTest {
    @Test
    void inputEditingDeletesByCodePointAndReplacesSelection() {
        JsxHostTree.Node input = new JsxHostTree.Node(1, "input", null, new java.util.LinkedHashMap<>(Map.of("value", "a😀c")));
        assertTrue(input.deleteBackward());
        assertEquals("a😀", input.inputValue);
        assertTrue(input.deleteBackward());
        assertEquals("a", input.inputValue);
        input.insertText("bc", 20);
        assertEquals("abc", input.inputValue);
        input.selectionStart = 1;
        input.selectionEnd = 3;
        input.insertText("Z", 20);
        assertEquals("aZ", input.inputValue);
        assertEquals(2, input.cursor);
        assertTrue(input.deleteForward() == false);
    }

    @Test
    void scrollHitPrefersTheScrollableAncestorOverItsContent() {
        JsxHostTree.Node scroll = new JsxHostTree.Node(1, "scroll", null, new java.util.LinkedHashMap<>());
        JsxHostTree.Node child = new JsxHostTree.Node(2, "label", null, new java.util.LinkedHashMap<>());
        scroll.x = 10;
        scroll.y = 10;
        scroll.width = 100;
        scroll.height = 50;
        child.x = 10;
        child.y = 10;
        child.width = 100;
        child.height = 20;
        scroll.children.add(child);
        assertEquals(scroll, JsxHostAdapter.scrollHit(List.of(scroll), 20, 20));
        assertEquals(null, JsxHostAdapter.scrollHit(List.of(scroll), 120, 20));
    }

    @Test
    void failedCommitAndRollbackPreserveCommittedTreeAndRemovedHandlesExpire() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object button = initial.create("button", "ok", Map.of("id", "ok"));
        initial.order(null, List.of(button));
        initial.commit(List.of(button));

        JsxHostTree.Transaction failed = tree.begin();
        Object staged = failed.create("label", "staged", Map.of("id", "staged"));
        failed.order(null, List.of(staged));
        assertThrows(IllegalStateException.class, () -> failed.commit(List.of()));
        failed.rollback();
        assertEquals("ok", tree.roots().getFirst().props.get("id"));

        JsxHostTree.Transaction layoutFailure = tree.begin();
        Object next = layoutFailure.create("label", "next", Map.of("id", "next"));
        layoutFailure.order(null, List.of(next));
        assertThrows(IllegalStateException.class, () -> layoutFailure.commit(List.of(next), ignored -> {
            throw new IllegalStateException("layout failed");
        }));
        layoutFailure.rollback();
        assertEquals("ok", tree.roots().getFirst().props.get("id"), "failed layout must not publish staged nodes");

        JsxHostTree.Transaction remove = tree.begin();
        remove.remove(button);
        remove.commit(List.of());
        assertTrue(tree.roots().isEmpty());
        JsxHostTree.Transaction stale = tree.begin();
        assertThrows(IllegalArgumentException.class,
                () -> stale.update(button, "button", "ok", Map.of("id", "ok")));
        stale.rollback();
    }

    @Test
    void failedRootCleanupRetainsHostNodesForRetry() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object node = initial.create("label", "label", Map.of("id", "label"));
        initial.order(null, List.of(node));
        initial.commit(List.of(node));

        assertThrows(IllegalStateException.class, () -> tree.close(() -> { throw new IllegalStateException("close failed"); }));
        assertEquals(1, tree.roots().size(), "failed root cleanup must retain the last host tree");
        tree.close(() -> { });
        assertTrue(tree.roots().isEmpty(), "successful retry releases host nodes");
    }

    @Test
    void screenPaintUsesCommittedHostNodesWithoutGuestRender() throws Exception {
        Path root = sourceRoot();
        String screen = Files.readString(root.resolve("JsxScreen.java"));
        String adapter = Files.readString(root.resolve("JsxHostAdapter.java"));
        String hostTree = Files.readString(root.resolve("JsxHostTree.java"));
        assertTrue(screen.contains("adapter.paintNode"), "paint must consume committed host nodes");
        assertTrue(!screen.contains("render("), "normal paint must not execute guest render");
        assertTrue(screen.contains("adapter.close();"), "Screen removal must use adapter cleanup");
        assertTrue(adapter.contains("tree.close(() ->"), "Screen cleanup must pass root release to the retained tree");
        assertTrue(hostTree.indexOf("closeRoot.run()") < hostTree.indexOf("clear();"), "failed root release must retain nodes");
        assertTrue(adapter.contains("void layout(Object tree, Object viewport, Object snapshot)"),
                "the adapter must match the common three-argument layout contract");
        assertTrue(adapter.contains("root.invokeMember(\"resize\", viewport())"),
                "screen resize must notify the bound common root");
        assertTrue(adapter.contains("graphics.fill(cursorX"),
                "focused inputs must paint an insertion cursor");
    }

    @Test
    void screenTeardownNotifiesTheGuestBeforeEnteringClosing() throws Exception {
        String adapter = Files.readString(sourceRoot().resolve("JsxHostAdapter.java"));
        String body = adapter.substring(adapter.indexOf("private void teardown("));
        int guestClose = body.indexOf("root.invokeMember(\"close\")");
        int beginClose = body.indexOf("lifecycle.beginClose()");
        assertTrue(guestClose > 0 && beginClose > 0, "teardown must notify the guest and enter CLOSING");
        // The common root releases its retained nodes through the same host transaction channel
        // as any other update, so notifyGuest must run while the lifecycle is still usable:
        // closing first made every Screen close report NEKO-7001 and lose the release.
        assertTrue(guestClose < beginClose,
                "the guest release must run before the lifecycle leaves CANDIDATE/ACTIVE");
    }

    @Test
    void narrationFollowsFocusAndFallsBackThroughPropsToPrimitiveName() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction txn = tree.begin();
        Object ok = txn.create("button", "ok", Map.of("text", "Confirm"));
        Object off = txn.create("button", "off", Map.of("text", "Nope", "disabled", true));
        Object named = txn.create("button", "named", Map.of("text", "Raw", "narration", "Explicit label"));
        Object field = txn.create("input", "field", Map.of("placeholder", "Search"));
        Object plain = txn.create("button", "plain", Map.of());
        txn.order(null, List.of(ok, off, named, field, plain));
        txn.commit(List.of(ok, off, named, field, plain));

        List<JsxHostTree.Node> roots = tree.roots();
        // No focus yet: nothing is narrated.
        assertEquals("", JsxHostAdapter.narrationText(roots));

        // text prop is used when no explicit narration is present.
        roots.get(0).focused = true;
        assertEquals("Confirm", JsxHostAdapter.narrationText(roots));
        roots.get(0).focused = false;

        // disabled stays audible rather than silently skipped; the localized marker is
        // composed by JsxScreen (nekojs.gui.narration.disabled), not by this Minecraft-free text.
        roots.get(1).focused = true;
        assertEquals("Nope", JsxHostAdapter.narrationText(roots));
        assertTrue(JsxHostAdapter.narrationDisabled(roots));
        roots.get(1).focused = false;
        assertFalse(JsxHostAdapter.narrationDisabled(roots), "nothing focused means no disabled marker");

        // explicit narration prop wins over text.
        roots.get(2).focused = true;
        assertEquals("Explicit label", JsxHostAdapter.narrationText(roots));
        roots.get(2).focused = false;

        // empty input narrates its placeholder, then its typed value.
        roots.get(3).focused = true;
        assertEquals("Search", JsxHostAdapter.narrationText(roots));
        roots.get(3).inputValue = "abc";
        assertEquals("abc", JsxHostAdapter.narrationText(roots));
        roots.get(3).focused = false;

        // last resort is the primitive name, never an empty announcement.
        roots.get(4).focused = true;
        assertEquals("button", JsxHostAdapter.narrationText(roots));
    }

    private static Path sourceRoot() {
        Path direct = Path.of("src/main/java/com/tkisor/nekojs/client/ui");
        return Files.isDirectory(direct) ? direct : Path.of("../../src/main/java/com/tkisor/nekojs/client/ui");
    }
}
//?}
